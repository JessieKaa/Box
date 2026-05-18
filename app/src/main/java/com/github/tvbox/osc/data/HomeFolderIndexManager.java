package com.github.tvbox.osc.data;

import android.text.TextUtils;

import com.github.tvbox.osc.cache.HomeFolderIndexEntry;
import com.github.tvbox.osc.cache.HomeFolderIndexEntryDao;
import com.github.tvbox.osc.cache.HomeFolderShortcut;
import com.github.tvbox.osc.cache.HomeFolderShortcutDao;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.StorageDriveType;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFolderIndexManager {
    private static final int INSERT_BATCH_SIZE = 200;
    private static final HomeFolderIndexManager INSTANCE = new HomeFolderIndexManager();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Set<Integer> pendingShortcutIds = Collections.synchronizedSet(new HashSet<Integer>());

    public static HomeFolderIndexManager get() {
        return INSTANCE;
    }

    public void rebuildShortcutIndex(final int shortcutId) {
        if (shortcutId <= 0) {
            return;
        }
        synchronized (pendingShortcutIds) {
            if (pendingShortcutIds.contains(shortcutId)) {
                return;
            }
            pendingShortcutIds.add(shortcutId);
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    doRebuildShortcutIndex(shortcutId);
                } finally {
                    pendingShortcutIds.remove(shortcutId);
                }
            }
        });
    }

    public void clearShortcutIndex(final int shortcutId) {
        if (shortcutId <= 0) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HomeFolderShortcutDao shortcutDao = AppDataManager.get().getHomeFolderShortcutDao();
                HomeFolderIndexEntryDao indexEntryDao = AppDataManager.get().getHomeFolderIndexEntryDao();
                HomeFolderShortcut shortcut = shortcutDao.getById(shortcutId);
                if (shortcut == null) {
                    return;
                }
                indexEntryDao.deleteByShortcutId(shortcutId);
                shortcutDao.clearIndexState(shortcutId, HomeFolderShortcut.STATUS_UNINDEXED, null);
                notifyShortcutRefresh();
            }
        });
    }

    public void deleteShortcut(final int shortcutId) {
        if (shortcutId <= 0) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HomeFolderShortcutDao shortcutDao = AppDataManager.get().getHomeFolderShortcutDao();
                HomeFolderShortcut shortcut = shortcutDao.getById(shortcutId);
                if (shortcut == null) {
                    return;
                }
                shortcutDao.deleteById(shortcutId);
                notifyShortcutRefresh();
            }
        });
    }

    private void doRebuildShortcutIndex(int shortcutId) {
        HomeFolderShortcutDao shortcutDao = AppDataManager.get().getHomeFolderShortcutDao();
        HomeFolderIndexEntryDao indexEntryDao = AppDataManager.get().getHomeFolderIndexEntryDao();
        HomeFolderShortcut shortcut = shortcutDao.getById(shortcutId);
        if (shortcut == null) {
            return;
        }

        shortcutDao.updateIndexRunningState(shortcutId, HomeFolderShortcut.STATUS_INDEXING, null);
        notifyShortcutRefresh();

        try {
            File rootDir = new File(shortcut.rootPath);
            if (!rootDir.exists() || !rootDir.isDirectory() || !rootDir.canRead()) {
                throw new IllegalStateException("目录不存在或不可访问");
            }

            final int[] indexedFileCount = new int[]{0};
            AppDataManager.get().runInTransaction(new Runnable() {
                @Override
                public void run() {
                    indexEntryDao.deleteByShortcutId(shortcutId);
                    indexedFileCount[0] = scanShortcut(shortcutId, shortcut.rootPath, rootDir, indexEntryDao);
                    shortcutDao.updateIndexResult(shortcutId, HomeFolderShortcut.STATUS_INDEXED, indexedFileCount[0], System.currentTimeMillis(), null);
                }
            });
            notifyShortcutRefresh();
        } catch (Exception e) {
            String error = TextUtils.isEmpty(e.getMessage()) ? e.toString() : e.getMessage();
            shortcutDao.updateIndexResult(shortcutId, HomeFolderShortcut.STATUS_FAILED, shortcut.indexedFileCount, shortcut.lastIndexedAt, error);
            notifyShortcutRefresh();
        }
    }

    private int scanShortcut(int shortcutId, String rootPath, File rootDir, HomeFolderIndexEntryDao indexEntryDao) {
        List<HomeFolderIndexEntry> buffer = new ArrayList<>(INSERT_BATCH_SIZE);
        ArrayDeque<File> queue = new ArrayDeque<>();
        int indexedCount = 0;
        queue.add(rootDir);
        while (!queue.isEmpty()) {
            File current = queue.removeFirst();
            File[] children = current.listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                if (shouldSkip(child)) {
                    continue;
                }
                if (child.isDirectory()) {
                    queue.addLast(child);
                    continue;
                }
                String fileType = getFileType(child.getName());
                if (!StorageDriveType.isVideoType(fileType)) {
                    continue;
                }
                HomeFolderIndexEntry entry = new HomeFolderIndexEntry();
                entry.shortcutId = shortcutId;
                entry.absolutePath = child.getAbsolutePath();
                entry.relativePath = buildRelativePath(rootPath, entry.absolutePath);
                entry.fileName = child.getName();
                entry.fileType = fileType;
                entry.lastModified = child.lastModified();
                entry.fileSize = child.length();
                buffer.add(entry);
                indexedCount++;
                if (buffer.size() >= INSERT_BATCH_SIZE) {
                    flushEntries(indexEntryDao, buffer);
                }
            }
        }
        flushEntries(indexEntryDao, buffer);
        return indexedCount;
    }

    private void flushEntries(HomeFolderIndexEntryDao indexEntryDao, List<HomeFolderIndexEntry> buffer) {
        if (buffer.isEmpty()) {
            return;
        }
        indexEntryDao.insertAll(buffer);
        buffer.clear();
    }

    private String buildRelativePath(String rootPath, String absolutePath) {
        if (absolutePath.equals(rootPath)) {
            return "";
        }
        String relative = absolutePath.startsWith(rootPath) ? absolutePath.substring(rootPath.length()) : absolutePath;
        while (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }
        return relative;
    }

    private boolean shouldSkip(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        String name = file.getName();
        if (TextUtils.isEmpty(name)) {
            return true;
        }
        if (name.startsWith(".") || file.isHidden()) {
            return true;
        }
        if (file.isDirectory() && !file.canRead()) {
            return true;
        }
        return false;
    }

    private String getFileType(String fileName) {
        int extNameStartIndex = fileName.lastIndexOf(".");
        if (extNameStartIndex < 0 || extNameStartIndex >= fileName.length() - 1) {
            return null;
        }
        return fileName.substring(extNameStartIndex + 1).toUpperCase(Locale.ROOT);
    }

    private void notifyShortcutRefresh() {
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HOME_FOLDER_SHORTCUT_REFRESH));
    }
}

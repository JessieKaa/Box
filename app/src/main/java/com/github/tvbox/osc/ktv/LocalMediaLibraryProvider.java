package com.github.tvbox.osc.ktv;

import android.text.TextUtils;

import com.github.tvbox.osc.cache.KtvMediaSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocalMediaLibraryProvider implements MediaLibraryProvider {
    private final KtvMediaSource source;
    private final File rootDir;

    public LocalMediaLibraryProvider(KtvMediaSource source) {
        this.source = source;
        this.rootDir = new File(source.rootPathOrUrl);
    }

    @Override
    public List<KtvMediaEntry> list(String path) throws Exception {
        File dir = TextUtils.isEmpty(path) ? rootDir : new File(rootDir, path);
        if (!dir.exists()) {
            throw new IllegalStateException("目录不存在: " + dir.getAbsolutePath());
        }
        if (!dir.isDirectory()) {
            throw new IllegalStateException("目录不可访问: " + dir.getAbsolutePath());
        }
        List<KtvMediaEntry> items = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) {
            throw new IllegalStateException("目录读取失败: " + dir.getAbsolutePath());
        }
        for (File file : files) {
            String fileType = null;
            int index = file.getName().lastIndexOf('.');
            if (file.isFile() && index > 0 && index < file.getName().length() - 1) {
                fileType = file.getName().substring(index + 1).toUpperCase(Locale.ROOT);
            }
            String relativePath = buildRelativePath(path, file.getName());
            items.add(new KtvMediaEntry(
                    relativePath,
                    file.getName(),
                    file.isFile(),
                    fileType,
                    file.lastModified(),
                    file.length()
            ));
        }
        return items;
    }

    @Override
    public String resolvePlayableUrl(KtvMediaEntry entry) {
        return new File(rootDir, entry.path).getAbsolutePath();
    }

    @Override
    public boolean supportsRecursiveScan() {
        return true;
    }

    private String buildRelativePath(String parent, String name) {
        if (TextUtils.isEmpty(parent)) {
            return name;
        }
        return parent + File.separator + name;
    }
}

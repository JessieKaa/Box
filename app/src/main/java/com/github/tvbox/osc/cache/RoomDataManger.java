package com.github.tvbox.osc.cache;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.KtvMediaSource;
import com.github.tvbox.osc.cache.KtvQueueItem;
import com.github.tvbox.osc.cache.KtvSong;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.data.HomeFolderIndexManager;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ktv.KtvMediaSourceType;
import com.github.tvbox.osc.util.StorageDriveType;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
public class RoomDataManger {
    static ExclusionStrategy vodInfoStrategy = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesFlags")) {
                return true;
            }
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesMap")) {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    };

    private static Gson getVodInfoGson() {
        return new GsonBuilder().addSerializationExclusionStrategy(vodInfoStrategy).create();
    }

    public static void insertVodRecord(String sourceKey, VodInfo vodInfo) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
        if (record == null) {
            record = new VodRecord();
        }
        record.sourceKey = sourceKey;
        record.vodId = vodInfo.id;
        record.updateTime = System.currentTimeMillis();
        record.dataJson = getVodInfoGson().toJson(vodInfo);
        AppDataManager.get().getVodRecordDao().insert(record);
    }

    public static VodInfo getVodInfo(String sourceKey, String vodId) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodId);
        try {
            if (record != null && record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                VodInfo vodInfo = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                }.getType());
                if (vodInfo.name == null)
                    return null;
                return vodInfo;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void deleteVodRecord(String sourceKey, VodInfo vodInfo) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
        if (record != null) {
            AppDataManager.get().getVodRecordDao().delete(record);
        }
    }

    public static List<VodInfo> getAllVodRecord(int limit) {
        // 历史记录超过60条时, 删除最旧的数据 只保留50条.
        int count = AppDataManager.get().getVodRecordDao().getCount();
        //if ( count > 60 ) {
        //    AppDataManager.get().getVodRecordDao().reserver(50);
        //}
        Integer index = Hawk.get(HawkConfig.HOME_NUM, 0);
        Integer hisNum = HistoryHelper.getHisNum(index);
        if ( count > hisNum ) {
            AppDataManager.get().getVodRecordDao().reserver(hisNum);
        }

        List<VodRecord> recordList = AppDataManager.get().getVodRecordDao().getAll(limit);
        List<VodInfo> vodInfoList = new ArrayList<>();
        if (recordList != null) {
            for (VodRecord record : recordList) {
                VodInfo info = null;
                try {
                    if (record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                        info = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                        }.getType());
                        info.sourceKey = record.sourceKey;
                        SourceBean sourceBean = ApiConfig.get().getSource(info.sourceKey);
                        if (sourceBean == null || info.name == null)
                            info = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (info != null)
                    vodInfoList.add(info);
            }
        }
        return vodInfoList;
    }

    public static void insertVodCollect(String sourceKey, VodInfo vodInfo) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
        if (record != null) {
            return;
        }
        record = new VodCollect();
        record.sourceKey = sourceKey;
        record.vodId = vodInfo.id;
        record.updateTime = System.currentTimeMillis();
        record.name = vodInfo.name;
        record.pic = vodInfo.pic;
        AppDataManager.get().getVodCollectDao().insert(record);
    }

    public static void deleteVodCollect(int id) {
        AppDataManager.get().getVodCollectDao().delete(id);
    }

    public static void deleteVodCollect(String sourceKey, VodInfo vodInfo) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
        if (record != null) {
            AppDataManager.get().getVodCollectDao().delete(record);
        }
    }

    public static void deleteVodCollectAll() {
        AppDataManager.get().getVodCollectDao().deleteAll();
    }

    public static void deleteVodRecordAll() {
        AppDataManager.get().getVodRecordDao().deleteAll();
    }

    public static boolean isVodCollect(String sourceKey, String vodId) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodId);
        return record != null;
    }
    public static List<VodCollect> getAllVodCollect() {
        return AppDataManager.get().getVodCollectDao().getAll();
    }

    public static long insertKtvMediaSource(KtvMediaSource source) {
        return AppDataManager.get().getKtvMediaSourceDao().insert(source);
    }

    public static List<KtvMediaSource> getAllKtvMediaSources() {
        return AppDataManager.get().getKtvMediaSourceDao().getAll();
    }

    public static KtvMediaSource getKtvMediaSource(int id) {
        return AppDataManager.get().getKtvMediaSourceDao().getById(id);
    }

    public static List<KtvMediaSource> getKtvMediaSources(String type, String rootPathOrUrl) {
        return AppDataManager.get().getKtvMediaSourceDao().getByTypeAndRootPathOrUrl(type, rootPathOrUrl);
    }

    public static List<KtvMediaSource> getKtvMediaSourcesByRootOrChildren(String type, String rootPathOrUrl) {
        return AppDataManager.get().getKtvMediaSourceDao().getByTypeAndRootPathOrUrlOrChildren(
                type,
                rootPathOrUrl,
                escapeSqlLikeKeyword(rootPathOrUrl) + "/%"
        );
    }

    public static void deleteKtvMediaSource(int id) {
        AppDataManager.get().getKtvMediaSourceDao().deleteById(id);
        AppDataManager.get().getKtvSongDao().deleteBySourceId(id);
    }

    public static void deleteKtvMediaSourcesByRootOrChildren(String type, String rootPathOrUrl) {
        List<KtvMediaSource> sources = getKtvMediaSourcesByRootOrChildren(type, normalizeKtvSourcePath(type, rootPathOrUrl));
        if (sources == null || sources.isEmpty()) {
            return;
        }
        for (KtvMediaSource source : sources) {
            deleteKtvMediaSource(source.getId());
        }
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_KTV_SOURCE_REFRESH));
    }

    public static long insertKtvSong(KtvSong song) {
        return AppDataManager.get().getKtvSongDao().insert(song);
    }

    public static long[] insertKtvSongs(List<KtvSong> songs) {
        return AppDataManager.get().getKtvSongDao().insertAll(songs);
    }

    public static List<KtvSong> getKtvSongs(int sourceId) {
        if (sourceId <= 0) {
            return AppDataManager.get().getKtvSongDao().getAll();
        }
        return AppDataManager.get().getKtvSongDao().getBySourceId(sourceId);
    }

    public static List<KtvSong> searchKtvSongs(int sourceId, String keyword) {
        String normalized = "%" + escapeSqlLikeKeyword(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        if (sourceId > 0) {
            return AppDataManager.get().getKtvSongDao().searchByKeyword(sourceId, normalized);
        }
        return AppDataManager.get().getKtvSongDao().searchAll(normalized);
    }

    public static KtvSong getKtvSong(int id) {
        return AppDataManager.get().getKtvSongDao().getById(id);
    }

    public static void clearKtvSongsBySource(int sourceId) {
        AppDataManager.get().getKtvSongDao().deleteBySourceId(sourceId);
    }

    public static long insertKtvQueueItem(KtvQueueItem item) {
        return AppDataManager.get().getKtvQueueItemDao().insert(item);
    }

    public static long[] insertKtvQueueItems(List<KtvQueueItem> items) {
        return AppDataManager.get().getKtvQueueItemDao().insertAll(items);
    }

    public static List<KtvQueueItem> getAllKtvQueueItems() {
        return AppDataManager.get().getKtvQueueItemDao().getAll();
    }

    public static KtvQueueItem getCurrentKtvQueueItem() {
        return AppDataManager.get().getKtvQueueItemDao().getFirstByStatus(KtvQueueItem.STATUS_PLAYING);
    }

    public static KtvQueueItem getKtvQueueItem(int id) {
        return AppDataManager.get().getKtvQueueItemDao().getById(id);
    }

    public static List<KtvQueueItem> getPendingKtvQueueItems() {
        return AppDataManager.get().getKtvQueueItemDao().getByStatus(KtvQueueItem.STATUS_PENDING);
    }

    public static KtvQueueItem getNextPendingKtvQueueItem() {
        return AppDataManager.get().getKtvQueueItemDao().getFirstByStatus(KtvQueueItem.STATUS_PENDING);
    }

    public static long getNextKtvQueueOrder() {
        Long max = AppDataManager.get().getKtvQueueItemDao().getMaxQueueOrder();
        return max == null ? 0L : max + 1L;
    }

    public static void markKtvQueueItemStatus(int id, String status) {
        AppDataManager.get().getKtvQueueItemDao().updateStatus(id, status);
    }

    public static void markAllPlayingAsDone() {
        AppDataManager.get().getKtvQueueItemDao().updateStatusByOldStatus(KtvQueueItem.STATUS_PLAYING, KtvQueueItem.STATUS_DONE);
    }

    public static void clearKtvPendingQueue() {
        AppDataManager.get().getKtvQueueItemDao().deleteByStatus(KtvQueueItem.STATUS_PENDING);
    }

    public static void deleteKtvQueueItem(int id) {
        AppDataManager.get().getKtvQueueItemDao().deleteById(id);
    }

    public static void clearKtvQueue() {
        AppDataManager.get().getKtvQueueItemDao().deleteAll();
    }

    public static void insertDriveRecord(@NonNull String name, @NonNull StorageDriveType.TYPE type, JsonObject config) {
        StorageDrive drive = new StorageDrive();
        drive.name = name;
        drive.type = type.ordinal();
        drive.configJson = config == null ? null : config.toString();
        AppDataManager.get().getStorageDriveDao().insert(drive);
    }

    public static void updateDriveRecord(@NonNull StorageDrive drive) {
        AppDataManager.get().getStorageDriveDao().insert(drive);
    }

    public static List<StorageDrive> getAllDrives() {
        return AppDataManager.get().getStorageDriveDao().getAll();
    }

    public static void deleteDrive(int id) {
        AppDataManager.get().getStorageDriveDao().delete(id);
    }

    public static List<HomeFolderShortcut> getAllHomeFolderShortcuts() {
        return AppDataManager.get().getHomeFolderShortcutDao().getAll();
    }

    public static HomeFolderShortcut getHomeFolderShortcut(int shortcutId) {
        return AppDataManager.get().getHomeFolderShortcutDao().getById(shortcutId);
    }

    public static HomeFolderShortcut getHomeFolderShortcutByRootPath(@NonNull String rootPath) {
        String normalizedPath = normalizeLocalRootPath(rootPath);
        if (TextUtils.isEmpty(normalizedPath)) {
            return null;
        }
        return AppDataManager.get().getHomeFolderShortcutDao().getByRootPath(normalizedPath);
    }

    public static HomeFolderShortcut insertHomeFolderShortcut(@NonNull String rootPath, String preferredName) {
        String normalizedPath = normalizeLocalRootPath(rootPath);
        if (TextUtils.isEmpty(normalizedPath)) {
            return null;
        }
        HomeFolderShortcut existing = AppDataManager.get().getHomeFolderShortcutDao().getByRootPath(normalizedPath);
        if (existing != null) {
            return existing;
        }
        HomeFolderShortcut shortcut = new HomeFolderShortcut();
        shortcut.rootPath = normalizedPath;
        shortcut.name = buildShortcutName(normalizedPath, preferredName);
        shortcut.sortOrder = AppDataManager.get().getHomeFolderShortcutDao().getMaxSortOrder() + 1;
        shortcut.indexStatus = HomeFolderShortcut.STATUS_UNINDEXED;
        long shortcutId = AppDataManager.get().getHomeFolderShortcutDao().insert(shortcut);
        shortcut.setId((int) shortcutId);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HOME_FOLDER_SHORTCUT_REFRESH));
        return shortcut;
    }

    public static void deleteHomeFolderShortcut(int shortcutId) {
        HomeFolderIndexManager.get().deleteShortcut(shortcutId);
    }

    public static List<HomeFolderIndexEntry> searchShortcutIndex(int shortcutId, String keyword) {
        if (shortcutId <= 0 || TextUtils.isEmpty(keyword)) {
            return new ArrayList<>();
        }
        return AppDataManager.get().getHomeFolderIndexEntryDao().searchByKeyword(shortcutId, escapeSqlLikeKeyword(keyword.trim()));
    }

    public static void rebuildShortcutIndex(int shortcutId) {
        HomeFolderIndexManager.get().rebuildShortcutIndex(shortcutId);
    }

    public static void clearShortcutIndex(int shortcutId) {
        HomeFolderIndexManager.get().clearShortcutIndex(shortcutId);
    }

    public static String normalizeLocalRootPath(String rootPath) {
        if (TextUtils.isEmpty(rootPath)) {
            return null;
        }
        String normalizedPath;
        try {
            normalizedPath = new File(rootPath).getCanonicalPath();
        } catch (Exception e) {
            normalizedPath = new File(rootPath).getAbsolutePath();
        }
        if (normalizedPath.length() > 1 && normalizedPath.endsWith(File.separator)) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }
        return normalizedPath;
    }

    public static String normalizeKtvSourcePath(String type, String rootPathOrUrl) {
        if (TextUtils.isEmpty(rootPathOrUrl)) {
            return "";
        }
        if (KtvMediaSourceType.WEBDAV.name().equals(type)) {
            return normalizeKtvWebDavRootUrl(rootPathOrUrl);
        }
        String normalized = normalizeLocalRootPath(rootPathOrUrl);
        return TextUtils.isEmpty(normalized) ? "" : normalized;
    }

    public static String normalizeKtvWebDavRootUrl(String rootUrl) {
        if (TextUtils.isEmpty(rootUrl)) {
            return "";
        }
        String normalized = rootUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static void syncKtvWebDavSourceConfig(String oldRootUrl, String newRootUrl, String displayName, String configJson) {
        String normalizedOldRootUrl = normalizeKtvSourcePath(KtvMediaSourceType.WEBDAV.name(), oldRootUrl);
        String normalizedNewRootUrl = normalizeKtvSourcePath(KtvMediaSourceType.WEBDAV.name(), newRootUrl);
        if (TextUtils.isEmpty(normalizedNewRootUrl)) {
            return;
        }
        String lookupRootUrl = TextUtils.isEmpty(normalizedOldRootUrl) ? normalizedNewRootUrl : normalizedOldRootUrl;
        List<KtvMediaSource> sources = getKtvMediaSourcesByRootOrChildren(KtvMediaSourceType.WEBDAV.name(), lookupRootUrl);
        if (sources == null || sources.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (KtvMediaSource source : sources) {
            boolean needsUpdate = false;
            String originalRootPathOrUrl = normalizeKtvSourcePath(source.type, source.rootPathOrUrl);
            String updatedRootPathOrUrl = originalRootPathOrUrl;
            if (!TextUtils.isEmpty(normalizedOldRootUrl)
                    && !TextUtils.isEmpty(originalRootPathOrUrl)
                    && (originalRootPathOrUrl.equals(normalizedOldRootUrl) || originalRootPathOrUrl.startsWith(normalizedOldRootUrl + "/"))) {
                String suffix = originalRootPathOrUrl.substring(normalizedOldRootUrl.length());
                updatedRootPathOrUrl = normalizedNewRootUrl + suffix;
                source.rootPathOrUrl = updatedRootPathOrUrl;
                needsUpdate = true;
            }
            if (!TextUtils.isEmpty(displayName)
                    && TextUtils.equals(updatedRootPathOrUrl, normalizedNewRootUrl)
                    && !TextUtils.equals(displayName, source.displayName)) {
                source.displayName = displayName;
                needsUpdate = true;
            }
            if (!TextUtils.equals(configJson, source.configJson)) {
                source.configJson = configJson;
                needsUpdate = true;
            }
            if (needsUpdate) {
                insertKtvMediaSource(source);
                changed = true;
            }
        }
        if (changed) {
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_KTV_SOURCE_REFRESH));
        }
    }

    public static void rescanKtvWebDavSources(String oldRootUrl, String newRootUrl) {
        String normalizedOldRootUrl = normalizeKtvSourcePath(KtvMediaSourceType.WEBDAV.name(), oldRootUrl);
        String normalizedNewRootUrl = normalizeKtvSourcePath(KtvMediaSourceType.WEBDAV.name(), newRootUrl);
        if (TextUtils.isEmpty(normalizedNewRootUrl)) {
            return;
        }
        String lookupRootUrl = TextUtils.isEmpty(normalizedOldRootUrl) ? normalizedNewRootUrl : normalizedOldRootUrl;
        List<KtvMediaSource> sources = getKtvMediaSourcesByRootOrChildren(KtvMediaSourceType.WEBDAV.name(), lookupRootUrl);
        if (sources == null || sources.isEmpty()) {
            return;
        }
        for (KtvMediaSource source : sources) {
            String originalRootPathOrUrl = normalizeKtvSourcePath(source.type, source.rootPathOrUrl);
            if (TextUtils.isEmpty(originalRootPathOrUrl)) {
                continue;
            }
            if (TextUtils.isEmpty(normalizedOldRootUrl)
                    || originalRootPathOrUrl.equals(normalizedNewRootUrl)
                    || originalRootPathOrUrl.startsWith(normalizedNewRootUrl + "/")) {
                com.github.tvbox.osc.ktv.KtvLibraryIndexManager.get().scan(source, null);
            }
        }
    }

    public static String buildShortcutName(String rootPath, String preferredName) {
        if (!TextUtils.isEmpty(preferredName)) {
            return preferredName.trim();
        }
        File file = new File(rootPath);
        String name = file.getName();
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        return rootPath;
    }

    private static String escapeSqlLikeKeyword(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

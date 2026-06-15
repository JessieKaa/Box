package com.github.tvbox.osc.cache;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.util.StorageDriveType;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

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

    // ======================== Karaoke: history & favorites ========================

    public static void insertKaraokeHistory(com.github.tvbox.osc.karaoke.bean.KaraokeSong song, long playbackPosition) {
        try {
            KaraokeHistory existing = AppDataManager.get().getKaraokeHistoryDao().getByPath(song.filePath);
            KaraokeHistory record = existing != null ? existing : new KaraokeHistory();
            record.filePath = song.filePath;
            record.title = song.title;
            record.artist = song.artist;
            record.displayName = song.displayName;
            record.fileSize = song.fileSize;
            record.lastModified = song.lastModified;
            record.duration = song.duration;
            record.playedAt = System.currentTimeMillis();
            record.playbackPosition = playbackPosition;
            AppDataManager.get().getKaraokeHistoryDao().insert(record);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateKaraokePlaybackPosition(String filePath, long playbackPosition) {
        try {
            KaraokeHistory record = AppDataManager.get().getKaraokeHistoryDao().getByPath(filePath);
            if (record != null) {
                record.playbackPosition = playbackPosition;
                record.playedAt = System.currentTimeMillis();
                AppDataManager.get().getKaraokeHistoryDao().insert(record);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static KaraokeHistory getKaraokeHistory(String filePath) {
        try {
            return AppDataManager.get().getKaraokeHistoryDao().getByPath(filePath);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<KaraokeHistory> getKaraokeHistory(int limit) {
        try {
            return AppDataManager.get().getKaraokeHistoryDao().getAll(limit);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static boolean isKaraokeFavorite(String filePath) {
        try {
            return AppDataManager.get().getKaraokeFavoriteDao().getByPath(filePath) != null;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<KaraokeFavorite> getKaraokeFavorites() {
        try {
            return AppDataManager.get().getKaraokeFavoriteDao().getAll();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /** Returns true if the song is now a favorite, false if it was removed. */
    public static boolean toggleKaraokeFavorite(com.github.tvbox.osc.karaoke.bean.KaraokeSong song) {
        try {
            KaraokeFavoriteDao dao = AppDataManager.get().getKaraokeFavoriteDao();
            if (dao.getByPath(song.filePath) != null) {
                dao.removeByPath(song.filePath);
                return false;
            }
            KaraokeFavorite record = new KaraokeFavorite();
            record.filePath = song.filePath;
            record.title = song.title;
            record.artist = song.artist;
            record.displayName = song.displayName;
            record.fileSize = song.fileSize;
            record.lastModified = song.lastModified;
            record.duration = song.duration;
            record.favoritedAt = System.currentTimeMillis();
            dao.insert(record);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return song != null && isKaraokeFavorite(song.filePath);
        }
    }
}
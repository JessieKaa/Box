package com.github.tvbox.osc.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.github.tvbox.osc.cache.Cache;
import com.github.tvbox.osc.cache.CacheDao;
import com.github.tvbox.osc.cache.KaraokeFavorite;
import com.github.tvbox.osc.cache.KaraokeFavoriteDao;
import com.github.tvbox.osc.cache.KaraokeHistory;
import com.github.tvbox.osc.cache.KaraokeHistoryDao;
import com.github.tvbox.osc.cache.SearchDao;
import com.github.tvbox.osc.cache.SearchHistory;
import com.github.tvbox.osc.cache.StorageDrive;
import com.github.tvbox.osc.cache.StorageDriveDao;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.cache.VodCollectDao;
import com.github.tvbox.osc.cache.VodRecord;
import com.github.tvbox.osc.cache.VodRecordDao;

@Database(entities = {Cache.class, VodRecord.class, VodCollect.class, StorageDrive.class,
        SearchHistory.class, KaraokeHistory.class, KaraokeFavorite.class}, version = 5)
public abstract class AppDataBase extends RoomDatabase {
    public abstract CacheDao getCacheDao();

    public abstract VodRecordDao getVodRecordDao();

    public abstract VodCollectDao getVodCollectDao();

    public abstract StorageDriveDao getStorageDriveDao();

    public abstract SearchDao getSearchDao();

    public abstract KaraokeHistoryDao getKaraokeHistoryDao();

    public abstract KaraokeFavoriteDao getKaraokeFavoriteDao();
}

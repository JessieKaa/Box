package com.github.tvbox.osc.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.github.tvbox.osc.cache.Cache;
import com.github.tvbox.osc.cache.CacheDao;
import com.github.tvbox.osc.cache.KtvMediaSource;
import com.github.tvbox.osc.cache.KtvMediaSourceDao;
import com.github.tvbox.osc.cache.KtvQueueItem;
import com.github.tvbox.osc.cache.KtvQueueItemDao;
import com.github.tvbox.osc.cache.KtvSong;
import com.github.tvbox.osc.cache.KtvSongDao;
import com.github.tvbox.osc.cache.HomeFolderIndexEntry;
import com.github.tvbox.osc.cache.HomeFolderIndexEntryDao;
import com.github.tvbox.osc.cache.HomeFolderShortcut;
import com.github.tvbox.osc.cache.HomeFolderShortcutDao;
import com.github.tvbox.osc.cache.SearchDao;
import com.github.tvbox.osc.cache.SearchHistory;
import com.github.tvbox.osc.cache.StorageDrive;
import com.github.tvbox.osc.cache.StorageDriveDao;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.cache.VodCollectDao;
import com.github.tvbox.osc.cache.VodRecord;
import com.github.tvbox.osc.cache.VodRecordDao;


/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
@Database(entities = {Cache.class, VodRecord.class, VodCollect.class, StorageDrive.class, SearchHistory.class, HomeFolderShortcut.class, HomeFolderIndexEntry.class, KtvMediaSource.class, KtvSong.class, KtvQueueItem.class}, version = 5)
public abstract class AppDataBase extends RoomDatabase {
    public abstract CacheDao getCacheDao();

    public abstract VodRecordDao getVodRecordDao();

    public abstract VodCollectDao getVodCollectDao();

    public abstract StorageDriveDao getStorageDriveDao();

    public abstract SearchDao getSearchDao();

    public abstract HomeFolderShortcutDao getHomeFolderShortcutDao();

    public abstract HomeFolderIndexEntryDao getHomeFolderIndexEntryDao();

    public abstract KtvMediaSourceDao getKtvMediaSourceDao();

    public abstract KtvSongDao getKtvSongDao();

    public abstract KtvQueueItemDao getKtvQueueItemDao();
}

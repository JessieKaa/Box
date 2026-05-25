package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface KtvQueueItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(KtvQueueItem item);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertAll(List<KtvQueueItem> items);

    @Query("select * from ktvQueueItem order by queueOrder asc, id asc")
    List<KtvQueueItem> getAll();

    @Query("select * from ktvQueueItem where status=:status order by queueOrder asc, id asc limit 1")
    KtvQueueItem getFirstByStatus(String status);

    @Query("select * from ktvQueueItem where status=:status order by queueOrder asc, id asc")
    List<KtvQueueItem> getByStatus(String status);

    @Query("select * from ktvQueueItem where status='PLAYING' order by queueOrder asc, id asc limit 1")
    KtvQueueItem getCurrentPlaying();

    @Query("select * from ktvQueueItem where id=:id limit 1")
    KtvQueueItem getById(int id);

    @Query("select max(queueOrder) from ktvQueueItem")
    Long getMaxQueueOrder();

    @Query("delete from ktvQueueItem where id=:id")
    int deleteById(int id);

    @Delete
    int delete(KtvQueueItem item);

    @Query("update ktvQueueItem set status=:status where id=:id")
    int updateStatus(int id, String status);

    @Query("update ktvQueueItem set status=:status where status=:oldStatus")
    int updateStatusByOldStatus(String oldStatus, String status);

    @Query("delete from ktvQueueItem where status=:status")
    int deleteByStatus(String status);

    @Query("delete from ktvQueueItem")
    void deleteAll();
}

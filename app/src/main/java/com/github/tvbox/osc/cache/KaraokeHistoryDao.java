package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface KaraokeHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(KaraokeHistory record);

    @Query("SELECT * FROM karaokeHistory ORDER BY playedAt DESC LIMIT :limit")
    List<KaraokeHistory> getAll(int limit);

    @Query("SELECT * FROM karaokeHistory WHERE filePath = :filePath LIMIT 1")
    KaraokeHistory getByPath(String filePath);

    @Query("SELECT * FROM karaokeHistory WHERE identityKey = :key LIMIT 1")
    KaraokeHistory getByIdentityKey(String key);

    @Query("DELETE FROM karaokeHistory WHERE filePath = :filePath")
    int deleteByPath(String filePath);

    @Query("DELETE FROM karaokeHistory")
    void clearAll();
}

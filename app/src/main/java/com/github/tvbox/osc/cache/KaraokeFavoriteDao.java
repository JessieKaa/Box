package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface KaraokeFavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(KaraokeFavorite record);

    @Query("SELECT * FROM karaokeFavorite ORDER BY favoritedAt DESC")
    List<KaraokeFavorite> getAll();

    @Query("SELECT * FROM karaokeFavorite WHERE filePath = :filePath LIMIT 1")
    KaraokeFavorite getByPath(String filePath);

    @Query("DELETE FROM karaokeFavorite WHERE filePath = :filePath")
    int removeByPath(String filePath);

    @Query("DELETE FROM karaokeFavorite")
    void clearAll();
}

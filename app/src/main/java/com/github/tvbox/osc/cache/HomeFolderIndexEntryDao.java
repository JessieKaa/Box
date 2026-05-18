package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HomeFolderIndexEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<HomeFolderIndexEntry> entries);

    @Query("delete from homeFolderIndexEntry where shortcutId=:shortcutId")
    void deleteByShortcutId(int shortcutId);

    @Query("select * from homeFolderIndexEntry where shortcutId=:shortcutId and fileName like '%' || :keyword || '%' escape '\\' order by fileName collate nocase asc, relativePath collate nocase asc")
    List<HomeFolderIndexEntry> searchByKeyword(int shortcutId, String keyword);
}

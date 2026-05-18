package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HomeFolderShortcutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(HomeFolderShortcut shortcut);

    @Query("select * from homeFolderShortcut order by sortOrder asc, id asc")
    List<HomeFolderShortcut> getAll();

    @Query("select * from homeFolderShortcut where id=:shortcutId limit 1")
    HomeFolderShortcut getById(int shortcutId);

    @Query("select * from homeFolderShortcut where rootPath=:rootPath limit 1")
    HomeFolderShortcut getByRootPath(String rootPath);

    @Query("select COALESCE(MAX(sortOrder), -1) from homeFolderShortcut")
    int getMaxSortOrder();

    @Query("delete from homeFolderShortcut where id=:shortcutId")
    void deleteById(int shortcutId);

    @Query("update homeFolderShortcut set indexStatus=:indexStatus, lastError=:lastError where id=:shortcutId")
    void updateIndexRunningState(int shortcutId, int indexStatus, String lastError);

    @Query("update homeFolderShortcut set indexStatus=:indexStatus, indexedFileCount=:indexedFileCount, lastIndexedAt=:lastIndexedAt, lastError=:lastError where id=:shortcutId")
    void updateIndexResult(int shortcutId, int indexStatus, int indexedFileCount, long lastIndexedAt, String lastError);

    @Query("update homeFolderShortcut set indexStatus=:indexStatus, indexedFileCount=0, lastIndexedAt=0, lastError=:lastError where id=:shortcutId")
    void clearIndexState(int shortcutId, int indexStatus, String lastError);
}

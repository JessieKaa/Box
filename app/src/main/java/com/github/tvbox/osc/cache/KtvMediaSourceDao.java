package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface KtvMediaSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(KtvMediaSource source);

    @Query("select * from ktvMediaSource order by id asc")
    List<KtvMediaSource> getAll();

    @Query("select * from ktvMediaSource where id=:id limit 1")
    KtvMediaSource getById(int id);

    @Query("select * from ktvMediaSource where type=:type and rootPathOrUrl=:rootPathOrUrl")
    List<KtvMediaSource> getByTypeAndRootPathOrUrl(String type, String rootPathOrUrl);

    @Query("select * from ktvMediaSource where type=:type and (rootPathOrUrl=:rootPathOrUrl or rootPathOrUrl like :childPathPattern escape '\\')")
    List<KtvMediaSource> getByTypeAndRootPathOrUrlOrChildren(String type, String rootPathOrUrl, String childPathPattern);

    @Delete
    int delete(KtvMediaSource source);

    @Query("delete from ktvMediaSource where id=:id")
    int deleteById(int id);
}

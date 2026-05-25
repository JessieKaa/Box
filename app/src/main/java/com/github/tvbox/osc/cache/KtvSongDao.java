package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface KtvSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(KtvSong song);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertAll(List<KtvSong> songs);

    @Query("select * from ktvSong order by lower(title), lower(artist), lower(fileName)")
    List<KtvSong> getAll();

    @Query("select * from ktvSong where sourceId=:sourceId order by lower(title), lower(artist), lower(fileName)")
    List<KtvSong> getBySourceId(int sourceId);

    @Query("select * from ktvSong where sourceId=:sourceId and (title like :keyword escape '\\' or artist like :keyword escape '\\' or fileName like :keyword escape '\\' or searchText like :keyword escape '\\') order by lower(title), lower(artist), lower(fileName)")
    List<KtvSong> searchByKeyword(int sourceId, String keyword);

    @Query("select * from ktvSong where title like :keyword escape '\\' or artist like :keyword escape '\\' or fileName like :keyword escape '\\' or searchText like :keyword escape '\\' order by lower(title), lower(artist), lower(fileName)")
    List<KtvSong> searchAll(String keyword);

    @Query("delete from ktvSong where sourceId=:sourceId")
    int deleteBySourceId(int sourceId);

    @Query("select * from ktvSong where id=:id limit 1")
    KtvSong getById(int id);

    @Delete
    int delete(KtvSong song);
}

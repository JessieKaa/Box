package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "karaokeFavorite", indices = {@Index(value = "filePath", unique = true)})
public class KaraokeFavorite implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "filePath")
    public String filePath;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "artist")
    public String artist;

    @ColumnInfo(name = "displayName")
    public String displayName;

    @ColumnInfo(name = "fileSize")
    public long fileSize;

    @ColumnInfo(name = "lastModified")
    public long lastModified;

    @ColumnInfo(name = "duration")
    public long duration;

    @ColumnInfo(name = "favoritedAt")
    public long favoritedAt;
}

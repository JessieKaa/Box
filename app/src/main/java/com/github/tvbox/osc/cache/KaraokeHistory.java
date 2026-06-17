package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "karaokeHistory", indices = {@Index(value = "identityKey", unique = true)})
public class KaraokeHistory implements Serializable {
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

    @ColumnInfo(name = "playedAt")
    public long playedAt;

    @ColumnInfo(name = "playbackPosition")
    public long playbackPosition;

    @ColumnInfo(name = "identityKey")
    public String identityKey;

    @ColumnInfo(name = "trackId")
    public String trackId;

    @ColumnInfo(name = "sourceType")
    public String sourceType;

    @ColumnInfo(name = "streamUrl")
    public String streamUrl;

    @ColumnInfo(name = "artworkUrl")
    public String artworkUrl;
}

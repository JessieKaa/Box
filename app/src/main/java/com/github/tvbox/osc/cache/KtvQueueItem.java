package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "ktvQueueItem", indices = {
        @Index("songId"),
        @Index("queueOrder"),
        @Index("status")
})
public class KtvQueueItem {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PLAYING = "PLAYING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    @PrimaryKey(autoGenerate = true)
    private int id;
    @ColumnInfo(name = "songId")
    public int songId;
    @ColumnInfo(name = "songTitle")
    public String songTitle;
    @ColumnInfo(name = "artist")
    public String artist;
    @ColumnInfo(name = "playUrl")
    public String playUrl;
    @ColumnInfo(name = "sourceType")
    public String sourceType;
    @ColumnInfo(name = "sourcePath")
    public String sourcePath;
    @ColumnInfo(name = "queueOrder")
    public long queueOrder;
    @ColumnInfo(name = "status")
    public String status;
    @ColumnInfo(name = "createdAt")
    public long createdAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}

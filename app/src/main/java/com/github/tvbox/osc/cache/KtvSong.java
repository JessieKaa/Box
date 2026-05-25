package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "ktvSong", indices = {
        @Index("sourceId"),
        @Index("fileName"),
        @Index("title"),
        @Index("artist")
})
public class KtvSong {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @ColumnInfo(name = "sourceId")
    public int sourceId;
    @ColumnInfo(name = "sourceType")
    public String sourceType;
    @ColumnInfo(name = "filePath")
    public String filePath;
    @ColumnInfo(name = "playUrl")
    public String playUrl;
    @ColumnInfo(name = "fileName")
    public String fileName;
    @ColumnInfo(name = "title")
    public String title;
    @ColumnInfo(name = "artist")
    public String artist;
    @ColumnInfo(name = "searchText")
    public String searchText;
    @ColumnInfo(name = "initials")
    public String initials;
    @ColumnInfo(name = "lastModified")
    public long lastModified;
    @ColumnInfo(name = "fileSize")
    public long fileSize;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}

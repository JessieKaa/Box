package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ktvMediaSource")
public class KtvMediaSource {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @ColumnInfo(name = "type")
    public String type;
    @ColumnInfo(name = "displayName")
    public String displayName;
    @ColumnInfo(name = "rootPathOrUrl")
    public String rootPathOrUrl;
    @ColumnInfo(name = "configJson")
    public String configJson;
    @ColumnInfo(name = "enabled")
    public int enabled;
    @ColumnInfo(name = "scanStatus")
    public String scanStatus;
    @ColumnInfo(name = "scanError")
    public String scanError;
    @ColumnInfo(name = "lastScanAt")
    public long lastScanAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}

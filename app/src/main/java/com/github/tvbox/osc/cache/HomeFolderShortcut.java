package com.github.tvbox.osc.cache;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "homeFolderShortcut", indices = {
        @Index(value = {"rootPath"}, unique = true),
        @Index(value = {"sortOrder"})
})
public class HomeFolderShortcut {
    public static final int STATUS_UNINDEXED = 0;
    public static final int STATUS_INDEXING = 1;
    public static final int STATUS_INDEXED = 2;
    public static final int STATUS_FAILED = 3;

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "name")
    public String name;

    @NonNull
    @ColumnInfo(name = "rootPath")
    public String rootPath = "";

    @ColumnInfo(name = "sortOrder")
    public int sortOrder;

    @ColumnInfo(name = "indexStatus")
    public int indexStatus = STATUS_UNINDEXED;

    @ColumnInfo(name = "indexedFileCount")
    public int indexedFileCount;

    @ColumnInfo(name = "lastIndexedAt")
    public long lastIndexedAt;

    @ColumnInfo(name = "lastError")
    public String lastError;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}

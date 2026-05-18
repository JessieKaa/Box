package com.github.tvbox.osc.cache;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "homeFolderIndexEntry",
        foreignKeys = @ForeignKey(entity = HomeFolderShortcut.class,
                parentColumns = "id",
                childColumns = "shortcutId",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(value = {"shortcutId"}),
                @Index(value = {"shortcutId", "absolutePath"}, unique = true),
                @Index(value = {"fileName"})
        })
public class HomeFolderIndexEntry {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "shortcutId")
    public int shortcutId;

    @NonNull
    @ColumnInfo(name = "absolutePath")
    public String absolutePath = "";

    @NonNull
    @ColumnInfo(name = "relativePath")
    public String relativePath = "";

    @NonNull
    @ColumnInfo(name = "fileName")
    public String fileName = "";

    @ColumnInfo(name = "fileType")
    public String fileType;

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

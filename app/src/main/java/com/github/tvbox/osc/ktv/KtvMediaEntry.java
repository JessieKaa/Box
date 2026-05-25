package com.github.tvbox.osc.ktv;

public class KtvMediaEntry {
    public final String path;
    public final String name;
    public final boolean isFile;
    public final String fileType;
    public final long lastModified;
    public final long fileSize;

    public KtvMediaEntry(String path, String name, boolean isFile, String fileType, long lastModified, long fileSize) {
        this.path = path;
        this.name = name;
        this.isFile = isFile;
        this.fileType = fileType;
        this.lastModified = lastModified;
        this.fileSize = fileSize;
    }
}

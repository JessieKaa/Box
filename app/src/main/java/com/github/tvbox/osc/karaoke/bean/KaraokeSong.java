package com.github.tvbox.osc.karaoke.bean;

import java.io.Serializable;
import java.util.Objects;

public class KaraokeSong implements Serializable {

    public String filePath;
    public String title;
    public String artist;
    public String displayName;
    public long fileSize;
    public long lastModified;

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KaraokeSong that = (KaraokeSong) o;
        return Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath);
    }
}

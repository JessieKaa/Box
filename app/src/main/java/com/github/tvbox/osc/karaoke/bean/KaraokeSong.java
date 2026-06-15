package com.github.tvbox.osc.karaoke.bean;

import com.github.tvbox.osc.cache.KaraokeFavorite;
import com.github.tvbox.osc.cache.KaraokeHistory;

import java.io.Serializable;
import java.util.Objects;

public class KaraokeSong implements Serializable {

    public String filePath;
    public String title;
    public String artist;
    public String displayName;
    public long fileSize;
    public long lastModified;
    public long duration;
    public long playbackPosition;
    public boolean favorite;

    public static KaraokeSong fromHistory(KaraokeHistory h) {
        KaraokeSong s = new KaraokeSong();
        s.filePath = h.filePath;
        s.title = h.title;
        s.artist = h.artist;
        s.displayName = h.displayName;
        s.fileSize = h.fileSize;
        s.lastModified = h.lastModified;
        s.duration = h.duration;
        s.playbackPosition = h.playbackPosition;
        return s;
    }

    public static KaraokeSong fromFavorite(KaraokeFavorite f) {
        KaraokeSong s = new KaraokeSong();
        s.filePath = f.filePath;
        s.title = f.title;
        s.artist = f.artist;
        s.displayName = f.displayName;
        s.fileSize = f.fileSize;
        s.lastModified = f.lastModified;
        s.duration = f.duration;
        s.favorite = true;
        return s;
    }

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

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

    // Source-aware identity for online library integration.
    public String sourceType = "local";   // "local" / "remote"
    public String trackId;                // API track_id (remote only)
    public String streamUrl;              // Remote stream URL (mutable, not identity)
    public String artworkUrl;             // Remote cover art URL
    public String lyricsUrl;              // Remote LRC URL
    public String identityKey;            // stable key used for equals/hashCode/Room unique index
    public String mediaType;              // remote API media_type ("mv"/"video" = video, else audio)

    public String identityKey() {
        if (identityKey != null && !identityKey.isEmpty()) return identityKey;
        if ("remote".equals(sourceType)) return "remote:" + (trackId != null ? trackId : "");
        return "local:" + (filePath != null ? filePath : "");
    }

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
        s.identityKey = h.identityKey;
        s.trackId = h.trackId;
        s.sourceType = h.sourceType;
        s.streamUrl = h.streamUrl;
        s.artworkUrl = h.artworkUrl;
        if (s.identityKey == null || s.identityKey.isEmpty()) {
            s.identityKey = "local:" + (s.filePath != null ? s.filePath : "");
            s.sourceType = "local";
        }
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
        s.identityKey = f.identityKey;
        s.trackId = f.trackId;
        s.sourceType = f.sourceType;
        s.streamUrl = f.streamUrl;
        s.artworkUrl = f.artworkUrl;
        if (s.identityKey == null || s.identityKey.isEmpty()) {
            s.identityKey = "local:" + (s.filePath != null ? s.filePath : "");
            s.sourceType = "local";
        }
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
        return Objects.equals(identityKey(), that.identityKey());
    }

    @Override
    public int hashCode() {
        return identityKey().hashCode();
    }
}

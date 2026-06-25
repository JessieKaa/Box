package com.github.tvbox.osc.karaoke.bean;

public class KaraokeTrack {

    public Integer id;
    public String title;
    public String artist;
    public String album;
    public Integer duration;
    public String media_type;
    public String artifact_type;
    public String container;
    public Long size_bytes;
    public String modified_at;
    public String upload_date;
    public String video_id;
    public String source_provider;

    public String stream_url;
    public String stream_url_absolute;
    public String artwork_url;
    public String artwork_url_absolute;
    public String lyrics_url;
    public String lyrics_url_absolute;
    public String lyrics_source;

    public KaraokeSong toKaraokeSong(String baseUrl) {
        if (id == null || id <= 0) return null;
        KaraokeSong song = new KaraokeSong();
        song.sourceType = "remote";
        song.trackId = String.valueOf(id);
        song.identityKey = "remote:" + song.trackId;
        song.title = title != null ? title : "";
        song.artist = artist != null ? artist : "";
        song.displayName = (artist != null ? artist : "") + " - " + (title != null ? title : "");
        song.duration = duration != null ? duration : 0;
        song.fileSize = size_bytes != null ? size_bytes : 0;
        song.lastModified = parseModifiedAt(modified_at);
        // Prefer the API-provided absolute URL when available; falls back to resolving
        // the (possibly relative) stream_url against baseUrl. Same idea for artwork.
        String resolvedStream = resolveUrl(firstNonEmpty(stream_url_absolute, stream_url), baseUrl);
        song.filePath = resolvedStream;
        song.streamUrl = resolvedStream;
        song.artworkUrl = resolveUrl(firstNonEmpty(artwork_url_absolute, artwork_url), baseUrl);
        song.lyricsUrl = resolveUrl(firstNonEmpty(lyrics_url_absolute, lyrics_url), baseUrl);
        song.mediaType = media_type;
        return song;
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b;
    }

    private static long parseModifiedAt(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date d = fmt.parse(raw);
            return d != null ? d.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String resolveUrl(String raw, String baseUrl) {
        if (raw == null || raw.isEmpty()) return "";
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
        if (raw.startsWith("//")) {
            if (baseUrl != null && baseUrl.startsWith("https")) return "https:" + raw;
            return "http:" + raw;
        }
        if (baseUrl == null || baseUrl.isEmpty()) return raw;
        try {
            return new java.net.URL(new java.net.URL(baseUrl), raw).toString();
        } catch (Exception e) {
            return raw;
        }
    }
}

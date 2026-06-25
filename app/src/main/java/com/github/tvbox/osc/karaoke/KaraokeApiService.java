package com.github.tvbox.osc.karaoke;

import android.util.Log;

import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.karaoke.bean.KaraokeTrack;
import com.github.tvbox.osc.karaoke.bean.KaraokeTrackListResponse;
import com.github.tvbox.osc.karaoke.discovery.KaraokeDiscoveryStore;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.urlhttp.OkHttpUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orhanobut.hawk.Hawk;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;

public class KaraokeApiService {

    private static final String TAG = "karaoke_remote";
    private static final String DETAIL_TAG = "karaoke_remote_detail";
    private static final String HEALTH_TAG = "karaoke_remote_health";
    private static final Gson GSON = new Gson();
    private static volatile KaraokeApiService instance;

    public interface ListCallback {
        void onSuccess(List<KaraokeSong> songs, String nextCursor);
        void onFailure(String msg);
    }

    public interface DetailCallback {
        void onSuccess(KaraokeSong song);
        void onFailure(String msg);
    }

    public interface HealthCallback {
        void onSuccess();
        void onFailure(String msg);
    }

    private KaraokeApiService() {
    }

    public static KaraokeApiService get() {
        if (instance == null) {
            synchronized (KaraokeApiService.class) {
                if (instance == null) {
                    instance = new KaraokeApiService();
                }
            }
        }
        return instance;
    }

    private String baseOrigin() {
        return KaraokeDiscoveryStore.stripTrailingSlash(Hawk.get(HawkConfig.KARAOKE_API_URL, "").trim());
    }

    private String apiPath() {
        return KaraokeDiscoveryStore.normalizeApiPath(Hawk.get(HawkConfig.KARAOKE_API_PATH, "/api"));
    }

    private String resourceBaseUrl() {
        return KaraokeDiscoveryStore.buildResourceBaseUrl(baseOrigin(), apiPath());
    }

    public void listMvs(String cursor, int limit, ListCallback cb) {
        String base = baseOrigin();
        if (base.isEmpty()) {
            if (cb != null) cb.onFailure("empty url");
            return;
        }
        final String listUrlBase = KaraokeDiscoveryStore.buildEndpoint(base, apiPath(), "/v1/library/tracks");
        final String finalResourceBase = resourceBaseUrl();
        final String finalCursor = cursor;
        final int finalLimit = Math.max(1, Math.min(limit, 200));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuilder sb = new StringBuilder(listUrlBase);
                    sb.append("?limit=").append(finalLimit);
                    if (finalCursor != null && !finalCursor.isEmpty()) {
                        sb.append("&cursor=").append(URLEncoder.encode(finalCursor, "UTF-8"));
                    }
                    String url = sb.toString();
                    Log.i(TAG, "list url=" + url);
                    OkHttpClient client = com.github.tvbox.osc.util.OkGoHelper.getDefaultClient();
                    String body = OkHttpUtil.string(client, url, TAG, null, null, null);
                    if (body == null || body.isEmpty()) {
                        Log.w(TAG, "list failed: empty response");
                        postFailure(cb, "empty response");
                        return;
                    }
                    KaraokeTrackListResponse resp = GSON.fromJson(body, KaraokeTrackListResponse.class);
                    if (resp == null || resp.items == null) {
                        Log.w(TAG, "list failed: invalid response, preview=" + preview(body));
                        postFailure(cb, "invalid response");
                        return;
                    }
                    List<KaraokeSong> songs = new ArrayList<>();
                    int skipped = 0;
                    for (KaraokeTrack t : resp.items) {
                        KaraokeSong s = t != null ? t.toKaraokeSong(finalResourceBase) : null;
                        if (s != null) {
                            songs.add(s);
                        } else {
                            skipped++;
                        }
                    }
                    Log.i(TAG, "list success: items=" + resp.items.size() + ", songs=" + songs.size() + ", skipped=" + skipped + ", nextCursor=" + resp.next_cursor);
                    if (resp.items.isEmpty()) {
                        Log.w(TAG, "list empty, preview=" + preview(body));
                    } else if (songs.size() > 0) {
                        KaraokeSong first = songs.get(0);
                        Log.i(TAG, "first song mediaType=" + first.mediaType + ", streamUrl=" + first.streamUrl + ", artworkUrl=" + first.artworkUrl);
                    }
                    postSuccess(cb, songs, resp.next_cursor);
                } catch (Exception e) {
                    Log.w(TAG, "list failed", e);
                    postFailure(cb, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    public void getTrackDetail(String trackId, DetailCallback cb) {
        String base = baseOrigin();
        if (base.isEmpty()) {
            if (cb != null) cb.onFailure("empty url");
            return;
        }
        if (trackId == null || trackId.isEmpty()) {
            if (cb != null) cb.onFailure("empty track id");
            return;
        }
        final String url = KaraokeDiscoveryStore.buildEndpoint(base, apiPath(), "/v1/library/tracks/" + urlEncode(trackId));
        final String finalResourceBase = resourceBaseUrl();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OkHttpClient client = com.github.tvbox.osc.util.OkGoHelper.getDefaultClient();
                    String body = OkHttpUtil.string(client, url, DETAIL_TAG, null, null, null);
                    if (body == null || body.isEmpty()) {
                        postFailure(cb, "empty response");
                        return;
                    }
                    KaraokeTrack track = GSON.fromJson(body, KaraokeTrack.class);
                    Log.i(DETAIL_TAG, "detail url=" + url + ", preview=" + preview(body));
                    if (body.contains("lyrics")) {
                        Log.i(DETAIL_TAG, "detail body contains lyrics field");
                    }
                    if (track == null || track.id == null || track.id <= 0) {
                        postFailure(cb, "invalid track");
                        return;
                    }
                    KaraokeSong song = track.toKaraokeSong(finalResourceBase);
                    Log.i(DETAIL_TAG, "detail parsed: id=" + track.id + ", mediaType=" + track.media_type + ", streamAbs=" + track.stream_url_absolute + ", stream=" + track.stream_url + ", artworkAbs=" + track.artwork_url_absolute + ", artwork=" + track.artwork_url + ", lyricsAbs=" + track.lyrics_url_absolute + ", lyrics=" + track.lyrics_url + ", lyricsSource=" + track.lyrics_source + ", songLyricsUrl=" + (song != null ? song.lyricsUrl : null));
                    if (song == null) {
                        postFailure(cb, "invalid track");
                        return;
                    }
                    postSuccess(cb, song);
                } catch (Exception e) {
                    postFailure(cb, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    public void checkHealth(HealthCallback cb) {
        String base = baseOrigin();
        if (base.isEmpty()) {
            if (cb != null) cb.onFailure("empty url");
            return;
        }
        final String url = KaraokeDiscoveryStore.buildEndpoint(base, apiPath(), "/health");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OkHttpClient client = com.github.tvbox.osc.util.OkGoHelper.getDefaultClient();
                    Log.i(HEALTH_TAG, "health url=" + url);
                    String body = OkHttpUtil.string(client, url, HEALTH_TAG, null, null, null);
                    if (!isHealthOk(body)) {
                        Log.w(HEALTH_TAG, "health failed, preview=" + preview(body));
                        postFailure(cb, "health failed");
                        return;
                    }
                    Log.i(HEALTH_TAG, "health ok");
                    postSuccess(cb);
                } catch (Exception e) {
                    Log.w(HEALTH_TAG, "health failed", e);
                    postFailure(cb, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }).start();
    }

    public void searchMvs(String query, int offset, int limit, ListCallback cb) {
        throw new UnsupportedOperationException("remote search not implemented in phase 1");
    }

    public void cancelAll() {
        OkHttpUtil.cancel(com.github.tvbox.osc.util.OkGoHelper.getDefaultClient(), TAG);
        OkHttpUtil.cancel(com.github.tvbox.osc.util.OkGoHelper.getDefaultClient(), HEALTH_TAG);
    }

    public void cancelDetail() {
        OkHttpUtil.cancel(com.github.tvbox.osc.util.OkGoHelper.getDefaultClient(), DETAIL_TAG);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private boolean isHealthOk(String body) {
        if (body == null || body.trim().isEmpty()) return false;
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            return json.has("status") && "ok".equalsIgnoreCase(json.get("status").getAsString());
        } catch (Throwable e) {
            return body.contains("\"status\":\"ok\"");
        }
    }

    private static String preview(String body) {
        if (body == null) return "null";
        String compact = body.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 180 ? compact.substring(0, 180) : compact;
    }

    private void postSuccess(final ListCallback cb, final List<KaraokeSong> songs, final String nextCursor) {
        if (cb == null) return;
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                cb.onSuccess(songs, nextCursor);
            }
        });
    }

    private void postFailure(final ListCallback cb, final String msg) {
        if (cb == null) return;
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                cb.onFailure(msg);
            }
        });
    }

    private void postSuccess(final DetailCallback cb, final KaraokeSong song) {
        if (cb == null) return;
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                cb.onSuccess(song);
            }
        });
    }

    private void postFailure(final DetailCallback cb, final String msg) {
        if (cb == null) return;
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                cb.onFailure(msg);
            }
        });
    }

    private void postSuccess(final HealthCallback cb) {
        if (cb == null) return;
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                cb.onSuccess();
            }
        });
    }

    private void postFailure(final HealthCallback cb, final String msg) {
        if (cb == null) return;
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                cb.onFailure(msg);
            }
        });
    }
}

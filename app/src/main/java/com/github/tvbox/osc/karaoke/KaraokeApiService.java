package com.github.tvbox.osc.karaoke;

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
                    sb.append("?media_type=mv");
                    sb.append("&limit=").append(finalLimit);
                    if (finalCursor != null && !finalCursor.isEmpty()) {
                        sb.append("&cursor=").append(URLEncoder.encode(finalCursor, "UTF-8"));
                    }
                    String url = sb.toString();
                    OkHttpClient client = com.github.tvbox.osc.util.OkGoHelper.getDefaultClient();
                    String body = OkHttpUtil.string(client, url, TAG, null, null, null);
                    if (body == null || body.isEmpty()) {
                        postFailure(cb, "empty response");
                        return;
                    }
                    KaraokeTrackListResponse resp = GSON.fromJson(body, KaraokeTrackListResponse.class);
                    if (resp == null || resp.items == null) {
                        postFailure(cb, "invalid response");
                        return;
                    }
                    List<KaraokeSong> songs = new ArrayList<>();
                    for (KaraokeTrack t : resp.items) {
                        KaraokeSong s = t != null ? t.toKaraokeSong(finalResourceBase) : null;
                        if (s != null) songs.add(s);
                    }
                    postSuccess(cb, songs, resp.next_cursor);
                } catch (Exception e) {
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
                    if (track == null || track.id == null || track.id <= 0) {
                        postFailure(cb, "invalid track");
                        return;
                    }
                    KaraokeSong song = track.toKaraokeSong(finalResourceBase);
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
                    String body = OkHttpUtil.string(client, url, HEALTH_TAG, null, null, null);
                    if (!isHealthOk(body)) {
                        postFailure(cb, "health failed");
                        return;
                    }
                    postSuccess(cb);
                } catch (Exception e) {
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

    private String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
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

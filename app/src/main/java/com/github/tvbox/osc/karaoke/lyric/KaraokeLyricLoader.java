package com.github.tvbox.osc.karaoke.lyric;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.subtitle.SubtitleLoadSuccessResult;
import com.github.tvbox.osc.subtitle.SubtitleLoader;
import com.github.tvbox.osc.subtitle.format.FormatLRC;
import com.github.tvbox.osc.subtitle.model.TimedTextObject;
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Async loader for external {@code .lrc} karaoke lyrics.
 *
 * Key behaviour:
 *  - Locates {@code <songDir>/<songName>.lrc} (also tries uppercase); falls back to remote LRC URL.
 *  - {@link #loadFor(KaraokeSong, Callback)} is safe to call repeatedly across rapid song switches:
 *    a monotonic generation counter invalidates any in-flight load whose song is no longer current.
 *  - LRU cache (size 50) keyed by file path or remote lyrics URL so the same song doesn't re-read.
 */
public class KaraokeLyricLoader {

    public interface Callback {
        void onLyricReady(KaraokeSong song, TimedTextObject tto);
        void onNoLyric(KaraokeSong song);
    }

    private static final String TAG = "KaraokeLyricLoader";
    private static final int LRU_SIZE = 50;

    private final WeakReference<Context> contextRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, TimedTextObject> lru = new LinkedHashMap<String, TimedTextObject>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TimedTextObject> eldest) {
            return size() > LRU_SIZE;
        }
    };
    private int generation = 0;

    public KaraokeLyricLoader(Context context) {
        this.contextRef = context != null ? new WeakReference<>(context) : null;
    }

    /**
     * Load the lyric for {@code song} and call back on the main thread.
     * Pass {@code null} to invalidate in-flight work without a callback.
     */
    public void loadFor(final KaraokeSong song, final Callback callback) {
        final int myGen = ++generation;
        if (song == null) {
            Log.w(TAG, "loadFor abort: song null");
            return;
        }

        final String cacheKey = buildCacheKey(song);
        Log.i(TAG, "loadFor: title=" + song.displayName + ", filePath=" + song.filePath + ", lyricsUrl=" + song.lyricsUrl + ", cacheKey=" + cacheKey);
        if (cacheKey == null || cacheKey.isEmpty()) {
            Log.w(TAG, "loadFor abort: empty cacheKey for title=" + song.displayName);
            return;
        }
        synchronized (lru) {
            TimedTextObject cached = lru.get(cacheKey);
            if (cached != null) {
                Log.i(TAG, "loadFor hit cache: title=" + song.displayName + ", captions=" + (cached.captions != null ? cached.captions.size() : -1));
                deliver(callback, song, cached, myGen);
                return;
            }
        }

        final File lrc = locateLrcFile(song);
        final String remoteLyricsUrl = (song.lyricsUrl != null && !song.lyricsUrl.isEmpty()) ? song.lyricsUrl : null;
        Log.i(TAG, "loadFor source: title=" + song.displayName + ", localLrc=" + (lrc != null ? lrc.getAbsolutePath() : null) + ", remoteLyricsUrl=" + remoteLyricsUrl);
        if (lrc == null && remoteLyricsUrl == null) {
            Log.w(TAG, "loadFor no lyric source: title=" + song.displayName);
            deliverNoLyric(callback, song, myGen);
            return;
        }

        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                TimedTextObject tto = null;
                try {
                    SubtitleLoadSuccessResult result;
                    if (lrc != null) {
                        Log.i(TAG, "loading local lyric: " + lrc.getAbsolutePath());
                        result = loadLocal(lrc.getAbsolutePath());
                    } else {
                        Log.i(TAG, "loading remote lyric: " + remoteLyricsUrl);
                        result = loadRemote(remoteLyricsUrl);
                    }
                    if (result == null) {
                        Log.w(TAG, "lyric load result null: title=" + song.displayName);
                    } else {
                        Log.i(TAG, "lyric load result ok: title=" + song.displayName + ", subtitlePath=" + result.subtitlePath + ", fileName=" + result.fileName + ", contentLen=" + (result.content != null ? result.content.length() : -1));
                    }
                    if (result != null) tto = result.timedTextObject;
                } catch (Throwable t) {
                    Log.w(TAG, "LRC load failed: " + t.getMessage(), t);
                }
                if (tto == null) {
                    Log.w(TAG, "timed text null: title=" + song.displayName);
                    deliverNoLyric(callback, song, myGen);
                    return;
                }
                Log.i(TAG, "timed text parsed: title=" + song.displayName + ", captions=" + (tto.captions != null ? tto.captions.size() : -1));
                if (tto.captions == null || tto.captions.isEmpty()) {
                    Log.w(TAG, "timed text empty captions: title=" + song.displayName);
                    deliverNoLyric(callback, song, myGen);
                    return;
                }
                synchronized (lru) {
                    lru.put(cacheKey, tto);
                }
                deliver(callback, song, tto, myGen);
            }
        });
    }

    /** Cancel all in-flight loads. Safe to call from any thread. */
    public void cancelAll() {
        generation++;
    }

    private String buildCacheKey(KaraokeSong song) {
        if (song == null) return null;
        File lrc = locateLrcFile(song);
        if (lrc != null) return lrc.getAbsolutePath();
        if (song.lyricsUrl != null && !song.lyricsUrl.isEmpty()) return song.lyricsUrl;
        return song.filePath;
    }

    private File locateLrcFile(KaraokeSong song) {
        if (song == null || song.filePath == null || song.filePath.isEmpty()) return null;
        File media = new File(song.filePath);
        File parent = media.getParentFile();
        if (parent == null || !parent.exists()) return null;
        String name = media.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        File lower = new File(parent, base + ".lrc");
        if (lower.exists()) return lower;
        File upper = new File(parent, base + ".LRC");
        if (upper.exists()) return upper;
        return null;
    }

    private SubtitleLoadSuccessResult loadRemote(String path) {
        try {
            SubtitleLoadSuccessResult result = SubtitleLoader.getInstance().loadSubtitle(path);
            // Remote karaoke lyrics endpoints usually have no file extension
            // (e.g. /api/v1/library/tracks/{id}/lyrics), so SubtitleLoader's
            // extension-based dispatch falls into a generic fallback loop where
            // FormatSRT silently returns an empty TimedTextObject and preempts
            // FormatLRC. Re-parse as LRC when we have content but no captions.
            if (result != null
                    && result.content != null && !result.content.isEmpty()
                    && (result.timedTextObject == null
                        || result.timedTextObject.captions == null
                        || result.timedTextObject.captions.isEmpty())) {
                try {
                    ByteArrayInputStream is = new ByteArrayInputStream(result.content.getBytes());
                    TimedTextObject tto = new FormatLRC().parseFile("remote.lrc", is);
                    if (tto != null && tto.captions != null && !tto.captions.isEmpty()) {
                        Log.i(TAG, "LRC re-parse succeeded: captions=" + tto.captions.size());
                        result.timedTextObject = tto;
                    } else {
                        Log.w(TAG, "LRC re-parse yielded empty captions");
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "LRC re-parse failed: " + t.getMessage());
                }
            }
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Wraps SubtitleLoader's static path; returns null if file is absent or unparseable. */
    private SubtitleLoadSuccessResult loadLocal(String path) {
        try {
            return SubtitleLoader.getInstance().loadSubtitle(path);
        } catch (Throwable t) {
            return null;
        }
    }

    private void deliver(final Callback cb, final KaraokeSong song, final TimedTextObject tto, final int myGen) {
        if (myGen != generation) return;
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (myGen != generation) return;
                cb.onLyricReady(song, tto);
            }
        });
    }

    private void deliverNoLyric(final Callback cb, final KaraokeSong song, final int myGen) {
        if (myGen != generation) return;
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (myGen != generation) return;
                cb.onNoLyric(song);
            }
        });
    }
}

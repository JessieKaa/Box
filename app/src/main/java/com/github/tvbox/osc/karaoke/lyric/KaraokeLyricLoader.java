package com.github.tvbox.osc.karaoke.lyric;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.subtitle.SubtitleLoadSuccessResult;
import com.github.tvbox.osc.subtitle.SubtitleLoader;
import com.github.tvbox.osc.subtitle.model.TimedTextObject;
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Async loader for external {@code .lrc} karaoke lyrics.
 *
 * Key behaviour:
 *  - Locates {@code <songDir>/<songName>.lrc} (also tries uppercase). No embedded fallback.
 *  - {@link #loadFor(KaraokeSong, Callback)} is safe to call repeatedly across rapid song switches:
 *    a monotonic generation counter invalidates any in-flight load whose song is no longer current.
 *  - LRU cache (size 50) keyed by file path so the same song doesn't re-read disk.
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
        if (song == null || song.filePath == null) return;

        final String cacheKey = song.filePath;
        synchronized (lru) {
            TimedTextObject cached = lru.get(cacheKey);
            if (cached != null) {
                deliver(callback, song, cached, myGen);
                return;
            }
        }

        final File lrc = locateLrcFile(song);
        if (lrc == null) {
            deliverNoLyric(callback, song, myGen);
            return;
        }

        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                TimedTextObject tto = null;
                try {
                    SubtitleLoadSuccessResult result = loadLocal(lrc.getAbsolutePath());
                    if (result != null) tto = result.timedTextObject;
                } catch (Throwable t) {
                    Log.w(TAG, "LRC load failed: " + t.getMessage());
                }
                if (tto == null) {
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

    private File locateLrcFile(KaraokeSong song) {
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

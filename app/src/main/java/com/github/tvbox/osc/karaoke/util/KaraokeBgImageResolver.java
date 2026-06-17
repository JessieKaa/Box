package com.github.tvbox.osc.karaoke.util;

import android.media.MediaMetadataRetriever;
import android.util.LruCache;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the background image list for a single karaoke song. Resolution is async and
 * guarded by a generation counter — {@link #nextGeneration()} bumps the counter, the
 * activity captures the gen at call time, and stale callbacks are dropped on the floor
 * (same pattern as {@link com.github.tvbox.osc.karaoke.lyric.KaraokeLyricLoader}).
 *
 * Fallback chain (in order, first non-empty result wins):
 *  1. {@code <songBase>_bg*.jpg/.jpeg/.png} — flat naming next to the song
 *  2. {@code <songDir>/bg/*.jpg/.jpeg/.png} — subdirectory next to the song
 *  3. {@code <songBase>.jpg} or {@code cover.jpg} — single-image seed
 *  4. {@link MediaMetadataRetriever#getEmbeddedPicture()} — embedded cover art bytes
 *  5. Five app-level default {@code R.drawable.bg_karaoke_default_*} drawables
 *
 * Sources are returned as a heterogeneous {@code List<Object>} that
 * {@link com.github.tvbox.osc.util.ImgUtil#load(Object, android.widget.ImageView)} can
 * consume directly.
 */
public class KaraokeBgImageResolver {

    public interface Callback {
        void onResolved(int generation, List<Object> sources);
    }

    /** App-level default drawable list, returned when no per-song art is found. */
    private static final List<Object> DEFAULTS = Collections.unmodifiableList(Arrays.asList(
            (Object) R.drawable.bg_karaoke_default_1,
            R.drawable.bg_karaoke_default_2,
            R.drawable.bg_karaoke_default_3,
            R.drawable.bg_karaoke_default_4,
            R.drawable.bg_karaoke_default_5
    ));

    private static final LruCache<String, byte[]> EMBEDDED_CACHE = new LruCache<String, byte[]>(20) {
        @Override
        protected int sizeOf(String key, byte[] value) {
            return 1; // count entries, not bytes — size budget is "20 songs"
        }
    };

    private int generation = 0;

    /** Bump and return the new generation token. Callers should capture this and pass to resolveAsync. */
    public synchronized int nextGeneration() {
        return ++generation;
    }

    /** Invalidate any in-flight resolution. Safe to call from any thread. */
    public synchronized void cancelAll() {
        generation++;
    }

    public void resolveAsync(final KaraokeSong song, final int gen, final Callback cb) {
        if (song == null || song.filePath == null) {
            deliver(gen, cb, DEFAULTS);
            return;
        }
        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                List<Object> result = resolveOnDisk(song);
                deliver(gen, cb, result);
            }
        });
    }

    private void deliver(final int gen, final Callback cb, final List<Object> sources) {
        if (cb == null) return;
        if (gen != currentGeneration()) return;
        AppTaskExecutor.mainThread().execute(new Runnable() {
            @Override
            public void run() {
                if (gen != currentGeneration()) return;
                cb.onResolved(gen, sources);
            }
        });
    }

    private synchronized int currentGeneration() {
        return generation;
    }

    private static List<Object> resolveOnDisk(KaraokeSong song) {
        File media = new File(song.filePath);
        File parent = media.getParentFile();
        String base = stripExt(media.getName());

        List<Object> out = new ArrayList<>();

        // 1. <songBase>_bg*.{jpg,jpeg,png} flat
        if (parent != null && parent.isDirectory()) {
            collectNumberedBgs(parent, base, out);
        }
        if (!out.isEmpty()) return out;

        // 2. <songDir>/bg/*.{jpg,jpeg,png}
        if (parent != null && parent.isDirectory()) {
            File bgDir = new File(parent, "bg");
            if (bgDir.isDirectory()) {
                collectDirImages(bgDir, out);
            }
        }
        if (!out.isEmpty()) return out;

        // 3. <songBase>.jpg / cover.jpg seed (single image, expanded to a 1-item list)
        if (parent != null && parent.isDirectory()) {
            File seed = findSeedImage(parent, base);
            if (seed != null) {
                out.add(seed);
                return out;
            }
        }

        // 4. Embedded cover art (with cache)
        byte[] embedded = loadEmbeddedCover(song.filePath);
        if (embedded != null) {
            out.add(embedded);
            return out;
        }

        // 5. App-level defaults
        return DEFAULTS;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void collectNumberedBgs(File parent, String base, List<Object> out) {
        // Match <base>_bg1.jpg, <base>_bg2.jpg, etc. Also accept <base>_bg.jpg (no number)
        File[] candidates = parent.listFiles();
        if (candidates == null) return;
        List<File> matched = new ArrayList<>();
        String prefixLower = (base + "_bg").toLowerCase(Locale.ROOT);
        for (File f : candidates) {
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!n.startsWith(prefixLower)) continue;
            if (!(n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"))) continue;
            matched.add(f);
        }
        if (matched.isEmpty()) return;
        // Sort by trailing number if present, otherwise by name
        Collections.sort(matched, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                int na = trailingNumber(a.getName());
                int nb = trailingNumber(b.getName());
                if (na == -1 && nb == -1) return a.getName().compareToIgnoreCase(b.getName());
                if (na == -1) return 1;
                if (nb == -1) return -1;
                return Integer.compare(na, nb);
            }
        });
        out.addAll(matched);
    }

    private static void collectDirImages(File dir, List<Object> out) {
        File[] candidates = dir.listFiles();
        if (candidates == null) return;
        List<File> matched = new ArrayList<>();
        for (File f : candidates) {
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")) {
                matched.add(f);
            }
        }
        if (matched.isEmpty()) return;
        Collections.sort(matched, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        out.addAll(matched);
    }

    private static File findSeedImage(File parent, String base) {
        String[] candidates = { base + ".jpg", base + ".jpeg", base + ".png", "cover.jpg", "cover.jpeg", "cover.png" };
        for (String c : candidates) {
            File f = new File(parent, c);
            if (f.isFile()) return f;
        }
        return null;
    }

    private static int trailingNumber(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) dot = name.length();
        int i = dot - 1;
        while (i >= 0 && Character.isDigit(name.charAt(i))) i--;
        if (i + 1 >= dot) return -1; // no digits
        try {
            return Integer.parseInt(name.substring(i + 1, dot));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static byte[] loadEmbeddedCover(String filePath) {
        synchronized (EMBEDDED_CACHE) {
            byte[] hit = EMBEDDED_CACHE.get(filePath);
            if (hit != null) return hit;
        }
        MediaMetadataRetriever mmr = null;
        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(filePath);
            byte[] art = mmr.getEmbeddedPicture();
            if (art != null && art.length > 0) {
                synchronized (EMBEDDED_CACHE) {
                    EMBEDDED_CACHE.put(filePath, art);
                }
                return art;
            }
        } catch (Throwable ignore) {
        } finally {
            if (mmr != null) {
                try { mmr.release(); } catch (Throwable ignore) {
                }
            }
        }
        return null;
    }

    /** Convenience accessor for testing/preview — exposes the default drawable list. */
    public static List<Object> getDefaults() {
        return DEFAULTS;
    }

    @SuppressWarnings("unused")
    private static App getApp() {
        return App.getInstance();
    }
}

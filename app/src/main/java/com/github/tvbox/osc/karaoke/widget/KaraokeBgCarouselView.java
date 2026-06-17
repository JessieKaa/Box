package com.github.tvbox.osc.karaoke.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-layer cross-fading background carousel for karaoke audio-only playback.
 *
 * Holds two {@link ImageView}s stacked; every {@link #INTERVAL_MS} ms the back layer
 * advances to the next source and cross-fades to the front. Sources can be a mix of
 * {@link java.io.File}, {@code byte[]} (embedded cover art) and {@link Integer} (drawable
 * res id). Loading goes through {@link ImgUtil#load(Object, ImageView)} so no URL/header
 * machinery gets in the way.
 *
 * Lifecycle is explicit: {@link #start()} schedules the swap runnable on the main thread,
 * {@link #stop()} clears it. The activity is responsible for calling stop in onPause /
 * onDestroy and start in onResume.
 */
public class KaraokeBgCarouselView extends FrameLayout {

    private static final long INTERVAL_MS = 10_000L;
    private static final long CROSSFADE_MS = 800L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ImageView layerA;
    private final ImageView layerB;

    private final List<Object> sources = new ArrayList<>();
    private int frontIndex = 0; // which ImageView (0=A, 1=B) is currently opaque
    private int sourceIndex = 0;
    private boolean running = false;

    private final Runnable swapRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || sources.isEmpty()) return;
            sourceIndex = (sourceIndex + 1) % sources.size();
            showSource(sourceIndex, true);
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    public KaraokeBgCarouselView(Context context) {
        super(context);
        layerA = createLayer();
        layerB = createLayer();
        addView(layerA);
        addView(layerB);
        layerB.setAlpha(0f);
    }

    public KaraokeBgCarouselView(Context context, AttributeSet attrs) {
        super(context, attrs);
        layerA = createLayer();
        layerB = createLayer();
        addView(layerA);
        addView(layerB);
        layerB.setAlpha(0f);
    }

    private ImageView createLayer() {
        ImageView iv = new ImageView(getContext());
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setAlpha(0f);
        return iv;
    }

    /**
     * Replace the carousel sources. Resets the index to the first source and immediately
     * shows it on the front layer (no cross-fade for the initial load).
     */
    public void setSources(List<?> sources) {
        this.sources.clear();
        if (sources != null) this.sources.addAll(sources);
        sourceIndex = 0;
        if (this.sources.isEmpty()) {
            layerA.setAlpha(0f);
            layerB.setAlpha(0f);
            return;
        }
        // Initial show on front layer (no fade)
        showSource(0, false);
        frontIndex = 0;
    }

    /** Begin the periodic cross-fade swap. No-op if already running or no sources. */
    public void start() {
        if (running) return;
        if (sources.isEmpty()) return;
        running = true;
        handler.removeCallbacks(swapRunnable);
        handler.postDelayed(swapRunnable, INTERVAL_MS);
    }

    /** Cancel any pending cross-fade. Safe to call when not running. */
    public void stop() {
        running = false;
        handler.removeCallbacks(swapRunnable);
    }

    private void showSource(int index, boolean crossfade) {
        if (sources.isEmpty() || index < 0 || index >= sources.size()) return;
        Object src = sources.get(index);
        if (crossfade) {
            ImageView front = (frontIndex == 0) ? layerA : layerB;
            ImageView back = (frontIndex == 0) ? layerB : layerA;
            ImgUtil.load(src, back);
            back.animate().alpha(1f).setDuration(CROSSFADE_MS).withEndAction(new Runnable() {
                @Override
                public void run() {
                    front.animate().alpha(0f).setDuration(0).start();
                }
            }).start();
            frontIndex = (frontIndex == 0) ? 1 : 0;
        } else {
            // Initial show: load into A and snap A to opaque, B to transparent.
            ImgUtil.load(src, layerA);
            layerA.setAlpha(1f);
            layerB.setAlpha(0f);
            frontIndex = 0;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
        layerA.animate().cancel();
        layerB.animate().cancel();
    }
}

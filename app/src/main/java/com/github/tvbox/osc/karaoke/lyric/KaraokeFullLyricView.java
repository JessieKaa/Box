package com.github.tvbox.osc.karaoke.lyric;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.github.tvbox.osc.subtitle.model.Subtitle;
import com.github.tvbox.osc.subtitle.model.TimedTextObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Full-screen scrolling lyric view for karaoke audio-only playback.
 *
 * Two modes:
 *  - {@link #MODE_SCROLL}: external {@code .lrc} with full timeline; renders up to 7 lines
 *    with the current line bold/white/center, surrounding lines dimmed, and a smooth
 *    {@link ValueAnimator}-driven Y offset transition when the active line changes.
 *  - {@link #MODE_LIVE}: only the current line is known (IJK {@code OnTimedTextListener}
 *    callback). Renders that single line centered; no seek/history because IJK's
 *    {@code IjkTimedText} API exposes no timestamps.
 *
 * Mode is set explicitly by the activity; the view stays {@link #MODE_HIDDEN} until told
 * otherwise. Visibility is the activity's responsibility (this view doesn't toggle itself).
 */
public class KaraokeFullLyricView extends View {

    public static final int MODE_HIDDEN = 0;
    public static final int MODE_SCROLL = 1;
    public static final int MODE_LIVE = 2;

    private static final int VISIBLE_ROWS = 7;
    private static final int CENTER_INDEX = VISIBLE_ROWS / 2; // 3
    private static final int SCROLL_ANIM_MS = 250;

    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int mode = MODE_HIDDEN;
    private TimedTextObject tto;
    private NavigableMap<Integer, Subtitle> captionMap; // startMs -> subtitle
    private final List<Subtitle> ordered = new ArrayList<>(); // indexable captions in time order
    private int activeIndex = -1;

    private float activeTextSize;
    private float inactiveTextSize;
    private float rowHeight; // px between baselines
    private float scrollOffset = 0f; // animated px offset along Y
    private ValueAnimator scrollAnimator;

    private String liveText = null;

    private LinearGradient edgeFade;

    public KaraokeFullLyricView(Context context) {
        super(context);
        init();
    }

    public KaraokeFullLyricView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KaraokeFullLyricView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        activeTextSize = 34f * density;
        inactiveTextSize = 22f * density;
        rowHeight = activeTextSize * 1.55f;

        activePaint.setColor(Color.WHITE);
        activePaint.setTextSize(activeTextSize);
        activePaint.setFakeBoldText(true);
        activePaint.setShadowLayer(8f * density, 0f, 2f * density, 0x99000000);

        inactivePaint.setColor(0x99FFFFFF);
        inactivePaint.setTextSize(inactiveTextSize);

        // Alpha-fade at top/bottom so rows blend into the background carousel.
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    public void setMode(int newMode) {
        if (this.mode == newMode) return;
        this.mode = newMode;
        if (newMode != MODE_SCROLL) {
            cancelScroll();
            scrollOffset = 0f;
            activeIndex = -1;
        }
        if (newMode != MODE_LIVE) {
            liveText = null;
        }
        invalidate();
    }

    public int getMode() {
        return mode;
    }

    /** Whether the LIVE-mode view currently has any text to render. Used by the activity's
     *  embedded-lyric watchdog to decide whether to surface a "no embedded lyric" toast. */
    public boolean hasLiveText() {
        return liveText != null && !liveText.isEmpty();
    }

    /** Enter {@link #MODE_SCROLL} with a fully parsed {@link TimedTextObject}. Resets scroll. */
    public void setTimedTextObject(TimedTextObject tto) {
        cancelScroll();
        this.tto = tto;
        this.ordered.clear();
        this.captionMap = null;
        this.activeIndex = -1;
        this.scrollOffset = 0f;
        if (tto != null && tto.captions != null && !tto.captions.isEmpty()) {
            // TreeMap<Integer, Subtitle> keyed by start ms already in sorted order.
            captionMap = new TreeMap<>(tto.captions);
            for (Map.Entry<Integer, Subtitle> e : captionMap.entrySet()) {
                ordered.add(e.getValue());
            }
        }
        this.mode = MODE_SCROLL;
        invalidate();
    }

    /**
     * Enter {@link #MODE_LIVE} showing only the supplied text (or clears if {@code null}).
     * Use this from the IJK {@code OnTimedTextListener} callback — IJK gives us no
     * timestamps so we can't reconstruct a timeline.
     */
    public void setLiveText(String text) {
        if (!TextUtils.isEmpty(text)) {
            // collapse HTML break tags and trim
            text = text.replaceAll("(?i)<br\\s*/?>", " ").trim();
        }
        this.liveText = text;
        this.mode = MODE_LIVE;
        invalidate();
    }

    /**
     * Advance the active row based on the player's current position. Only meaningful in
     * {@link #MODE_SCROLL}; other modes are no-ops.
     */
    public void setCurrentPositionMs(long ms) {
        if (mode != MODE_SCROLL || captionMap == null || captionMap.isEmpty()) return;
        Map.Entry<Integer, Subtitle> floor = captionMap.floorEntry((int) ms);
        int newIndex;
        if (floor == null) {
            newIndex = -1;
        } else {
            // indexOf via the ordered list — O(n) but n is small for one song.
            newIndex = ordered.indexOf(floor.getValue());
        }
        if (newIndex == activeIndex) return;
        int previous = activeIndex;
        activeIndex = newIndex;
        animateToIndex(previous, newIndex);
    }

    public void reset() {
        cancelScroll();
        tto = null;
        if (captionMap != null) captionMap.clear();
        captionMap = null;
        ordered.clear();
        activeIndex = -1;
        scrollOffset = 0f;
        liveText = null;
        mode = MODE_HIDDEN;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            edgeFade = new LinearGradient(
                    0f, 0f, 0f, h,
                    new int[] { 0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF },
                    new float[] { 0f, 0.18f, 0.82f, 1f },
                    Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mode == MODE_LIVE) {
            drawLive(canvas);
            return;
        }
        if (mode != MODE_SCROLL) return;
        if (ordered.isEmpty() || activeIndex < 0) {
            // Pre-roll: show first few lines statically at the bottom of the visible window
            drawScrollRows(canvas, 0, -1);
            return;
        }
        drawScrollRows(canvas, scrollOffset, activeIndex);
    }

    private void drawScrollRows(Canvas canvas, float yOffset, int centerIndex) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        float centerX = width / 2f;
        float centerY = height / 2f;
        float baseY = centerY + (activeTextSize / 3f) + yOffset; // baseline of active row

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int targetIndex = centerIndex - CENTER_INDEX + i;
            if (targetIndex < 0 || targetIndex >= ordered.size()) continue;
            String text = captionText(ordered.get(targetIndex));
            if (TextUtils.isEmpty(text)) continue;

            float rowY = baseY + (i - CENTER_INDEX) * rowHeight;
            boolean isActive = (i == CENTER_INDEX);
            Paint p = isActive ? activePaint : inactivePaint;
            // Distance-from-center alpha falloff for inactive rows
            int alpha = isActive ? 255 : alphaForDistance(Math.abs(i - CENTER_INDEX));
            p.setAlpha(alpha);
            p.setTextAlign(Paint.Align.CENTER);

            // Word-wrap if too long
            float maxWidth = width * 0.86f;
            String[] lines = wrap(text, p, maxWidth);
            Paint.FontMetrics fm = p.getFontMetrics();
            float lineH = fm.descent - fm.ascent;
            float totalH = lineH * lines.length;
            float startY = rowY - totalH / 2f - fm.ascent;
            for (int j = 0; j < lines.length; j++) {
                canvas.drawText(lines[j], centerX, startY + j * lineH, p);
            }
        }
        // restore alpha so paints are clean for next pass
        activePaint.setAlpha(255);
        inactivePaint.setAlpha(0x99);
    }

    private void drawLive(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        if (TextUtils.isEmpty(liveText)) return;
        float centerX = width / 2f;
        float centerY = height / 2f;
        activePaint.setAlpha(255);
        activePaint.setTextAlign(Paint.Align.CENTER);
        float maxWidth = width * 0.84f;
        String[] lines = wrap(liveText, activePaint, maxWidth);
        Paint.FontMetrics fm = activePaint.getFontMetrics();
        float lineH = fm.descent - fm.ascent;
        float totalH = lineH * lines.length;
        float startY = centerY - totalH / 2f - fm.ascent;
        for (int j = 0; j < lines.length; j++) {
            canvas.drawText(lines[j], centerX, startY + j * lineH, activePaint);
        }
    }

    private String captionText(Subtitle s) {
        if (s == null || s.content == null) return "";
        return s.content.replaceAll("(?i)<br\\s*/?>", " ").trim();
    }

    private int alphaForDistance(int d) {
        // CENTER_INDEX=3, so d in [1..3]. Falloff: 0x99 / 0x66 / 0x33.
        switch (d) {
            case 1: return 0x99;
            case 2: return 0x66;
            default: return 0x33;
        }
    }

    private String[] wrap(String text, Paint paint, float maxWidth) {
        if (text == null || text.isEmpty()) return new String[] { "" };
        // Simple greedy char/word wrap. LRC content is usually short; prefer keeping each
        // caption on one line when it fits.
        float textWidth = paint.measureText(text);
        if (textWidth <= maxWidth) return new String[] { text };
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        float acc = 0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float w = paint.measureText(String.valueOf(c));
            if (acc + w > maxWidth && buf.length() > 0) {
                out.add(buf.toString());
                buf.setLength(0);
                acc = 0f;
            }
            buf.append(c);
            acc += w;
        }
        if (buf.length() > 0) out.add(buf.toString());
        return out.toArray(new String[0]);
    }

    private void animateToIndex(int previousIndex, int newIndex) {
        cancelScroll();
        // Animate the Y offset from "previous active row is centered" to "new active row
        // is centered". The relative delta is (previous - new) rowHeight — when moving
        // down by 1 row, newIndex = previous + 1, offset goes from +rowHeight to 0.
        float startOffset;
        if (previousIndex < 0) {
            // No previous active — start from one row above the new active so it slides in
            startOffset = -rowHeight;
        } else {
            startOffset = (previousIndex - newIndex) * rowHeight;
        }
        scrollOffset = startOffset;
        scrollAnimator = ValueAnimator.ofFloat(startOffset, 0f);
        scrollAnimator.setDuration(SCROLL_ANIM_MS);
        scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scrollOffset = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        scrollAnimator.start();
    }

    private void cancelScroll() {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelScroll();
    }
}

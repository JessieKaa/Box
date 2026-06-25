package com.github.tvbox.osc.karaoke.controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView;

import xyz.doikki.videoplayer.controller.BaseVideoController;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

public class KaraokeController extends BaseVideoController {

    public interface KaraokeControllerCallback {
        void onPrevious();
        void onNext();
        void onTogglePlayPause();
        void onSwitchAudioTrack();
        void onBackToSelect();
    }

    private LinearLayout llBottomBar;
    private ImageView ivPlayPause;
    private ProgressBar pbLoading;
    private SimpleSubtitleView simpleLyricView;

    // Seek bar
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;

    // Seek overlay (top-right)
    private LinearLayout llSeekOverlay;
    private TextView tvSeekTime;
    private ImageView ivSeekIcon;
    private ProgressBar pbSeekProgress;

    // Fast-forward / rewind state
    private boolean simSlideStart = false;
    private int simSeekPosition = 0;
    private long simSlideOffset = 0;
    private long lastSlideTime = 0;
    private boolean wasPlayingBeforeSeek = false;

    // D-pad LEFT/RIGHT gesture state machine.
    //
    // Three modes per side, driven by ACTION_DOWN/ACTION_UP + a couple of timers:
    //   - IDLE (lastXxxTapMs == 0 && !xxxSeekHolding && !xxxDoubleTapFired)
    //   - PENDING_LONG_PRESS: 250ms timer armed; if UP arrives first → single tap,
    //     if timer fires first → SEEK_HOLDING.
    //   - SEEK_HOLDING: long-press confirmed; 100ms pulse keeps calling tvSlideStart(dir)
    //     until UP commits with tvSlideStop().
    //
    // Double-tap detection: ACTION_DOWN within DOUBLE_TAP_WINDOW_MS of the previous
    // single-tap UP → fire onPrevious/onNext and set xxxDoubleTapFired so the matching
    // UP doesn't re-stamp lastXxxTapMs (otherwise a 3rd press would loop into a 2nd
    // double-tap, contrary to PLAN's "triple = double + new single tap").
    //
    // System key repeat: while SEEK_HOLDING, Android may keep sending repeated
    // ACTION_DOWN events with getRepeatCount() > 0; we must swallow those — otherwise
    // handleDpadHorizontal would be re-entered, the seek pulse state would be reset,
    // and the final UP could be misrouted to the single-tap branch, dropping the
    // tvSlideStop() commit.
    public static final long LONG_PRESS_THRESHOLD_MS = 250;
    public static final long DOUBLE_TAP_WINDOW_MS = 300;
    public static final long SEEK_PULSE_INTERVAL_MS = 100;
    private final Handler dpadGestureHandler = new Handler(Looper.getMainLooper());
    private long lastLeftTapMs = 0;
    private long lastRightTapMs = 0;
    private boolean leftDoubleTapFired = false;
    private boolean rightDoubleTapFired = false;
    private boolean leftSeekHolding = false;
    private boolean rightSeekHolding = false;
    private final Runnable leftLongArmRunnable = new Runnable() {
        @Override
        public void run() {
            // Long-press confirmed: kick off continuous seek until ACTION_UP.
            leftSeekHolding = true;
            tvSlideStart(-1);
            dpadGestureHandler.postDelayed(leftSeekPulseRunnable, SEEK_PULSE_INTERVAL_MS);
        }
    };
    private final Runnable rightLongArmRunnable = new Runnable() {
        @Override
        public void run() {
            rightSeekHolding = true;
            tvSlideStart(1);
            dpadGestureHandler.postDelayed(rightSeekPulseRunnable, SEEK_PULSE_INTERVAL_MS);
        }
    };
    private final Runnable leftSeekPulseRunnable = new Runnable() {
        @Override
        public void run() {
            if (!leftSeekHolding) return;
            tvSlideStart(-1);
            dpadGestureHandler.postDelayed(this, SEEK_PULSE_INTERVAL_MS);
        }
    };
    private final Runnable rightSeekPulseRunnable = new Runnable() {
        @Override
        public void run() {
            if (!rightSeekHolding) return;
            tvSlideStart(1);
            dpadGestureHandler.postDelayed(this, SEEK_PULSE_INTERVAL_MS);
        }
    };

    private KaraokeControllerCallback callback;
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Handler seekOverlayHandler = new Handler(Looper.getMainLooper());
    private static final int AUTO_HIDE_DELAY = 5000;
    private boolean isPlaying = false;
    private boolean audioOnlyMode = false;

    /** Cancel any pending hide/overlay/gesture callbacks. Call from Activity.onDestroy to prevent leaks. */
    public void release() {
        cancelDpadGesture();
        hideHandler.removeCallbacksAndMessages(null);
        seekOverlayHandler.removeCallbacksAndMessages(null);
        dpadGestureHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Tear down any in-progress LEFT/RIGHT gesture: cancel pending long-press arming,
     * stop the seek pulse, clear double-tap markers and tap timestamps. Call from
     * any path that leaves the gesture surface (hide/cancelSeekState/release, or
     * when the activity switches out of PLAY mode without delivering the matching UP).
     * Does NOT touch {@code simSlideStart} — callers that need to commit or discard
     * the underlying seek are expected to invoke {@link #tvSlideStop()} (commit) or
     * clear {@code simSlideStart} themselves (discard), as appropriate for that path.
     */
    private void cancelDpadGesture() {
        dpadGestureHandler.removeCallbacks(leftLongArmRunnable);
        dpadGestureHandler.removeCallbacks(rightLongArmRunnable);
        dpadGestureHandler.removeCallbacks(leftSeekPulseRunnable);
        dpadGestureHandler.removeCallbacks(rightSeekPulseRunnable);
        leftSeekHolding = false;
        rightSeekHolding = false;
        leftDoubleTapFired = false;
        rightDoubleTapFired = false;
        lastLeftTapMs = 0;
        lastRightTapMs = 0;
    }

    private final Runnable autoHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private final Runnable hideSeekOverlayRunnable = new Runnable() {
        @Override
        public void run() {
            llSeekOverlay.setVisibility(GONE);
        }
    };

    public KaraokeController(@NonNull Context context) {
        super(context);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.controller_karaoke;
    }

    @Override
    protected void initView() {
        super.initView();
        llBottomBar = findViewById(R.id.llBottomBar);
        ivPlayPause = findViewById(R.id.ivPlayPause);
        pbLoading = findViewById(R.id.pbLoading);
        simpleLyricView = findViewById(R.id.karaokeSubtitleView);

        seekBar = findViewById(R.id.seekBar);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        llSeekOverlay = findViewById(R.id.llSeekOverlay);
        tvSeekTime = findViewById(R.id.tvSeekTime);
        ivSeekIcon = findViewById(R.id.ivSeekIcon);
        pbSeekProgress = findViewById(R.id.pbSeekProgress);

        findViewById(R.id.ivPrev).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                resetAutoHide();
                if (callback != null) callback.onPrevious();
            }
        });
        findViewById(R.id.ivNext).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                resetAutoHide();
                if (callback != null) callback.onNext();
            }
        });
        ivPlayPause.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                resetAutoHide();
                if (callback != null) callback.onTogglePlayPause();
            }
        });
        findViewById(R.id.ivAudioTrack).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                resetAutoHide();
                if (callback != null) callback.onSwitchAudioTrack();
            }
        });
        findViewById(R.id.ivBack).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) callback.onBackToSelect();
            }
        });
    }

    @Override
    protected void setProgress(int duration, int position) {
        if (simSlideStart) return;
        tvCurrentTime.setText(PlayerUtils.stringForTime(position));
        tvTotalTime.setText(PlayerUtils.stringForTime(duration));
        if (duration > 0) {
            seekBar.setProgress((int) (position * 1.0 / duration * seekBar.getMax()));
        }
    }

    private void resetProgressUI() {
        tvCurrentTime.setText(PlayerUtils.stringForTime(0));
        tvTotalTime.setText(PlayerUtils.stringForTime(0));
        seekBar.setProgress(0);
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        switch (playState) {
            case VideoView.STATE_IDLE:
                pbLoading.setVisibility(GONE);
                stopProgress();
                cancelSeekState();
                resetProgressUI();
                break;
            case VideoView.STATE_PLAYING:
                isPlaying = true;
                ivPlayPause.setImageResource(R.drawable.v_pause);
                pbLoading.setVisibility(GONE);
                startProgress();
                break;
            case VideoView.STATE_PAUSED:
                isPlaying = false;
                ivPlayPause.setImageResource(R.drawable.v_play);
                pbLoading.setVisibility(GONE);
                stopProgress();
                break;
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_BUFFERING:
                pbLoading.setVisibility(VISIBLE);
                if (playState == VideoView.STATE_PREPARING) {
                    stopProgress();
                    cancelSeekState();
                    resetProgressUI();
                }
                break;
            case VideoView.STATE_PREPARED:
            case VideoView.STATE_ERROR:
            case VideoView.STATE_BUFFERED:
                pbLoading.setVisibility(GONE);
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                pbLoading.setVisibility(GONE);
                isPlaying = false;
                ivPlayPause.setImageResource(R.drawable.v_play);
                stopProgress();
                cancelSeekState();
                break;
        }
    }

    public void setCallback(KaraokeControllerCallback callback) {
        this.callback = callback;
    }

    /** Returns the karaoke lyric view embedded in this controller, or null if not present. */
    public com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView getLyricView() {
        return findViewById(R.id.karaokeSubtitleView);
    }

    /**
     * Switch the controller between audio-only and video modes. In audio-only mode the
     * legacy single-line {@link SimpleSubtitleView} is hidden because the activity's
     * full-screen com.github.tvbox.osc.karaoke.lyric.KaraokeFullLyricView takes over.
     * The full-screen view lives in the activity layout (not the controller) so it keeps
     * screen-sized bounds even when the player surface is GONE.
     */
    public void setAudioOnlyMode(boolean audioOnly) {
        this.audioOnlyMode = audioOnly;
        if (audioOnly) {
            if (simpleLyricView != null) simpleLyricView.setVisibility(GONE);
        } else {
            if (simpleLyricView != null) simpleLyricView.setVisibility(VISIBLE);
        }
    }

    public boolean isAudioOnlyMode() {
        return audioOnlyMode;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
        ivPlayPause.setImageResource(playing ? R.drawable.v_pause : R.drawable.v_play);
    }

    @Override
    public void show() {
        llBottomBar.setVisibility(VISIBLE);
        mShowing = true;
        resetAutoHide();
    }

    @Override
    public void hide() {
        // Stop the dpad seek pulse BEFORE the seek-commit block below: the pulse
        // keeps calling tvSlideStart(dir) which mutates simSeekPosition, racing the
        // commit. Cancelling first guarantees a stable position to land on. This also
        // covers the case where the user leaves PLAY mode (e.g. via BACK) without a
        // matching LEFT/RIGHT UP ever reaching handleKeyEvent.
        cancelDpadGesture();
        if (mShowing) {
            llBottomBar.setVisibility(GONE);
            mShowing = false;
            hideHandler.removeCallbacks(autoHideRunnable);
        }
        // Commit any in-progress seek so the position isn't silently lost
        if (simSlideStart) {
            mControlWrapper.seekTo(simSeekPosition);
            if (wasPlayingBeforeSeek && !mControlWrapper.isPlaying()) {
                mControlWrapper.start();
            }
            simSlideStart = false;
            simSeekPosition = 0;
            simSlideOffset = 0;
        }
        hideSeekOverlay();
        seekOverlayHandler.removeCallbacks(hideSeekOverlayRunnable);
    }

    private void resetAutoHide() {
        hideHandler.removeCallbacks(autoHideRunnable);
        hideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY);
    }

    private void cancelSeekState() {
        // Mirror hide()'s gesture teardown — on STATE_ERROR / STATE_PREPARING / etc the
        // controller is being reset while a long-press pulse may still be running.
        cancelDpadGesture();
        if (simSlideStart) {
            simSlideStart = false;
            simSeekPosition = 0;
            simSlideOffset = 0;
        }
        hideSeekOverlay();
        seekOverlayHandler.removeCallbacks(hideSeekOverlayRunnable);
    }

    // ======================== Fast-forward / Rewind ========================

    public void tvSlideStart(int dir) {
        int duration = (int) mControlWrapper.getDuration();
        if (duration <= 0) return;

        long currentTime = System.currentTimeMillis();
        final int baseSkip = 10000;
        final float accelerationFactor = 1.5f;
        final long threshold = 500;

        if (!simSlideStart) {
            wasPlayingBeforeSeek = mControlWrapper.isPlaying();
            simSlideStart = true;
            simSlideOffset = (long) baseSkip * dir;
        } else {
            if (currentTime - lastSlideTime <= threshold) {
                simSlideOffset += (long) (baseSkip * accelerationFactor) * dir;
            } else {
                simSlideOffset = (long) baseSkip * dir;
            }
        }
        lastSlideTime = currentTime;
        int currentPosition = (int) mControlWrapper.getCurrentPosition();
        int position = (int) (currentPosition + simSlideOffset);
        if (position > duration) position = duration;
        if (position < 0) position = 0;
        updateSeekOverlay(currentPosition, position, duration);
        simSeekPosition = position;
    }

    public void tvSlideStop() {
        if (!simSlideStart) return;
        mControlWrapper.seekTo(simSeekPosition);
        if (wasPlayingBeforeSeek && !mControlWrapper.isPlaying()) {
            mControlWrapper.start();
        }
        simSlideStart = false;
        int pos = simSeekPosition;
        simSeekPosition = 0;
        simSlideOffset = 0;
        hideSeekOverlay();
        if (!wasPlayingBeforeSeek) {
            setProgress((int) mControlWrapper.getDuration(), pos);
        }
    }

    private void updateSeekOverlay(int curr, int seekTo, int duration) {
        ivSeekIcon.setImageResource(seekTo > curr ? R.drawable.play_ffwd : R.drawable.play_rewind);
        tvSeekTime.setText(PlayerUtils.stringForTime(seekTo) + " / " + PlayerUtils.stringForTime(duration));
        pbSeekProgress.setProgress(duration > 0 ? (int) (seekTo * 100.0 / duration) : 0);
        llSeekOverlay.setVisibility(VISIBLE);
        seekOverlayHandler.removeCallbacks(hideSeekOverlayRunnable);
        seekOverlayHandler.postDelayed(hideSeekOverlayRunnable, 1500);
        show();
        resetAutoHide();
    }

    private void hideSeekOverlay() {
        llSeekOverlay.setVisibility(GONE);
    }

    // ======================== Key Handling ========================

    public boolean handleKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                finishDpadHorizontal(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                finishDpadHorizontal(1);
                return true;
            }
            return false;
        }

        if (action != KeyEvent.ACTION_DOWN) {
            return false;
        }

        // System key-repeat: while holding LEFT/RIGHT the platform keeps sending
        // additional ACTION_DOWN events with getRepeatCount() > 0. Once we've armed
        // the long-press pulse these are noise — swallow them so the state machine
        // stays in SEEK_HOLDING until ACTION_UP. For other keys, also collapse
        // autorepeat (we don't act on repeats anywhere).
        if (event.getRepeatCount() > 0) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_SPACE
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return true;
            }
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (callback != null) callback.onTogglePlayPause();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (callback != null) callback.onSwitchAudioTrack();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // Swallow by default. Old behaviour fired onNext, which collides with
                // "double-tap RIGHT = next song" and would skip songs on stray presses.
                // A-key still triggers audio track switching; MENU/INFO still exits.
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                if (callback != null) callback.onBackToSelect();
                return true;
            case KeyEvent.KEYCODE_A:
                if (callback != null) callback.onSwitchAudioTrack();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                startDpadHorizontal(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                startDpadHorizontal(1);
                return true;
        }
        return false;
    }

    /**
     * ACTION_DOWN handler for LEFT/RIGHT. Resolves to one of:
     * <ul>
     *   <li>Double-tap: previous same-direction single-tap UP was within
     *       {@link #DOUBLE_TAP_WINDOW_MS} → fire {@code onPrevious/onNext} and mark
     *       {@code xxxDoubleTapFired} so the matching UP doesn't re-stamp.</li>
     *   <li>Pending long-press: arm a {@link #LONG_PRESS_THRESHOLD_MS} timer. If UP
     *       arrives first → single tap (timestamp recorded for next double-tap window).
     *       If the timer fires first → enters SEEK_HOLDING via {@code xxxLongArmRunnable}.</li>
     * </ul>
     */
    private void startDpadHorizontal(int dir) {
        long now = System.currentTimeMillis();
        if (dir < 0) {
            if (lastLeftTapMs != 0 && (now - lastLeftTapMs) <= DOUBLE_TAP_WINDOW_MS) {
                dpadGestureHandler.removeCallbacks(leftLongArmRunnable);
                leftSeekHolding = false;
                dpadGestureHandler.removeCallbacks(leftSeekPulseRunnable);
                lastLeftTapMs = 0;
                leftDoubleTapFired = true;
                if (callback != null) callback.onPrevious();
            } else {
                lastLeftTapMs = 0;
                dpadGestureHandler.removeCallbacks(leftLongArmRunnable);
                leftSeekHolding = false;
                dpadGestureHandler.removeCallbacks(leftSeekPulseRunnable);
                dpadGestureHandler.postDelayed(leftLongArmRunnable, LONG_PRESS_THRESHOLD_MS);
            }
        } else {
            if (lastRightTapMs != 0 && (now - lastRightTapMs) <= DOUBLE_TAP_WINDOW_MS) {
                dpadGestureHandler.removeCallbacks(rightLongArmRunnable);
                rightSeekHolding = false;
                dpadGestureHandler.removeCallbacks(rightSeekPulseRunnable);
                lastRightTapMs = 0;
                rightDoubleTapFired = true;
                if (callback != null) callback.onNext();
            } else {
                lastRightTapMs = 0;
                dpadGestureHandler.removeCallbacks(rightLongArmRunnable);
                rightSeekHolding = false;
                dpadGestureHandler.removeCallbacks(rightSeekPulseRunnable);
                dpadGestureHandler.postDelayed(rightLongArmRunnable, LONG_PRESS_THRESHOLD_MS);
            }
        }
    }

    /**
     * ACTION_UP handler for LEFT/RIGHT. Branches on current gesture state:
     * <ul>
     *   <li>SEEK_HOLDING: stop the pulse and {@link #tvSlideStop()} to commit the seek.</li>
     *   <li>Double-tap marker: consume, reset timestamp to 0 (third press must start a
     *       fresh single-tap window).</li>
     *   <li>Pending long-press: cancel the timer and record the single-tap timestamp
     *       so the next DOWN within the double-tap window fires song switch.</li>
     * </ul>
     */
    private void finishDpadHorizontal(int dir) {
        if (dir < 0) {
            if (leftSeekHolding) {
                dpadGestureHandler.removeCallbacks(leftSeekPulseRunnable);
                tvSlideStop();
                leftSeekHolding = false;
                lastLeftTapMs = 0;
            } else if (leftDoubleTapFired) {
                leftDoubleTapFired = false;
                lastLeftTapMs = 0;
            } else {
                dpadGestureHandler.removeCallbacks(leftLongArmRunnable);
                lastLeftTapMs = System.currentTimeMillis();
            }
        } else {
            if (rightSeekHolding) {
                dpadGestureHandler.removeCallbacks(rightSeekPulseRunnable);
                tvSlideStop();
                rightSeekHolding = false;
                lastRightTapMs = 0;
            } else if (rightDoubleTapFired) {
                rightDoubleTapFired = false;
                lastRightTapMs = 0;
            } else {
                dpadGestureHandler.removeCallbacks(rightLongArmRunnable);
                lastRightTapMs = System.currentTimeMillis();
            }
        }
    }
}

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
import com.github.tvbox.osc.karaoke.lyric.KaraokeFullLyricView;
import com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView;

import java.util.List;

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

    private LinearLayout llTopBar;
    private LinearLayout llBottomBar;
    private TextView tvSongTitle;
    private TextView tvTrackInfo;
    private ImageView ivPlayPause;
    private ProgressBar pbLoading;
    private KaraokeFullLyricView fullLyricView;
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

    // Next-up preview (top-right, alongside seek overlay)
    private LinearLayout llNextUp;
    private TextView tvNextUp1;
    private TextView tvNextUp2;
    private TextView tvNextUp3;
    private TextView tvNextUpEmpty;

    // Fast-forward / rewind state
    private boolean simSlideStart = false;
    private int simSeekPosition = 0;
    private long simSlideOffset = 0;
    private long lastSlideTime = 0;
    private boolean wasPlayingBeforeSeek = false;

    private KaraokeControllerCallback callback;
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Handler seekOverlayHandler = new Handler(Looper.getMainLooper());
    private final Handler lyricPollHandler = new Handler(Looper.getMainLooper());
    private static final int AUTO_HIDE_DELAY = 5000;
    private static final long LYRIC_POLL_INTERVAL_MS = 200L;
    private boolean isPlaying = false;
    private boolean audioOnlyMode = false;

    /** Cancel any pending hide/overlay callbacks. Call from Activity.onDestroy to prevent leaks. */
    public void release() {
        hideHandler.removeCallbacksAndMessages(null);
        seekOverlayHandler.removeCallbacksAndMessages(null);
        lyricPollHandler.removeCallbacksAndMessages(null);
    }

    private final Runnable lyricPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (fullLyricView != null && mControlWrapper != null && fullLyricView.getMode() == KaraokeFullLyricView.MODE_SCROLL) {
                fullLyricView.setCurrentPositionMs(mControlWrapper.getCurrentPosition());
            }
            lyricPollHandler.postDelayed(this, LYRIC_POLL_INTERVAL_MS);
        }
    };

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
        llTopBar = findViewById(R.id.llTopBar);
        llBottomBar = findViewById(R.id.llBottomBar);
        tvSongTitle = findViewById(R.id.tvSongTitle);
        tvTrackInfo = findViewById(R.id.tvTrackInfo);
        ivPlayPause = findViewById(R.id.ivPlayPause);
        pbLoading = findViewById(R.id.pbLoading);
        fullLyricView = findViewById(R.id.karaokeFullLyricView);
        simpleLyricView = findViewById(R.id.karaokeSubtitleView);

        seekBar = findViewById(R.id.seekBar);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        llSeekOverlay = findViewById(R.id.llSeekOverlay);
        tvSeekTime = findViewById(R.id.tvSeekTime);
        ivSeekIcon = findViewById(R.id.ivSeekIcon);
        pbSeekProgress = findViewById(R.id.pbSeekProgress);

        llNextUp = findViewById(R.id.llNextUp);
        tvNextUp1 = findViewById(R.id.tvNextUp1);
        tvNextUp2 = findViewById(R.id.tvNextUp2);
        tvNextUp3 = findViewById(R.id.tvNextUp3);
        tvNextUpEmpty = findViewById(R.id.tvNextUpEmpty);
        setNextUp(null);

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
                stopLyricPoll();
                break;
            case VideoView.STATE_PLAYING:
                isPlaying = true;
                ivPlayPause.setImageResource(R.drawable.v_pause);
                pbLoading.setVisibility(GONE);
                startProgress();
                if (audioOnlyMode) startLyricPoll();
                break;
            case VideoView.STATE_PAUSED:
                isPlaying = false;
                ivPlayPause.setImageResource(R.drawable.v_play);
                pbLoading.setVisibility(GONE);
                stopProgress();
                stopLyricPoll();
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
                stopLyricPoll();
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

    /** Returns the full-screen scrolling lyric view (audio-only mode), or null if not present. */
    public KaraokeFullLyricView getFullLyricView() {
        return fullLyricView;
    }

    /**
     * Switch the controller between audio-only and video modes. In audio-only mode the
     * legacy single-line {@link SimpleSubtitleView} is hidden (the full-screen view takes
     * over); in video mode the full-screen view is hidden and the simple view is left for
     * the activity to drive as before. Also starts/stops the lyric position poller.
     */
    public void setAudioOnlyMode(boolean audioOnly) {
        this.audioOnlyMode = audioOnly;
        if (audioOnly) {
            if (simpleLyricView != null) simpleLyricView.setVisibility(GONE);
            // fullLyricView visibility is the activity's responsibility — it must first
            // push a TimedTextObject / live text in, then make it VISIBLE.
        } else {
            if (fullLyricView != null) {
                fullLyricView.reset();
                fullLyricView.setVisibility(GONE);
            }
            stopLyricPoll();
        }
    }

    public boolean isAudioOnlyMode() {
        return audioOnlyMode;
    }

    /** Begin periodic position → lyric-row updates. Only forwards in SCROLL mode. */
    public void startLyricPoll() {
        lyricPollHandler.removeCallbacks(lyricPollRunnable);
        lyricPollHandler.postDelayed(lyricPollRunnable, LYRIC_POLL_INTERVAL_MS);
    }

    /** Stop the position poller. Safe to call when not running. */
    public void stopLyricPoll() {
        lyricPollHandler.removeCallbacks(lyricPollRunnable);
    }

    public void setSongTitle(String title) {
        tvSongTitle.setText(title);
    }

    public void setTrackInfo(String info) {
        tvTrackInfo.setText(info);
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
        ivPlayPause.setImageResource(playing ? R.drawable.v_pause : R.drawable.v_play);
    }

    public void setNextUp(List<String> titles) {
        boolean empty = titles == null || titles.isEmpty();
        TextView[] slots = {tvNextUp1, tvNextUp2, tvNextUp3};
        if (empty) {
            for (TextView slot : slots) slot.setVisibility(GONE);
            tvNextUpEmpty.setVisibility(VISIBLE);
            return;
        }
        tvNextUpEmpty.setVisibility(GONE);
        for (int i = 0; i < slots.length; i++) {
            if (i < titles.size()) {
                slots[i].setText(titles.get(i));
                slots[i].setVisibility(VISIBLE);
            } else {
                slots[i].setVisibility(GONE);
            }
        }
    }

    @Override
    public void show() {
        llTopBar.setVisibility(VISIBLE);
        llBottomBar.setVisibility(VISIBLE);
        llNextUp.setVisibility(VISIBLE);
        mShowing = true;
        resetAutoHide();
    }

    @Override
    public void hide() {
        if (mShowing) {
            llTopBar.setVisibility(GONE);
            llBottomBar.setVisibility(GONE);
            llNextUp.setVisibility(GONE);
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

        if (event.getAction() == KeyEvent.ACTION_UP) {
            if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && simSlideStart) {
                tvSlideStop();
                return true;
            }
            return false;
        }

        // ACTION_DOWN
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (callback != null) callback.onTogglePlayPause();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (callback != null) callback.onPrevious();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (callback != null) callback.onNext();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                if (callback != null) callback.onBackToSelect();
                return true;
            case KeyEvent.KEYCODE_A:
                if (callback != null) callback.onSwitchAudioTrack();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                tvSlideStart(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                tvSlideStart(1);
                return true;
        }
        return false;
    }
}

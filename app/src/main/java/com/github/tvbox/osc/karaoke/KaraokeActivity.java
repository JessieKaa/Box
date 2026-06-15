package com.github.tvbox.osc.karaoke;

import android.Manifest;
import android.graphics.Bitmap;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.DiffUtil;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.karaoke.adapter.KaraokeArtistAdapter;
import com.github.tvbox.osc.karaoke.adapter.KaraokeQueueAdapter;
import com.github.tvbox.osc.karaoke.adapter.KaraokeSongGridAdapter;
import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.karaoke.controller.KaraokeController;
import com.github.tvbox.osc.karaoke.lyric.KaraokeLyricLoader;
import com.github.tvbox.osc.karaoke.playlist.KaraokeSession;
import com.github.tvbox.osc.karaoke.util.KaraokeFileScanner;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.player.IjkmPlayer;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.player.TrackAwarePlayer;
import com.github.tvbox.osc.player.TrackInfo;
import com.github.tvbox.osc.player.TrackInfoBean;
import com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.HawkConfig;
import com.obsez.android.lib.filechooser.ChooserDialog;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import androidx.recyclerview.widget.GridLayoutManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.PlayerFactory;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.render.TextureRenderViewFactory;

public class KaraokeActivity extends BaseActivity {

    enum Mode { SELECT, PLAY }
    enum Tab { ALL, QUEUE, FAVORITES }

    private Mode currentMode = Mode.SELECT;
    private KaraokeSong currentPlayingSong = null;
    private KaraokeSong currentSongForLyric = null;
    private long lastPlaybackPosition = 0;
    private boolean userPaused = false;
    private int savedAudioTrackId = -1;
    private boolean pendingAudioTrackApply = false;
    private int errorCount = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingErrorPlay = null;
    private Runnable pendingAudioSwitch = null;
    private KaraokeFileScannerImpl scanCancelSignal = null;
    private KaraokeLyricLoader lyricLoader;

    private MyVideoView mVideoView;
    private KaraokeController mController;
    private KaraokeSession session;
    private SimpleSubtitleView karaokeSubtitleView;

    // Select layer views
    private LinearLayout llSelectLayer;
    private EditText etSearch;
    private TextView tvTabAll;
    private TextView tvTabQueue;
    private TextView tvTabFavorites;
    private TvRecyclerView rvArtists;
    private TvRecyclerView rvSongGrid;
    private TvRecyclerView rvQueue;
    private TvRecyclerView rvFavorites;
    private TextView tvNowPlaying;
    private TextView tvStartPlay;
    private ImageView ivPlayPauseBottom;
    private ImageView ivNextBottom;
    private LinearLayout llQRCode;
    private ImageView ivQRCode;

    // Adapters
    private KaraokeSongGridAdapter songGridAdapter;
    private KaraokeSongGridAdapter favoriteAdapter;
    private KaraokeQueueAdapter queueAdapter;
    private KaraokeArtistAdapter artistAdapter;

    // Filter state
    private String activeArtist = null;
    private String activeSearch = "";
    private Tab activeTab = Tab.ALL;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_karaoke;
    }

    @Override
    protected void init() {
        hideSystemUI(false);
        session = new KaraokeSession();
        lyricLoader = new KaraokeLyricLoader(this);

        // Video player setup
        mVideoView = findViewById(R.id.mVideoView);
        mController = new KaraokeController(this);
        mController.setCallback(new KaraokeController.KaraokeControllerCallback() {
            @Override
            public void onPrevious() {
                playPrevious();
            }

            @Override
            public void onNext() {
                playNext();
            }

            @Override
            public void onTogglePlayPause() {
                togglePlayPause();
            }

            @Override
            public void onSwitchAudioTrack() {
                switchAudioTrack();
            }

            @Override
            public void onBackToSelect() {
                enterSelectMode();
            }
        });
        mVideoView.setVideoController(mController);
        mVideoView.setPlayerFactory(new PlayerFactory<IjkmPlayer>() {
            @Override
            public IjkmPlayer createPlayer(Context context) {
                return new IjkmPlayer(context, null);
            }
        });
        mVideoView.setRenderViewFactory(TextureRenderViewFactory.create());
        mVideoView.setScreenScaleType(VideoView.SCREEN_SCALE_CENTER_CROP);

        mVideoView.setOnStateChangeListener(new VideoView.OnStateChangeListener() {
            @Override
            public void onPlayerStateChanged(int playerState) {
            }

            @Override
            public void onPlayStateChanged(int playState) {
                if (playState == VideoView.STATE_PLAYBACK_COMPLETED) {
                    errorCount = 0;
                    playNext();
                } else if (playState == VideoView.STATE_ERROR) {
                    errorCount++;
                    Toast.makeText(mContext, getString(R.string.karaoke_play_error), Toast.LENGTH_SHORT).show();
                    if (errorCount > 3) {
                        Toast.makeText(mContext, getString(R.string.karaoke_error_too_many), Toast.LENGTH_LONG).show();
                        enterSelectMode();
                        return;
                    }
                    if (pendingErrorPlay != null) mainHandler.removeCallbacks(pendingErrorPlay);
                    pendingErrorPlay = new Runnable() {
                        @Override
                        public void run() {
                            pendingErrorPlay = null;
                            playNext();
                        }
                    };
                    mainHandler.postDelayed(pendingErrorPlay, 1500);
                } else if (playState == VideoView.STATE_PLAYING) {
                    errorCount = 0;
                    if (pendingAudioTrackApply && savedAudioTrackId > 0) {
                        if (applySavedAudioTrack()) {
                            pendingAudioTrackApply = false;
                        }
                    }
                }
            }
        });

        // Select layer setup
        llSelectLayer = findViewById(R.id.llSelectLayer);
        etSearch = findViewById(R.id.etSearch);
        tvTabAll = findViewById(R.id.tvTabAll);
        tvTabQueue = findViewById(R.id.tvTabQueue);
        tvTabFavorites = findViewById(R.id.tvTabFavorites);
        rvArtists = findViewById(R.id.rvArtists);
        rvSongGrid = findViewById(R.id.rvSongGrid);
        rvQueue = findViewById(R.id.rvQueue);
        rvFavorites = findViewById(R.id.rvFavorites);
        tvNowPlaying = findViewById(R.id.tvNowPlaying);
        tvStartPlay = findViewById(R.id.tvStartPlay);
        ivPlayPauseBottom = findViewById(R.id.ivPlayPauseBottom);
        ivNextBottom = findViewById(R.id.ivNextBottom);
        llQRCode = findViewById(R.id.llQRCode);
        ivQRCode = findViewById(R.id.ivQRCode);

        // Karaoke lyric view (lives inside the play-mode controller)
        karaokeSubtitleView = mController.getLyricView();

        generateQRCode();

        initAdapters();
        initTopBar();
        initBottomBar();

        // Start in SELECT mode
        currentMode = Mode.SELECT;
        updateNowPlayingText();

        // Load saved folder or pick one
        String savedFolder = Hawk.get(HawkConfig.KARAOKE_FOLDER, "");
        if (!savedFolder.isEmpty() && new File(savedFolder).exists()) {
            loadFolder(savedFolder);
        } else {
            openFolderPicker();
        }

        KaraokeRemoteManager.get().attach(this);
    }

    private void initAdapters() {
        // Artist sidebar
        artistAdapter = new KaraokeArtistAdapter();
        rvArtists.setLayoutManager(new V7LinearLayoutManager(this));
        rvArtists.setAdapter(artistAdapter);
        artistAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (position == 0) {
                    activeArtist = null;
                } else {
                    activeArtist = artistAdapter.getItem(position);
                }
                artistAdapter.setSelectedPosition(position);
                rebuildVisibleSongs();
            }
        });

        // Song grid
        songGridAdapter = new KaraokeSongGridAdapter();
        rvSongGrid.setLayoutManager(new V7GridLayoutManager(this, 3));
        rvSongGrid.setAdapter(songGridAdapter);
        songGridAdapter.setFavoriteClickListener(new KaraokeSongGridAdapter.OnFavoriteClickListener() {
            @Override
            public void onFavoriteClick(int position, KaraokeSong song) {
                toggleFavorite(song);
            }
        });
        songGridAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                KaraokeSong song = songGridAdapter.getItem(position);
                if (song == null) return;
                if (session.isInQueue(song)) {
                    Toast.makeText(mContext, getString(R.string.karaoke_already_queued), Toast.LENGTH_SHORT).show();
                    return;
                }
                session.addToQueue(song);
                Toast.makeText(mContext, String.format(getString(R.string.karaoke_add_to_queue), song.title), Toast.LENGTH_SHORT).show();
                songGridAdapter.updateQueuedSet(session.getQueue());
                updateQueueTabCount();
                updateStartPlayButton();
            }
        });

        // Favorites grid (reuses the same adapter class)
        favoriteAdapter = new KaraokeSongGridAdapter();
        rvFavorites.setLayoutManager(new V7GridLayoutManager(this, 3));
        rvFavorites.setAdapter(favoriteAdapter);
        favoriteAdapter.setFavoriteClickListener(new KaraokeSongGridAdapter.OnFavoriteClickListener() {
            @Override
            public void onFavoriteClick(int position, KaraokeSong song) {
                toggleFavorite(song);
            }
        });
        favoriteAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                KaraokeSong song = favoriteAdapter.getItem(position);
                if (song == null) return;
                if (session.isInQueue(song)) {
                    Toast.makeText(mContext, getString(R.string.karaoke_already_queued), Toast.LENGTH_SHORT).show();
                    return;
                }
                session.addToQueue(song);
                Toast.makeText(mContext, String.format(getString(R.string.karaoke_add_to_queue), song.title), Toast.LENGTH_SHORT).show();
                favoriteAdapter.updateQueuedSet(session.getQueue());
                updateQueueTabCount();
                updateStartPlayButton();
            }
        });

        // Queue list
        queueAdapter = new KaraokeQueueAdapter();
        queueAdapter.setItemClickListener(new KaraokeQueueAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                // Play the selected queue song
                session.playAt(position);
                playSong(session.getCurrentSong());
                enterPlayMode(session.getCurrentSong());
            }
        });
        queueAdapter.setDeleteListener(new KaraokeQueueAdapter.OnItemDeleteListener() {
            @Override
            public void onItemDelete(int position) {
                onRemoveFromQueue(position);
            }
        });
        queueAdapter.setFavoriteClickListener(new KaraokeQueueAdapter.OnFavoriteClickListener() {
            @Override
            public void onFavoriteClick(int position, KaraokeSong song) {
                toggleFavorite(song);
            }
        });
        rvQueue.setLayoutManager(new V7LinearLayoutManager(this));
        rvQueue.setAdapter(queueAdapter);
    }

    private void initTopBar() {
        // Back button
        findViewById(R.id.ivBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Change folder
        findViewById(R.id.tvChangeFolder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFolderPicker();
            }
        });

        // Search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                activeSearch = s.toString().trim();
                rebuildVisibleSongs();
            }
        });

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    etSearch.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });

        // Tab: All
        tvTabAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(Tab.ALL);
            }
        });

        // Tab: Queue
        tvTabQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(Tab.QUEUE);
            }
        });

        // Tab: Favorites
        tvTabFavorites.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(Tab.FAVORITES);
            }
        });

        // Settings (recursive scan + lyric toggle)
        findViewById(R.id.tvSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openKaraokeSettings();
            }
        });

        updateQueueTabCount();
    }

    private void initBottomBar() {
        tvStartPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (session.getQueueSize() == 0) return;
                if (currentPlayingSong != null && !userPaused) {
                    // Resume from where we left off
                    enterPlayMode(currentPlayingSong);
                } else if (currentPlayingSong != null && userPaused) {
                    enterPlayMode(currentPlayingSong);
                } else {
                    // Start from beginning of queue
                    session.playAt(0);
                    playSong(session.getCurrentSong());
                    enterPlayMode(session.getCurrentSong());
                }
            }
        });

        ivPlayPauseBottom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPlayingSong != null) {
                    if (userPaused) {
                        userPaused = false;
                        enterPlayMode(currentPlayingSong);
                    } else {
                        enterPlayMode(currentPlayingSong);
                    }
                }
            }
        });

        ivNextBottom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNextFromSelect();
            }
        });
    }

    // ======================== Focus Graph ========================

    /**
     * Recomputes the bottom-bar focus graph based on whether the bottom controls are visible.
     * When the bottom controls are GONE (no current song), the middle RVs set nextFocusDown
     * to themselves so the focus doesn't fall back to geometric FocusFinder behavior.
     * Also handles empty list states for both the All and Queue tabs.
     */
    private void updateFocusGraph() {
        boolean bottomVisible = tvStartPlay.isShown();

        // Middle → bottom (down)
        if (bottomVisible) {
            rvArtists.setNextFocusDownId(R.id.tvStartPlay);
            rvSongGrid.setNextFocusDownId(R.id.tvStartPlay);
            rvQueue.setNextFocusDownId(R.id.tvStartPlay);
            rvFavorites.setNextFocusDownId(R.id.tvStartPlay);
        } else {
            // Self-loop so focus stays in the RV container when bottom is hidden
            rvArtists.setNextFocusDownId(R.id.rvArtists);
            rvSongGrid.setNextFocusDownId(R.id.rvSongGrid);
            rvQueue.setNextFocusDownId(R.id.rvQueue);
            rvFavorites.setNextFocusDownId(R.id.rvFavorites);
        }

        // Top → middle (down): depends on which tab and list content.
        int topDownTarget;
        if (activeTab == Tab.QUEUE) {
            topDownTarget = queueAdapter.getItemCount() == 0 ? R.id.tvTabQueue : R.id.rvQueue;
        } else if (activeTab == Tab.FAVORITES) {
            topDownTarget = favoriteAdapter.getItemCount() == 0 ? R.id.tvTabFavorites : R.id.rvFavorites;
        } else {
            topDownTarget = songGridAdapter.getItemCount() == 0 ? R.id.tvChangeFolder : R.id.rvSongGrid;
        }
        etSearch.setNextFocusDownId(topDownTarget);
        tvTabAll.setNextFocusDownId(topDownTarget);
        tvTabQueue.setNextFocusDownId(topDownTarget);
        tvTabFavorites.setNextFocusDownId(topDownTarget);
        findViewById(R.id.tvSettings).setNextFocusDownId(topDownTarget);
        findViewById(R.id.tvChangeFolder).setNextFocusDownId(topDownTarget);

        // Bottom → middle (up): also close the queue/all cycle
        int bottomUpTarget;
        if (activeTab == Tab.QUEUE) {
            bottomUpTarget = R.id.rvQueue;
        } else if (activeTab == Tab.FAVORITES) {
            bottomUpTarget = R.id.rvFavorites;
        } else {
            bottomUpTarget = R.id.rvSongGrid;
        }
        tvStartPlay.setNextFocusUpId(bottomUpTarget);
        ivPlayPauseBottom.setNextFocusUpId(bottomUpTarget);
        ivNextBottom.setNextFocusUpId(bottomUpTarget);
    }

    /**
     * Builds the up-to-3 next-up title list from the session queue and pushes it to the controller.
     */
    private void pushNextUpToController() {
        if (session == null) {
            mController.setNextUp(Collections.emptyList());
            return;
        }
        List<KaraokeSong> queue = session.getQueue();
        int curr = session.getCurrentQueueIndex();
        List<String> next = new ArrayList<>();
        if (queue != null && curr >= 0) {
            for (int i = curr + 1; i < queue.size() && next.size() < 3; i++) {
                KaraokeSong s = queue.get(i);
                if (s == null) continue;
                next.add(s.displayName != null ? s.displayName : s.title);
            }
        }
        mController.setNextUp(next);
    }

    // ======================== Mode Switching ========================

    private void enterSelectMode() {
        if (currentMode == Mode.SELECT) return;
        currentMode = Mode.SELECT;

        if (mVideoView.isPlaying()) {
            mVideoView.pause();
        }
        lastPlaybackPosition = mVideoView.getCurrentPosition();
        persistCurrentPlaybackPosition();

        // Cancel pending lyric loads so stale callbacks don't fire on the select screen.
        if (lyricLoader != null) lyricLoader.cancelAll();
        currentSongForLyric = null;
        if (karaokeSubtitleView != null) {
            karaokeSubtitleView.setVisibility(View.GONE);
            karaokeSubtitleView.reset();
        }

        mVideoView.setVisibility(View.GONE);
        llSelectLayer.setVisibility(View.VISIBLE);
        llQRCode.setVisibility(View.VISIBLE);

        updateNowPlayingText();
        updateStartPlayButton();
        updateQueueTabCount();

        if (activeTab == Tab.QUEUE) {
            queueAdapter.setNewDiffData(session.getQueue());
            queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
        } else if (activeTab == Tab.FAVORITES) {
            loadFavorites();
        } else {
            songGridAdapter.updateQueuedSet(session.getQueue());
        }
    }

    private void enterPlayMode(KaraokeSong song) {
        if (song == null) return;
        currentMode = Mode.PLAY;
        userPaused = false;

        // Same song and has saved position → resume
        if (currentPlayingSong != null
                && song.equals(currentPlayingSong)
                && lastPlaybackPosition > 0) {
            mVideoView.setVisibility(View.VISIBLE);
            llSelectLayer.setVisibility(View.GONE);
            llQRCode.setVisibility(View.GONE);
            mVideoView.seekTo(lastPlaybackPosition);
            mVideoView.start();
            mController.setSongTitle(currentPlayingSong.displayName);
            pushNextUpToController();
            startLyricForSong(currentPlayingSong);
        } else {
            // New song — pull history for resume position
            KaraokeSong loaded = song;
            long resumeMs = 0;
            com.github.tvbox.osc.cache.KaraokeHistory h = RoomDataManger.getKaraokeHistory(song.filePath);
            if (h != null && h.playbackPosition > 5000 && h.duration > h.playbackPosition + 1000) {
                resumeMs = h.playbackPosition;
                loaded.duration = h.duration;
            }
            loaded.favorite = RoomDataManger.isKaraokeFavorite(song.filePath);
            currentPlayingSong = loaded;
            lastPlaybackPosition = resumeMs;
            pendingAudioTrackApply = true;
            mVideoView.release();
            mVideoView.setUrl(loaded.filePath);
            mVideoView.setVisibility(View.VISIBLE);
            llSelectLayer.setVisibility(View.GONE);
            llQRCode.setVisibility(View.GONE);
            mVideoView.start();
            mController.setSongTitle(loaded.displayName);
            mController.setTrackInfo("");
            queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
            pushNextUpToController();
            recordKaraokeHistory(loaded, resumeMs);
            startLyricForSong(loaded);
        }
    }

    private void startLyricForSong(KaraokeSong song) {
        currentSongForLyric = song;
        boolean lyricEnabled = Hawk.get(HawkConfig.KARAOKE_LYRIC_ENABLED, true);
        if (!lyricEnabled || lyricLoader == null || karaokeSubtitleView == null) {
            karaokeSubtitleView.setVisibility(View.GONE);
            return;
        }
        lyricLoader.loadFor(song, new KaraokeLyricLoader.Callback() {
            @Override
            public void onLyricReady(KaraokeSong requested, com.github.tvbox.osc.subtitle.model.TimedTextObject tto) {
                if (currentSongForLyric == null || !currentSongForLyric.equals(requested)) return;
                karaokeSubtitleView.bindToMediaPlayer(mVideoView.getMediaPlayer());
                // FormatLRC.toFile() emits real `[mm:ss.xx]lyric` lines so SubtitleLoader's
                // extension-based dispatch routes the temp file back to FormatLRC.
                try {
                    File tmp = new File(getCacheDir(), "karaoke_lyric_" + requested.filePath.hashCode() + ".lrc");
                    String[] lines = tto != null ? new com.github.tvbox.osc.subtitle.format.FormatLRC().toFile(tto) : null;
                    if (lines == null || lines.length == 0) {
                        karaokeSubtitleView.setVisibility(View.GONE);
                        return;
                    }
                    java.io.PrintWriter pw = new java.io.PrintWriter(tmp, "UTF-8");
                    try {
                        for (String s : lines) pw.println(s);
                    } finally {
                        pw.close();
                    }
                    karaokeSubtitleView.setSubtitlePath(tmp.getAbsolutePath());
                    karaokeSubtitleView.setVisibility(View.VISIBLE);
                } catch (Throwable t) {
                    karaokeSubtitleView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNoLyric(KaraokeSong requested) {
                if (currentSongForLyric == null || !currentSongForLyric.equals(requested)) return;
                if (karaokeSubtitleView != null) {
                    karaokeSubtitleView.setVisibility(View.GONE);
                    karaokeSubtitleView.reset();
                }
            }
        });
    }

    private void recordKaraokeHistory(final KaraokeSong song, final long playbackPosition) {
        if (song == null || song.filePath == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                RoomDataManger.insertKaraokeHistory(song, playbackPosition);
            }
        }).start();
    }

    private void persistCurrentPlaybackPosition() {
        final KaraokeSong song = currentPlayingSong;
        if (song == null || song.filePath == null) return;
        final long position = lastPlaybackPosition;
        new Thread(new Runnable() {
            @Override
            public void run() {
                RoomDataManger.updateKaraokePlaybackPosition(song.filePath, position);
            }
        }).start();
    }

    // ======================== Playback ========================

    private void playSong(KaraokeSong song) {
        if (song == null) return;
        currentPlayingSong = song;
        lastPlaybackPosition = 0;
        userPaused = false;
        pendingAudioTrackApply = true;
        mVideoView.release();
        mVideoView.setUrl(song.filePath);
        mVideoView.start();
        mController.setSongTitle(song.displayName);
        mController.setTrackInfo("");
        queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
        pushNextUpToController();
        recordKaraokeHistory(song, 0);
        startLyricForSong(song);
    }

    private void playNext() {
        KaraokeSong next = session.playNext();
        if (next != null) {
            playSong(next);
        } else {
            // Queue finished
            currentPlayingSong = null;
            lastPlaybackPosition = 0;
            enterSelectMode();
        }
    }

    private void playPrevious() {
        KaraokeSong prev = session.playPrevious();
        if (prev != null) {
            playSong(prev);
        } else {
            Toast.makeText(mContext, getString(R.string.karaoke_no_more), Toast.LENGTH_SHORT).show();
        }
    }

    private void playNextFromSelect() {
        if (currentPlayingSong == null) return;
        KaraokeSong next = session.playNext();
        if (next != null) {
            playSong(next);
            enterPlayMode(next);
        } else {
            Toast.makeText(mContext, getString(R.string.karaoke_no_more), Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlayPause() {
        if (mVideoView.isPlaying()) {
            mVideoView.pause();
            userPaused = true;
        } else {
            mVideoView.resume();
            userPaused = false;
        }
    }

    // ======================== Queue Operations ========================

    private void onRemoveFromQueue(int position) {
        KaraokeSession.RemoveResult result = session.removeFromQueue(position);
        queueAdapter.setNewDiffData(session.getQueue());
        queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
        updateQueueTabCount();
        updateStartPlayButton();
        songGridAdapter.updateQueuedSet(session.getQueue());
        updateFocusGraph();

        switch (result.action) {
            case SWITCH_TO_SONG:
                if (currentMode == Mode.PLAY) {
                    playSong(result.nextSong);
                    enterPlayMode(result.nextSong);
                } else {
                    // SELECT mode: update state but don't start playback
                    currentPlayingSong = result.nextSong;
                    lastPlaybackPosition = 0;
                    queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
                    updateNowPlayingText();
                    updateStartPlayButton();
                }
                break;
            case RETURN_TO_SELECT:
                currentPlayingSong = null;
                lastPlaybackPosition = 0;
                updateNowPlayingText();
                updateStartPlayButton();
                if (currentMode == Mode.PLAY) {
                    mController.setNextUp(Collections.emptyList());
                    enterSelectMode();
                }
                break;
            case KEEP_PLAYING:
                if (currentMode == Mode.PLAY) {
                    pushNextUpToController();
                }
                break;
        }
    }

    // ======================== UI Helpers ========================

    private void generateQRCode() {
        String address = ControlManager.get().getAddress(false) + "karaoke";
        Bitmap bitmap = QRCodeGen.generateBitmap(address, 240, 240, 1);
        if (bitmap != null) {
            ivQRCode.setImageBitmap(bitmap);
        }
    }

    private void switchTab(Tab tab) {
        activeTab = tab;
        rvSongGrid.setVisibility(tab == Tab.ALL ? View.VISIBLE : View.GONE);
        rvQueue.setVisibility(tab == Tab.QUEUE ? View.VISIBLE : View.GONE);
        rvFavorites.setVisibility(tab == Tab.FAVORITES ? View.VISIBLE : View.GONE);
        tvTabAll.setSelected(tab == Tab.ALL);
        tvTabQueue.setSelected(tab == Tab.QUEUE);
        tvTabFavorites.setSelected(tab == Tab.FAVORITES);
        if (tab == Tab.QUEUE) {
            queueAdapter.setNewDiffData(session.getQueue());
            queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
        } else if (tab == Tab.ALL) {
            rebuildVisibleSongs();
        } else if (tab == Tab.FAVORITES) {
            loadFavorites();
        }
        updateFocusGraph();
    }

    private void loadFavorites() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<KaraokeSong> favorites = new ArrayList<>();
                try {
                    for (com.github.tvbox.osc.cache.KaraokeFavorite f : RoomDataManger.getKaraokeFavorites()) {
                        favorites.add(KaraokeSong.fromFavorite(f));
                    }
                } catch (Throwable ignore) {
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        session.setFavorites(favorites);
                        favoriteAdapter.setNewDiffData(favorites);
                        favoriteAdapter.updateQueuedSet(session.getQueue());
                        updateFocusGraph();
                    }
                });
            }
        }).start();
    }

    private void toggleFavorite(final KaraokeSong song) {
        if (song == null || song.filePath == null) return;
        final String path = song.filePath;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean isFav = RoomDataManger.toggleKaraokeFavorite(song);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Synchronize the favorite flag across every backing dataset that may hold
                        // a different KaraokeSong instance for the same path. Queue items are added
                        // by reference from the library; favorites-grid items are re-hydrated from
                        // Room rows. Without touching each dataset, the heart on the unchanged rows
                        // keeps showing the stale value after a notifyItemChanged rebind.
                        applyFavoriteTo(session.getLibrary(), path, isFav);
                        applyFavoriteTo(session.getQueue(), path, isFav);
                        applyFavoriteTo(session.getFavorites(), path, isFav);
                        applyFavoriteTo(favoriteAdapter.getData(), path, isFav);
                        applyFavoriteTo(queueAdapter.getData(), path, isFav);
                        // Targeted refresh: only notify rows whose song matches this path so D-pad
                        // focus is preserved across the whole library grid + queue + favorites grid.
                        songGridAdapter.notifyFavoriteChanged(path);
                        favoriteAdapter.notifyFavoriteChanged(path);
                        queueAdapter.notifyFavoriteChanged(path);
                        Toast.makeText(mContext, getString(isFav
                                ? R.string.karaoke_favorite_added
                                : R.string.karaoke_favorite_removed), Toast.LENGTH_SHORT).show();
                        if (activeTab == Tab.FAVORITES) loadFavorites();
                    }
                });
            }
        }).start();
    }

    private static void applyFavoriteTo(List<KaraokeSong> list, String path, boolean fav) {
        if (list == null || path == null) return;
        for (KaraokeSong s : list) {
            if (s != null && path.equals(s.filePath)) {
                s.favorite = fav;
            }
        }
    }

    private void rebuildVisibleSongs() {
        List<KaraokeSong> library = session.getLibrary();
        List<KaraokeSong> filtered = new ArrayList<>();
        for (KaraokeSong song : library) {
            if (activeArtist != null && !activeArtist.equals(song.artist)) {
                continue;
            }
            if (!activeSearch.isEmpty()) {
                String search = activeSearch.toLowerCase();
                boolean matchTitle = song.title != null && song.title.toLowerCase().contains(search);
                boolean matchArtist = song.artist != null && song.artist.toLowerCase().contains(search);
                if (!matchTitle && !matchArtist) continue;
            }
            filtered.add(song);
        }
        songGridAdapter.setNewDiffData(filtered);
        songGridAdapter.updateQueuedSet(session.getQueue());
        updateFocusGraph();
    }

    private void updateQueueTabCount() {
        int count = session.getQueueSize();
        tvTabQueue.setText(String.format(getString(R.string.karaoke_queued_songs), count));
    }

    private void openKaraokeSettings() {
        final SelectDialog<String> dialog = new SelectDialog<>(this);
        dialog.setTip(getString(R.string.karaoke_settings));
        final String optRecursive = formatToggle(R.string.karaoke_settings_scan_recursive,
                Hawk.get(HawkConfig.KARAOKE_SCAN_RECURSIVE, true));
        final String optLyric = formatToggle(R.string.karaoke_settings_lyric,
                Hawk.get(HawkConfig.KARAOKE_LYRIC_ENABLED, true));
        final String optRescan = getString(R.string.karaoke_settings_rescan);
        final String[] items = new String[] { optRecursive, optLyric, optRescan };
        final boolean[] state = new boolean[] {
                Hawk.get(HawkConfig.KARAOKE_SCAN_RECURSIVE, true),
                Hawk.get(HawkConfig.KARAOKE_LYRIC_ENABLED, true),
                false
        };
        java.util.List<String> data = new java.util.ArrayList<>(java.util.Arrays.asList(items));
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                dialog.dismiss();
                if (pos == 0) {
                    boolean newVal = !state[0];
                    Hawk.put(HawkConfig.KARAOKE_SCAN_RECURSIVE, newVal);
                    Toast.makeText(mContext, formatToggle(R.string.karaoke_settings_scan_recursive, newVal),
                            Toast.LENGTH_SHORT).show();
                    // Setting change invalidates cached signature; reload to apply.
                    String folder = Hawk.get(HawkConfig.KARAOKE_FOLDER, "");
                    if (!folder.isEmpty() && new File(folder).exists()) {
                        loadFolder(folder);
                    }
                } else if (pos == 1) {
                    boolean newVal = !state[1];
                    Hawk.put(HawkConfig.KARAOKE_LYRIC_ENABLED, newVal);
                    Toast.makeText(mContext, formatToggle(R.string.karaoke_settings_lyric, newVal),
                            Toast.LENGTH_SHORT).show();
                    if (!newVal && currentMode == Mode.PLAY && karaokeSubtitleView != null) {
                        karaokeSubtitleView.setVisibility(View.GONE);
                        karaokeSubtitleView.reset();
                        if (lyricLoader != null) lyricLoader.cancelAll();
                    } else if (newVal && currentMode == Mode.PLAY && currentPlayingSong != null) {
                        startLyricForSong(currentPlayingSong);
                    }
                } else if (pos == 2) {
                    String folder = Hawk.get(HawkConfig.KARAOKE_FOLDER, "");
                    if (!folder.isEmpty() && new File(folder).exists()) {
                        stopPlaybackAndReturnToSelect();
                        loadFolder(folder);
                    } else {
                        Toast.makeText(mContext, getString(R.string.karaoke_error_no_folder), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }
        }, data, 0);
        dialog.show();
    }

    private String formatToggle(int resId, boolean on) {
        String base = getString(resId);
        String suffix = on ? ": ON" : ": OFF";
        return base + suffix;
    }

    private void updateStartPlayButton() {
        int queueSize = session.getQueueSize();
        if (queueSize > 0) {
            tvStartPlay.setVisibility(View.VISIBLE);
            if (currentPlayingSong != null && lastPlaybackPosition > 0) {
                tvStartPlay.setText(getString(R.string.karaoke_continue_play));
            } else {
                tvStartPlay.setText(String.format(getString(R.string.karaoke_start_play), queueSize));
            }
        } else {
            tvStartPlay.setVisibility(View.GONE);
        }

        // Show bottom play controls only when there's a current song
        if (currentPlayingSong != null) {
            ivPlayPauseBottom.setVisibility(View.VISIBLE);
            ivNextBottom.setVisibility(View.VISIBLE);
            ivPlayPauseBottom.setImageResource(userPaused ? R.drawable.v_play : R.drawable.v_pause);
        } else {
            ivPlayPauseBottom.setVisibility(View.GONE);
            ivNextBottom.setVisibility(View.GONE);
        }
        updateFocusGraph();
    }

    private void updateNowPlayingText() {
        if (currentPlayingSong != null) {
            tvNowPlaying.setText(String.format(getString(R.string.karaoke_now_playing), currentPlayingSong.title));
        } else {
            tvNowPlaying.setText(getString(R.string.karaoke_not_playing));
        }
    }

    // ======================== Remote Reload ========================

    void remoteReloadFolder() {
        String folderPath = Hawk.get(HawkConfig.KARAOKE_FOLDER, "");
        if (folderPath.isEmpty() || !new File(folderPath).exists()) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopPlaybackAndReturnToSelect();
                loadFolder(folderPath);
            }
        });
    }

    void stopPlaybackAndReturnToSelect() {
        if (currentMode == Mode.PLAY) {
            if (mVideoView != null) {
                mVideoView.pause();
                mVideoView.release();
            }
            currentPlayingSong = null;
            lastPlaybackPosition = 0;
            userPaused = false;
            enterSelectMode();
        }
    }

    // ======================== Folder Loading ========================

    private void openFolderPicker() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (App.getInstance().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(KaraokeActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return;
            }
        }
        ChooserDialog dialog = new ChooserDialog(mContext, R.style.FileChooserStyle);
        dialog
                .withStringResources(getString(R.string.karaoke_pick_folder), getString(R.string.karaoke_confirm), getString(R.string.karaoke_cancel))
                .titleFollowsDir(true)
                .displayPath(true)
                .enableDpad(true)
                .withFilter(true, true)
                .withChosenListener(new ChooserDialog.Result() {
                    @Override
                    public void onChoosePath(String dir, File dirFile) {
                        String absPath = dirFile.getAbsolutePath();
                        Hawk.put(HawkConfig.KARAOKE_FOLDER, absPath);
                        loadFolder(absPath);
                    }
                }).show();
    }

    void loadFolder(final String folderPath) {
        if (scanCancelSignal instanceof KaraokeFileScannerImpl) {
            scanCancelSignal.cancel();
        }
        scanCancelSignal = new KaraokeFileScannerImpl();
        final KaraokeFileScanner.CancelSignal signal = scanCancelSignal;
        final boolean recursive = Hawk.get(HawkConfig.KARAOKE_SCAN_RECURSIVE, true);

        // Unified cache key + signature key, used by CacheManager + Hawk.
        final File folder = new File(folderPath);
        final String cacheKey;
        try {
            cacheKey = "karaoke_lib_" + Integer.toHexString(folder.getCanonicalPath().hashCode()) + (recursive ? "_r" : "_f");
        } catch (Exception e) {
            return;
        }
        final String sigKey = cacheKey + "_sig";

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Quick signature pass — only File metadata, no parsing.
                final long newSig = KaraokeFileScanner.computeSignature(folder, recursive);
                final Long oldSig = Hawk.get(sigKey, (Long) null);

                List<KaraokeSong> songs = null;
                if (oldSig != null && oldSig == newSig) {
                    // Cache hit — try to restore the library without rescanning.
                    try {
                        Object cached = com.github.tvbox.osc.cache.CacheManager.getCache(cacheKey);
                        if (cached instanceof List) {
                            //noinspection unchecked
                            songs = (List<KaraokeSong>) cached;
                        }
                    } catch (Throwable ignore) {
                    }
                }

                if (songs == null) {
                    songs = KaraokeFileScanner.scanFolder(folder, recursive, signal);
                    if (signal.isCanceled()) return;
                    try {
                        com.github.tvbox.osc.cache.CacheManager.save(cacheKey, songs);
                        Hawk.put(sigKey, newSig);
                    } catch (Throwable ignore) {
                    }
                }

                final List<KaraokeSong> finalSongs = songs;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (signal.isCanceled()) return;
                        // Changing folder resets everything, even when empty
                        session.setLibrary(finalSongs);
                        session.clearQueue();
                        currentPlayingSong = null;
                        lastPlaybackPosition = 0;
                        userPaused = false;
                        activeArtist = null;
                        activeSearch = "";

                        if (finalSongs.isEmpty()) {
                            artistAdapter.setNewData(new ArrayList<String>());
                            artistAdapter.setSelectedPosition(0);
                            songGridAdapter.setNewDiffData(new ArrayList<KaraokeSong>());
                            switchTab(Tab.ALL);
                            updateQueueTabCount();
                            updateStartPlayButton();
                            updateNowPlayingText();
                            Toast.makeText(mContext, getString(R.string.karaoke_no_videos), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Hydrate library with persisted favorite / playback-position info so the
                        // grid and queue views show heart badges and resume suggestions correctly.
                        for (KaraokeSong s : finalSongs) {
                            try {
                                com.github.tvbox.osc.cache.KaraokeHistory h = RoomDataManger.getKaraokeHistory(s.filePath);
                                if (h != null) {
                                    s.duration = h.duration;
                                    s.playbackPosition = h.playbackPosition;
                                }
                                s.favorite = RoomDataManger.isKaraokeFavorite(s.filePath);
                            } catch (Throwable ignore) {
                            }
                        }

                        // Setup artist sidebar
                        List<String> artists = new ArrayList<>();
                        artists.add(getString(R.string.karaoke_all_artists));
                        artists.addAll(session.getArtists());
                        artistAdapter.setNewData(artists);
                        artistAdapter.setSelectedPosition(0);

                        // Setup song grid
                        songGridAdapter.setNewDiffData(finalSongs);
                        songGridAdapter.updateQueuedSet(session.getQueue());

                        // Reset to all songs tab
                        switchTab(Tab.ALL);
                        tvTabAll.requestFocus();
                        updateQueueTabCount();
                        updateStartPlayButton();
                        updateNowPlayingText();
                    }
                });
            }
        }).start();
    }

    /** Concrete CancelSignal implementation so loadFolder can cancel an in-flight scan. */
    private static class KaraokeFileScannerImpl implements KaraokeFileScanner.CancelSignal {
        private volatile boolean cancelled = false;

        @Override
        public boolean isCanceled() {
            return cancelled;
        }

        public void cancel() {
            cancelled = true;
        }
    }

    // ======================== Audio Track Switching ========================

    private void switchAudioTrack() {
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        if (!(mediaPlayer instanceof TrackAwarePlayer)) {
            Toast.makeText(mContext, getString(R.string.karaoke_ijk_only), Toast.LENGTH_SHORT).show();
            return;
        }
        final TrackAwarePlayer trackPlayer = (TrackAwarePlayer) mediaPlayer;
        final AbstractPlayer absPlayer = mediaPlayer;
        TrackInfo trackInfo = trackPlayer.getTrackInfo();
        if (trackInfo == null) {
            Toast.makeText(mContext, getString(R.string.karaoke_no_audio_track), Toast.LENGTH_SHORT).show();
            return;
        }
        final List<TrackInfoBean> audioTracks = trackInfo.getAudio();
        if (audioTracks.isEmpty()) {
            Toast.makeText(mContext, getString(R.string.karaoke_no_audio_track), Toast.LENGTH_SHORT).show();
            return;
        }
        int selected = 0;
        for (int i = 0; i < audioTracks.size(); i++) {
            if (audioTracks.get(i).selected) {
                selected = i;
                break;
            }
        }
        SelectDialog<TrackInfoBean> dialog = new SelectDialog<>(this);
        dialog.setTip(getString(R.string.karaoke_select_audio_track));
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<TrackInfoBean>() {
            @Override
            public void click(TrackInfoBean value, int pos) {
                savedAudioTrackId = value.trackId;
                try {
                    for (TrackInfoBean audio : audioTracks) {
                        audio.selected = audio.trackId == value.trackId;
                    }
                    absPlayer.pause();
                    final long progress = absPlayer.getCurrentPosition();
                    trackPlayer.setTrack(value.trackId);
                    mController.setTrackInfo(value.name);
                    if (pendingAudioSwitch != null) mainHandler.removeCallbacks(pendingAudioSwitch);
                    pendingAudioSwitch = new Runnable() {
                        @Override
                        public void run() {
                            pendingAudioSwitch = null;
                            absPlayer.seekTo(progress);
                            absPlayer.start();
                        }
                    };
                    mainHandler.postDelayed(pendingAudioSwitch, 800);
                    dialog.dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public String getDisplay(TrackInfoBean val) {
                return val.name;
            }
        }, new DiffUtil.ItemCallback<TrackInfoBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull TrackInfoBean oldItem, @NonNull TrackInfoBean newItem) {
                return oldItem.trackId == newItem.trackId;
            }

            @Override
            public boolean areContentsTheSame(@NonNull TrackInfoBean oldItem, @NonNull TrackInfoBean newItem) {
                return oldItem.trackId == newItem.trackId;
            }
        }, audioTracks, selected);
        dialog.show();
    }

    // ======================== Key Handling ========================

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (currentMode == Mode.PLAY) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                enterSelectMode();
                return true;
            }
            if (mController.handleKeyEvent(event)) {
                if (currentMode == Mode.PLAY) {
                    mController.show();
                }
                return true;
            }
        } else if (currentMode == Mode.SELECT
                && event.getAction() == KeyEvent.ACTION_DOWN
                && etSearch != null && etSearch.hasFocus()) {
            // EditText swallows D-pad arrows for cursor movement once it has focus, so the
            // nextFocusLeft/Right attributes on etSearch never fire and the user is stuck
            // (must press BACK to escape). Intercept here and route focus explicitly.
            int direction = keyCodeToFocusDirection(event.getKeyCode());
            if (direction != 0) {
                View next = etSearch.focusSearch(direction);
                if (next != null && next != etSearch && next.isFocusable()) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                    etSearch.clearFocus();
                    next.requestFocus();
                    return true;
                }
            }
        } else if (currentMode == Mode.SELECT
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN
                && currentPlayingSong == null) {
            // Bottom controls are hidden — prevent focus escaping to geometric fallback,
            // but only when the focused RV item is already at the bottom edge (or the RV
            // is empty), so normal intra-RV downward navigation still works.
            if (shouldConsumeDownAtRvBottom()) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static int keyCodeToFocusDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            default: return 0;
        }
    }

    /**
     * Returns true when the currently focused view sits at the bottom edge of one of our
     * TvRecyclerViews (or that RV is empty), and the bottom bar is therefore unreachable.
     * Used by {@link #dispatchKeyEvent} to defensively swallow DPAD_DOWN rather than let
     * Android's geometric FocusFinder pick an arbitrary target.
     *
     * For grids (multiple spans) every item in the last row counts as bottom-edge, not
     * just the final adapter position.
     */
    private boolean shouldConsumeDownAtRvBottom() {
        View focused = getCurrentFocus();
        if (focused == null) return false;
        TvRecyclerView rv = findContainingTvRv(focused);
        if (rv == null) return false;
        BaseQuickAdapter<?, ?> adapter = (BaseQuickAdapter<?, ?>) rv.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0) {
            // RV is empty: nothing to navigate within, so swallow to keep focus put
            return true;
        }
        // focused may be a grandchild (itemMain / ivFavorite / ivDelete) — resolve to the
        // direct RV child so getChildAdapterPosition returns a real position, not NO_POSITION.
        View directChild = rv.getFocusedChild();
        if (directChild == null) return false;
        int pos = rv.getChildAdapterPosition(directChild);
        // pos == -1 happens when the focused view is no longer attached; treat as bottom
        if (pos == -1) return true;

        int totalItems = adapter.getItemCount();
        int spanCount = 1;
        if (rv.getLayoutManager() instanceof GridLayoutManager) {
            spanCount = ((GridLayoutManager) rv.getLayoutManager()).getSpanCount();
        }
        int lastRowStart = ((totalItems - 1) / spanCount) * spanCount;
        return pos >= lastRowStart;
    }

    private TvRecyclerView findContainingTvRv(View view) {
        android.view.ViewParent p = view.getParent();
        while (p instanceof View) {
            if (p == rvArtists) return rvArtists;
            if (p == rvSongGrid) return rvSongGrid;
            if (p == rvQueue) return rvQueue;
            p = p.getParent();
        }
        return null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (currentMode == Mode.SELECT) {
            return handleSelectModeKey(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean handleSelectModeKey(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // If search has focus, clear it
            if (etSearch.hasFocus()) {
                etSearch.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                return true;
            }
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            if (activeTab == Tab.QUEUE) {
                // In queue tab: delete focused item
                View focused = rvQueue.getFocusedChild();
                if (focused != null) {
                    int pos = rvQueue.getChildAdapterPosition(focused);
                    if (pos >= 0) {
                        onRemoveFromQueue(pos);
                        return true;
                    }
                }
            }
            switchTab(Tab.QUEUE);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            etSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ======================== Lifecycle ========================

    @Override
    protected void onResume() {
        super.onResume();
        setScreenOn();
        if (mVideoView != null && currentMode == Mode.PLAY && !userPaused) {
            mVideoView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        setScreenOff();
        if (mVideoView != null && currentMode == Mode.PLAY) {
            mVideoView.pause();
            lastPlaybackPosition = mVideoView.getCurrentPosition();
            persistCurrentPlaybackPosition();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanCancelSignal instanceof KaraokeFileScannerImpl) {
            ((KaraokeFileScannerImpl) scanCancelSignal).cancel();
        }
        if (lyricLoader != null) lyricLoader.cancelAll();
        if (pendingErrorPlay != null) mainHandler.removeCallbacks(pendingErrorPlay);
        if (pendingAudioSwitch != null) mainHandler.removeCallbacks(pendingAudioSwitch);
        mainHandler.removeCallbacksAndMessages(null);
        if (mController != null) mController.release();
        KaraokeRemoteManager.get().detach();
        if (mVideoView != null) mVideoView.release();
        if (session != null) session.reset();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openFolderPicker();
            } else {
                Toast.makeText(mContext, getString(R.string.karaoke_no_perm), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // ======================== Remote Control Bridge ========================

    private Map<String, String> songToMap(KaraokeSong song) {
        Map<String, String> item = new HashMap<>();
        if (song != null) {
            item.put("filePath", song.filePath);
            item.put("title", song.title);
            item.put("artist", song.artist);
            item.put("displayName", song.displayName);
        }
        return item;
    }

    Map<String, Object> getRemoteState() {
        Map<String, Object> state = new HashMap<>();
        state.put("active", true);
        state.put("mode", currentMode.name());
        state.put("playing", mVideoView != null && mVideoView.isPlaying());
        state.put("currentSong", currentPlayingSong != null ? songToMap(currentPlayingSong) : null);
        state.put("position", mVideoView != null ? mVideoView.getCurrentPosition() : 0);
        state.put("duration", mVideoView != null ? mVideoView.getDuration() : 0);
        state.put("queueSize", session != null ? session.getQueueSize() : 0);
        state.put("currentIndex", session != null ? session.getCurrentQueueIndex() : -1);
        return state;
    }

    KaraokeRemoteManager.LibrarySnapshot getRemoteLibrary(String search, String artist) {
        if (session == null) return new KaraokeRemoteManager.LibrarySnapshot(new ArrayList<>(), new ArrayList<>());
        List<KaraokeSong> library = session.getLibrary();
        List<String> artists = session.getArtists();

        List<Map<String, String>> songs = new ArrayList<>();
        for (KaraokeSong song : library) {
            if (artist != null && !artist.isEmpty() && !artist.equals(song.artist)) continue;
            if (search != null && !search.isEmpty()) {
                String q = search.toLowerCase();
                boolean matchTitle = song.title != null && song.title.toLowerCase().contains(q);
                boolean matchArtist = song.artist != null && song.artist.toLowerCase().contains(q);
                if (!matchTitle && !matchArtist) continue;
            }
            songs.add(songToMap(song));
        }
        return new KaraokeRemoteManager.LibrarySnapshot(songs, new ArrayList<>(artists));
    }

    KaraokeRemoteManager.QueueSnapshot getRemoteQueue() {
        if (session == null) return new KaraokeRemoteManager.QueueSnapshot(new ArrayList<>(), -1);
        List<KaraokeSong> queue = new ArrayList<>(session.getQueue());
        List<Map<String, String>> queueList = new ArrayList<>();
        for (KaraokeSong song : queue) {
            queueList.add(songToMap(song));
        }
        return new KaraokeRemoteManager.QueueSnapshot(queueList, session.getCurrentQueueIndex());
    }

    void remoteTogglePlayPause() {
        if (currentMode == Mode.SELECT && currentPlayingSong != null) {
            enterPlayMode(currentPlayingSong);
            return;
        }
        togglePlayPause();
    }

    void remotePlayNext() {
        if (currentMode == Mode.SELECT) {
            playNextFromSelect();
        } else {
            playNext();
        }
    }

    void remotePlayPrevious() {
        if (currentMode == Mode.SELECT && currentPlayingSong != null) {
            KaraokeSong prev = session.playPrevious();
            if (prev != null) {
                playSong(prev);
                enterPlayMode(prev);
            }
        } else {
            playPrevious();
        }
    }

    void remoteResumePlay() {
        if (currentPlayingSong == null) return;
        if (currentMode == Mode.SELECT) {
            enterPlayMode(currentPlayingSong);
        } else if (mVideoView != null && !mVideoView.isPlaying()) {
            mVideoView.start();
            userPaused = false;
        }
    }

    void remotePausePlay() {
        if (mVideoView != null && mVideoView.isPlaying()) {
            mVideoView.pause();
            userPaused = true;
        }
    }

    void remoteAddToQueue(String filePath) {
        if (session == null || session.getLibrary() == null) return;
        for (KaraokeSong song : session.getLibrary()) {
            if (filePath.equals(song.filePath)) {
                if (!session.isInQueue(song)) {
                    session.addToQueue(song);
                    updateQueueTabCount();
                    updateStartPlayButton();
                    if (activeTab == Tab.QUEUE) {
                        queueAdapter.setNewDiffData(session.getQueue());
                        queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
                        updateFocusGraph();
                    } else {
                        songGridAdapter.updateQueuedSet(session.getQueue());
                    }
                    if (currentMode == Mode.PLAY) {
                        pushNextUpToController();
                    }
                }
                return;
            }
        }
    }

    void remoteRemoveFromQueue(int position) {
        onRemoveFromQueue(position);
    }

    void remotePlayAt(int position) {
        if (session == null) return;
        session.playAt(position);
        KaraokeSong song = session.getCurrentSong();
        if (song != null) {
            playSong(song);
            enterPlayMode(song);
        }
    }

    List<Map<String, Object>> getRemoteAudioTracks() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (mVideoView == null) return result;
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        if (!(mediaPlayer instanceof TrackAwarePlayer)) return result;
        TrackAwarePlayer trackPlayer = (TrackAwarePlayer) mediaPlayer;
        TrackInfo trackInfo = trackPlayer.getTrackInfo();
        if (trackInfo == null) return result;
        for (TrackInfoBean bean : trackInfo.getAudio()) {
            Map<String, Object> item = new HashMap<>();
            item.put("trackId", bean.trackId);
            item.put("name", bean.name);
            item.put("selected", bean.selected);
            result.add(item);
        }
        return result;
    }

    boolean remoteSwitchAudioTrack(int trackId) {
        if (mVideoView == null || isFinishing()) return false;
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        if (!(mediaPlayer instanceof TrackAwarePlayer)) return false;
        final TrackAwarePlayer trackPlayer = (TrackAwarePlayer) mediaPlayer;
        final AbstractPlayer absPlayer = mediaPlayer;
        try {
            TrackInfo trackInfo = trackPlayer.getTrackInfo();
            if (trackInfo != null) {
                List<TrackInfoBean> audioTracks = trackInfo.getAudio();
                boolean found = false;
                for (TrackInfoBean b : audioTracks) {
                    if (b.trackId == trackId) found = true;
                    b.selected = b.trackId == trackId;
                }
                if (!found) return false;
            }
            savedAudioTrackId = trackId;
            absPlayer.pause();
            final long progress = absPlayer.getCurrentPosition();
            trackPlayer.setTrack(trackId);
            if (pendingAudioSwitch != null) mainHandler.removeCallbacks(pendingAudioSwitch);
            pendingAudioSwitch = new Runnable() {
                @Override
                public void run() {
                    pendingAudioSwitch = null;
                    if (!isFinishing() && !isDestroyed()) {
                        absPlayer.seekTo(progress);
                        absPlayer.start();
                    }
                }
            };
            mainHandler.postDelayed(pendingAudioSwitch, 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean applySavedAudioTrack() {
        if (mVideoView == null) return false;
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        if (!(mediaPlayer instanceof TrackAwarePlayer)) return false;
        TrackAwarePlayer trackPlayer = (TrackAwarePlayer) mediaPlayer;
        TrackInfo trackInfo = trackPlayer.getTrackInfo();
        if (trackInfo == null) return false;
        List<TrackInfoBean> audioTracks = trackInfo.getAudio();
        if (audioTracks.isEmpty()) return false;
        if (savedAudioTrackId <= 0) return true;
        TrackInfoBean target = null;
        for (TrackInfoBean b : audioTracks) {
            if (b.trackId == savedAudioTrackId) {
                target = b;
                break;
            }
        }
        if (target == null) return false;
        try {
            trackPlayer.setTrack(target.trackId);
            mController.setTrackInfo(target.name);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

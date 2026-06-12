package com.github.tvbox.osc.karaoke;

import android.Manifest;
import android.graphics.Bitmap;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
import com.github.tvbox.osc.karaoke.adapter.KaraokeArtistAdapter;
import com.github.tvbox.osc.karaoke.adapter.KaraokeQueueAdapter;
import com.github.tvbox.osc.karaoke.adapter.KaraokeSongGridAdapter;
import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.karaoke.controller.KaraokeController;
import com.github.tvbox.osc.karaoke.playlist.KaraokeSession;
import com.github.tvbox.osc.karaoke.util.KaraokeFileScanner;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.player.IjkmPlayer;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.player.TrackInfo;
import com.github.tvbox.osc.player.TrackInfoBean;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.obsez.android.lib.filechooser.ChooserDialog;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.PlayerFactory;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.render.TextureRenderViewFactory;

public class KaraokeActivity extends BaseActivity {

    enum Mode { SELECT, PLAY }

    private Mode currentMode = Mode.SELECT;
    private KaraokeSong currentPlayingSong = null;
    private long lastPlaybackPosition = 0;
    private boolean userPaused = false;
    private int savedAudioTrackIndex = -1;
    private boolean pendingAudioTrackApply = false;

    private MyVideoView mVideoView;
    private KaraokeController mController;
    private KaraokeSession session;

    // Select layer views
    private LinearLayout llSelectLayer;
    private EditText etSearch;
    private TextView tvTabAll;
    private TextView tvTabQueue;
    private TvRecyclerView rvArtists;
    private TvRecyclerView rvSongGrid;
    private TvRecyclerView rvQueue;
    private TextView tvNowPlaying;
    private TextView tvStartPlay;
    private ImageView ivPlayPauseBottom;
    private ImageView ivNextBottom;
    private LinearLayout llQRCode;
    private ImageView ivQRCode;

    // Adapters
    private KaraokeSongGridAdapter songGridAdapter;
    private KaraokeQueueAdapter queueAdapter;
    private KaraokeArtistAdapter artistAdapter;

    // Filter state
    private String activeArtist = null;
    private String activeSearch = "";
    private boolean showingQueue = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_karaoke;
    }

    @Override
    protected void init() {
        hideSystemUI(false);
        session = new KaraokeSession();

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
                    playNext();
                } else if (playState == VideoView.STATE_ERROR) {
                    Toast.makeText(mContext, getString(R.string.karaoke_play_error), Toast.LENGTH_SHORT).show();
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            playNext();
                        }
                    }, 1500);
                } else if (playState == VideoView.STATE_PLAYING) {
                    if (pendingAudioTrackApply && savedAudioTrackIndex > 0) {
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
        rvArtists = findViewById(R.id.rvArtists);
        rvSongGrid = findViewById(R.id.rvSongGrid);
        rvQueue = findViewById(R.id.rvQueue);
        tvNowPlaying = findViewById(R.id.tvNowPlaying);
        tvStartPlay = findViewById(R.id.tvStartPlay);
        ivPlayPauseBottom = findViewById(R.id.ivPlayPauseBottom);
        ivNextBottom = findViewById(R.id.ivNextBottom);
        llQRCode = findViewById(R.id.llQRCode);
        ivQRCode = findViewById(R.id.ivQRCode);

        generateQRCode();

        initAdapters();
        initTopBar();
        initBottomBar();

        // Start in SELECT mode
        currentMode = Mode.SELECT;
        updateNowPlayingText();

        // Load saved folder or pick one
        String savedFolder = Hawk.get("karaoke_folder", "");
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

        // Queue list
        queueAdapter = new KaraokeQueueAdapter();
        queueAdapter.setDeleteListener(new KaraokeQueueAdapter.OnItemDeleteListener() {
            @Override
            public void onItemDelete(int position) {
                onRemoveFromQueue(position);
            }
        });
        rvQueue.setLayoutManager(new V7LinearLayoutManager(this));
        rvQueue.setAdapter(queueAdapter);
        queueAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                // Play the selected queue song
                session.playAt(position);
                playSong(session.getCurrentSong());
                enterPlayMode(session.getCurrentSong());
            }
        });
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
                switchTab(false);
            }
        });

        // Tab: Queue
        tvTabQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(true);
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

    // ======================== Mode Switching ========================

    private void enterSelectMode() {
        if (currentMode == Mode.SELECT) return;
        currentMode = Mode.SELECT;

        if (mVideoView.isPlaying()) {
            mVideoView.pause();
        }
        lastPlaybackPosition = mVideoView.getCurrentPosition();

        mVideoView.setVisibility(View.GONE);
        llSelectLayer.setVisibility(View.VISIBLE);
        llQRCode.setVisibility(View.VISIBLE);

        updateNowPlayingText();
        updateStartPlayButton();
        updateQueueTabCount();

        if (showingQueue) {
            queueAdapter.setNewData(session.getQueue());
            queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
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
        } else {
            // New song
            currentPlayingSong = song;
            lastPlaybackPosition = 0;
            pendingAudioTrackApply = true;
            mVideoView.release();
            mVideoView.setUrl(song.filePath);
            mVideoView.setVisibility(View.VISIBLE);
            llSelectLayer.setVisibility(View.GONE);
            llQRCode.setVisibility(View.GONE);
            mVideoView.start();
            mController.setSongTitle(song.displayName);
            mController.setTrackInfo("");
            queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
        }
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
        queueAdapter.setNewData(session.getQueue());
        queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
        updateQueueTabCount();
        updateStartPlayButton();
        songGridAdapter.updateQueuedSet(session.getQueue());

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
                    enterSelectMode();
                }
                break;
            case KEEP_PLAYING:
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

    private void switchTab(boolean toQueue) {
        showingQueue = toQueue;
        if (toQueue) {
            rvSongGrid.setVisibility(View.GONE);
            rvQueue.setVisibility(View.VISIBLE);
            queueAdapter.setNewData(session.getQueue());
            queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
        } else {
            rvSongGrid.setVisibility(View.VISIBLE);
            rvQueue.setVisibility(View.GONE);
            rebuildVisibleSongs();
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
        songGridAdapter.setNewData(filtered);
        songGridAdapter.updateQueuedSet(session.getQueue());
    }

    private void updateQueueTabCount() {
        int count = session.getQueueSize();
        tvTabQueue.setText(String.format(getString(R.string.karaoke_queued_songs), count));
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
        String folderPath = Hawk.get("karaoke_folder", "");
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
                        Hawk.put("karaoke_folder", absPath);
                        loadFolder(absPath);
                    }
                }).show();
    }

    void loadFolder(String folderPath) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File folder = new File(folderPath);
                List<KaraokeSong> songs = KaraokeFileScanner.scanFolder(folder);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Changing folder resets everything, even when empty
                        session.setLibrary(songs);
                        session.clearQueue();
                        currentPlayingSong = null;
                        lastPlaybackPosition = 0;
                        userPaused = false;
                        activeArtist = null;
                        activeSearch = "";

                        if (songs.isEmpty()) {
                            artistAdapter.setNewData(new ArrayList<String>());
                            artistAdapter.setSelectedPosition(0);
                            songGridAdapter.setNewData(new ArrayList<KaraokeSong>());
                            switchTab(false);
                            updateQueueTabCount();
                            updateStartPlayButton();
                            updateNowPlayingText();
                            Toast.makeText(mContext, getString(R.string.karaoke_no_videos), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Setup artist sidebar
                        List<String> artists = new ArrayList<>();
                        artists.add(getString(R.string.karaoke_all_artists));
                        artists.addAll(session.getArtists());
                        artistAdapter.setNewData(artists);
                        artistAdapter.setSelectedPosition(0);

                        // Setup song grid
                        songGridAdapter.setNewData(songs);
                        songGridAdapter.updateQueuedSet(session.getQueue());

                        // Reset to all songs tab
                        switchTab(false);
                        tvTabAll.requestFocus();
                        updateQueueTabCount();
                        updateStartPlayButton();
                        updateNowPlayingText();
                    }
                });
            }
        }).start();
    }

    // ======================== Audio Track Switching ========================

    private void switchAudioTrack() {
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        if (!(mediaPlayer instanceof IjkmPlayer)) {
            Toast.makeText(mContext, getString(R.string.karaoke_ijk_only), Toast.LENGTH_SHORT).show();
            return;
        }
        IjkmPlayer ijkPlayer = (IjkmPlayer) mediaPlayer;
        TrackInfo trackInfo = ijkPlayer.getTrackInfo();
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
                savedAudioTrackIndex = pos;
                try {
                    for (TrackInfoBean audio : audioTracks) {
                        audio.selected = audio.trackId == value.trackId;
                    }
                    ijkPlayer.pause();
                    long progress = ijkPlayer.getCurrentPosition();
                    ijkPlayer.setTrack(value.trackId);
                    mController.setTrackInfo(value.name);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ijkPlayer.seekTo(progress);
                            ijkPlayer.start();
                        }
                    }, 800);
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
        }
        return super.dispatchKeyEvent(event);
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
            if (showingQueue) {
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
            switchTab(true);
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
        if (mVideoView != null && currentMode == Mode.PLAY && !userPaused) {
            mVideoView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null && currentMode == Mode.PLAY) {
            mVideoView.pause();
            lastPlaybackPosition = mVideoView.getCurrentPosition();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
                    if (showingQueue) {
                        queueAdapter.setNewData(session.getQueue());
                        queueAdapter.setCurrentlyPlaying(session.getCurrentQueueIndex());
                    } else {
                        songGridAdapter.updateQueuedSet(session.getQueue());
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
        if (!(mediaPlayer instanceof IjkmPlayer)) return result;
        IjkmPlayer ijkPlayer = (IjkmPlayer) mediaPlayer;
        TrackInfo trackInfo = ijkPlayer.getTrackInfo();
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
        if (!(mediaPlayer instanceof IjkmPlayer)) return false;
        IjkmPlayer ijkPlayer = (IjkmPlayer) mediaPlayer;
        try {
            TrackInfo trackInfo = ijkPlayer.getTrackInfo();
            if (trackInfo != null) {
                List<TrackInfoBean> audioTracks = trackInfo.getAudio();
                for (int i = 0; i < audioTracks.size(); i++) {
                    if (audioTracks.get(i).trackId == trackId) {
                        savedAudioTrackIndex = i;
                        break;
                    }
                }
            }
            ijkPlayer.pause();
            long progress = ijkPlayer.getCurrentPosition();
            ijkPlayer.setTrack(trackId);
            new Handler().postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    ijkPlayer.seekTo(progress);
                    ijkPlayer.start();
                }
            }, 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean applySavedAudioTrack() {
        if (mVideoView == null) return false;
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        if (!(mediaPlayer instanceof IjkmPlayer)) return false;
        IjkmPlayer ijkPlayer = (IjkmPlayer) mediaPlayer;
        TrackInfo trackInfo = ijkPlayer.getTrackInfo();
        if (trackInfo == null) return false;
        List<TrackInfoBean> audioTracks = trackInfo.getAudio();
        if (audioTracks.isEmpty()) return false;
        int idx = Math.min(savedAudioTrackIndex, audioTracks.size() - 1);
        if (idx <= 0) return true;
        TrackInfoBean target = audioTracks.get(idx);
        try {
            ijkPlayer.setTrack(target.trackId);
            mController.setTrackInfo(target.name);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

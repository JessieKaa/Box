package com.github.tvbox.osc.ui.activity.ktv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.cache.KtvQueueItem;
import com.github.tvbox.osc.cache.KtvSong;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ktv.KtvIntent;
import com.github.tvbox.osc.ktv.KtvQueueManager;
import com.github.tvbox.osc.ui.adapter.ktv.KtvSongAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.greenrobot.eventbus.EventBus;

public class KtvSongListActivity extends BaseActivity {
    private static final String EXTRA_SOURCE_ID = "extra_source_id";
    private static final String EXTRA_TITLE = "extra_title";
    private static final long SEARCH_DEBOUNCE_MS = 250L;

    private EditText etSearch;
    private TvRecyclerView recyclerView;
    private final KtvSongAdapter adapter = new KtvSongAdapter();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor searchExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    private int sourceId;
    private String currentKeyword = "";
    private int searchVersion = 0;
    private volatile boolean destroyed = false;
    private final Runnable searchRunnable = new Runnable() {
        @Override
        public void run() {
            performLoadSongs(currentKeyword);
        }
    };

    public static void start(Context context, int sourceId, String title) {
        Intent intent = new Intent(context, KtvSongListActivity.class);
        intent.putExtra(EXTRA_SOURCE_ID, sourceId);
        intent.putExtra(EXTRA_TITLE, title);
        context.startActivity(intent);
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_ktv_song_list;
    }

    @Override
    protected void init() {
        sourceId = getIntent().getIntExtra(EXTRA_SOURCE_ID, 0);
        initView();
        scheduleLoadSongs("");
    }

    private void initView() {
        ((android.widget.TextView) findViewById(R.id.textView)).setText(getIntent().getStringExtra(EXTRA_TITLE));
        etSearch = findViewById(R.id.etSearch);
        recyclerView = findViewById(R.id.mGridView);
        recyclerView.setLayoutManager(new V7LinearLayoutManager(this, V7LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);
        recyclerView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                KtvSong song = adapter.getItem(position);
                if (song == null) {
                    return;
                }
                KtvQueueItem item = KtvQueueManager.get().playNow(song);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_KTV_QUEUE_REFRESH));
                KtvIntent.startPlayback(KtvSongListActivity.this, item);
            }
        });
        recyclerView.setOnLongClickListener(v -> false);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleLoadSongs(s == null ? "" : s.toString());
            }
        });
        adapter.setOnItemLongClickListener((adapter1, view, position) -> {
            KtvSong song = adapter.getItem(position);
            if (song == null) {
                return false;
            }
            KtvQueueManager.get().addToQueue(song);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_KTV_QUEUE_REFRESH));
            Toast.makeText(this, "已加入已点", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleLoadSongs(etSearch == null ? currentKeyword : etSearch.getText().toString());
    }

    private void scheduleLoadSongs(String keyword) {
        currentKeyword = keyword == null ? "" : keyword;
        handler.removeCallbacks(searchRunnable);
        showLoading();
        handler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
    }

    private void performLoadSongs(String keyword) {
        if (destroyed || searchExecutor.isShutdown()) {
            return;
        }
        final int version = ++searchVersion;
        final String query = keyword == null ? "" : keyword;
        try {
            searchExecutor.getQueue().clear();
            searchExecutor.execute(() -> {
                List<KtvSong> songs = query.trim().isEmpty()
                        ? RoomDataManger.getKtvSongs(sourceId)
                        : RoomDataManger.searchKtvSongs(sourceId, query);
                if (destroyed || version != searchVersion) {
                    return;
                }
                runOnUiThread(() -> {
                    if (destroyed || isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (version != searchVersion) {
                        return;
                    }
                    adapter.setNewData(songs);
                    showSuccess();
                });
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacks(searchRunnable);
        searchExecutor.shutdownNow();
        super.onDestroy();
    }
}

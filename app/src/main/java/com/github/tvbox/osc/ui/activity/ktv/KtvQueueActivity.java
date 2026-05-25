package com.github.tvbox.osc.ui.activity.ktv;

import android.view.View;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.cache.KtvQueueItem;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ktv.KtvIntent;
import com.github.tvbox.osc.ktv.KtvQueueManager;
import com.github.tvbox.osc.ui.adapter.ktv.KtvQueueAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class KtvQueueActivity extends BaseActivity {
    private final KtvQueueAdapter adapter = new KtvQueueAdapter();

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_ktv_queue;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            KtvQueueManager.get().clearPending();
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_KTV_QUEUE_REFRESH));
            Toast.makeText(KtvQueueActivity.this, "已清空待播队列", Toast.LENGTH_SHORT).show();
            loadQueue();
        });
        TvRecyclerView recyclerView = findViewById(R.id.mGridView);
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
                KtvQueueItem item = adapter.getItem(position);
                if (item == null) {
                    return;
                }
                if (KtvQueueItem.STATUS_PLAYING.equals(item.status)) {
                    KtvIntent.startPlayback(KtvQueueActivity.this, item);
                    return;
                }
                if (KtvQueueItem.STATUS_PENDING.equals(item.status)) {
                    KtvQueueManager.get().removePendingItem(item.getId());
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_KTV_QUEUE_REFRESH));
                    Toast.makeText(KtvQueueActivity.this, "已移除待播", Toast.LENGTH_SHORT).show();
                    loadQueue();
                }
            }
        });
        loadQueue();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadQueue();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_KTV_QUEUE_REFRESH) {
            loadQueue();
        }
    }

    private void loadQueue() {
        adapter.setNewData(KtvQueueManager.get().getQueueItems());
        showSuccess();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}

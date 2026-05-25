package com.github.tvbox.osc.ui.adapter.ktv;

import android.text.TextUtils;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.cache.KtvQueueItem;

import java.util.ArrayList;

public class KtvQueueAdapter extends BaseQuickAdapter<KtvQueueItem, BaseViewHolder> {
    public KtvQueueAdapter() {
        super(R.layout.item_search_lite, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, KtvQueueItem item) {
        String artist = TextUtils.isEmpty(item.artist) ? "" : (" - " + item.artist);
        helper.setText(R.id.tvName, "[" + item.status + "] " + item.songTitle + artist);
    }
}

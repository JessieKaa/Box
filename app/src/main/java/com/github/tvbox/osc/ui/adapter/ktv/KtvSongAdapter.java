package com.github.tvbox.osc.ui.adapter.ktv;

import android.text.TextUtils;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.cache.KtvSong;

import java.util.ArrayList;

public class KtvSongAdapter extends BaseQuickAdapter<KtvSong, BaseViewHolder> {
    public KtvSongAdapter() {
        super(R.layout.item_search_lite, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, KtvSong item) {
        String artist = TextUtils.isEmpty(item.artist) ? "" : (" - " + item.artist);
        helper.setText(R.id.tvName, item.title + artist + "  [" + item.fileName + "]");
    }
}

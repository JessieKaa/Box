package com.github.tvbox.osc.ui.adapter.ktv;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.cache.KtvMediaSource;

import java.util.ArrayList;

public class KtvSourceAdapter extends BaseQuickAdapter<KtvMediaSource, BaseViewHolder> {
    public KtvSourceAdapter() {
        super(R.layout.item_search_lite, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, KtvMediaSource item) {
        String status = item.scanStatus == null ? "未扫描" : item.scanStatus;
        String error = item.scanError == null || item.scanError.isEmpty() ? "" : ("  错误:" + item.scanError);
        helper.setText(R.id.tvName, item.displayName + "  [" + item.type + "]  " + status + error);
    }
}

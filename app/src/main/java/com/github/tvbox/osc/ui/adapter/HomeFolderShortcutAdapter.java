package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.cache.HomeFolderShortcut;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import java.io.File;
import java.util.ArrayList;

public class HomeFolderShortcutAdapter extends BaseQuickAdapter<HomeFolderShortcut, BaseViewHolder> {
    public HomeFolderShortcutAdapter() {
        super(R.layout.item_home_folder_shortcut, new ArrayList<HomeFolderShortcut>());
    }

    @Override
    protected void convert(BaseViewHolder helper, HomeFolderShortcut item) {
        helper.setText(R.id.tvShortcutName, item.name);
        helper.setText(R.id.tvShortcutPath, buildPathSummary(item.rootPath));
        helper.setText(R.id.tvShortcutStatus, getStatusText(item.indexStatus));
        helper.setText(R.id.tvShortcutCount, "视频 " + item.indexedFileCount);
        TextView pathView = helper.getView(R.id.tvShortcutPath);
        TextView statusView = helper.getView(R.id.tvShortcutStatus);
        TextView countView = helper.getView(R.id.tvShortcutCount);
        LinearLayout cardRoot = helper.getView(R.id.cardRoot);
        cardRoot.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                pathView.setSelected(hasFocus);
                statusView.setSelected(hasFocus);
                countView.setSelected(hasFocus);
                ((TvRecyclerView) helper.itemView.getParent()).onFocusChange(helper.itemView, hasFocus);
            }
        });
        cardRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((TvRecyclerView) helper.itemView.getParent()).onClick(helper.itemView);
            }
        });
    }

    private String buildPathSummary(String rootPath) {
        if (TextUtils.isEmpty(rootPath)) {
            return "";
        }
        File file = new File(rootPath);
        File parent = file.getParentFile();
        if (parent == null) {
            return rootPath;
        }
        String parentName = parent.getName();
        if (TextUtils.isEmpty(parentName)) {
            return rootPath;
        }
        return parentName + File.separator + file.getName();
    }

    private String getStatusText(int status) {
        switch (status) {
            case HomeFolderShortcut.STATUS_INDEXING:
                return "索引中";
            case HomeFolderShortcut.STATUS_INDEXED:
                return "已索引";
            case HomeFolderShortcut.STATUS_FAILED:
                return "索引失败";
            default:
                return "未索引";
        }
    }
}

package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.cache.HomeFolderIndexEntry;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class FolderIndexSearchAdapter extends BaseQuickAdapter<HomeFolderIndexEntry, BaseViewHolder> {
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault());

    public FolderIndexSearchAdapter() {
        super(R.layout.item_folder_index_search, new ArrayList<HomeFolderIndexEntry>());
    }

    @Override
    protected void convert(BaseViewHolder helper, HomeFolderIndexEntry item) {
        helper.setText(R.id.tvFileName, item.fileName);
        helper.setText(R.id.tvFilePath, TextUtils.isEmpty(item.relativePath) ? "/" : item.relativePath);
        helper.setText(R.id.tvFileMeta, buildMeta(item));
        TextView pathView = helper.getView(R.id.tvFilePath);
        TextView metaView = helper.getView(R.id.tvFileMeta);
        LinearLayout itemRoot = helper.getView(R.id.itemRoot);
        itemRoot.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                pathView.setSelected(hasFocus);
                metaView.setSelected(hasFocus);
                ((TvRecyclerView) helper.itemView.getParent()).onFocusChange(helper.itemView, hasFocus);
            }
        });
        itemRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((TvRecyclerView) helper.itemView.getParent()).onClick(helper.itemView);
            }
        });
    }

    private String buildMeta(HomeFolderIndexEntry item) {
        String fileType = TextUtils.isEmpty(item.fileType) ? "VIDEO" : item.fileType;
        return fileType + "  " + timeFormat.format(new Date(item.lastModified));
    }
}

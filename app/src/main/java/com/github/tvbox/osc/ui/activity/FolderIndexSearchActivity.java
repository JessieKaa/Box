package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.cache.HomeFolderIndexEntry;
import com.github.tvbox.osc.cache.HomeFolderShortcut;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.adapter.FolderIndexSearchAdapter;
import com.github.tvbox.osc.ui.tv.widget.CustomEditText;
import com.github.tvbox.osc.util.DrivePlayHelper;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FolderIndexSearchActivity extends BaseActivity {
    public static final String EXTRA_SHORTCUT_ID = "extra_shortcut_id";
    public static final String EXTRA_SHORTCUT_NAME = "extra_shortcut_name";

    private int shortcutId;
    private String shortcutName;
    private TextView tvTitle;
    private TextView tvSearch;
    private TextView tvClear;
    private TextView tvStatus;
    private CustomEditText etKeyword;
    private TvRecyclerView mGridView;
    private FolderIndexSearchAdapter adapter;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_folder_index_search;
    }

    @Override
    protected void init() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        shortcutId = getIntent().getIntExtra(EXTRA_SHORTCUT_ID, 0);
        shortcutName = getIntent().getStringExtra(EXTRA_SHORTCUT_NAME);
        HomeFolderShortcut shortcut = RoomDataManger.getHomeFolderShortcut(shortcutId);
        if (shortcut == null) {
            Toast.makeText(mContext, "快捷方式不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (TextUtils.isEmpty(shortcutName)) {
            shortcutName = shortcut.name;
        }
        initView();
    }

    private void initView() {
        tvTitle = findViewById(R.id.tvTitle);
        tvSearch = findViewById(R.id.tvSearch);
        tvClear = findViewById(R.id.tvClear);
        tvStatus = findViewById(R.id.tvStatus);
        etKeyword = findViewById(R.id.etKeyword);
        mGridView = findViewById(R.id.mGridView);
        tvTitle.setText("索引搜索 - " + shortcutName);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7LinearLayoutManager(this, 1, false));
        adapter = new FolderIndexSearchAdapter();
        mGridView.setAdapter(adapter);
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.03f).scaleY(1.03f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });
        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                HomeFolderIndexEntry item = FolderIndexSearchActivity.this.adapter.getItem(position);
                if (item == null) {
                    return;
                }
                File file = new File(item.absolutePath);
                if (!file.exists() || !file.isFile()) {
                    Toast.makeText(mContext, "文件不存在", Toast.LENGTH_SHORT).show();
                    return;
                }
                DrivePlayHelper.playFile(FolderIndexSearchActivity.this, item.fileName, item.absolutePath);
            }
        });

        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                performSearch();
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                etKeyword.setText("");
                adapter.setNewData(new ArrayList<HomeFolderIndexEntry>());
                tvStatus.setText("请输入关键词");
            }
        });
        etKeyword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etKeyword, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
        etKeyword.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    performSearch();
                    return true;
                }
                return false;
            }
        });
        etKeyword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (TextUtils.isEmpty(s.toString().trim())) {
                    adapter.setNewData(new ArrayList<HomeFolderIndexEntry>());
                    tvStatus.setText("请输入关键词");
                }
            }
        });

        tvStatus.setText("请输入关键词");
    }

    private void performSearch() {
        String keyword = etKeyword.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            Toast.makeText(mContext, getString(R.string.search_input), Toast.LENGTH_SHORT).show();
            return;
        }
        List<HomeFolderIndexEntry> results = RoomDataManger.searchShortcutIndex(shortcutId, keyword);
        adapter.setNewData(results);
        tvStatus.setText(results.isEmpty() ? "未命中索引结果" : "共找到 " + results.size() + " 个结果");
    }
}

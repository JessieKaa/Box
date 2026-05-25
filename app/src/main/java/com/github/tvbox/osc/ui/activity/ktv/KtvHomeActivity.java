package com.github.tvbox.osc.ui.activity.ktv;

import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.cache.KtvMediaSource;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.StorageDrive;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ktv.KtvLibraryIndexManager;
import com.github.tvbox.osc.ktv.KtvMediaSourceType;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.ktv.KtvSourceAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.WebdavDialog;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.StorageDriveType;
import com.github.tvbox.osc.util.StorageRootFinder;
import com.github.tvbox.osc.util.StorageRootFinder.StorageRoot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KtvHomeActivity extends BaseActivity {
    private static final int REQUEST_LOCAL_STORAGE_PERMISSION = 1;

    private TvRecyclerView recyclerView;
    private final KtvSourceAdapter adapter = new KtvSourceAdapter();
    private Set<Integer> pendingWebDavDriveIds = new HashSet<>();
    private boolean pendingOpenLocalSourceDialog = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_ktv_home;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        initView();
        loadSources();
    }

    private void initView() {
        recyclerView = findViewById(R.id.mGridView);
        recyclerView.setLayoutManager(new V7LinearLayoutManager(this, V7LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);
        adapter.setOnItemLongClickListener((adapter1, view, position) -> {
            KtvMediaSource source = adapter.getItem(position);
            if (source == null) {
                return false;
            }
            rescanSource(source);
            return true;
        });
        recyclerView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                KtvMediaSource source = adapter.getItem(position);
                if (source == null) {
                    return;
                }
                KtvSongListActivity.start(KtvHomeActivity.this, source.getId(), source.displayName);
            }
        });
        findViewById(R.id.btnHome).setOnClickListener(v -> finish());
        findViewById(R.id.btnSearch).setOnClickListener(v -> KtvSongListActivity.start(KtvHomeActivity.this, 0, "全部歌曲"));
        findViewById(R.id.btnQueue).setOnClickListener(v -> jumpActivity(KtvQueueActivity.class));
        findViewById(R.id.btnAddSource).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            openAddSourceDialog();
        });
    }

    private void loadSources() {
        adapter.setNewData(RoomDataManger.getAllKtvMediaSources());
        showSuccess();
    }

    private void openAddSourceDialog() {
        SelectDialog<String> dialog = new SelectDialog<>(this);
        dialog.setTip("添加 KTV 歌库");
        List<String> options = Arrays.asList("本地目录", "WebDAV");
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                dialog.dismiss();
                if ("本地目录".equals(value)) {
                    openLocalSourceDialog();
                } else {
                    openWebDavDialog();
                }
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                return oldItem.equals(newItem);
            }
        }, options, 0);
        dialog.show();
    }

    private void openLocalSourceDialog() {
        requestLocalStoragePermission(() -> {
            List<StorageRoot> roots = StorageRootFinder.find(this);
            if (roots.isEmpty()) {
                Toast.makeText(this, "未找到可用本地目录", Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> options = new ArrayList<>();
            for (StorageRoot root : roots) {
                options.add(root.label + "  " + root.path);
            }
            SelectDialog<String> dialog = new SelectDialog<>(this);
            dialog.setTip("选择本地歌库");
            dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
                @Override
                public void click(String value, int pos) {
                    dialog.dismiss();
                    StorageRoot root = roots.get(pos);
                    String normalizedPath = RoomDataManger.normalizeKtvSourcePath(KtvMediaSourceType.LOCAL.name(), root.path);
                    KtvMediaSource source = findSource(KtvMediaSourceType.LOCAL.name(), normalizedPath);
                    if (source == null) {
                        source = new KtvMediaSource();
                        source.type = KtvMediaSourceType.LOCAL.name();
                        source.displayName = root.label;
                        source.rootPathOrUrl = normalizedPath;
                        source.enabled = 1;
                        source.scanStatus = "IDLE";
                        long id = RoomDataManger.insertKtvMediaSource(source);
                        source.setId((int) id);
                    }
                    rescanSource(source);
                }

                @Override
                public String getDisplay(String val) {
                    return val;
                }
            }, new DiffUtil.ItemCallback<String>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                    return oldItem.equals(newItem);
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                    return oldItem.equals(newItem);
                }
            }, options, 0);
            dialog.show();
        });
    }

    private void requestLocalStoragePermission(Runnable onGranted) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (XXPermissions.isGranted(this, DefaultConfig.StoragePermissionGroup())) {
                onGranted.run();
                return;
            }
            XXPermissions.with(this)
                    .permission(DefaultConfig.StoragePermissionGroup())
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                onGranted.run();
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            Toast.makeText(KtvHomeActivity.this, "未获取到存储权限", Toast.LENGTH_SHORT).show();
                        }
                    });
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                && !hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            pendingOpenLocalSourceDialog = true;
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_LOCAL_STORAGE_PERMISSION);
            return;
        }
        onGranted.run();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCAL_STORAGE_PERMISSION) {
            return;
        }
        boolean granted = grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (granted && pendingOpenLocalSourceDialog) {
            pendingOpenLocalSourceDialog = false;
            openLocalSourceDialog();
            return;
        }
        pendingOpenLocalSourceDialog = false;
        Toast.makeText(this, "未获取到存储权限", Toast.LENGTH_SHORT).show();
    }

    private void openWebDavDialog() {
        pendingWebDavDriveIds = new HashSet<>();
        for (StorageDrive drive : RoomDataManger.getAllDrives()) {
            if (drive.type == StorageDriveType.TYPE.WEBDAV.ordinal()) {
                pendingWebDavDriveIds.add(drive.getId());
            }
        }
        WebdavDialog dialog = new WebdavDialog(this, null);
        EventBus.getDefault().register(dialog);
        dialog.setOnDismissListener(d -> {
            try {
                EventBus.getDefault().unregister(dialog);
            } catch (Exception ignored) {
            }
            importNewWebDavDrives(true);
        });
        dialog.show();
    }

    private void importNewWebDavDrives(boolean triggerScan) {
        for (StorageDrive drive : RoomDataManger.getAllDrives()) {
            if (drive.type != StorageDriveType.TYPE.WEBDAV.ordinal() || pendingWebDavDriveIds.contains(drive.getId())) {
                continue;
            }
            KtvMediaSource source = importWebDavDrive(drive);
            if (triggerScan) {
                rescanSource(source);
            }
        }
        loadSources();
    }

    private KtvMediaSource importWebDavDrive(StorageDrive drive) {
        JsonObject config = JsonParser.parseString(drive.configJson).getAsJsonObject();
        String rootUrl = RoomDataManger.normalizeKtvSourcePath(KtvMediaSourceType.WEBDAV.name(), config.get("url").getAsString());
        KtvMediaSource source = findSource(KtvMediaSourceType.WEBDAV.name(), rootUrl);
        if (source == null) {
            source = new KtvMediaSource();
            source.type = KtvMediaSourceType.WEBDAV.name();
            source.scanStatus = "IDLE";
        }
        source.displayName = drive.name;
        source.rootPathOrUrl = rootUrl;
        source.configJson = drive.configJson;
        source.enabled = 1;
        long sourceId = RoomDataManger.insertKtvMediaSource(source);
        source.setId((int) sourceId);
        return source;
    }

    private KtvMediaSource findSource(String type, String rootPathOrUrl) {
        String normalized = RoomDataManger.normalizeKtvSourcePath(type, rootPathOrUrl);
        for (KtvMediaSource source : RoomDataManger.getAllKtvMediaSources()) {
            if (type.equals(source.type) && normalized.equals(RoomDataManger.normalizeKtvSourcePath(source.type, source.rootPathOrUrl))) {
                return source;
            }
        }
        return null;
    }

    private void rescanSource(KtvMediaSource source) {
        Toast.makeText(this, "开始扫描歌库", Toast.LENGTH_SHORT).show();
        KtvLibraryIndexManager.get().scan(source, new KtvLibraryIndexManager.ScanCallback() {
            @Override
            public void onSuccess(int count) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_KTV_SOURCE_REFRESH));
                Toast.makeText(KtvHomeActivity.this, "扫描完成，共 " + count + " 首", Toast.LENGTH_SHORT).show();
                loadSources();
            }

            @Override
            public void onError(String error) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_KTV_SOURCE_REFRESH));
                Toast.makeText(KtvHomeActivity.this, "扫描失败: " + error, Toast.LENGTH_SHORT).show();
                loadSources();
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_KTV_SOURCE_REFRESH || event.type == RefreshEvent.TYPE_DRIVE_REFRESH) {
            loadSources();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}

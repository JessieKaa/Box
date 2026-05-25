package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.DriveFolderFile;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.StorageDrive;
import com.github.tvbox.osc.cache.KtvMediaSource;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ktv.KtvLibraryIndexManager;
import com.github.tvbox.osc.ktv.KtvMediaSourceType;
import com.github.tvbox.osc.ui.adapter.DriveAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.AlistDriveDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.WebdavDialog;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.DrivePlayHelper;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.StorageRootFinder;
import com.github.tvbox.osc.util.StorageDriveType;
import com.github.tvbox.osc.util.StringUtils;
import com.github.tvbox.osc.viewmodel.drive.AbstractDriveViewModel;
import com.github.tvbox.osc.viewmodel.drive.AlistDriveViewModel;
import com.github.tvbox.osc.viewmodel.drive.LocalDriveViewModel;
import com.github.tvbox.osc.viewmodel.drive.WebDAVDriveViewModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;
import com.lzy.okgo.OkGo;
import com.obsez.android.lib.filechooser.ChooserDialog;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class DriveActivity extends BaseActivity {
    public static final String EXTRA_LOCAL_ROOT_PATH = "extra_local_root_path";
    public static final String EXTRA_LOCAL_ROOT_TITLE = "extra_local_root_title";

    private static final int REQUEST_LOCAL_STORAGE_PERMISSION = 1;

    private TextView txtTitle;
    private TvRecyclerView mGridView;
    private ImageButton btnAddServer;
    private ImageButton btnRemoveServer;
    private ImageButton btnSort;
    private DriveAdapter adapter = new DriveAdapter();
    private List<DriveFolderFile> drives = null;
    List<DriveFolderFile> searchResult = null;
    private AbstractDriveViewModel viewModel = null;
    private AbstractDriveViewModel backupViewModel = null;
    private int sortType = 0;
    private View footLoading;
    private boolean isInSearch = false;

    private boolean delMode = false;
    private boolean pendingOpenLocalDriveDialog = false;
    private boolean pendingOpenDirectLocalEntry = false;
    private boolean isDirectLocalEntry = false;
    private String directLocalRootPath;
    private String directLocalRootTitle;

    private Handler mHandler = new Handler();

    public static Intent newLocalRootIntent(Context context, String rootPath, String title) {
        Intent intent = new Intent(context, DriveActivity.class);
        intent.putExtra(EXTRA_LOCAL_ROOT_PATH, rootPath);
        intent.putExtra(EXTRA_LOCAL_ROOT_TITLE, title);
        return intent;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_drive;
    }

    @Override
    protected void init() {
        directLocalRootPath = RoomDataManger.normalizeLocalRootPath(getIntent().getStringExtra(EXTRA_LOCAL_ROOT_PATH));
        directLocalRootTitle = getIntent().getStringExtra(EXTRA_LOCAL_ROOT_TITLE);
        isDirectLocalEntry = !TextUtils.isEmpty(directLocalRootPath);
        initView();
        initData();
    }

    private void initView() {
        EventBus.getDefault().register(this);
        this.txtTitle = findViewById(R.id.textView);
        this.btnAddServer = findViewById(R.id.btnAddServer);
        this.mGridView = findViewById(R.id.mGridView);
        this.btnRemoveServer = findViewById(R.id.btnRemoveServer);
        this.btnSort = findViewById(R.id.btnSort);
        footLoading = getLayoutInflater().inflate(R.layout.item_search_lite, null);
        footLoading.findViewById(R.id.tvName).setVisibility(View.GONE);
        this.btnRemoveServer.setColorFilter(ContextCompat.getColor(mContext, R.color.color_FFFFFF));
        this.btnRemoveServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleDelMode();
            }
        });
        findViewById(R.id.btnHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DriveActivity.super.onBackPressed();
            }
        });
        this.btnSort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                openSortDialog();
            }
        });
        this.btnAddServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                if (shouldShowAddShortcutAction()) {
                    openCurrentDirectoryAddDialog();
                    return;
                }
                StorageDriveType.TYPE[] types = StorageDriveType.TYPE.values();
                SelectDialog<StorageDriveType.TYPE> dialog = new SelectDialog<>(DriveActivity.this);
                dialog.setTip("请选择存盘类型");
                dialog.setItemCheckDisplay(false);
                String[] typeNames = StorageDriveType.getTypeNames();
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<StorageDriveType.TYPE>() {
                    @Override
                    public void click(StorageDriveType.TYPE value, int pos) {
                        if (value == StorageDriveType.TYPE.LOCAL) {
                            ensureLocalStoragePermission(new Runnable() {
                                @Override
                                public void run() {
                                    openLocalDriveDialog();
                                }
                            });
                            dialog.dismiss();
                        } else if (value == StorageDriveType.TYPE.WEBDAV) {
                            openWebdavDialog(null);
                            dialog.dismiss();
                        } else if (value == StorageDriveType.TYPE.ALISTWEB) {
                            openAlistDriveDialog(null);
                            dialog.dismiss();
                        }
                    }

                    @Override
                    public String getDisplay(StorageDriveType.TYPE val) {
                        return typeNames[val.ordinal()];
                    }
                }, new DiffUtil.ItemCallback<StorageDriveType.TYPE>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull StorageDriveType.TYPE oldItem, @NonNull @NotNull StorageDriveType.TYPE newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull StorageDriveType.TYPE oldItem, @NonNull @NotNull StorageDriveType.TYPE newItem) {
                        return oldItem.equals(newItem);
                    }
                }, Arrays.asList(types), 0);
                dialog.show();
            }
        });
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, V7LinearLayoutManager.VERTICAL, false));
        this.mGridView.setSpacingWithMargins(AutoSizeUtils.mm2px(this.mContext, 10), 0);
        this.mGridView.setAdapter(this.adapter);
        this.adapter.bindToRecyclerView(this.mGridView);
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (position >= 0)
                    adapter.getData().get(position).isSelected = false;
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (position >= 0)
                    adapter.getData().get(position).isSelected = true;
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (delMode) {
                    DriveFolderFile selectedDrive = drives.get(position);
                    deleteAssociatedKtvSources(selectedDrive);
                    RoomDataManger.deleteDrive(selectedDrive.getDriveData().getId());
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
                    return;
                }
                btnAddServer.setVisibility(View.GONE);
                btnRemoveServer.setVisibility(View.GONE);
                DriveFolderFile selectedItem = DriveActivity.this.adapter.getItem(position);

                if ((selectedItem == selectedItem.parentFolder || selectedItem.parentFolder == null) && selectedItem.name == null) {
                    returnPreviousFolder();
                    return;
                }
                if (viewModel == null) {
                    if (selectedItem.getDriveType() == StorageDriveType.TYPE.LOCAL) {
                        viewModel = new LocalDriveViewModel();
                    } else if (selectedItem.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
                        viewModel = new WebDAVDriveViewModel();
                    } else if (selectedItem.getDriveType() == StorageDriveType.TYPE.ALISTWEB) {
                        viewModel = new AlistDriveViewModel();
                    }
                    viewModel.setCurrentDrive(selectedItem);
                    if (!selectedItem.isFile) {
                        loadDriveData();
                        return;
                    }
                }

                if (!selectedItem.isFile) {
                    viewModel.setCurrentDriveNote(selectedItem);
                    loadDriveData();
                } else {
                    // takagen99 - To only play media file
                    if (StorageDriveType.isVideoType(selectedItem.fileType)) {
                        DriveFolderFile currentDrive = viewModel.getCurrentDrive();
                        if (currentDrive.getDriveType() == StorageDriveType.TYPE.LOCAL)
                            playFile(currentDrive.name + selectedItem.getAccessingPathStr() + selectedItem.name);
                        else if (currentDrive.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
                            JsonObject config = currentDrive.getConfig();
                            String targetPath = selectedItem.getAccessingPathStr() + selectedItem.name;
                            playFile(config.get("url").getAsString() + targetPath);
                        } else if (currentDrive.getDriveType() == StorageDriveType.TYPE.ALISTWEB) {
                            AlistDriveViewModel boxedViewModel = (AlistDriveViewModel) viewModel;

                            boxedViewModel.loadFile(selectedItem, new AlistDriveViewModel.LoadFileCallback() {
                                @Override
                                public void callback(String fileUrl) {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            playFile(fileUrl);
                                        }
                                    });
                                }

                                @Override
                                public void fail(String msg) {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast toast = Toast.makeText(mContext, msg, Toast.LENGTH_SHORT);
                                            toast.show();
                                        }
                                    });
                                }
                            });
                        }
                    } else {
                        Toast.makeText(DriveActivity.this, "Media Unsupported", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        setLoadSir(findViewById(R.id.mLayout));
    }

    private void playFile(String fileUrl) {
        DriveFolderFile currentDrive = viewModel.getCurrentDrive();
        if (currentDrive.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
            String credentialStr = currentDrive.getWebDAVBase64Credential();
            if (credentialStr != null) {
                JsonObject playerConfig = new JsonObject();
                JsonArray headers = new JsonArray();
                JsonElement authorization = JsonParser.parseString(
                        "{ \"name\": \"authorization\", \"value\": \"Basic " + credentialStr + "\" }");
                headers.add(authorization);
                playerConfig.add("headers", headers);
                DrivePlayHelper.playFile(this, "存储", fileUrl, playerConfig.toString());
                return;
            }
        }
        DrivePlayHelper.playFile(this, "存储", fileUrl);
    }

    private void openSortDialog() {
        List<String> options = Arrays.asList("按名字升序", "按名字降序", "按修改时间升序", "按修改时间降序");
        int sort = Hawk.get(HawkConfig.STORAGE_DRIVE_SORT, 0);
        SelectDialog<String> dialog = new SelectDialog<>(DriveActivity.this);
        dialog.setTip("请选择列表排序方式");
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                sortType = pos;
                Hawk.put(HawkConfig.STORAGE_DRIVE_SORT, pos);
                dialog.dismiss();
                loadDriveData();
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, null, options, sort);
        dialog.show();
    }

    private Comparator<DriveFolderFile> sortComparator = new Comparator<DriveFolderFile>() {
        @Override
        public int compare(DriveFolderFile o1, DriveFolderFile o2) {
            switch (sortType) {
                case 1:
                    return Collator.getInstance(Locale.CHINESE).compare(o2.name.toUpperCase(Locale.CHINESE), o1.name.toUpperCase(Locale.CHINESE));
                case 2:
                    return Long.compare(o1.lastModifiedDate, o2.lastModifiedDate);
                case 3:
                    return Long.compare(o2.lastModifiedDate, o1.lastModifiedDate);
                default:
                    return Collator.getInstance(Locale.CHINESE).compare(o1.name.toUpperCase(Locale.CHINESE), o2.name.toUpperCase(Locale.CHINESE));
            }
        }
    };

    private void openFilePicker() {
        if (delMode)
            toggleDelMode();
        ChooserDialog dialog = new ChooserDialog(mContext, R.style.FileChooserStyle);
        dialog
                .withStringResources("选择一个文件夹", "确定", "取消")
                .titleFollowsDir(true)
                .displayPath(true)
                .enableDpad(true)
                .withFilter(true, true)
                .withChosenListener(new ChooserDialog.Result() {
                    @Override
                    public void onChoosePath(String dir, File dirFile) {
                        addLocalDrive(dirFile.getAbsolutePath());
                    }
                }).show();
    }

    private void openLocalDriveDialog() {
        List<LocalDriveOption> options = new ArrayList<>();
        for (StorageRootFinder.StorageRoot root : StorageRootFinder.find(this)) {
            options.add(LocalDriveOption.fromRoot(root));
        }
        options.add(LocalDriveOption.manual());

        SelectDialog<LocalDriveOption> dialog = new SelectDialog<>(DriveActivity.this);
        dialog.setTip("请选择本地存储");
        dialog.setItemCheckDisplay(false);
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<LocalDriveOption>() {
            @Override
            public void click(LocalDriveOption value, int pos) {
                if (value.manual) {
                    openFilePicker();
                } else {
                    addLocalDrive(value.path);
                }
                dialog.dismiss();
            }

            @Override
            public String getDisplay(LocalDriveOption val) {
                return val.displayName;
            }
        }, new DiffUtil.ItemCallback<LocalDriveOption>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull LocalDriveOption oldItem, @NonNull @NotNull LocalDriveOption newItem) {
                return oldItem.path.equals(newItem.path);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull LocalDriveOption oldItem, @NonNull @NotNull LocalDriveOption newItem) {
                return oldItem.displayName.equals(newItem.displayName) && oldItem.manual == newItem.manual;
            }
        }, options, 0);
        dialog.show();
    }

    private void addLocalDrive(String absPath) {
        for (DriveFolderFile drive : drives) {
            if (drive.getDriveType() == StorageDriveType.TYPE.LOCAL && absPath.equals(drive.getDriveData().name)) {
                Toast.makeText(mContext, "此文件夹之前已被添加到空间列表！", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        RoomDataManger.insertDriveRecord(absPath, StorageDriveType.TYPE.LOCAL, null);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
    }

    private void ensureLocalStoragePermission(Runnable onGranted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (XXPermissions.isGranted(this, DefaultConfig.StoragePermissionGroup())) {
                onGranted.run();
                return;
            }
            XXPermissions.with(this)
                    .permission(DefaultConfig.StoragePermissionGroup())
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all && onGranted != null) {
                                onGranted.run();
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                Toast.makeText(mContext, "存储权限被永久拒绝，请前往设置开启", Toast.LENGTH_SHORT).show();
                                XXPermissions.startPermissionActivity((Activity) mContext, permissions);
                            } else {
                                Toast.makeText(mContext, "未获取到存储权限", Toast.LENGTH_SHORT).show();
                            }
                            if (isDirectLocalEntry) {
                                finish();
                            }
                        }
                    });
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            if (isDirectLocalEntry) {
                pendingOpenDirectLocalEntry = true;
            } else {
                pendingOpenLocalDriveDialog = true;
            }
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_LOCAL_STORAGE_PERMISSION);
            return;
        }
        onGranted.run();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCAL_STORAGE_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (granted) {
                if (pendingOpenLocalDriveDialog) {
                    pendingOpenLocalDriveDialog = false;
                    openLocalDriveDialog();
                } else if (pendingOpenDirectLocalEntry) {
                    pendingOpenDirectLocalEntry = false;
                    openDirectLocalEntry();
                }
            } else if (!granted) {
                pendingOpenLocalDriveDialog = false;
                pendingOpenDirectLocalEntry = false;
                Toast.makeText(mContext, "未获取到存储权限", Toast.LENGTH_SHORT).show();
                if (isDirectLocalEntry) {
                    finish();
                }
            }
        }
    }

    private static class LocalDriveOption {
        public final String path;
        public final String displayName;
        public final boolean manual;

        private LocalDriveOption(String path, String displayName, boolean manual) {
            this.path = path;
            this.displayName = displayName;
            this.manual = manual;
        }

        public static LocalDriveOption fromRoot(StorageRootFinder.StorageRoot root) {
            return new LocalDriveOption(root.path, root.label + "  " + root.path, false);
        }

        public static LocalDriveOption manual() {
            return new LocalDriveOption("__manual__", "手动选择文件夹", true);
        }
    }

    private void openWebdavDialog(StorageDrive drive) {
        WebdavDialog webdavDialog = new WebdavDialog(mContext, drive);
        EventBus.getDefault().register(webdavDialog);
        webdavDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                EventBus.getDefault().unregister(dialog);
            }
        });
        webdavDialog.show();
    }

    private void deleteAssociatedKtvSources(DriveFolderFile drive) {
        if (drive == null || drive.getDriveData() == null) {
            return;
        }
        if (drive.getDriveType() == StorageDriveType.TYPE.LOCAL) {
            RoomDataManger.deleteKtvMediaSourcesByRootOrChildren(KtvMediaSourceType.LOCAL.name(), drive.getDriveData().name);
            return;
        }
        if (drive.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
            String rootUrl = null;
            try {
                if (drive.getConfig() != null && drive.getConfig().has("url")) {
                    rootUrl = drive.getConfig().get("url").getAsString();
                }
            } catch (Exception ignored) {
            }
            RoomDataManger.deleteKtvMediaSourcesByRootOrChildren(KtvMediaSourceType.WEBDAV.name(), rootUrl);
        }
    }

    private void openAlistDriveDialog(StorageDrive drive) {
        AlistDriveDialog dialog = new AlistDriveDialog(mContext, drive);
        EventBus.getDefault().register(dialog);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                EventBus.getDefault().unregister(dialog);
            }
        });
        dialog.show();
    }

    public void toggleDelMode() {
        delMode = !delMode;
        if (delMode) {
            // takagen99: Added Theme Color
//            this.btnRemoveServer.setColorFilter(ContextCompat.getColor(mContext, R.color.color_theme));
            this.btnRemoveServer.setColorFilter(getThemeColor());
        } else {
            this.btnRemoveServer.setColorFilter(ContextCompat.getColor(mContext, R.color.color_FFFFFF));
        }
        adapter.toggleDelMode(delMode);
    }

    private void initData() {
        if (isDirectLocalEntry) {
            ensureLocalStoragePermission(new Runnable() {
                @Override
                public void run() {
                    openDirectLocalEntry();
                }
            });
            return;
        }
        this.txtTitle.setText(getString(R.string.act_drive));
        sortType = Hawk.get(HawkConfig.STORAGE_DRIVE_SORT, 0);
        btnSort.setVisibility(View.GONE);
        if (drives == null) {
            drives = new ArrayList<>();
            List<StorageDrive> storageDrives = RoomDataManger.getAllDrives();
            for (StorageDrive storageDrive : storageDrives) {
                DriveFolderFile drive = new DriveFolderFile(storageDrive);
                if (delMode)
                    drive.isDelMode = true;
                drives.add(drive);
            }
        }
        adapter.setNewData(drives);
        this.setSelectedItem(drives);
        updateToolbarActions();
        showSuccess();
    }

    private void openDirectLocalEntry() {
        this.txtTitle.setText(TextUtils.isEmpty(directLocalRootTitle) ? directLocalRootPath : directLocalRootTitle);
        sortType = Hawk.get(HawkConfig.STORAGE_DRIVE_SORT, 0);
        drives = new ArrayList<>();
        StorageDrive storageDrive = new StorageDrive();
        storageDrive.name = directLocalRootPath;
        storageDrive.type = StorageDriveType.TYPE.LOCAL.ordinal();
        DriveFolderFile drive = new DriveFolderFile(storageDrive);
        viewModel = new LocalDriveViewModel();
        viewModel.setCurrentDrive(drive);
        viewModel.setCurrentDriveNote(null);
        updateToolbarActions();
        loadDriveData();
    }

    private void setSelectedItem(List<DriveFolderFile> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).isSelected) {
                int isIndex = i;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mGridView.setSelection(isIndex);
                    }
                }, 50);
                return;
            }
        }
        mGridView.setSelection(0);
    }

    private void loadDriveData() {
        updateToolbarActions();
        viewModel.setSortType(sortType);
        btnSort.setVisibility(View.VISIBLE);
        showLoading();
        String path = viewModel.loadData(new AbstractDriveViewModel.LoadDataCallback() {
            @Override
            public void callback(List<DriveFolderFile> list, boolean alreadyHasChildren) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showSuccess();
                        updateToolbarActions();
                        if (alreadyHasChildren) {
                            adapter.setNewData(viewModel.getCurrentDriveNote().getChildren());
                            setSelectedItem(viewModel.getCurrentDriveNote().getChildren());
                        } else {
                            adapter.setNewData(viewModel.getCurrentDriveNote().getChildren());
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mGridView.setSelection(0);
                                }
                            }, 50);
                        }
                    }
                });
            }

            @Override
            public void fail(String message) {
                showSuccess();
                viewModel = null;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateToolbarActions();
                        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                        if (isDirectLocalEntry) {
                            finish();
                        }
                    }
                });
            }
        });
        if (StringUtils.isNotEmpty(path)) {
            this.txtTitle.setText(getDisplayTitle(path));
        }
    }

    private String getDisplayTitle(String fallbackPath) {
        if (isDirectLocalEntry
                && !TextUtils.isEmpty(directLocalRootTitle)
                && viewModel != null
                && viewModel.getCurrentDriveNote() != null
                && viewModel.getCurrentDriveNote().parentFolder == null
                && TextUtils.isEmpty(viewModel.getCurrentDriveNote().name)) {
            return directLocalRootTitle;
        }
        return fallbackPath;
    }

    private void cancel() {
        OkGo.getInstance().cancelTag("drive");
    }

    private void returnPreviousFolder() {
        if (isInSearch && viewModel == null) {
            //if already in search list
            isInSearch = false;
            viewModel = backupViewModel;
            backupViewModel = null;
            if (viewModel == null) {
                //if no last view list, return to main menu
                initData();
            } else {
                //return to last view list
                loadDriveData();
            }
            return;
        }
        viewModel.getCurrentDriveNote().setChildren(null);
        viewModel.setCurrentDriveNote(viewModel.getCurrentDriveNote().parentFolder);
        if (viewModel.getCurrentDriveNote() == null) {
            if (isInSearch) {
                //if returns from a search result, back to search result
                this.txtTitle.setText("搜索结果");
                adapter.setNewData(searchResult);
                viewModel = null;
                return;
            }
            if (isDirectLocalEntry) {
                viewModel = null;
                finish();
                return;
            }
            viewModel = null;
            updateToolbarActions();
            initData();
            return;
        }
        loadDriveData();
    }

    @Override
    public void onBackPressed() {
        if (viewModel != null) {
            cancel();
//            mGridView.onClick(mGridView.getChildAt(0));
            returnPreviousFolder();
            return;
        }
        if (isDirectLocalEntry) {
            finish();
            return;
        }
        if (!delMode)
            super.onBackPressed();
        else
            toggleDelMode();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_DRIVE_REFRESH) {
            if (isDirectLocalEntry) {
                return;
            }
            drives = null;
            initData();
        }
    }

    private void updateToolbarActions() {
        if (viewModel == null) {
            btnAddServer.setVisibility(View.VISIBLE);
            btnRemoveServer.setVisibility(isDirectLocalEntry ? View.GONE : View.VISIBLE);
            btnSort.setVisibility(View.GONE);
            return;
        }
        btnRemoveServer.setVisibility(View.GONE);
        btnAddServer.setVisibility(shouldShowAddShortcutAction() ? View.VISIBLE : View.GONE);
    }

    private boolean shouldShowAddShortcutAction() {
        if (viewModel == null || viewModel.getCurrentDrive() == null) {
            return false;
        }
        if (viewModel.getCurrentDrive().getDriveType() == StorageDriveType.TYPE.LOCAL) {
            return !TextUtils.isEmpty(getCurrentLocalDirectoryPath());
        }
        if (viewModel.getCurrentDrive().getDriveType() == StorageDriveType.TYPE.WEBDAV) {
            return true;
        }
        return false;
    }

    private String getCurrentLocalDirectoryPath() {
        if (viewModel == null || viewModel.getCurrentDrive() == null || viewModel.getCurrentDriveNote() == null) {
            return null;
        }
        if (viewModel.getCurrentDrive().getDriveType() != StorageDriveType.TYPE.LOCAL) {
            return null;
        }
        return RoomDataManger.normalizeLocalRootPath(viewModel.getCurrentDrive().name
                + viewModel.getCurrentDriveNote().getAccessingPathStr()
                + viewModel.getCurrentDriveNote().name);
    }

    private void addCurrentFolderShortcut() {
        String currentPath = getCurrentLocalDirectoryPath();
        if (TextUtils.isEmpty(currentPath)) {
            Toast.makeText(mContext, "当前目录不可添加", Toast.LENGTH_SHORT).show();
            return;
        }
        if (RoomDataManger.getHomeFolderShortcutByRootPath(currentPath) != null) {
            Toast.makeText(mContext, "该目录已添加到首页", Toast.LENGTH_SHORT).show();
            return;
        }
        String shortcutName = RoomDataManger.buildShortcutName(currentPath, null);
        com.github.tvbox.osc.cache.HomeFolderShortcut shortcut = RoomDataManger.insertHomeFolderShortcut(currentPath, shortcutName);
        if (shortcut == null) {
            Toast.makeText(mContext, "添加首页失败", Toast.LENGTH_SHORT).show();
            return;
        }
        RoomDataManger.rebuildShortcutIndex(shortcut.getId());
        Toast.makeText(mContext, "已添加到首页并开始索引", Toast.LENGTH_SHORT).show();
    }

    private void openCurrentDirectoryAddDialog() {
        List<String> actions = new ArrayList<>();
        if (viewModel != null && viewModel.getCurrentDrive() != null
                && viewModel.getCurrentDrive().getDriveType() == StorageDriveType.TYPE.LOCAL) {
            actions.add("添加到首页");
        }
        actions.add("加入 KTV 歌库");
        SelectDialog<String> dialog = new SelectDialog<>(this);
        dialog.setTip("选择操作");
        dialog.setItemCheckDisplay(false);
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                dialog.dismiss();
                if ("加入 KTV 歌库".equals(value)) {
                    addCurrentFolderToKtv();
                } else {
                    addCurrentFolderShortcut();
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
        }, actions, 0);
        dialog.show();
    }

    private void addCurrentFolderToKtv() {
        if (viewModel != null && viewModel.getCurrentDrive() != null
                && viewModel.getCurrentDrive().getDriveType() == StorageDriveType.TYPE.WEBDAV) {
            addCurrentWebDavToKtv();
            return;
        }
        String currentPath = getCurrentLocalDirectoryPath();
        if (TextUtils.isEmpty(currentPath)) {
            Toast.makeText(mContext, "当前目录不可添加", Toast.LENGTH_SHORT).show();
            return;
        }
        KtvMediaSource source = findExistingKtvSource(KtvMediaSourceType.LOCAL.name(), currentPath);
        if (source == null) {
            source = new KtvMediaSource();
            source.type = KtvMediaSourceType.LOCAL.name();
            source.displayName = RoomDataManger.buildShortcutName(currentPath, null);
            source.rootPathOrUrl = RoomDataManger.normalizeKtvSourcePath(KtvMediaSourceType.LOCAL.name(), currentPath);
            source.enabled = 1;
            source.scanStatus = "IDLE";
            long sourceId = RoomDataManger.insertKtvMediaSource(source);
            source.setId((int) sourceId);
        }
        KtvMediaSource finalSource = source;
        Toast.makeText(mContext, "已加入 KTV，开始扫描", Toast.LENGTH_SHORT).show();
        KtvLibraryIndexManager.get().scan(finalSource, new KtvLibraryIndexManager.ScanCallback() {
            @Override
            public void onSuccess(int count) {
                mHandler.post(() -> Toast.makeText(mContext, "KTV 扫描完成，共 " + count + " 首", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String error) {
                mHandler.post(() -> Toast.makeText(mContext, "KTV 扫描失败: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void addCurrentWebDavToKtv() {
        if (viewModel == null || viewModel.getCurrentDrive() == null) {
            Toast.makeText(mContext, "当前目录不可添加", Toast.LENGTH_SHORT).show();
            return;
        }
        DriveFolderFile currentDrive = viewModel.getCurrentDrive();
        String relative = "";
        if (viewModel.getCurrentDriveNote() != null) {
            relative = viewModel.getCurrentDriveNote().getAccessingPathStr() + viewModel.getCurrentDriveNote().name;
        }
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        String baseUrl = currentDrive.getConfig().get("url").getAsString();
        String rootUrl = buildWebDavKtvRootUrl(baseUrl, relative);
        KtvMediaSource source = findExistingKtvSource(KtvMediaSourceType.WEBDAV.name(), rootUrl);
        if (source == null) {
            source = new KtvMediaSource();
            source.type = KtvMediaSourceType.WEBDAV.name();
            source.displayName = currentDrive.name + (TextUtils.isEmpty(relative) ? "" : (" / " + relative));
            source.rootPathOrUrl = rootUrl;
            source.configJson = currentDrive.getDriveData().configJson;
            source.enabled = 1;
            source.scanStatus = "IDLE";
            long sourceId = RoomDataManger.insertKtvMediaSource(source);
            source.setId((int) sourceId);
        }
        KtvMediaSource finalSource = source;
        Toast.makeText(mContext, "已加入 KTV，开始扫描", Toast.LENGTH_SHORT).show();
        KtvLibraryIndexManager.get().scan(finalSource, new KtvLibraryIndexManager.ScanCallback() {
            @Override
            public void onSuccess(int count) {
                mHandler.post(() -> Toast.makeText(mContext, "KTV 扫描完成，共 " + count + " 首", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String error) {
                mHandler.post(() -> Toast.makeText(mContext, "KTV 扫描失败: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private KtvMediaSource findExistingKtvSource(String type, String rootPathOrUrl) {
        String normalized = RoomDataManger.normalizeKtvSourcePath(type, rootPathOrUrl);
        for (KtvMediaSource source : RoomDataManger.getAllKtvMediaSources()) {
            if (type.equals(source.type) && normalized.equals(RoomDataManger.normalizeKtvSourcePath(source.type, source.rootPathOrUrl))) {
                return source;
            }
        }
        return null;
    }

    private String buildWebDavKtvRootUrl(String baseUrl, String relative) {
        if (TextUtils.isEmpty(baseUrl)) {
            return "";
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String normalizedRelative = TextUtils.isEmpty(relative) ? "" : relative;
        return RoomDataManger.normalizeKtvSourcePath(KtvMediaSourceType.WEBDAV.name(), normalizedBase + normalizedRelative);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}

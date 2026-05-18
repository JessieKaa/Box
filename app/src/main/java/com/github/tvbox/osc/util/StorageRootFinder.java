package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StorageRootFinder {

    private static final String ANDROID_DATA_SEGMENT = "/Android/";

    private StorageRootFinder() {
    }

    public static class StorageRoot {
        public final String path;
        public final String label;
        public final boolean removable;

        public StorageRoot(String path, String label, boolean removable) {
            this.path = path;
            this.label = label;
            this.removable = removable;
        }
    }

    public static List<StorageRoot> find(Context context) {
        LinkedHashMap<String, StorageRoot> roots = new LinkedHashMap<>();

        addRoot(roots, Environment.getExternalStorageDirectory(), "内部存储", false);

        File[] externalDirs = context.getExternalFilesDirs(null);
        int removableCount = 1;
        for (File dir : externalDirs) {
            if (dir == null) continue;
            String absolutePath = dir.getAbsolutePath();
            int androidDataIndex = absolutePath.indexOf(ANDROID_DATA_SEGMENT);
            File root = androidDataIndex > 0 ? new File(absolutePath.substring(0, androidDataIndex)) : dir;
            boolean removable = Environment.isExternalStorageRemovable(dir);
            String label = removable ? "外接存储 " + removableCount++ : "内部共享存储";
            addRoot(roots, root, label, removable);
        }

        removableCount = scanParent(roots, new File("/storage"), removableCount);
        removableCount = scanParent(roots, new File("/mnt"), removableCount);
        scanParent(roots, new File("/mnt/media_rw"), removableCount);

        return new ArrayList<>(roots.values());
    }

    private static int scanParent(Map<String, StorageRoot> roots, File parent, int removableCount) {
        if (parent == null || !parent.exists() || !parent.canRead() || !parent.isDirectory()) {
            return removableCount;
        }
        File[] children = parent.listFiles();
        if (children == null) return removableCount;
        for (File child : children) {
            if (child == null || !child.isDirectory()) continue;
            String name = child.getName();
            if ("emulated".equalsIgnoreCase(name)
                    || "self".equalsIgnoreCase(name)
                    || "enc_emulated".equalsIgnoreCase(name)) {
                continue;
            }
            boolean removable = !child.getAbsolutePath().startsWith("/storage/emulated");
            String label = removable ? "外接存储 " + removableCount++ : "存储设备";
            addRoot(roots, child, label, removable);
        }
        return removableCount;
    }

    private static void addRoot(Map<String, StorageRoot> roots, File root, String label, boolean removable) {
        if (root == null || !root.exists() || !root.canRead() || !root.isDirectory()) {
            return;
        }
        try {
            String canonicalPath = root.getCanonicalPath();
            if (roots.containsKey(canonicalPath)) return;
            roots.put(canonicalPath, new StorageRoot(canonicalPath, label, removable));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

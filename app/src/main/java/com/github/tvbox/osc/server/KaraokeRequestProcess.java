package com.github.tvbox.osc.server;

import com.github.tvbox.osc.karaoke.KaraokeRemoteManager;
import com.github.tvbox.osc.util.StorageDriveType;
import com.google.gson.Gson;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class KaraokeRequestProcess implements RequestProcess {

    private final Gson gson = new Gson();

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        return fileName.equals("/karaoke/api") || fileName.startsWith("/karaoke/api/");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName,
                                          Map<String, String> params, Map<String, String> files) {
        String path = fileName.substring("/karaoke/api".length());
        if (path.isEmpty()) path = "/";

        if (session.getMethod() == NanoHTTPD.Method.GET) {
            return handleGet(path, params);
        } else if (session.getMethod() == NanoHTTPD.Method.POST) {
            return handlePost(path, params);
        }

        return errorResponse("Method not allowed");
    }

    private NanoHTTPD.Response handleGet(String path, Map<String, String> params) {
        switch (path) {
            case "/state":
                return respondState();
            case "/library":
                return respondLibrary(params);
            case "/queue":
                return respondQueue();
            case "/audioTracks":
                return respondAudioTracks();
            case "/files":
                return respondFiles();
            default:
                return errorResponse("Unknown endpoint: " + path);
        }
    }

    private NanoHTTPD.Response handlePost(String path, Map<String, String> params) {
        KaraokeRemoteManager manager = KaraokeRemoteManager.get();

        // File management endpoints work regardless of activity state
        switch (path) {
            case "/rescan":
                return doRescan();
            case "/renameFile":
                return handleRenameFile(params);
            case "/deleteFile":
                return handleDeleteFile(params);
        }

        if (!manager.isActive()) {
            return jsonResult(false);
        }

        boolean success = false;
        switch (path) {
            case "/togglePlayPause":
                success = manager.togglePlayPause();
                break;
            case "/play":
                success = manager.resumePlay();
                break;
            case "/pause":
                success = manager.pausePlay();
                break;
            case "/next":
                success = manager.playNext();
                break;
            case "/prev":
                success = manager.playPrevious();
                break;
            case "/addToQueue": {
                String filePath = params.get("filePath");
                if (filePath != null && !filePath.isEmpty()) {
                    success = manager.addToQueue(filePath);
                }
                break;
            }
            case "/removeFromQueue": {
                String posStr = params.get("position");
                if (posStr != null) {
                    try {
                        int position = Integer.parseInt(posStr);
                        success = manager.removeFromQueue(position);
                    } catch (NumberFormatException e) {
                        success = false;
                    }
                }
                break;
            }
            case "/playAt": {
                String posStr = params.get("position");
                if (posStr != null) {
                    try {
                        int position = Integer.parseInt(posStr);
                        success = manager.playAt(position);
                    } catch (NumberFormatException e) {
                        success = false;
                    }
                }
                break;
            }
            case "/switchAudioTrack": {
                String idStr = params.get("trackId");
                if (idStr != null) {
                    try {
                        int trackId = Integer.parseInt(idStr);
                        success = manager.switchAudioTrack(trackId);
                    } catch (NumberFormatException e) {
                        success = false;
                    }
                }
                break;
            }
            default:
                return errorResponse("Unknown endpoint: " + path);
        }

        return jsonResult(success);
    }

    private NanoHTTPD.Response respondState() {
        KaraokeRemoteManager manager = KaraokeRemoteManager.get();
        Map<String, Object> state = manager.getState();
        return jsonResponse(gson.toJson(state));
    }

    private NanoHTTPD.Response respondLibrary(Map<String, String> params) {
        KaraokeRemoteManager manager = KaraokeRemoteManager.get();

        if (!manager.isActive()) {
            return jsonResponse("{\"songs\":[],\"artists\":[]}");
        }

        String search = params.get("search");
        String artist = params.get("artist");
        KaraokeRemoteManager.LibrarySnapshot snapshot = manager.getLibrarySnapshot(search, artist);
        if (snapshot == null) {
            return jsonResponse("{\"songs\":[],\"artists\":[]}");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("songs", snapshot.songs);
        result.put("artists", snapshot.artists);
        return jsonResponse(gson.toJson(result));
    }

    private NanoHTTPD.Response respondQueue() {
        KaraokeRemoteManager manager = KaraokeRemoteManager.get();

        if (!manager.isActive()) {
            return jsonResponse("{\"queue\":[],\"currentIndex\":-1}");
        }

        KaraokeRemoteManager.QueueSnapshot snapshot = manager.getQueueSnapshot();
        if (snapshot == null) {
            return jsonResponse("{\"queue\":[],\"currentIndex\":-1}");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("queue", snapshot.queue);
        result.put("currentIndex", snapshot.currentIndex);
        return jsonResponse(gson.toJson(result));
    }

    private NanoHTTPD.Response respondAudioTracks() {
        KaraokeRemoteManager manager = KaraokeRemoteManager.get();
        if (!manager.isActive()) {
            return jsonResponse("{\"tracks\":[]}");
        }
        List<Map<String, Object>> tracks = manager.getAudioTracks();
        if (tracks == null) {
            tracks = new ArrayList<>();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("tracks", tracks);
        return jsonResponse(gson.toJson(result));
    }

    private File getKaraokeFolder() {
        String folderPath = Hawk.get("karaoke_folder", "");
        if (folderPath == null || folderPath.isEmpty()) {
            return null;
        }
        return new File(folderPath);
    }

    private File validatePath(String filePath, File rootFolder) {
        if (filePath == null || filePath.isEmpty() || rootFolder == null) {
            return null;
        }
        try {
            String rootCanonical = rootFolder.getCanonicalPath();
            File file = new File(rootFolder, filePath);
            String fileCanonical = file.getCanonicalPath();
            if (!fileCanonical.equals(rootCanonical) && !fileCanonical.startsWith(rootCanonical + File.separator)) {
                return null;
            }
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isValidNewName(String newName) {
        if (newName == null || newName.isEmpty()) {
            return false;
        }
        if (newName.contains("/") || newName.contains("\\") || newName.contains("..") || newName.contains("\0")) {
            return false;
        }
        if (newName.contains(File.separator)) {
            return false;
        }
        return true;
    }

    private boolean isVideoFile(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot >= fileName.length() - 1) return false;
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return StorageDriveType.isVideoType(ext);
    }

    private NanoHTTPD.Response respondFiles() {
        File folder = getKaraokeFolder();
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return errorResponse("未设置卡拉OK文件夹");
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return jsonResponse("{\"files\":[]}");
        }
        List<File> fileList = new ArrayList<>();
        for (File f : files) {
            if (f.isFile()) {
                fileList.add(f);
            }
        }
        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        List<Map<String, Object>> resultFiles = new ArrayList<>();
        for (File file : fileList) {
            Map<String, Object> item = new HashMap<>();
            String name = file.getName();
            item.put("name", name);
            int dot = name.lastIndexOf('.');
            String nameNoExt = dot > 0 ? name.substring(0, dot) : name;
            item.put("nameNoExt", nameNoExt);
            item.put("filePath", name);
            item.put("size", file.length());
            item.put("isVideo", isVideoFile(name));
            resultFiles.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("files", resultFiles);
        return jsonResponse(gson.toJson(result));
    }

    private NanoHTTPD.Response handleDeleteFile(Map<String, String> params) {
        File folder = getKaraokeFolder();
        if (folder == null) {
            return errorResponse("未设置卡拉OK文件夹");
        }
        String filePath = params.get("filePath");
        File file = validatePath(filePath, folder);
        if (file == null) {
            return errorResponse("非法路径");
        }
        if (!file.isFile()) {
            return errorResponse("文件不存在");
        }
        if (!file.delete()) {
            return errorResponse("删除失败");
        }
        doRescan();
        return jsonResult(true);
    }

    private NanoHTTPD.Response handleRenameFile(Map<String, String> params) {
        File folder = getKaraokeFolder();
        if (folder == null) {
            return errorResponse("未设置卡拉OK文件夹");
        }
        String filePath = params.get("filePath");
        File file = validatePath(filePath, folder);
        if (file == null || !file.isFile()) {
            return errorResponse("文件不存在");
        }

        String newName = params.get("newName");
        if (newName != null) {
            newName = newName.trim();
        }
        if (!isValidNewName(newName)) {
            return errorResponse("文件名不合法");
        }

        String originalName = file.getName();
        int dot = originalName.lastIndexOf('.');
        String originalExt = "";
        if (dot > 0 && dot < originalName.length() - 1) {
            originalExt = originalName.substring(dot).toLowerCase(Locale.ROOT);
        }

        int newDot = newName.lastIndexOf('.');
        String newNameBody = newName;
        String newNameExt = "";
        if (newDot > 0 && newDot < newName.length() - 1) {
            newNameBody = newName.substring(0, newDot);
            newNameExt = newName.substring(newDot).toLowerCase(Locale.ROOT);
        }

        String finalName;
        if (!originalExt.isEmpty() && newNameExt.equalsIgnoreCase(originalExt)) {
            finalName = newNameBody + originalExt;
        } else {
            finalName = newNameBody + originalExt;
        }

        if (finalName.equalsIgnoreCase(originalName)) {
            return jsonResult(true);
        }

        File dest = new File(folder, finalName);
        try {
            if (dest.exists() && !dest.getCanonicalPath().equals(file.getCanonicalPath())) {
                return errorResponse("文件名已存在");
            }
        } catch (IOException e) {
            return errorResponse("重命名失败");
        }

        if (!file.renameTo(dest)) {
            return errorResponse("重命名失败");
        }
        doRescan();
        return jsonResult(true);
    }

    private NanoHTTPD.Response doRescan() {
        File folder = getKaraokeFolder();
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return errorResponse("未设置卡拉OK文件夹");
        }
        if (!KaraokeRemoteManager.get().triggerRescan()) {
            return errorResponse("TV未连接，无法刷新曲库。下次打开卡拉OK时会自动扫描");
        }
        return jsonResult(true);
    }

    private NanoHTTPD.Response jsonResponse(String json) {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json; charset=utf-8", json);
    }

    private NanoHTTPD.Response jsonResult(boolean success) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        return jsonResponse(gson.toJson(result));
    }

    private NanoHTTPD.Response errorResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return jsonResponse(gson.toJson(result));
    }
}

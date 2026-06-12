package com.github.tvbox.osc.server;

import com.github.tvbox.osc.karaoke.KaraokeRemoteManager;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
            default:
                return errorResponse("Unknown endpoint: " + path);
        }
    }

    private NanoHTTPD.Response handlePost(String path, Map<String, String> params) {
        KaraokeRemoteManager manager = KaraokeRemoteManager.get();

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

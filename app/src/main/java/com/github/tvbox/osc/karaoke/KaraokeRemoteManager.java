package com.github.tvbox.osc.karaoke;

import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.karaoke.playlist.KaraokeSession;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class KaraokeRemoteManager {

    private static KaraokeRemoteManager instance;
    private WeakReference<KaraokeActivity> activityRef;

    private static final long UI_TIMEOUT_MS = 3000;

    private KaraokeRemoteManager() {}

    public static synchronized KaraokeRemoteManager get() {
        if (instance == null) {
            instance = new KaraokeRemoteManager();
        }
        return instance;
    }

    public void attach(KaraokeActivity activity) {
        activityRef = new WeakReference<>(activity);
    }

    public void detach() {
        activityRef = null;
    }

    public boolean isActive() {
        return getActivity() != null;
    }

    private KaraokeActivity getActivity() {
        if (activityRef == null) return null;
        KaraokeActivity activity = activityRef.get();
        if (activity == null) {
            activityRef = null;
        }
        return activity;
    }

    private <T> T runOnUiAndWait(Callable<T> callable) {
        KaraokeActivity activity = getActivity();
        if (activity == null) return null;
        FutureTask<T> future = new FutureTask<>(callable);
        activity.runOnUiThread(future);
        try {
            return future.get(UI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> getState() {
        Map<String, Object> result = runOnUiAndWait(() -> {
            KaraokeActivity activity = getActivity();
            if (activity == null) return null;
            return activity.getRemoteState();
        });
        if (result == null) {
            result = new HashMap<>();
            result.put("active", false);
        }
        return result;
    }

    public LibrarySnapshot getLibrarySnapshot(String search, String artist) {
        return runOnUiAndWait(() -> {
            KaraokeActivity activity = getActivity();
            if (activity == null) return null;
            return activity.getRemoteLibrary(search, artist);
        });
    }

    public QueueSnapshot getQueueSnapshot() {
        return runOnUiAndWait(() -> {
            KaraokeActivity activity = getActivity();
            if (activity == null) return null;
            return activity.getRemoteQueue();
        });
    }

    public boolean togglePlayPause() {
        KaraokeActivity activity = getActivity();
        if (activity == null) return false;
        activity.runOnUiThread(() -> activity.remoteTogglePlayPause());
        return true;
    }

    public boolean playNext() {
        KaraokeActivity activity = getActivity();
        if (activity == null) return false;
        activity.runOnUiThread(() -> activity.remotePlayNext());
        return true;
    }

    public boolean playPrevious() {
        KaraokeActivity activity = getActivity();
        if (activity == null) return false;
        activity.runOnUiThread(() -> activity.remotePlayPrevious());
        return true;
    }

    public boolean resumePlay() {
        KaraokeActivity activity = getActivity();
        if (activity == null) return false;
        activity.runOnUiThread(() -> activity.remoteResumePlay());
        return true;
    }

    public boolean pausePlay() {
        KaraokeActivity activity = getActivity();
        if (activity == null) return false;
        activity.runOnUiThread(() -> activity.remotePausePlay());
        return true;
    }

    public boolean addToQueue(String filePath) {
        KaraokeActivity activity = getActivity();
        if (activity == null) return false;
        activity.runOnUiThread(() -> activity.remoteAddToQueue(filePath));
        return true;
    }

    public boolean removeFromQueue(int position) {
        KaraokeActivity activity = getActivity();
        if (activity == null) return false;
        activity.runOnUiThread(() -> activity.remoteRemoveFromQueue(position));
        return true;
    }

    public boolean playAt(int position) {
        KaraokeActivity activity = getActivity();
        if (activity == null) return false;
        activity.runOnUiThread(() -> activity.remotePlayAt(position));
        return true;
    }

    public List<Map<String, Object>> getAudioTracks() {
        return runOnUiAndWait(() -> {
            KaraokeActivity activity = getActivity();
            if (activity == null) return new ArrayList<>();
            return activity.getRemoteAudioTracks();
        });
    }

    public boolean switchAudioTrack(int trackId) {
        Boolean result = runOnUiAndWait(() -> {
            KaraokeActivity activity = getActivity();
            if (activity == null) return false;
            return activity.remoteSwitchAudioTrack(trackId);
        });
        return result != null && result;
    }

    public static class LibrarySnapshot {
        public final List<Map<String, String>> songs;
        public final List<String> artists;

        LibrarySnapshot(List<Map<String, String>> songs, List<String> artists) {
            this.songs = songs;
            this.artists = artists;
        }
    }

    public static class QueueSnapshot {
        public final List<Map<String, String>> queue;
        public final int currentIndex;

        QueueSnapshot(List<Map<String, String>> queue, int currentIndex) {
            this.queue = queue;
            this.currentIndex = currentIndex;
        }
    }
}

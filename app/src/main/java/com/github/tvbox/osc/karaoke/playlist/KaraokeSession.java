package com.github.tvbox.osc.karaoke.playlist;

import com.github.tvbox.osc.karaoke.bean.KaraokeSong;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class KaraokeSession {

    public static class RemoveResult {
        public enum Action { KEEP_PLAYING, SWITCH_TO_SONG, RETURN_TO_SELECT }
        public Action action;
        public KaraokeSong nextSong;

        RemoveResult(Action action) {
            this.action = action;
        }

        RemoveResult(Action action, KaraokeSong nextSong) {
            this.action = action;
            this.nextSong = nextSong;
        }

        public static RemoveResult keepPlaying() {
            return new RemoveResult(Action.KEEP_PLAYING);
        }

        public static RemoveResult switchTo(KaraokeSong song) {
            return new RemoveResult(Action.SWITCH_TO_SONG, song);
        }

        public static RemoveResult returnToSelect() {
            return new RemoveResult(Action.RETURN_TO_SELECT);
        }
    }

    private List<KaraokeSong> library = new ArrayList<>();
    private List<KaraokeSong> queue = new ArrayList<>();
    private int currentQueueIndex = -1;

    public void setLibrary(List<KaraokeSong> songs) {
        library = songs != null ? Collections.unmodifiableList(new ArrayList<>(songs)) : Collections.emptyList();
    }

    public List<KaraokeSong> getLibrary() {
        return library;
    }

    public List<String> getArtists() {
        Set<String> artists = new LinkedHashSet<>();
        for (KaraokeSong song : library) {
            if (song.artist != null && !song.artist.isEmpty()) {
                artists.add(song.artist);
            }
        }
        return new ArrayList<>(artists);
    }

    public void addToQueue(KaraokeSong song) {
        queue.add(song);
    }

    public RemoveResult removeFromQueue(int position) {
        if (position < 0 || position >= queue.size()) return RemoveResult.keepPlaying();

        queue.remove(position);

        if (currentQueueIndex < 0) return RemoveResult.keepPlaying();

        if (position < currentQueueIndex) {
            currentQueueIndex--;
            return RemoveResult.keepPlaying();
        }

        if (position == currentQueueIndex) {
            if (queue.isEmpty()) {
                currentQueueIndex = -1;
                return RemoveResult.returnToSelect();
            }
            if (currentQueueIndex >= queue.size()) {
                currentQueueIndex = queue.size() - 1;
            }
            return RemoveResult.switchTo(queue.get(currentQueueIndex));
        }

        // position > currentQueueIndex
        return RemoveResult.keepPlaying();
    }

    public List<KaraokeSong> getQueue() {
        return queue;
    }

    public boolean isInQueue(KaraokeSong song) {
        return queue.contains(song);
    }

    public void clearQueue() {
        queue.clear();
        currentQueueIndex = -1;
    }

    public KaraokeSong getCurrentSong() {
        if (currentQueueIndex >= 0 && currentQueueIndex < queue.size()) {
            return queue.get(currentQueueIndex);
        }
        return null;
    }

    public int getCurrentQueueIndex() {
        return currentQueueIndex;
    }

    public void playAt(int queuePosition) {
        if (queuePosition >= 0 && queuePosition < queue.size()) {
            currentQueueIndex = queuePosition;
        }
    }

    public KaraokeSong playNext() {
        if (currentQueueIndex >= 0 && currentQueueIndex < queue.size() - 1) {
            currentQueueIndex++;
            return queue.get(currentQueueIndex);
        }
        return null;
    }

    public KaraokeSong playPrevious() {
        if (currentQueueIndex > 0) {
            currentQueueIndex--;
            return queue.get(currentQueueIndex);
        }
        return null;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public void reset() {
        queue.clear();
        currentQueueIndex = -1;
    }
}

package com.github.tvbox.osc.ktv;

import android.text.TextUtils;

import com.github.tvbox.osc.cache.KtvQueueItem;
import com.github.tvbox.osc.cache.KtvSong;
import com.github.tvbox.osc.cache.RoomDataManger;

import java.util.List;

public class KtvQueueManager {
    private static volatile KtvQueueManager instance;

    public static KtvQueueManager get() {
        if (instance == null) {
            synchronized (KtvQueueManager.class) {
                if (instance == null) {
                    instance = new KtvQueueManager();
                }
            }
        }
        return instance;
    }

    public KtvQueueItem addToQueue(KtvSong song) {
        KtvQueueItem item = buildQueueItem(song);
        item.queueOrder = RoomDataManger.getNextKtvQueueOrder();
        item.status = KtvQueueItem.STATUS_PENDING;
        RoomDataManger.insertKtvQueueItem(item);
        return item;
    }

    public KtvQueueItem playNow(KtvSong song) {
        KtvQueueItem current = RoomDataManger.getCurrentKtvQueueItem();
        if (current != null) {
            RoomDataManger.markKtvQueueItemStatus(current.getId(), KtvQueueItem.STATUS_DONE);
        }
        KtvQueueItem item = buildQueueItem(song);
        item.queueOrder = RoomDataManger.getNextKtvQueueOrder();
        item.status = KtvQueueItem.STATUS_PLAYING;
        long id = RoomDataManger.insertKtvQueueItem(item);
        item.setId((int) id);
        return item;
    }

    public KtvQueueItem getCurrentPlaying() {
        return RoomDataManger.getCurrentKtvQueueItem();
    }

    public List<KtvQueueItem> getQueueItems() {
        return RoomDataManger.getAllKtvQueueItems();
    }

    public void removePendingItem(int id) {
        for (KtvQueueItem item : RoomDataManger.getPendingKtvQueueItems()) {
            if (item.getId() == id) {
                RoomDataManger.deleteKtvQueueItem(id);
                return;
            }
        }
    }

    public void clearPending() {
        RoomDataManger.clearKtvPendingQueue();
    }

    public KtvQueueItem onPlaybackCompleted() {
        KtvQueueItem current = RoomDataManger.getCurrentKtvQueueItem();
        if (current != null) {
            RoomDataManger.markKtvQueueItemStatus(current.getId(), KtvQueueItem.STATUS_DONE);
        }
        return promoteNextPending();
    }

    public KtvQueueItem onPlaybackFailed() {
        KtvQueueItem current = RoomDataManger.getCurrentKtvQueueItem();
        if (current != null) {
            RoomDataManger.markKtvQueueItemStatus(current.getId(), KtvQueueItem.STATUS_FAILED);
        }
        return promoteNextPending();
    }

    public KtvQueueItem restoreOrPromoteCurrent() {
        KtvQueueItem current = RoomDataManger.getCurrentKtvQueueItem();
        if (current != null) {
            return current;
        }
        return promoteNextPending();
    }

    public KtvQueueItem restoreOrPromoteCurrent(int preferredQueueId) {
        KtvQueueItem preferred = preferredQueueId > 0 ? RoomDataManger.getKtvQueueItem(preferredQueueId) : null;
        if (preferred != null) {
            if (KtvQueueItem.STATUS_PLAYING.equals(preferred.status)) {
                return preferred;
            }
            if (KtvQueueItem.STATUS_PENDING.equals(preferred.status)) {
                RoomDataManger.markAllPlayingAsDone();
                RoomDataManger.markKtvQueueItemStatus(preferred.getId(), KtvQueueItem.STATUS_PLAYING);
                preferred.status = KtvQueueItem.STATUS_PLAYING;
                return preferred;
            }
        }
        return restoreOrPromoteCurrent();
    }

    private KtvQueueItem promoteNextPending() {
        KtvQueueItem next = RoomDataManger.getNextPendingKtvQueueItem();
        if (next != null) {
            RoomDataManger.markKtvQueueItemStatus(next.getId(), KtvQueueItem.STATUS_PLAYING);
            next.status = KtvQueueItem.STATUS_PLAYING;
        }
        return next;
    }

    private KtvQueueItem buildQueueItem(KtvSong song) {
        KtvQueueItem item = new KtvQueueItem();
        item.songId = song.getId();
        item.songTitle = song.title;
        item.artist = song.artist;
        item.playUrl = song.playUrl;
        item.sourceType = song.sourceType;
        item.sourcePath = song.filePath;
        item.createdAt = System.currentTimeMillis();
        if (TextUtils.isEmpty(item.artist)) {
            item.artist = "";
        }
        return item;
    }
}

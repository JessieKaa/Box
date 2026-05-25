package com.github.tvbox.osc.ktv;

import android.text.TextUtils;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.cache.KtvMediaSource;
import com.github.tvbox.osc.cache.KtvSong;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.util.StorageDriveType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KtvLibraryIndexManager {
    public interface ScanCallback {
        void onSuccess(int count);

        void onError(String error);
    }

    private static volatile KtvLibraryIndexManager instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static KtvLibraryIndexManager get() {
        if (instance == null) {
            synchronized (KtvLibraryIndexManager.class) {
                if (instance == null) {
                    instance = new KtvLibraryIndexManager();
                }
            }
        }
        return instance;
    }

    public void scan(KtvMediaSource source, ScanCallback callback) {
        executor.execute(() -> doScan(source, callback));
    }

    private void doScan(KtvMediaSource source, ScanCallback callback) {
        try {
            source.scanStatus = "SCANNING";
            source.scanError = null;
            RoomDataManger.insertKtvMediaSource(source);
            MediaLibraryProvider provider = createProvider(source);
            List<KtvSong> songs = scanAllSongs(provider, source);
            AppDataManager.get().runInTransaction(() -> {
                RoomDataManger.clearKtvSongsBySource(source.getId());
                if (!songs.isEmpty()) {
                    RoomDataManger.insertKtvSongs(songs);
                }
                source.scanStatus = "DONE";
                source.scanError = null;
                source.lastScanAt = System.currentTimeMillis();
                RoomDataManger.insertKtvMediaSource(source);
            });
            if (callback != null) {
                App.post(() -> callback.onSuccess(songs.size()));
            }
        } catch (Exception e) {
            source.scanStatus = "FAILED";
            source.scanError = e.getMessage();
            source.lastScanAt = System.currentTimeMillis();
            RoomDataManger.insertKtvMediaSource(source);
            if (callback != null) {
                App.post(() -> callback.onError(e.getMessage()));
            }
        }
    }

    private List<KtvSong> scanAllSongs(MediaLibraryProvider provider, KtvMediaSource source) throws Exception {
        List<KtvSong> songs = new ArrayList<>();
        scanRecursiveCollect(provider, source, "", songs);
        return songs;
    }

    private void scanRecursiveCollect(MediaLibraryProvider provider, KtvMediaSource source, String path, List<KtvSong> songs) throws Exception {
        List<KtvMediaEntry> items = provider.list(path);
        for (KtvMediaEntry entry : items) {
            if (entry.isFile) {
                if (!StorageDriveType.isVideoType(entry.fileType)) {
                    continue;
                }
                songs.add(toSong(source, provider, entry));
            } else if (provider.supportsRecursiveScan()) {
                scanRecursiveCollect(provider, source, entry.path, songs);
            }
        }
    }

    private KtvSong toSong(KtvMediaSource source, MediaLibraryProvider provider, KtvMediaEntry entry) throws Exception {
        KtvSongFileNameParser.ParsedSong parsed = KtvSongFileNameParser.parse(entry.name);
        KtvSong song = new KtvSong();
        song.sourceId = source.getId();
        song.sourceType = source.type;
        song.filePath = entry.path;
        song.playUrl = provider.resolvePlayableUrl(entry);
        song.fileName = entry.name;
        song.title = parsed.title;
        song.artist = parsed.artist;
        song.initials = parsed.initials;
        song.lastModified = entry.lastModified;
        song.fileSize = entry.fileSize;
        song.searchText = buildSearchText(song.title, song.artist, song.fileName);
        return song;
    }

    private String buildSearchText(String title, String artist, String fileName) {
        return (safe(title) + " " + safe(artist) + " " + safe(fileName)).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return TextUtils.isEmpty(value) ? "" : value;
    }

    public static MediaLibraryProvider createProvider(KtvMediaSource source) {
        KtvMediaSourceType type = KtvMediaSourceType.valueOf(source.type);
        if (type == KtvMediaSourceType.WEBDAV) {
            return new WebDavMediaLibraryProvider(source);
        }
        return new LocalMediaLibraryProvider(source);
    }
}

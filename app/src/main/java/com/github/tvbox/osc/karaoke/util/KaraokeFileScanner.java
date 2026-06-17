package com.github.tvbox.osc.karaoke.util;

import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.util.StorageDriveType;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KaraokeFileScanner {

    private static final String TAG = "KaraokeFileScanner";
    private static final Pattern SONG_PATTERN1 = Pattern.compile("^(.+?)\\s*[-\\u2014\\u2013]\\s*(.+)$");
    private static final Pattern SONG_PATTERN2 = Pattern.compile("^\\[(.+?)\\]\\s*(.+)$");

    public interface CancelSignal {
        boolean isCanceled();
    }

    public static List<KaraokeSong> scanFolder(File folder, boolean recursive, CancelSignal cancelSignal) {
        List<KaraokeSong> songs = new ArrayList<>();
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return songs;
        }

        Set<String> canonicalPaths = new HashSet<>();
        Deque<File> queue = new ArrayDeque<>();
        queue.add(folder);

        while (!queue.isEmpty()) {
            if (cancelSignal != null && cancelSignal.isCanceled()) {
                break;
            }
            File current = queue.poll();
            File[] children = current.listFiles();
            if (children == null) continue;

            for (File child : children) {
                if (cancelSignal != null && cancelSignal.isCanceled()) {
                    break;
                }
                if (child.isDirectory()) {
                    if (recursive) {
                        queue.add(child);
                    }
                } else if (child.isFile() && isVideoFile(child.getName())) {
                    try {
                        String canonical = child.getCanonicalPath();
                        if (!canonicalPaths.add(canonical)) continue;
                    } catch (Exception e) {
                        // fall back to absolute path on failure
                        if (!canonicalPaths.add(child.getAbsolutePath())) continue;
                    }
                    songs.add(parseSong(child));
                }
            }
        }

        Collections.sort(songs, new Comparator<KaraokeSong>() {
            @Override
            public int compare(KaraokeSong o1, KaraokeSong o2) {
                return o1.displayName.compareToIgnoreCase(o2.displayName);
            }
        });
        return songs;
    }

    /** Computes a cumulative signature across all video files reachable from the folder. */
    public static long computeSignature(File folder, boolean recursive) {
        long signature = 0;
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return signature;
        }
        Set<String> visited = new HashSet<>();
        Deque<File> queue = new ArrayDeque<>();
        queue.add(folder);
        while (!queue.isEmpty()) {
            File current = queue.poll();
            File[] children = current.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) {
                    if (recursive) queue.add(child);
                } else if (child.isFile() && isVideoFile(child.getName())) {
                    try {
                        String canonical = child.getCanonicalPath();
                        if (!visited.add(canonical)) continue;
                    } catch (Exception e) {
                        if (!visited.add(child.getAbsolutePath())) continue;
                    }
                    signature += child.lastModified() ^ child.length();
                }
            }
        }
        return signature;
    }

    private static boolean isVideoFile(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1);
        // Karaoke library accepts both video containers and karaoke audio containers
        // (MKA / FLAC / etc.). DriveActivity / DriveAdapter continue to use the
        // stricter StorageDriveType.isVideoType so the drive browser doesn't start
        // showing audio files as videos.
        return StorageDriveType.isVideoType(ext) || StorageDriveType.isKaraokeAudioType(ext);
    }

    private static KaraokeSong parseSong(File file) {
        KaraokeSong song = new KaraokeSong();
        song.filePath = file.getAbsolutePath();
        song.fileSize = file.length();
        song.lastModified = file.lastModified();

        MediaMetadataRetriever mmr = null;
        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(file.getAbsolutePath());
            String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (title != null && !title.trim().isEmpty()) {
                song.title = title.trim();
            }
            if (artist != null && !artist.trim().isEmpty()) {
                song.artist = artist.trim();
            }
            if (duration != null) {
                try {
                    song.duration = Long.parseLong(duration);
                } catch (NumberFormatException ignore) {
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "MMR parse failed for " + file.getAbsolutePath() + ": " + t.getMessage());
        } finally {
            if (mmr != null) {
                try { mmr.release(); } catch (Throwable ignore) {
                }
            }
        }

        if (song.title == null || song.title.isEmpty()) {
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            String nameWithoutExt = dot > 0 ? fileName.substring(0, dot) : fileName;

            Matcher m1 = SONG_PATTERN1.matcher(nameWithoutExt);
            if (m1.matches()) {
                if (song.artist == null) song.artist = m1.group(1).trim();
                song.title = m1.group(2).trim();
            } else {
                Matcher m2 = SONG_PATTERN2.matcher(nameWithoutExt);
                if (m2.matches()) {
                    if (song.artist == null) song.artist = m2.group(1).trim();
                    song.title = m2.group(2).trim();
                } else {
                    song.title = nameWithoutExt.trim();
                }
            }
        }

        song.displayName = song.artist != null && !song.artist.isEmpty()
                ? song.artist + " - " + song.title
                : song.title;
        song.sourceType = "local";
        song.identityKey = "local:" + song.filePath;
        return song;
    }
}

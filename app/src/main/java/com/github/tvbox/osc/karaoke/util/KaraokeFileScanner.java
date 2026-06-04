package com.github.tvbox.osc.karaoke.util;

import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.util.StorageDriveType;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KaraokeFileScanner {

    private static final Pattern SONG_PATTERN1 = Pattern.compile("^(.+?)\\s*[-\\u2014\\u2013]\\s*(.+)$");
    private static final Pattern SONG_PATTERN2 = Pattern.compile("^\\[(.+?)\\]\\s*(.+)$");

    public static List<KaraokeSong> scanFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return new ArrayList<>();
        }
        File[] files = folder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                int dot = name.lastIndexOf('.');
                if (dot < 0) return false;
                String ext = name.substring(dot + 1);
                return StorageDriveType.isVideoType(ext);
            }
        });
        if (files == null) return new ArrayList<>();

        List<KaraokeSong> songs = new ArrayList<>();
        for (File file : files) {
            if (file.isFile()) {
                songs.add(parseSong(file));
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

    private static KaraokeSong parseSong(File file) {
        KaraokeSong song = new KaraokeSong();
        song.filePath = file.getAbsolutePath();
        song.fileSize = file.length();
        song.lastModified = file.lastModified();

        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        String nameWithoutExt = dot > 0 ? fileName.substring(0, dot) : fileName;

        Matcher m1 = SONG_PATTERN1.matcher(nameWithoutExt);
        if (m1.matches()) {
            song.artist = m1.group(1).trim();
            song.title = m1.group(2).trim();
        } else {
            Matcher m2 = SONG_PATTERN2.matcher(nameWithoutExt);
            if (m2.matches()) {
                song.artist = m2.group(1).trim();
                song.title = m2.group(2).trim();
            } else {
                song.title = nameWithoutExt.trim();
            }
        }
        song.displayName = song.artist != null && !song.artist.isEmpty()
                ? song.artist + " - " + song.title
                : song.title;
        return song;
    }
}

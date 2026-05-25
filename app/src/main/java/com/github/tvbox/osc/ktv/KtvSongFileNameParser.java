package com.github.tvbox.osc.ktv;

import android.text.TextUtils;

import java.util.Locale;

public final class KtvSongFileNameParser {
    private KtvSongFileNameParser() {
    }

    public static ParsedSong parse(String fileName) {
        String baseName = removeExtension(fileName);
        String artist = "";
        String title = baseName;

        String[] patterns = new String[]{" - ", "-", "_"};
        for (String pattern : patterns) {
            String[] first = splitOnce(baseName, pattern);
            if (first != null) {
                artist = first[0];
                title = first[1];
                break;
            }
            String[] second = splitLast(baseName, pattern);
            if (second != null) {
                title = second[0];
                artist = second[1];
                break;
            }
        }

        artist = safeTrim(artist);
        title = safeTrim(title);
        if (TextUtils.isEmpty(title)) {
            title = baseName;
        }
        return new ParsedSong(title, artist, buildInitials(title, artist, fileName));
    }

    private static String[] splitOnce(String value, String delimiter) {
        int index = value.indexOf(delimiter);
        if (index <= 0 || index >= value.length() - delimiter.length()) {
            return null;
        }
        String left = safeTrim(value.substring(0, index));
        String right = safeTrim(value.substring(index + delimiter.length()));
        if (TextUtils.isEmpty(left) || TextUtils.isEmpty(right)) {
            return null;
        }
        return new String[]{left, right};
    }

    private static String[] splitLast(String value, String delimiter) {
        int index = value.lastIndexOf(delimiter);
        if (index <= 0 || index >= value.length() - delimiter.length()) {
            return null;
        }
        String left = safeTrim(value.substring(0, index));
        String right = safeTrim(value.substring(index + delimiter.length()));
        if (TextUtils.isEmpty(left) || TextUtils.isEmpty(right)) {
            return null;
        }
        return new String[]{left, right};
    }

    private static String removeExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String buildInitials(String title, String artist, String fileName) {
        String text = (title + " " + artist).trim();
        StringBuilder builder = new StringBuilder();
        for (String token : text.split("[\\s_\\-]+")) {
            if (token.isEmpty()) {
                continue;
            }
            char first = token.charAt(0);
            if (Character.isLetterOrDigit(first)) {
                builder.append(Character.toLowerCase(first));
            }
        }
        if (builder.length() == 0) {
            return removeExtension(fileName).toLowerCase(Locale.ROOT);
        }
        return builder.toString();
    }

    public static final class ParsedSong {
        public final String title;
        public final String artist;
        public final String initials;

        public ParsedSong(String title, String artist, String initials) {
            this.title = title;
            this.artist = artist;
            this.initials = initials;
        }
    }
}

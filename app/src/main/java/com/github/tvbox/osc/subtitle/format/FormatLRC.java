package com.github.tvbox.osc.subtitle.format;

import com.github.tvbox.osc.subtitle.exception.FatalParsingException;
import com.github.tvbox.osc.subtitle.model.Subtitle;
import com.github.tvbox.osc.subtitle.model.TimedTextObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an external .lrc karaoke lyric file.
 *
 * Supports:
 *  - Multiple timestamps per line: [01:30.00][02:15.00]lyrics
 *  - Word-level {@code <mm:ss.xx>} tags (degraded to whole-line highlight)
 *  - ID tags {@code [ti:...]}, {@code [ar:...]}, {@code [al:...]} (ignored)
 *  - CRLF / LF line terminators
 */
public class FormatLRC implements TimedTextFileFormat {

    private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern ID_TAG = Pattern.compile("^\\[(ti|ar|al|by|offset|length|re|ve):(.*)\\]$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_TAG = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");

    @Override
    public TimedTextObject parseFile(String fileName, InputStream is) throws IOException, FatalParsingException {
        TimedTextObject tto = new TimedTextObject();
        tto.fileName = fileName;

        // accumulator: timestamp -> concatenated lyric fragments in source order
        TreeMap<Integer, StringBuilder> buffer = new TreeMap<>();
        TreeMap<Integer, Subtitle> captions = new TreeMap<>();

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // skip ID tag lines (they sometimes look like time tags but their field name isn't numeric)
                Matcher idMatcher = ID_TAG.matcher(trimmed);
                if (idMatcher.matches()) {
                    String key = idMatcher.group(1).toLowerCase();
                    String value = idMatcher.group(2).trim();
                    if ("ti".equals(key)) tto.title = value;
                    else if ("ar".equals(key)) tto.author = value;
                    continue;
                }

                // collect all [mm:ss.xx] timestamps at the start of the line
                List<Integer> offsets = new ArrayList<>();
                String remainder = trimmed;
                Matcher m = TIME_TAG.matcher(remainder);
                // Only consume leading bracketed timestamps (the LRC convention).
                while (true) {
                    if (!m.find()) break;
                    if (m.start() != 0) break;
                    int mm = Integer.parseInt(m.group(1));
                    int ss = Integer.parseInt(m.group(2));
                    String frac = m.group(3);
                    int fracValue = 0;
                    if (frac != null && !frac.isEmpty()) {
                        // pad/truncate to 3 digits so .5 → 500ms, .50 → 500ms, .500 → 500ms
                        String padded = (frac + "000").substring(0, 3);
                        fracValue = Integer.parseInt(padded);
                    }
                    int ms = fracValue + ss * 1000 + mm * 60000;
                    offsets.add(ms);
                    remainder = remainder.substring(m.end());
                    m = TIME_TAG.matcher(remainder);
                }
                if (offsets.isEmpty()) {
                    // No timestamp on this line: skip silently (could be metadata that
                    // didn't match the strict ID_TAG regex above).
                    continue;
                }

                // strip per-word <mm:ss.xx> tags — degrade to whole-line highlight
                String lyric = WORD_TAG.matcher(remainder).replaceAll("").trim();
                if (lyric.isEmpty()) lyric = "♪";

                for (int startMs : offsets) {
                    StringBuilder sb = buffer.get(startMs);
                    if (sb == null) {
                        sb = new StringBuilder();
                        buffer.put(startMs, sb);
                    }
                    if (sb.length() > 0) sb.append("<br />");
                    sb.append(lyric);
                }
            }
        } finally {
            try { is.close(); } catch (IOException ignore) {
            }
        }

        // Build captions with derived end times (next line's start).
        List<Integer> starts = new ArrayList<>(buffer.keySet());
        Collections.sort(starts);
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : start + 5000;

            Subtitle caption = new Subtitle();
            caption.start = new com.github.tvbox.osc.subtitle.model.Time("hh:mm:ss,ms", formatHms(start));
            caption.end = new com.github.tvbox.osc.subtitle.model.Time("hh:mm:ss,ms", formatHms(end));
            caption.content = buffer.get(start).toString();

            int key = start;
            while (captions.containsKey(key)) key++;
            captions.put(key, caption);
        }

        tto.captions = new TreeMap<>(captions);
        tto.built = true;
        return tto;
    }

    private static String formatHms(int ms) {
        if (ms < 0) ms = 0;
        int totalSeconds = ms / 1000;
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        int millis = ms % 1000;
        return String.format("%02d:%02d:%02d,%03d", h, m, s, millis);
    }

    @Override
    public String[] toFile(TimedTextObject tto) {
        if (tto == null || tto.captions == null) return new String[0];
        List<String> out = new ArrayList<>();
        for (Subtitle caption : tto.captions.values()) {
            int ms = caption.start.mseconds;
            int m = ms / 60000;
            int s = (ms % 60000) / 1000;
            int frac = (ms % 1000) / 10;
            String stamp = String.format("[%02d:%02d.%02d]", m, s, frac);
            String text = caption.content == null ? "" : caption.content.replaceAll("<br />", " ");
            out.add(stamp + text);
        }
        return out.toArray(new String[0]);
    }
}

package dev.jax.skylyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the user's own lyric files. Two formats:
 * synced LRC ("[mm:ss.xx] text", optional [ti:] / [ar:] tags) or plain text (one line per cue).
 */
final class LyricsParser {
    private LyricsParser() {}

    static final class Cue {
        final double time;
        final String text;

        Cue(double time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    static final class Song {
        final String file;
        final String title;
        final String artist;
        final boolean timed;
        final List<Cue> cues;

        Song(String file, String title, String artist, boolean timed, List<Cue> cues) {
            this.file = file;
            this.title = title;
            this.artist = artist;
            this.timed = timed;
            this.cues = cues;
        }
    }

    private static final Pattern TIME = Pattern.compile("^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern META = Pattern.compile("^\\[([A-Za-z]+):(.*)\\]$");
    private static final Pattern SECTION = Pattern.compile("^\\[[^\\]]*\\]$");

    private static double toSeconds(String min, String sec, String frac) {
        double t = Integer.parseInt(min) * 60.0 + Integer.parseInt(sec);
        if (frac != null && !frac.isEmpty()) {
            t += Double.parseDouble("0." + frac);
        }
        return t;
    }

    static Song parse(String file, String fallbackTitle, List<String> raw, double interval) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String s = raw.get(i);
            if (i == 0 && s.startsWith("\uFEFF")) {
                s = s.substring(1);
            }
            lines.add(s.strip());
        }

        boolean timed = false;
        for (String s : lines) {
            if (TIME.matcher(s).find()) {
                timed = true;
                break;
            }
        }

        String title = fallbackTitle;
        String artist = "";
        List<Cue> cues = new ArrayList<>();

        if (timed) {
            for (String s : lines) {
                Matcher meta = META.matcher(s);
                if (meta.matches()) {
                    String key = meta.group(1).toLowerCase(Locale.ROOT);
                    String val = meta.group(2).strip();
                    if (key.equals("ti") && !val.isEmpty()) {
                        title = val;
                    } else if (key.equals("ar") && !val.isEmpty()) {
                        artist = val;
                    }
                    continue;
                }
                List<Double> times = new ArrayList<>();
                String rest = s;
                while (true) {
                    Matcher tag = TIME.matcher(rest);
                    if (!tag.find()) {
                        break;
                    }
                    times.add(toSeconds(tag.group(1), tag.group(2), tag.group(3)));
                    rest = rest.substring(tag.end()).stripLeading();
                }
                if (times.isEmpty() || rest.isEmpty()) {
                    continue;
                }
                for (double when : times) {
                    cues.add(new Cue(when, rest));
                }
            }
            cues.sort(Comparator.comparingDouble(c -> c.time));
        } else {
            int n = 0;
            for (String s : lines) {
                if (s.isEmpty() || s.startsWith("#") || SECTION.matcher(s).matches()) {
                    continue;
                }
                cues.add(new Cue(n * interval, s));
                n++;
            }
        }
        return new Song(file, title, artist, timed, cues);
    }
}

package dev.jax.skylyrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;

/**
 * Watches the JSON file written by smtc_helper.py.
 * When the track changes, it picks the best-matching lyric file and queues a play request.
 */
final class NowPlayingWatcher {
    private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics/NowPlaying");
    private static final double POLL_SECONDS = 2.0;

    private final Path watchFile;
    private final Path lyricsDir;

    private FileTime lastModified;
    private String lastTitle = "";
    private double timer;
    private NowPlaying queued;

    NowPlayingWatcher(Path watchFile, Path lyricsDir) {
        this.watchFile = watchFile;
        this.lyricsDir = lyricsDir;
    }

    record NowPlaying(String title, String artist) {}

    /**
     * Call every frame with elapsed seconds.
     * Returns a NowPlaying when the track has changed, null otherwise.
     */
    NowPlaying tick(double dt) {
        timer -= dt;
        if (timer > 0.0) {
            return null;
        }
        timer = POLL_SECONDS;

        if (watchFile == null || !Files.exists(watchFile)) {
            return null;
        }

        try {
            FileTime mt = Files.getLastModifiedTime(watchFile);
            if (mt.equals(lastModified)) {
                return null;
            }
            lastModified = mt;

            String json = Files.readString(watchFile, StandardCharsets.UTF_8);
            String title = field(json, "title");
            String normTitle = field(json, "normalized_title");
            String artist = field(json, "artist");

            if (normTitle.equals(lastTitle)) {
                return null;
            }
            lastTitle = normTitle;
            if (title.isEmpty()) {
                return null;
            }
            return new NowPlaying(title, artist);
        } catch (IOException | RuntimeException e) {
            LOG.error("NowPlaying read error: {}", e.toString());
            return null;
        }
    }

    /** Fuzzy-match a NowPlaying against all available song files. Returns best match or null. */
    LyricsParser.Song findMatch(List<LyricsParser.Song> songs, NowPlaying np) {
        String normTrack = normalize(np.title() + " " + np.artist());
        String normTitle = normalize(np.title());

        LyricsParser.Song best = null;
        int bestScore = 0;

        if (normalize(np.title()).isEmpty()) {
            return null;
        }
        for (LyricsParser.Song s : songs) {
            String st = normalize(s.title);
            String sf = normalize(s.file.replaceAll("\\.[^.]+$", ""));
            String sa = normalize(s.artist);

            int score = 0;
            if (st.equals(normTitle) || sf.equals(normTitle)) {
                score += 100;
            } else if (normTrack.contains(st) || st.contains(normTitle)) {
                score += 60;
            } else if (sf.contains(normTitle) || normTitle.contains(sf)) {
                score += 50;
            }
            if (!sa.isEmpty() && !normalize(np.artist()).isEmpty() && sa.contains(normalize(np.artist()))) {
                score += 20;
            }
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return bestScore >= 50 ? best : null;
    }

    private static String normalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    /** Minimal JSON field reader — avoids pulling in Gson here. */
    private static String field(String json, String key) {
        String needle = "\"" + key + "\"";
        int ki = json.indexOf(needle);
        if (ki < 0) {
            return "";
        }
        int colon = json.indexOf(':', ki + needle.length());
        if (colon < 0) {
            return "";
        }
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) {
            return "";
        }
        int q2 = q1 + 1;
        while (q2 < json.length()) {
            if (json.charAt(q2) == '\\') {
                q2 += 2;
            } else if (json.charAt(q2) == '"') {
                break;
            } else {
                q2++;
            }
        }
        return json.substring(q1 + 1, q2);
    }
}

package dev.jax.skylyrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Fetches synced lyrics from lrclib.net (free, no key required).
 * Results are cached on disk so the same song never downloads twice.
 */
final class LrcLib {
    private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics/LrcLib");
    private static final String API = "https://lrclib.net/api/get?";
    private static final int TIMEOUT_MS = 5000;

    private LrcLib() {}

    /**
     * Returns a Path to a cached .lrc file for this track, downloading it if needed.
     * Returns null when lyrics are not available.
     * This runs on a background thread — never call from the render thread directly.
     */
    static Path fetch(Path cacheDir, String title, String artist) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String safeName = safe(artist + " - " + title) + ".lrc";
        Path cached = cacheDir.resolve(safeName);

        // already on disk
        if (Files.exists(cached)) {
            LOG.info("Cache hit: {}", safeName);
            return cached;
        }

        // download
        try {
            String url = API
                    + "track_name=" + enc(title)
                    + "&artist_name=" + enc(artist);
            String body = get(url);
            if (body == null) {
                return null;
            }
            // prefer syncedLyrics, fall back to plainLyrics
            String lyrics = jsonStr(body, "syncedLyrics");
            boolean synced = true;
            if (lyrics.isEmpty()) {
                lyrics = jsonStr(body, "plainLyrics");
                synced = false;
            }
            if (lyrics.isEmpty() || lyrics.equals("null")) {
                LOG.info("No lyrics on lrclib for: {} - {}", artist, title);
                return null;
            }
            // write header + lyrics
            String lrc = "[ti:" + title + "]\n"
                    + "[ar:" + artist + "]\n"
                    + (synced ? "" : "")
                    + lyrics + "\n";
            Files.writeString(cached, lrc, StandardCharsets.UTF_8);
            LOG.info("Downloaded {}: {} ({})", synced ? "synced" : "plain", safeName, body.length() + "B");
            return cached;
        } catch (Exception e) {
            LOG.error("lrclib fetch failed for {} - {}: {}", artist, title, e.toString());
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String safe(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "SkyLyrics/1.0 (Minecraft mod)");
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        if (code == 404) {
            return null;
        }
        if (code != 200) {
            LOG.info("lrclib HTTP {}", code);
            return null;
        }
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String jsonStr(String json, String key) {
        String needle = "\"" + key + "\"";
        int ki = json.indexOf(needle);
        if (ki < 0) return "";
        int colon = json.indexOf(':', ki + needle.length());
        if (colon < 0) return "";
        int rest = colon + 1;
        while (rest < json.length() && json.charAt(rest) == ' ') rest++;
        if (rest >= json.length()) return "";
        char first = json.charAt(rest);
        if (first == 'n') return "";   // null
        if (first == '"') {
            StringBuilder sb = new StringBuilder();
            int i = rest + 1;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char n = json.charAt(i + 1);
                    switch (n) {
                        case 'n' -> { sb.append('\n'); i += 2; }
                        case 'r' -> { sb.append('\r'); i += 2; }
                        case 't' -> { sb.append('\t'); i += 2; }
                        default  -> { sb.append(n);    i += 2; }
                    }
                    continue;
                }
                if (c == '"') break;
                sb.append(c);
                i++;
            }
            return sb.toString();
        }
        int end = rest;
        while (end < json.length() && ",}]\n".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(rest, end).strip();
    }
}

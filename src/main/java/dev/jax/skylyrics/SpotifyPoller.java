package dev.jax.skylyrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Polls Spotify's built-in local web helper on localhost:4381.
 * No installs, no tokens. Works as long as Spotify is open.
 *
 * Spotify enables the local server when "Allow Spotify to be opened from the web" is on
 * (Settings > Advanced). It is on by default for most users.
 */
final class SpotifyPoller {
    private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics/Spotify");

    private static final String[] PORTS = {"4381", "4371", "4372", "4383"};
    private static final int TIMEOUT_MS = 1200;
    private static final double POLL_SECONDS = 2.5;
    private static final String CSRF_PATH = "/simplecsrf/token.json";
    private static final String STATUS_PATH = "/remote/status.json?oauth=&csrf=";
    private static final String ORIGIN = "https://open.spotify.com";

    private String port;
    private String csrf;
    private double timer;
    private String lastNorm = "";

    record Track(String title, String artist) {}

    /** Call once per frame. Returns a Track when the song changes, null otherwise. */
    Track tick(double dt) {
        timer -= dt;
        if (timer > 0.0) {
            return null;
        }
        timer = POLL_SECONDS;

        if (port == null) {
            discover();
            if (port == null) {
                return null;
            }
        }

        try {
            Track t = fetchStatus();
            if (t == null) {
                return null;
            }
            String norm = normalize(t.title() + " " + t.artist());
            if (norm.equals(lastNorm)) {
                return null;
            }
            lastNorm = norm;
            return t;
        } catch (Exception e) {
            LOG.info("Spotify poll error: {}", e.toString());
            port = null;
            csrf = null;
            return null;
        }
    }

    private void discover() {
        for (String p : PORTS) {
            try {
                String body = get("http://localhost:" + p + CSRF_PATH, false);
                if (body != null && body.contains("token")) {
                    port = p;
                    csrf = jsonStr(body, "token");
                    LOG.info("Spotify local API found on port {}", port);
                    return;
                }
            } catch (Exception ignored) { }
        }
    }

    private Track fetchStatus() throws Exception {
        if (csrf == null || csrf.isEmpty()) {
            discover();
            if (port == null) return null;
        }
        String url = "http://localhost:" + port + STATUS_PATH + csrf;
        String body = get(url, true);
        if (body == null || body.contains("\"error\"")) {
            csrf = null;
            return null;
        }
        // playing?
        String playing = jsonStr(body, "playing");
        if (!"true".equals(playing)) {
            return null;
        }
        String title = jsonStr(body, "name");
        String artist = artistFromJson(body);
        if (title.isEmpty()) {
            return null;
        }
        return new Track(title, artist);
    }

    private static String get(String url, boolean origin) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        if (origin) {
            conn.setRequestProperty("Origin", ORIGIN);
        }
        conn.setRequestProperty("User-Agent", "SkyLyrics/1.0");
        int code = conn.getResponseCode();
        if (code != 200) {
            return null;
        }
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Pulls the first string value for a given key out of flat JSON without a library. */
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
        if (first == '"') {
            int end = rest + 1;
            while (end < json.length()) {
                if (json.charAt(end) == '\\') { end += 2; continue; }
                if (json.charAt(end) == '"') break;
                end++;
            }
            return json.substring(rest + 1, end);
        }
        // bool / number
        int end = rest;
        while (end < json.length() && ",}]\n".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(rest, end).strip();
    }

    /** Finds the first artist name inside the "artists" array. */
    static String artistFromJson(String json) {
        int arr = json.indexOf("\"artists\"");
        if (arr < 0) return "";
        int nameAfter = json.indexOf("\"name\"", arr);
        if (nameAfter < 0) return "";
        return jsonStr(json.substring(nameAfter), "name");
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .strip();
    }
}

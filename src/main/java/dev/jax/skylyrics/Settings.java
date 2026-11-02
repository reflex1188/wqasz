package dev.jax.skylyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

final class Settings {
    private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics/Settings");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Seconds between lines for plain-text lyric files. */
    double interval = 3.5;
    /** How long each line stays up. */
    double lifetime = 5.5;
    /** Text size multiplier. */
    double textScale = 3.0;
    /** Automatically detect the current Spotify track and match lyrics. */
    boolean spotifyEnabled = true;
    String spotifyClientId = "";

    /** true = lines float at fixed spots in the sky, false = fixed on screen. */
    boolean skyMode = true;
    /** Show the song title banner when playback starts. */
    boolean showTitle = true;

    void sanitize() {
        interval = clamp(interval, 0.5, 30.0, 3.5);
        lifetime = clamp(lifetime, 2.0, 20.0, 5.5);
        textScale = clamp(textScale, 1.0, 6.0, 3.0);
    }

    private static double clamp(double v, double lo, double hi, double fallback) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return fallback;
        }
        return Math.max(lo, Math.min(hi, v));
    }

    static Settings load(Path file) {
        if (Files.notExists(file)) {
            Settings fresh = new Settings();
            fresh.save(file);
            return fresh;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Settings loaded = GSON.fromJson(reader, Settings.class);
            if (loaded == null) {
                throw new IOException("config file is empty");
            }
            loaded.sanitize();
            return loaded;
        } catch (Exception e) {
            LOG.error("Settings unreadable, using defaults: {}", e.toString());
            return new Settings();
        }
    }

    void save(Path file) {
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            LOG.error("Settings write failed: {}", e.toString());
        }
    }
}

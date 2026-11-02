package dev.jax.skylyrics;

import java.util.ArrayList;
import java.util.List;

/** Timeline: feed it frame time, it returns the cues that just became due. */
final class LyricsPlayer {
    private static final double TAIL_SECONDS = 2.0;

    private LyricsParser.Song song;
    private boolean playing;
    private double time;
    private int next;

    void start(LyricsParser.Song s) {
        song = s;
        time = 0.0;
        next = 0;
        playing = s != null && !s.cues.isEmpty();
    }

    void stop() {
        playing = false;
    }

    boolean isPlaying() {
        return playing;
    }

    double seconds() {
        return time;
    }

    /** Jumps the clock to the next cue so the following advance() emits it. */
    boolean skip() {
        if (!playing || song == null || next >= song.cues.size()) {
            return false;
        }
        time = Math.max(time, song.cues.get(next).time);
        return true;
    }

    List<LyricsParser.Cue> advance(double dt) {
        List<LyricsParser.Cue> due = new ArrayList<>();
        if (!playing || song == null) {
            return due;
        }
        time += Math.max(0.0, dt);
        List<LyricsParser.Cue> cues = song.cues;
        while (next < cues.size() && cues.get(next).time <= time) {
            due.add(cues.get(next));
            next++;
        }
        if (next >= cues.size() && time > cues.get(cues.size() - 1).time + TAIL_SECONDS) {
            playing = false;
        }
        return due;
    }
}

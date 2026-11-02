package dev.jax.skylyrics;

import java.util.List;

/** One lyric line currently floating in the sky. */
final class Pop {
    final List<String> lines;
    final int rgb;
    final double life;
    final int slot;
    final double yaw;
    final double pitch;
    double age;

    Pop(List<String> lines, int rgb, double life, int slot, double yaw, double pitch) {
        this.lines = lines;
        this.rgb = rgb;
        this.life = life;
        this.slot = slot;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}

package dev.jax.skylyrics;

import java.util.Random;

/** Hands out spread-out spots in the sky so simultaneous lines don't overlap. */
final class SkySlots {
    private static final double[] YAW = {-38.0, 0.0, 38.0, -19.0, 19.0};
    private static final double[] PITCH = {-14.0, -8.0, -17.0, -21.0, -11.0};

    private final Random rng;
    private int index;

    SkySlots(Random rng) {
        this.rng = rng;
    }

    int nextIndex() {
        return index++ % YAW.length;
    }

    /** Returns {yaw, pitch} of the spot, relative to where the player is looking now. */
    double[] spot(int slot, double camYaw, double camPitch) {
        double yaw = camYaw + YAW[slot] + (rng.nextDouble() - 0.5) * 10.0;
        double pitch = camPitch + PITCH[slot] + (rng.nextDouble() - 0.5) * 6.0;
        pitch = Math.max(-70.0, Math.min(-10.0, pitch));
        return new double[] {yaw, pitch};
    }
}

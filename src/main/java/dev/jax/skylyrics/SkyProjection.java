package dev.jax.skylyrics;

/**
 * Projects a world direction (yaw/pitch, Minecraft convention) into scaled-GUI screen
 * coordinates for a camera looking along (camYaw, camPitch). Returns null when behind the camera.
 */
final class SkyProjection {
    private SkyProjection() {}

    private static double[] dir(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        return new double[] {
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)
        };
    }

    static double[] project(double camYaw, double camPitch, double tgtYaw, double tgtPitch,
                            double vfovDeg, int width, int height) {
        double[] f = dir(camYaw, camPitch);
        double[] d = dir(tgtYaw, tgtPitch);

        double yaw = Math.toRadians(camYaw);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double ux = -rz * f[1];
        double uy = rz * f[0] - rx * f[2];
        double uz = rx * f[1];

        double zc = d[0] * f[0] + d[1] * f[1] + d[2] * f[2];
        if (zc < 0.05) {
            return null;
        }
        double xc = d[0] * rx + d[2] * rz;
        double yc = d[0] * ux + d[1] * uy + d[2] * uz;

        double focal = (height / 2.0) / Math.tan(Math.toRadians(vfovDeg) / 2.0);
        return new double[] {
                width / 2.0 + xc / zc * focal,
                height / 2.0 - yc / zc * focal
        };
    }
}

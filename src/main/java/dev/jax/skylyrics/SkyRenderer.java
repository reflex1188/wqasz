package dev.jax.skylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;

final class SkyRenderer {
    private SkyRenderer() {}

    static final double POP_IN = 0.45;
    static final double FADE_OUT = 0.9;
    private static final double FADE_IN = 0.12;
    private static final double RISE_DEG_PER_SEC = 1.5;

    static double easeOutBack(double t) {
        double x = Math.max(0.0, Math.min(1.0, t));
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0);
    }

    private static int withAlpha(int rgb, double a) {
        int alpha = (int) Math.round(Math.max(0.0, Math.min(1.0, a)) * 255.0);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    static void render(DrawContext ctx, MinecraftClient mc, List<Pop> pops, Settings st) {
        if (mc.player == null || pops.isEmpty()) {
            return;
        }
        TextRenderer tr = mc.textRenderer;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        double camYaw = mc.player.getYaw();
        double camPitch = mc.player.getPitch();
        double vfov = mc.options.getFov().getValue();

        for (Pop p : pops) {
            double scale = st.textScale * easeOutBack(p.age / POP_IN);
            double fade = Math.min(1.0, p.age / FADE_IN) * Math.min(1.0, (p.life - p.age) / FADE_OUT);
            if (scale < 0.05 || fade < 0.04) {
                continue;
            }

            double sx;
            double sy;
            if (st.skyMode) {
                double[] pt = SkyProjection.project(camYaw, camPitch, p.yaw,
                        p.pitch - p.age * RISE_DEG_PER_SEC, vfov, w, h);
                if (pt == null) {
                    continue;
                }
                sx = pt[0];
                sy = pt[1];
            } else {
                sx = w / 2.0;
                sy = h * 0.2 + (p.slot % 3) * 36.0;
            }
            if (sx < -w || sx > 2.0 * w || sy < -h || sy > 2.0 * h) {
                continue;
            }

            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate((float) sx, (float) sy, 0.0f);
            m.scale((float) scale, (float) scale, 1.0f);

            int n = p.lines.size();
            for (int i = 0; i < n; i++) {
                String line = p.lines.get(i);
                int x = -tr.getWidth(line) / 2;
                int y = (int) Math.round((i - (n - 1) / 2.0) * 10.0) - 4;
                int outline = withAlpha(0x0B0B16, 0.75 * fade);
                ctx.drawText(tr, line, x + 1, y, outline, false);
                ctx.drawText(tr, line, x - 1, y, outline, false);
                ctx.drawText(tr, line, x, y + 1, outline, false);
                ctx.drawText(tr, line, x, y - 1, outline, false);
                ctx.drawText(tr, line, x, y, withAlpha(p.rgb, fade), false);
            }
            m.pop();
        }
    }

    /** Small title banner, top-centre. */
    static void banner(DrawContext ctx, MinecraftClient mc, String text, double age) {
        double a = Math.min(1.0, age / 0.2) * Math.min(1.0, (3.0 - age) / 0.6);
        if (text.isEmpty() || a < 0.04) {
            return;
        }
        TextRenderer tr = mc.textRenderer;
        int w = mc.getWindow().getScaledWidth();
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(w / 2.0f, 14.0f, 0.0f);
        m.scale(1.4f, 1.4f, 1.0f);
        ctx.drawText(tr, text, -tr.getWidth(text) / 2 + 1, 1, withAlpha(0x000000, 0.6 * a), false);
        ctx.drawText(tr, text, -tr.getWidth(text) / 2, 0, withAlpha(0xFFFFFF, a), false);
        m.pop();
    }
}

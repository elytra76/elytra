package com.elytra.client.gui.render;

import com.elytra.client.gui.ElytraTheme;
import com.elytra.client.util.ColorUtils;
import imgui.ImDrawList;

import java.util.Random;

/**
 * Ambient particles behind the GUI. One fixed pool is shared by all three
 * effects; switching mode re-seeds the pool so each effect gets its own speed,
 * size and sway. No allocations happen after the pool is built.
 */
public final class EffectRenderer {

    public static final int NONE = 0;
    public static final int RAIN = 1;
    public static final int SNOW = 2;
    public static final int LEAVES = 3;
    public static final String[] NAMES = {"None", "Rain", "Snow", "Leaves"};

    private static final int COUNT = 140;
    private static final int[] LEAF_COLORS = {
            ColorUtils.hex("#7FB069"),
            ColorUtils.hex("#A8C66C"),
            ColorUtils.hex("#D8A657")
    };

    private final Particle[] particles = new Particle[COUNT];
    private final Random random = new Random(0xE11A7A1DL);
    private int lastMode = -1;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int active = COUNT;
    private boolean ready;

    /** Fraction of the pool to draw, 0-1. */
    public void setDensity(float density) {
        active = Math.max(1, Math.min(COUNT,
                (int) Math.round(COUNT * Math.max(0.05f, Math.min(1f, density)))));
    }

    public void render(ImDrawList dl, int mode, float deltaSeconds, float alpha,
                       int width, int height, ElytraTheme theme) {
        if (dl == null || width <= 0 || height <= 0 || alpha <= 0.01f || mode == NONE) {
            return;
        }
        int safeMode = Math.max(RAIN, Math.min(LEAVES, mode));
        boolean resized = width != lastWidth || height != lastHeight;
        if (!ready || safeMode != lastMode || resized) {
            for (int i = 0; i < COUNT; i++) {
                particles[i] = new Particle();
                seed(particles[i], safeMode, width, height, false);
            }
            ready = true;
            lastMode = safeMode;
            lastWidth = width;
            lastHeight = height;
        }

        float dt = Math.min(deltaSeconds, 0.05f);
        int color = effectColor(safeMode, theme);

        for (int i = 0; i < active; i++) {
            Particle p = particles[i];
            p.y += p.speed * dt;
            p.x += p.drift * dt;
            p.phase += p.spin * dt;

            if (p.y - p.size > height || p.x < -p.size * 2f || p.x > width + p.size * 2f) {
                seed(p, safeMode, width, height, true);
            }

            float x = p.x + (float) Math.sin(p.phase) * p.sway;
            float y = p.y;
            int base = safeMode == LEAVES ? LEAF_COLORS[p.tint] : color;
            int alphaColor = ColorUtils.fade(base, alpha * p.alpha);

            if (safeMode == RAIN) {
                dl.addLine(x, y, x + p.size * 0.18f, y + p.size * 3.2f,
                        alphaColor, p.thickness);
            } else if (safeMode == SNOW) {
                dl.addCircleFilled(x, y, p.size * 0.5f, alphaColor, 6);
            } else {
                float r = p.size;
                float c = (float) Math.cos(p.phase) * r;
                float s = (float) Math.sin(p.phase) * r * 0.55f;
                dl.addQuad(x - c, y - s, x + s, y - c,
                        x + c, y + s, x - s, y + c, alphaColor);
            }
        }
    }

    private static int effectColor(int mode, ElytraTheme theme) {
        if (mode == SNOW) {
            return ColorUtils.lerp(ColorUtils.hex("#E8EEFF"), theme.secondaryLavender, 0.25f);
        }
        return ColorUtils.lerp(ColorUtils.hex("#C8D4FF"), theme.secondaryLavender, 0.35f);
    }

    private void seed(Particle p, int mode, int width, int height, boolean fromTop) {
        if (mode == RAIN) {
            p.speed = lerp(420f, 980f, random.nextFloat());
            p.size = lerp(10f, 28f, random.nextFloat());
            p.thickness = lerp(0.8f, 1.6f, random.nextFloat());
            p.alpha = lerp(0.10f, 0.28f, random.nextFloat());
            p.drift = p.speed * 0.18f * lerp(0.7f, 1.3f, random.nextFloat());
            p.sway = 0f;
            p.spin = 0f;
        } else if (mode == SNOW) {
            p.speed = lerp(45f, 115f, random.nextFloat());
            p.size = lerp(2.2f, 5.2f, random.nextFloat());
            p.thickness = 1f;
            p.alpha = lerp(0.28f, 0.72f, random.nextFloat());
            p.drift = lerp(6f, 24f, random.nextFloat());
            p.sway = lerp(6f, 22f, random.nextFloat());
            p.spin = lerp(0.5f, 1.5f, random.nextFloat());
            p.phase = random.nextFloat() * 6.28f;
        } else {
            p.speed = lerp(85f, 205f, random.nextFloat());
            p.size = lerp(2.6f, 5.4f, random.nextFloat());
            p.thickness = 1f;
            p.alpha = lerp(0.28f, 0.62f, random.nextFloat());
            p.drift = lerp(10f, 40f, random.nextFloat());
            p.sway = lerp(4f, 16f, random.nextFloat());
            p.spin = lerp(0.8f, 2.6f, random.nextFloat()) * (random.nextBoolean() ? 1f : -1f);
            p.phase = random.nextFloat() * 6.28f;
            p.tint = random.nextInt(LEAF_COLORS.length);
        }
        p.x = random.nextFloat() * (width + height * 0.2f) - height * 0.1f;
        p.y = fromTop ? -p.size * 4f - random.nextFloat() * height * 0.2f
                : random.nextFloat() * height;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static final class Particle {
        float x;
        float y;
        float speed;
        float size;
        float thickness;
        float alpha;
        float drift;
        float sway;
        float spin;
        float phase;
        int tint;
    }
}

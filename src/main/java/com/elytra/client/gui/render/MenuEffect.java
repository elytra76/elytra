package com.elytra.client.gui.render;

import com.elytra.client.gui.ElytraScreenTheme;
import com.elytra.client.gui.ElytraTheme;
import com.elytra.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/**
 * Ambient effect drawn through Minecraft's DrawContext so it lands behind the
 * vanilla menu widgets. Mirrors the modes of {@link EffectRenderer}.
 */
public final class MenuEffect {

    private static final int COUNT = 130;
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

    public void setDensity(float density) {
        active = Math.max(1, Math.min(COUNT,
                (int) Math.round(COUNT * Math.max(0.05f, Math.min(1f, density)))));
    }

    public void draw(DrawContext context, int mode, int width, int height,
                     ElytraTheme theme, float delta) {
        if (width <= 0 || height <= 0 || mode == EffectRenderer.NONE) {
            return;
        }
        int safeMode = Math.max(EffectRenderer.RAIN, Math.min(EffectRenderer.LEAVES, mode));
        if (!ready || safeMode != lastMode || width != lastWidth || height != lastHeight) {
            for (int i = 0; i < COUNT; i++) {
                particles[i] = new Particle();
                seed(particles[i], safeMode, width, height, false);
            }
            ready = true;
            lastMode = safeMode;
            lastWidth = width;
            lastHeight = height;
        }

        float dt = Math.min(0.05f, delta);
        int base = safeMode == EffectRenderer.SNOW
                ? ElytraScreenTheme.toVanilla(ColorUtils.withAlpha(theme.text, 0.55f))
                : ElytraScreenTheme.toVanilla(ColorUtils.withAlpha(theme.secondaryLavender, 0.30f));

        for (int i = 0; i < active; i++) {
            Particle p = particles[i];
            p.y += p.speed * dt;
            p.x += p.drift * dt;
            p.phase += p.spin * dt;
            if (p.y > height || p.x > width || p.x < 0f) {
                seed(p, safeMode, width, height, true);
            }

            int x = (int) (p.x + (float) Math.sin(p.phase) * p.sway);
            int y = (int) p.y;
            int color = safeMode == EffectRenderer.LEAVES
                    ? ElytraScreenTheme.toVanilla(ColorUtils.withAlpha(LEAF_COLORS[p.tint], 0.55f))
                    : base;

            if (safeMode == EffectRenderer.RAIN) {
                context.fill(x, y, x + 1, Math.min(height, y + (int) p.size), color);
            } else if (safeMode == EffectRenderer.SNOW) {
                int r = (int) p.size;
                context.fill(x - r, y - r, x + r, y + r, color);
            } else {
                int r = (int) p.size;
                context.fill(x - r, y - r, x + r, y + r, color);
            }
        }
    }

    private void seed(Particle p, int mode, int width, int height, boolean fromTop) {
        if (mode == EffectRenderer.RAIN) {
            p.speed = 220f + random.nextFloat() * 300f;
            p.size = 8f + random.nextFloat() * 18f;
            p.drift = p.speed * 0.16f;
            p.sway = 0f;
            p.spin = 0f;
        } else if (mode == EffectRenderer.SNOW) {
            p.speed = 30f + random.nextFloat() * 70f;
            p.size = 1f + random.nextFloat();
            p.drift = 4f + random.nextFloat() * 16f;
            p.sway = 4f + random.nextFloat() * 14f;
            p.spin = 0.6f + random.nextFloat();
            p.phase = random.nextFloat() * 6.28f;
        } else {
            p.speed = 60f + random.nextFloat() * 110f;
            p.size = 1f + random.nextFloat();
            p.drift = 8f + random.nextFloat() * 28f;
            p.sway = 3f + random.nextFloat() * 12f;
            p.spin = 0.8f + random.nextFloat() * 2f;
            p.phase = random.nextFloat() * 6.28f;
            p.tint = random.nextInt(LEAF_COLORS.length);
        }
        p.x = random.nextFloat() * (width + height * 0.2f) - height * 0.1f;
        p.y = fromTop ? -p.size * 4f - random.nextFloat() * height * 0.15f
                : random.nextFloat() * height;
    }

    private static final class Particle {
        float x;
        float y;
        float speed;
        float size;
        float drift;
        float sway;
        float spin;
        float phase;
        int tint;
    }
}

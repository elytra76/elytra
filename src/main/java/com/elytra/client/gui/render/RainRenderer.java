package com.elytra.client.gui.render;

import com.elytra.client.gui.ElytraTheme;
import com.elytra.client.util.ColorUtils;
import imgui.ImDrawList;

import java.util.Random;

/**
 * Subtle animated rain drawn behind the GUI. Drops are recycled when they leave
 * the screen so the effect runs with no allocations after warm-up.
 */
public final class RainRenderer {

    private static final int DROP_COUNT = 140;
    private static final float MIN_SPEED = 420f;
    private static final float MAX_SPEED = 980f;
    private static final float MIN_LEN = 10f;
    private static final float MAX_LEN = 28f;
    private static final float MIN_THICKNESS = 0.8f;
    private static final float MAX_THICKNESS = 1.6f;
    private static final float DRIFT = 0.18f; // horizontal drift factor relative to fall

    private final Drop[] drops = new Drop[DROP_COUNT];
    private final Random random = new Random(0xE11A7A1DL);
    private int lastWidth = -1;
    private int lastHeight = -1;
    private boolean initialized;
    private int activeDrops = DROP_COUNT;

    /** Fraction of drops to draw (0-1). */
    public void setDensity(float density) {
        int count = (int) Math.round(DROP_COUNT * Math.max(0.05f, Math.min(1f, density)));
        activeDrops = Math.max(1, Math.min(DROP_COUNT, count));
    }

    public void render(ImDrawList drawList, float deltaSeconds, float alpha, int width, int height, ElytraTheme theme) {
        if (drawList == null || width <= 0 || height <= 0 || alpha <= 0.01f) {
            return;
        }
        ensureInitialized(width, height);

        if (width != lastWidth || height != lastHeight) {
            // Re-scatter on resize so drops stay distributed across the new bounds.
            for (Drop drop : drops) {
                if (drop.x > width) {
                    drop.x = random.nextFloat() * width;
                }
                if (drop.y > height) {
                    drop.y = random.nextFloat() * height;
                }
            }
            lastWidth = width;
            lastHeight = height;
        }

        int baseColor = ColorUtils.lerp(ColorUtils.hex("#C8D4FF"), theme.secondaryLavender, 0.35f);
        float dt = Math.min(deltaSeconds, 0.05f);

        for (int i = 0; i < activeDrops; i++) {
            Drop drop = drops[i];
            drop.y += drop.speed * dt;
            drop.x += drop.speed * DRIFT * dt * drop.driftMul;

            if (drop.y - drop.length > height || drop.x - drop.length * DRIFT > width) {
                respawn(drop, width, height, true);
            }

            float x2 = drop.x + drop.length * DRIFT * drop.driftMul;
            float y2 = drop.y + drop.length;
            int color = ColorUtils.fade(baseColor, alpha * drop.alpha);
            drawList.addLine(drop.x, drop.y, x2, y2, color, drop.thickness);
        }
    }

    private void ensureInitialized(int width, int height) {
        if (initialized && lastWidth == width && lastHeight == height) {
            return;
        }
        if (!initialized) {
            for (int i = 0; i < drops.length; i++) {
                drops[i] = new Drop();
                respawn(drops[i], width, height, false);
            }
            initialized = true;
        }
        lastWidth = width;
        lastHeight = height;
    }

    private void respawn(Drop drop, int width, int height, boolean fromTop) {
        drop.length = lerp(MIN_LEN, MAX_LEN, random.nextFloat());
        drop.speed = lerp(MIN_SPEED, MAX_SPEED, random.nextFloat());
        drop.thickness = lerp(MIN_THICKNESS, MAX_THICKNESS, random.nextFloat());
        drop.alpha = lerp(0.10f, 0.28f, random.nextFloat());
        drop.driftMul = lerp(0.7f, 1.3f, random.nextFloat());

        if (fromTop) {
            drop.x = random.nextFloat() * (width + height * 0.25f) - height * 0.15f;
            drop.y = -drop.length - random.nextFloat() * height * 0.2f;
        } else {
            drop.x = random.nextFloat() * (width + height * 0.25f) - height * 0.15f;
            drop.y = random.nextFloat() * (height + drop.length) - drop.length;
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static final class Drop {
        float x;
        float y;
        float length;
        float speed;
        float thickness;
        float alpha;
        float driftMul;
    }
}

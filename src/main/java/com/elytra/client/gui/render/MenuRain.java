package com.elytra.client.gui.render;

import com.elytra.client.gui.ElytraScreenTheme;
import com.elytra.client.gui.ElytraTheme;
import com.elytra.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/** Rain drawn through Minecraft's DrawContext so it lands behind menu widgets. */
public final class MenuRain {

    private static final int COUNT = 130;
    private static final float MIN_SPEED = 220f;
    private static final float MAX_SPEED = 520f;

    private final Drop[] drops = new Drop[COUNT];
    private final Random random = new Random(0xE11A7A1DL);
    private int lastWidth = -1;
    private int lastHeight = -1;
    private boolean ready;

    public void draw(DrawContext context, int width, int height, ElytraTheme theme, float delta) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (!ready || width != lastWidth || height != lastHeight) {
            for (int i = 0; i < COUNT; i++) {
                drops[i] = new Drop();
                respawn(drops[i], width, height, false);
            }
            ready = true;
            lastWidth = width;
            lastHeight = height;
        }

        float dt = Math.min(0.05f, delta);
        int color = ElytraScreenTheme.toVanilla(ColorUtils.withAlpha(theme.secondaryLavender, 0.30f));

        for (Drop drop : drops) {
            drop.y += drop.speed * dt;
            drop.x += drop.speed * 0.16f * dt;
            if (drop.y > height || drop.x > width) {
                respawn(drop, width, height, true);
            }
            int x = (int) drop.x;
            int y = (int) drop.y;
            int len = (int) drop.length;
            context.fill(x, y, x + 1, Math.min(height, y + len), color);
        }
    }

    private void respawn(Drop drop, int width, int height, boolean fromTop) {
        drop.length = 8f + random.nextFloat() * 18f;
        drop.speed = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED);
        drop.x = random.nextFloat() * (width + height * 0.2f) - height * 0.1f;
        drop.y = fromTop ? -drop.length - random.nextFloat() * height * 0.15f
                : random.nextFloat() * height;
    }

    private static final class Drop {
        float x;
        float y;
        float length;
        float speed;
    }
}

package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import com.elytra.client.ElytraConfig;
import com.elytra.client.gui.render.EffectRenderer;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.gui.render.MenuEffect;
import com.elytra.client.util.ColorUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;

/**
 * Themed backdrop for vanilla menu screens. Drawn through Minecraft's own
 * DrawContext from Screen.render HEAD, so it sits behind the widgets and uses
 * the exact same coordinate space - no ImGui frame-order surprises.
 */
public final class ElytraScreenTheme {

    private static final MenuEffect EFFECT = new MenuEffect();
    private static long lastFrameNanos;

    private ElytraScreenTheme() {
    }

    public static boolean shouldTheme(Screen screen) {
        return !ImGuiRenderer.isUnavailable() && (screen instanceof OptionsScreen
                || screen instanceof GameOptionsScreen
                || screen instanceof MultiplayerScreen
                || screen instanceof SelectWorldScreen
                || screen instanceof GameMenuScreen);
    }

    public static void renderBackdrop(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f / 60f
                : Math.min(0.1f, Math.max(0.001f, (now - lastFrameNanos) / 1_000_000_000f));
        lastFrameNanos = now;

        ElytraTheme theme = ElytraTheme.get();
        theme.tick(delta);

        // Light themed tint only - the world stays visible behind a soft blur.
        context.fill(0, 0, width, height,
                toVanilla(ColorUtils.withAlpha(theme.mainBackground, 0.32f)));

        // Ambient effect over the translucent backdrop, no framed panel box.
        ElytraConfig config = ElytraClient.config();
        if (config != null && config.rainEnabled()
                && config.effectMode() != EffectRenderer.NONE) {
            EFFECT.setDensity(config.rainDensity());
            EFFECT.draw(context, config.effectMode(), width, height, theme, delta);
        }
    }

    /** ImGui packs ABGR; vanilla DrawContext wants ARGB. */
    public static int toVanilla(int imguiColor) {
        int a = (imguiColor >>> 24) & 0xFF;
        int b = (imguiColor >>> 16) & 0xFF;
        int g = (imguiColor >>> 8) & 0xFF;
        int r = imguiColor & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

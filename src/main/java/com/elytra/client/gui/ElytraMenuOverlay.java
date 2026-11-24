package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import com.elytra.client.ElytraConfig;
import com.elytra.client.gui.render.GlowRenderer;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.gui.render.EffectRenderer;
import com.elytra.client.util.ColorUtils;
import com.elytra.client.util.RenderUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PressableWidget;

/** Elytra-branded overlay: header, rain and themed rounded buttons over vanilla menus. */
public final class ElytraMenuOverlay {

    private static final EffectRenderer EFFECT = new EffectRenderer();
    private static long lastFrameNanos;

    private ElytraMenuOverlay() {
    }

    public static void render(ElytraTheme theme, int width, int height) {
        ImGuiRenderer.beginFrame(width, height);
        theme.applyStyle();

        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f / 60f
                : Math.min(0.1f, Math.max(0.001f, (now - lastFrameNanos) / 1_000_000_000f));
        lastFrameNanos = now;

        ImDrawList drawList = ImGui.getBackgroundDrawList();
        ElytraConfig config = ElytraClient.config();
        if (config != null && config.rainEnabled()
                && config.effectMode() != EffectRenderer.NONE) {
            EFFECT.setDensity(config.rainDensity());
            EFFECT.render(drawList, config.effectMode(), delta, 0.7f, width, height, theme);
        }

        drawHeader(theme, drawList, width);
        drawButtons(theme, drawList, width, height);

        ImGuiRenderer.endFrame();
    }

    private static void drawHeader(ElytraTheme theme, ImDrawList drawList, int width) {
        float pillW = theme.s(260f);
        float pillH = theme.s(26f);
        float x = (width - pillW) * 0.5f;

        // Opaque backing hides the vanilla screen title underneath.
        drawList.addRectFilled(x - theme.s(4f), -theme.s(2f), x + pillW + theme.s(4f),
                pillH + theme.s(2f), ColorUtils.withAlpha(theme.mainBackground, 1f));

        GlowRenderer.pillGlow(drawList, x, 0f, x + pillW, pillH, pillH * 0.5f, 1f);
        RenderUtils.roundedPanel(drawList, x, 0f, x + pillW, pillH, pillH * 0.5f,
                ColorUtils.withAlpha(theme.panelBackground, 1f),
                ColorUtils.withAlpha(theme.panelBackgroundTop, 0f), 0);
        RenderUtils.roundedOutline(drawList, x, 0f, x + pillW, pillH, pillH * 0.5f,
                ColorUtils.withAlpha(theme.panelBorder, 0.7f));

        float titleSize = ImGuiRenderer.fontTitleSize();
        String title = "Elytra Client";
        float textW = ImGuiRenderer.fontTitle().calcTextSizeAX(titleSize, 4096f, 0f, title);
        RenderUtils.text(drawList, ImGuiRenderer.fontTitle(), titleSize,
                (width - textW) * 0.5f, (pillH - titleSize) * 0.5f, theme.primaryPink, title);

        float footerSize = ImGuiRenderer.fontSmallSize();
        String footer = theme.themeName();
        float footerW = ImGuiRenderer.fontSmall().calcTextSizeAX(footerSize, 4096f, 0f, footer);
        RenderUtils.text(drawList, ImGuiRenderer.fontSmall(), footerSize,
                width - footerW - theme.s(10f), theme.s(3f),
                ColorUtils.withAlpha(theme.textSecondary, 0.7f), footer);
    }

    private static void drawButtons(ElytraTheme theme, ImDrawList drawList, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        Screen screen = client.currentScreen;
        if (screen == null) {
            return;
        }

        float scaleX = width / (float) Math.max(1, client.getWindow().getScaledWidth());
        float scaleY = height / (float) Math.max(1, client.getWindow().getScaledHeight());
        ImGuiIO io = ImGui.getIO();
        float mouseX = io.getMousePosX();
        float mouseY = io.getMousePosY();
        float fontSize = ImGuiRenderer.fontBodySize();

        for (Element element : screen.children()) {
            if (!(element instanceof PressableWidget button)) {
                continue;
            }
            float bx = button.getX() * scaleX;
            float by = button.getY() * scaleY;
            float bw = button.getWidth() * scaleX;
            float bh = button.getHeight() * scaleY;
            if (bw <= 0f || bh <= 0f) {
                continue;
            }
            float bx2 = bx + bw;
            float by2 = by + bh;
            boolean hovered = mouseX >= bx && mouseX <= bx2 && mouseY >= by && mouseY <= by2;
            float radius = theme.s(6f);

            // Soft glow on hover, matching the title screen button treatment.
            if (hovered) {
                GlowRenderer.panelGlow(drawList, bx, by, bx2, by2, radius, 1f);
            }

            int fill = hovered ? theme.hoverBackground : theme.panelBackground;
            RenderUtils.roundedPanel(drawList, bx, by, bx2, by2, radius,
                    ColorUtils.withAlpha(fill, 0.94f),
                    ColorUtils.withAlpha(theme.panelBackgroundTop, 0f), 0);
            RenderUtils.roundedOutline(drawList, bx, by, bx2, by2, radius,
                    ColorUtils.withAlpha(hovered ? theme.primaryPink : theme.panelBorder,
                            hovered ? 0.95f : 0.65f));

            String label = button.getMessage().getString();
            if (label.isEmpty()) {
                continue;
            }
            float textW = ImGuiRenderer.fontBody().calcTextSizeAX(fontSize, 4096f, 0f, label);
            float tx = bx + (bw - textW) * 0.5f;
            float ty = by + (bh - fontSize) * 0.5f;
            RenderUtils.text(drawList, ImGuiRenderer.fontBody(), fontSize, tx, ty,
                    hovered ? theme.primaryPink : theme.text, label);
        }
    }
}

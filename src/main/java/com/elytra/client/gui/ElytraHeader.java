package com.elytra.client.gui;

import com.elytra.client.gui.render.GlowRenderer;
import com.elytra.client.gui.render.GuiAnimations;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.util.ColorUtils;
import com.elytra.client.util.RenderUtils;
import imgui.ImDrawList;
import imgui.ImGui;

/**
 * The three floating header elements: status pill, centre name plate and the
 * clickable control strip on the right.
 *
 * Everything is drawn manually on the draw list - ImGui's own widgets cannot
 * produce the pill silhouettes or the glow, so the only widgets used here are
 * invisible hit boxes.
 */
public final class ElytraHeader {

    public static final int ACTION_NONE = 0;
    public static final int ACTION_SETTINGS = 1;
    public static final int ACTION_CONFIGS = 2;
    public static final int ACTION_CLOSE = 3;

    private static final String[] SECTIONS = {"GUI Settings", "Configs"};
    private static final String[] HOVER_KEYS = {"hdr_settings", "hdr_configs"};

    private final GuiAnimations hover = new GuiAnimations();
    private final boolean[] hovered = new boolean[SECTIONS.length];

    public void tick(float deltaSeconds) {
        hover.tick(deltaSeconds);
    }

    public int render(ElytraTheme theme, ImDrawList backgroundDrawList, float alpha, boolean interactive,
                      float screenWidth, float screenHeight) {
        if (alpha <= 0.002f) {
            return ACTION_NONE;
        }
        renderCentrePill(theme, backgroundDrawList, alpha);
        renderBottomLeftLogo(theme, backgroundDrawList, alpha, screenWidth, screenHeight);
        return renderControlPill(theme, backgroundDrawList, alpha, interactive);
    }

    private int renderControlPill(ElytraTheme theme, ImDrawList backgroundDrawList, float alpha,
                                  boolean interactive) {
        float x = theme.headerRightX();
        float y = theme.headerRightY();
        float width = theme.headerRightW();
        float height = theme.headerRightH();
        float maxX = x + width;
        float maxY = y + height;
        float radius = theme.s(6f);

        GlowRenderer.pillGlow(backgroundDrawList, x, y, maxX, maxY, radius, alpha * 0.8f);
        RenderUtils.roundedPanel(backgroundDrawList, x, y, maxX, maxY, radius,
                ColorUtils.fade(ColorUtils.withAlpha(theme.panelBackground, 0.80f), alpha),
                ColorUtils.withAlpha(theme.panelBackgroundTop, 0f),
                ColorUtils.withAlpha(theme.primaryPink, 0.05f * alpha));
        RenderUtils.roundedOutline(backgroundDrawList, x, y, maxX, maxY, radius,
                ColorUtils.withAlpha(theme.panelBorder, 0.50f * alpha));

        float sectionWidth = width / SECTIONS.length;
        int action = ACTION_NONE;

        // Pass one: hit test and hand the results to the easing system.
        for (int index = 0; index < SECTIONS.length; index++) {
            float sectionX = x + index * sectionWidth;
            hovered[index] = false;

            if (interactive) {
                ImGui.setCursorScreenPos(sectionX, y);
                ImGui.invisibleButton("##elytra_header_" + index, sectionWidth, height);
                hovered[index] = ImGui.isItemHovered();
                if (hovered[index] && ImGui.isMouseClicked(0)) {
                    action = index == 0 ? ACTION_SETTINGS : ACTION_CONFIGS;
                }
            }
            hover.target(HOVER_KEYS[index], hovered[index] ? 1f : 0f);
        }

        // Pass two: draw with the eased values.
        for (int index = 0; index < SECTIONS.length; index++) {
            float sectionX = x + index * sectionWidth;
            float eased = hover.get(HOVER_KEYS[index]);

            if (index > 0) {
                RenderUtils.vDivider(backgroundDrawList, sectionX, y + theme.s(6f), maxY - theme.s(6f),
                        ColorUtils.withAlpha(theme.panelBorder, 0.28f * alpha));
            }

            if (eased > 0.01f) {
                float inset = theme.s(2f);
                backgroundDrawList.addRectFilled(sectionX + inset, y + inset, sectionX + sectionWidth - inset,
                        maxY - inset, ColorUtils.withAlpha(theme.hoverBackground, 0.70f * eased * alpha),
                        radius * 0.7f);
            }

            int baseColor = index == 0 ? theme.secondaryLavender : theme.textSecondary;
            int textColor = ColorUtils.lerp(baseColor, theme.primaryPink, eased);
            float fontSize = ImGuiRenderer.fontSmallSize();
            float textWidth = ImGuiRenderer.fontSmall().calcTextSizeAX(fontSize, 4096f, 0f, SECTIONS[index]);
            float textX = sectionX + (sectionWidth - textWidth) * 0.5f;
            float textY = y + height * 0.5f - fontSize * 0.5f + theme.s(0.5f);

            RenderUtils.text(backgroundDrawList, ImGuiRenderer.fontSmall(), fontSize, textX, textY,
                    ColorUtils.fade(textColor, alpha), SECTIONS[index]);
        }

        return action;
    }

    private void renderBottomLeftLogo(ElytraTheme theme, ImDrawList backgroundDrawList, float alpha,
                                      float screenWidth, float screenHeight) {
        int logo = ImGuiRenderer.logoTexture();
        if (logo == 0 || ImGuiRenderer.logoWidth() <= 0) {
            return;
        }
        float padding = theme.s(10f);
        float logoHeight = Math.min(theme.s(80f), screenHeight * 0.14f);
        float logoWidth = logoHeight * ImGuiRenderer.logoWidth() / (float) ImGuiRenderer.logoHeight();
        float logoX = padding;
        float logoY = screenHeight - logoHeight - padding;

        // Watermark: faint image only, no glow, no outline.
        backgroundDrawList.addImageRounded(logo,
                logoX, logoY, logoX + logoWidth, logoY + logoHeight,
                0f, 0f, 1f, 1f,
                ColorUtils.fade(ColorUtils.hex("#FFFFFF"), alpha * 0.12f), theme.s(8f), 0);
    }


    private void renderCentrePill(ElytraTheme theme, ImDrawList backgroundDrawList, float alpha) {
        float width = theme.headerCenterW();
        float height = theme.headerCenterH();
        float x = (theme.headerCenterX() + theme.headerCenterW() * 0.5f) - width * 0.5f;
        float y = theme.headerCenterY();
        float maxX = x + width;
        float maxY = y + height;
        float radius = height * 0.5f;

        GlowRenderer.pillGlow(backgroundDrawList, x, y, maxX, maxY, radius, alpha * 0.65f);
        RenderUtils.roundedPanel(backgroundDrawList, x, y, maxX, maxY, radius,
                ColorUtils.fade(ColorUtils.withAlpha(theme.panelBackground, 0.72f), alpha),
                ColorUtils.withAlpha(theme.panelBackgroundTop, 0f),
                ColorUtils.withAlpha(theme.primaryPink, 0.06f * alpha));
        RenderUtils.roundedOutline(backgroundDrawList, x, y, maxX, maxY, radius,
                ColorUtils.withAlpha(theme.panelBorder, 0.45f * alpha));

        String label = "Elytra Client";
        float fontSize = ImGuiRenderer.fontTitleSize();
        float textWidth = ImGuiRenderer.fontTitle().calcTextSizeAX(fontSize, 4096f, 0f, label);
        float textX = x + (width - textWidth) * 0.5f;
        RenderUtils.text(backgroundDrawList, ImGuiRenderer.fontTitle(), fontSize, textX,
                y + height * 0.5f - fontSize * 0.5f + theme.s(0.5f),
                ColorUtils.fade(theme.primaryPink, alpha), label);
    }
}

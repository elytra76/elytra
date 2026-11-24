package com.elytra.client.gui;

import com.elytra.client.gui.render.GlowRenderer;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.util.ColorUtils;
import com.elytra.client.util.RenderUtils;
import imgui.ImDrawList;
import imgui.ImGui;

/**
 * Shared drawing primitives for every floating Elytra panel.
 *
 * The category columns, GUI Settings and Configs all come through here, so a
 * panel can never drift away from the reference look: same glow, same rounded
 * gradient body, same hairline outline, same header strip and row metrics.
 */
public final class ElytraPanelChrome {

    /** {@link #rowInteraction} result flags. */
    public static final int HOVER = 1;
    public static final int ACTIVE = 2;
    public static final int CLICKED = 4;

    private ElytraPanelChrome() {
    }

    /** Glow behind the panel plus the rounded body and hairline outline. */
    public static void surface(ElytraTheme theme, ImDrawList backgroundDrawList, ImDrawList drawList,
                               float minX, float minY, float maxX, float maxY, float alpha) {
        GlowRenderer.panelGlow(backgroundDrawList, minX, minY, maxX, maxY, theme.panelRadius(), alpha);
        RenderUtils.roundedPanel(drawList, minX, minY, maxX, maxY, theme.panelRadius(),
                ColorUtils.fade(theme.panelBackground, alpha),
                ColorUtils.withAlpha(theme.panelBackgroundTop, 0f),
                ColorUtils.withAlpha(theme.primaryPink, 0.055f * alpha));
        RenderUtils.roundedOutline(drawList, minX, minY, maxX, maxY, theme.panelRadius(),
                ColorUtils.fade(theme.panelBorder, alpha));
    }

    /** Panel title in the header strip plus the divider under it. */
    public static void headerTitle(ElytraTheme theme, ImDrawList drawList, float minX, float minY, float maxX,
                                   float alpha, String title) {
        float headerHeight = theme.panelHeaderHeight();
        float titleSize = ImGuiRenderer.fontTitleSize();
        float textX = minX + theme.panelPaddingX();
        float textY = minY + headerHeight * 0.5f - titleSize * 0.5f + theme.s(0.5f);

        RenderUtils.text(drawList, ImGuiRenderer.fontTitle(), titleSize, textX, textY,
                ColorUtils.fade(theme.text, alpha), title);
        RenderUtils.divider(drawList, minX + theme.panelPaddingX(), maxX - theme.panelPaddingX(),
                minY + headerHeight - theme.s(1f),
                ColorUtils.withAlpha(theme.panelBorder, 0.22f * alpha));
    }

    /** Row backdrop: highlight tint plus the eased hover fill. */
    public static void rowBackground(ElytraTheme theme, ImDrawList drawList, float rowMinX, float rowTop,
                                     float rowMaxX, float rowHeight, float alpha, float hover,
                                     boolean highlighted) {
        float inset = theme.s(1f);
        if (highlighted) {
            drawList.addRectFilled(rowMinX, rowTop + inset, rowMaxX, rowTop + rowHeight - inset,
                    ColorUtils.withAlpha(theme.primaryPink, 0.16f * alpha), theme.rowRadius());
        }
        if (hover > 0.01f) {
            drawList.addRectFilled(rowMinX, rowTop + inset, rowMaxX, rowTop + rowHeight - inset,
                    ColorUtils.withAlpha(theme.hoverBackground, 0.85f * hover * alpha), theme.rowRadius());
        }
    }

    /** Row label, left aligned with the standard inset. */
    public static void rowLabel(ElytraTheme theme, ImDrawList drawList, float rowMinX, float rowTop,
                                float rowHeight, float alpha, int color, String label, int indent) {
        float bodySize = ImGuiRenderer.fontBodySize();
        float textY = rowTop + rowHeight * 0.5f - bodySize * 0.5f + theme.s(0.5f);
        labelAt(theme, drawList, rowMinX + indent * theme.s(7f), textY, alpha, color, label);
    }

    /** Right aligned value, with an optional chevron after it. */
    public static void rowValue(ElytraTheme theme, ImDrawList drawList, float rowMaxX, float rowTop,
                                float rowHeight, float alpha, int color, String value, boolean chevron) {
        float bodySize = ImGuiRenderer.fontBodySize();
        float textY = rowTop + rowHeight * 0.5f - bodySize * 0.5f + theme.s(0.5f);
        valueAt(theme, drawList, rowMaxX, textY, alpha, color, value, chevron);
    }

    /** Left aligned label at an explicit Y, used by slider rows. */
    public static void labelAt(ElytraTheme theme, ImDrawList drawList, float x, float y,
                               float alpha, int color, String label) {
        RenderUtils.text(drawList, ImGuiRenderer.fontBody(), Math.round(ImGuiRenderer.fontBodySize()),
                x + theme.s(6f), y, ColorUtils.fade(color, alpha), label);
    }

    /** Right aligned value at an explicit Y, used by slider rows. */
    public static void valueAt(ElytraTheme theme, ImDrawList drawList, float rightX, float y,
                               float alpha, int color, String value, boolean chevron) {
        float bodySize = Math.round(ImGuiRenderer.fontBodySize());
        float right = rightX - theme.s(6f) - (chevron ? theme.s(9f) : 0f);
        float textWidth = measure(theme, value);
        RenderUtils.text(drawList, ImGuiRenderer.fontBody(), bodySize, right - textWidth, y,
                ColorUtils.fade(color, alpha), value);

        if (chevron) {
            float chevronX = rightX - theme.s(9f);
            float chevronY = y + bodySize * 0.5f;
            RenderUtils.chevron(drawList, chevronX, chevronY, theme.s(3.2f),
                    ColorUtils.withAlpha(color, alpha));
        }
    }

    /** Width of a body-font string at the current scale. */
    public static float measure(ElytraTheme theme, String value) {
        imgui.ImFont font = ImGuiRenderer.fontBody();
        float size = Math.round(ImGuiRenderer.fontBodySize());
        if (font == null || value == null || value.isEmpty()) {
            return 0f;
        }
        return font.calcTextSizeAX(size, Float.MAX_VALUE, 0f, value);
    }

    /**
     * Invisible hit area over one row.
     *
     * @return bitmask of {@link #HOVER}, {@link #ACTIVE} and {@link #CLICKED}
     */
    public static int rowInteraction(String id, float x, float y, float width, float height) {
        ImGui.setCursorScreenPos(x, y);
        ImGui.invisibleButton(id, width, height);
        int flags = 0;
        if (ImGui.isItemHovered()) {
            flags |= HOVER;
        }
        if (ImGui.isItemActive()) {
            flags |= ACTIVE;
        }
        if (ImGui.isItemClicked()) {
            flags |= CLICKED;
        }
        return flags;
    }

    /** Close X in the header corner. */
    public static boolean closeButton(ElytraTheme theme, String id, float x, float y) {
        float size = theme.s(11f);
        ImGui.setCursorScreenPos(x, y);
        ImGui.invisibleButton(id, size, size);
        boolean hovered = ImGui.isItemHovered();
        boolean clicked = ImGui.isItemClicked();
        int color = hovered ? theme.primaryPink : ColorUtils.withAlpha(theme.textSecondary, 0.75f);
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addLine(x, y, x + size, y + size, color, theme.s(1.2f));
        drawList.addLine(x + size, y, x, y + size, color, theme.s(1.2f));
        return clicked;
    }

    /** Header drag handling shared by every floating panel. */
    public static final class Drag {
        private float offsetX;
        private float offsetY;

        public boolean active() {
            return dragging;
        }

        private boolean dragging;

        /** True while the pointer is over the draggable part of the header. */
        public static boolean overHeader(ElytraTheme theme, float minX, float minY, float maxX, float reserveRight) {
            return ImGui.isMouseHoveringRect(minX, minY, maxX - reserveRight,
                    minY + theme.panelHeaderHeight());
        }

        public void begin(float panelX, float panelY) {
            dragging = true;
            offsetX = ImGui.getMousePosX() - panelX;
            offsetY = ImGui.getMousePosY() - panelY;
        }

        public void cancel() {
            dragging = false;
        }

        /** @return true while the panel is being moved this frame */
        public boolean tick() {
            if (!dragging) {
                return false;
            }
            if (!ImGui.isMouseDown(0)) {
                dragging = false;
                return false;
            }
            return true;
        }

        public float x() {
            return ImGui.getMousePosX() - offsetX;
        }

        public float y() {
            return ImGui.getMousePosY() - offsetY;
        }
    }
}

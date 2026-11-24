package com.elytra.client.util;

import imgui.ImDrawList;
import imgui.ImVec2;

/**
 * Low level ImDrawList helpers shared by the panels, headers and popups.
 *
 * Everything here is allocation-light on purpose: the GUI redraws every frame and
 * the draw list is the hot path.
 */
public final class RenderUtils {

    private RenderUtils() {
    }

    /**
     * Filled rounded panel with a soft vertical tint.
     *
     * The gradient is drawn as an inset rectangle so it never fights the rounded
     * corners - a plain AddRectFilledMultiColor would square them off.
     */
    public static void roundedPanel(ImDrawList drawList, float minX, float minY, float maxX, float maxY,
                                    float rounding, int baseColor, int topColor, int bottomTint) {
        drawList.addRectFilled(minX, minY, maxX, maxY, baseColor, rounding);

        float inset = rounding * 0.85f;
        if (maxX - inset * 2f > 0f && maxY - inset * 2f > 0f) {
            drawList.addRectFilledMultiColor(
                    minX + inset, minY + inset,
                    maxX - inset, maxY - inset,
                    topColor, topColor, bottomTint, bottomTint);
        }
    }

    /** Hairline rounded outline. */
    public static void roundedOutline(ImDrawList drawList, float minX, float minY, float maxX, float maxY,
                                      float rounding, int color) {
        drawList.addRect(minX, minY, maxX, maxY, color, rounding);
    }

    /** Single pixel horizontal rule. */
    public static void divider(ImDrawList drawList, float fromX, float toX, float y, int color) {
        drawList.addLine(fromX, y, toX, y, color, 1f);
    }

    /** Small right-pointing chevron used at the trailing edge of every row. */
    public static void chevron(ImDrawList drawList, float x, float y, float size, int color) {
        drawList.addLine(x, y - size, x + size * 0.62f, y, color, 1.15f);
        drawList.addLine(x + size * 0.62f, y, x, y + size, color, 1.15f);
    }

    /** Vertical separator between header sections. */
    public static void vDivider(ImDrawList drawList, float x, float fromY, float toY, int color) {
        drawList.addLine(x, fromY, x, toY, color, 1f);
    }

    /**
     * Text with a manual horizontal offset, used where the layout needs the label
     * vertically centred rather than sitting on the ImGui cursor line.
     */
    public static void textAt(ImDrawList drawList, float x, float y, int color, String value) {
        drawList.addText(x, y, color, value);
    }

    /**
     * Font-aware text draw. ImGui's binding takes the font size as an integer, so the
     * value is rounded once here instead of at every call site.
     */
    public static void text(ImDrawList drawList, imgui.ImFont font, float fontSize,
                            float x, float y, int color, String value) {
        drawList.addText(font, Math.round(fontSize), x, y, color, value);
    }

    public static ImVec2 vec(float x, float y) {
        return new ImVec2(x, y);
    }
}

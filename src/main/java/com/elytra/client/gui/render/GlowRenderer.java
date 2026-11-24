package com.elytra.client.gui.render;

import com.elytra.client.gui.ElytraTheme;
import com.elytra.client.util.ColorUtils;
import imgui.ImDrawList;

/**
 * Fake bloom for the panels.
 *
 * Dear ImGui has no shadow primitive, so the glow is a stack of rounded outlines
 * expanding outwards with a falling alpha ramp. Five passes is the point where it
 * still reads as light rather than as a neon border.
 */
public final class GlowRenderer {

    private static final int OUTLINE_PASSES = 5;

    private GlowRenderer() {
    }

    public static void panelGlow(ImDrawList drawList, float minX, float minY, float maxX, float maxY,
                                 float rounding, float strength) {
        if (strength <= 0.01f) {
            return;
        }
        int glowColor = ElytraTheme.get().glow;
        float unit = ElytraTheme.get().uiScale();

        for (int pass = OUTLINE_PASSES; pass >= 1; pass--) {
            float expansion = pass * 1.55f * unit;
            float alpha = 0.095f * strength * (1f - (pass - 1) / (float) OUTLINE_PASSES);
            drawList.addRect(
                    minX - expansion, minY - expansion,
                    maxX + expansion, maxY + expansion,
                    ColorUtils.withAlpha(glowColor, alpha),
                    rounding + expansion,
                    0,
                    1f + pass * 0.35f * unit);
        }

        // Inner rim: a thin bright edge right on the panel boundary.
        drawList.addRect(minX - 0.5f * unit, minY - 0.5f * unit, maxX + 0.5f * unit, maxY + 0.5f * unit,
                ColorUtils.withAlpha(glowColor, 0.20f * strength), rounding + 0.5f * unit, 0, 1f);
    }

    /** Softer, tighter halo for the header pills. */
    public static void pillGlow(ImDrawList drawList, float minX, float minY, float maxX, float maxY,
                                float rounding, float strength) {
        if (strength <= 0.01f) {
            return;
        }
        int glowColor = ElytraTheme.get().glow;
        float unit = ElytraTheme.get().uiScale();

        for (int pass = 3; pass >= 1; pass--) {
            float expansion = pass * 1.2f * unit;
            float alpha = 0.06f * strength * (1f - (pass - 1) / 3f);
            drawList.addRect(
                    minX - expansion, minY - expansion,
                    maxX + expansion, maxY + expansion,
                    ColorUtils.withAlpha(glowColor, alpha),
                    rounding + expansion, 0, 1.2f);
        }
    }
}

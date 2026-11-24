package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import com.elytra.client.gui.render.EffectRenderer;
import com.elytra.client.gui.render.ImGuiInputHandler;
import com.elytra.client.util.ColorUtils;
import imgui.ImDrawList;
import imgui.ImGui;

/**
 * GUI Settings, built as a panel: same header strip, divider, glow, rounded body
 * and row treatment as the Combat/Render/Misc columns. Rows are display-only
 * chrome with a single hit area each - no framed ImGui widgets.
 */
public final class ElytraSettingsGUI extends ElytraFloatingPanel {

    private static final String POSITION_KEY = "GUI Settings";
    private static final float WIDTH = 170f;

    private static final int ROW_SCALE = 0;
    private static final int ROW_THEME = 1;
    private static final int ROW_EFFECT = 2;
    private static final int ROW_DENSITY = 3;
    private static final int ROW_RESET = 4;
    private static final int ROW_KEY = 5;
    private static final int ROW_COUNT = 6;

    private static final String THEME_POPUP = "##settings_theme_menu";
    private static final String EFFECT_POPUP = "##settings_effect_menu";

    /** Slider rows are taller so the track sits under the label instead of over it. */
    private static final float SLIDER_ROW_MULTIPLIER = 1.7f;

    private boolean listening;

    public ElytraSettingsGUI() {
        super("GUI Settings", POSITION_KEY, WIDTH);
    }

    public boolean isListening() {
        return listening;
    }

    public void startListening() {
        listening = true;
    }

    /** Called from the screen when a key is pressed while listening. */
    public void onKey(int glfwKey) {
        if (!listening) {
            return;
        }
        if (glfwKey == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            listening = false;
            return;
        }
        listening = false;
        ElytraClient.config().setOpenKey(glfwKey);
        try {
            ElytraClient.openKey().setBoundKey(
                    net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(glfwKey));
        } catch (Exception ignored) {
        }
        ElytraClient.resetKeyState();
        ElytraClient.requestConfigSave();
    }

    @Override
    public void setOpen(boolean value) {
        super.setOpen(value);
        if (!value) {
            listening = false;
        }
    }

    @Override
    protected float rowHeight(ElytraTheme theme, int index) {
        if (index == ROW_SCALE || index == ROW_DENSITY) {
            return theme.panelRowHeight() * SLIDER_ROW_MULTIPLIER;
        }
        return theme.panelRowHeight();
    }

    @Override
    protected float height(ElytraTheme theme) {
        float total = theme.panelHeaderHeight();
        for (int index = 0; index < ROW_COUNT; index++) {
            total += rowHeight(theme, index);
        }
        return total + theme.panelPaddingBottom();
    }

    @Override
    protected void drawBody(ElytraTheme theme, ImDrawList drawList, float minX, float minY, float maxX,
                            float alpha, boolean interactive, float deltaSeconds) {
        float rowMinX = minX + theme.panelPaddingX();
        float rowMaxX = maxX - theme.panelPaddingX();
        float rowWidth = rowMaxX - rowMinX;

        // ---- GUI scale ------------------------------------------------------
        int flags = rowShell(theme, drawList, ROW_SCALE, minX, maxX, rowTop(theme, minY, ROW_SCALE),
                alpha, interactive, false, deltaSeconds);
        if ((flags & ElytraPanelChrome.ACTIVE) != 0) {
            float t = clamp01((ImGui.getMousePosX() - rowMinX) / Math.max(1f, rowWidth));
            ElytraClient.config().setGuiScale(0.70f + t * 0.90f);
        }
        drawRow(theme, drawList, ROW_SCALE, minX, maxX, minY, alpha, "GUI Scale",
                String.format(java.util.Locale.ROOT, "%.2fx", ElytraClient.config().guiScale()), false);
        drawSlider(theme, drawList, ROW_SCALE, minX, maxX, minY, alpha, true,
                (ElytraClient.config().guiScale() - 0.70f) / 0.90f);

        // ---- Theme ----------------------------------------------------------
        flags = rowShell(theme, drawList, ROW_THEME, minX, maxX, rowTop(theme, minY, ROW_THEME),
                alpha, interactive, false, deltaSeconds);
        if ((flags & ElytraPanelChrome.CLICKED) != 0) {
            ImGui.openPopup(THEME_POPUP);
        }
        drawRow(theme, drawList, ROW_THEME, minX, maxX, minY, alpha, "Theme",
                theme.themeName(), true);

        // ---- Effect ---------------------------------------------------------
        flags = rowShell(theme, drawList, ROW_EFFECT, minX, maxX, rowTop(theme, minY, ROW_EFFECT),
                alpha, interactive, false, deltaSeconds);
        if ((flags & ElytraPanelChrome.CLICKED) != 0) {
            ImGui.openPopup(EFFECT_POPUP);
        }
        drawRow(theme, drawList, ROW_EFFECT, minX, maxX, minY, alpha, "Effect",
                EffectRenderer.NAMES[ElytraClient.config().effectMode()], true);

        // ---- Density --------------------------------------------------------
        boolean enabled = ElytraClient.config().effectMode() != EffectRenderer.NONE;
        flags = rowShell(theme, drawList, ROW_DENSITY, minX, maxX, rowTop(theme, minY, ROW_DENSITY),
                alpha, interactive && enabled, false, deltaSeconds);
        if (enabled && (flags & ElytraPanelChrome.ACTIVE) != 0) {
            float t = clamp01((ImGui.getMousePosX() - rowMinX) / Math.max(1f, rowWidth));
            ElytraClient.config().setRainDensity(0.15f + t * 0.85f);
        }
        drawRowDimmed(theme, drawList, ROW_DENSITY, minX, maxX, minY, alpha, "Density",
                Math.round(ElytraClient.config().rainDensity() * 100f) + "%", enabled);
        drawSlider(theme, drawList, ROW_DENSITY, minX, maxX, minY, alpha, enabled,
                (ElytraClient.config().rainDensity() - 0.15f) / 0.85f);

        // ---- Reset panel layout ---------------------------------------------
        flags = rowShell(theme, drawList, ROW_RESET, minX, maxX, rowTop(theme, minY, ROW_RESET),
                alpha, interactive, false, deltaSeconds);
        if ((flags & ElytraPanelChrome.CLICKED) != 0) {
            ElytraClient.resetPanelLayout();
        }
        drawRow(theme, drawList, ROW_RESET, minX, maxX, minY, alpha, "Reset Panels", "", false);

        // ---- Toggle key -----------------------------------------------------
        flags = rowShell(theme, drawList, ROW_KEY, minX, maxX, rowTop(theme, minY, ROW_KEY),
                alpha, interactive, false, deltaSeconds);
        if ((flags & ElytraPanelChrome.CLICKED) != 0) {
            startListening();
        }
        String keyLabel = listening ? "press..."
                : ImGuiInputHandler.keyName(ElytraClient.config().openKey());
        drawRow(theme, drawList, ROW_KEY, minX, maxX, minY, alpha, "Menu Key", keyLabel, false);

        drawThemePopup(theme);
        if (ImGui.beginPopup(EFFECT_POPUP)) {
            int current = ElytraClient.config().effectMode();
            for (int index = 0; index < EffectRenderer.NAMES.length; index++) {
                if (ImGui.menuItem(EffectRenderer.NAMES[index], null, index == current, true)) {
                    ElytraClient.config().setEffectMode(index);
                    ElytraClient.requestConfigSave();
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.endPopup();
        }
    }

    private void drawThemePopup(ElytraTheme theme) {
        if (!ImGui.beginPopup(THEME_POPUP)) {
            return;
        }
        String[] names = theme.themeNames();
        int current = ElytraClient.config().themeIndex();
        for (int index = 0; index < names.length; index++) {
            if (ImGui.menuItem(names[index], null, index == current, true)) {
                ElytraClient.config().setThemeIndex(index);
                theme.applyTheme(index);
                ElytraClient.requestConfigSave();
            }
        }
        ImGui.endPopup();
    }

    private void drawRow(ElytraTheme theme, ImDrawList drawList, int index, float minX, float maxX,
                         float minY, float alpha, String label, String value, boolean chevron) {
        float rowMinX = minX + theme.panelPaddingX();
        float rowMaxX = maxX - theme.panelPaddingX();
        float rowTop = rowTop(theme, minY, index);
        float thisRowHeight = rowHeight(theme, index);
        int color = rowTextColor(theme, index, false);
        if (isSlider(index)) {
            float textY = rowTop + theme.s(1f);
            ElytraPanelChrome.labelAt(theme, drawList, rowMinX, textY, alpha, color, label);
            if (!value.isEmpty()) {
                ElytraPanelChrome.valueAt(theme, drawList, rowMaxX, textY, alpha,
                        ColorUtils.lerp(color, theme.primaryPink, 0.55f), value, chevron);
            }
        } else {
            ElytraPanelChrome.rowLabel(theme, drawList, rowMinX, rowTop, thisRowHeight,
                    alpha, color, label, 0);
            if (!value.isEmpty()) {
                ElytraPanelChrome.rowValue(theme, drawList, rowMaxX, rowTop, thisRowHeight,
                        alpha, ColorUtils.lerp(color, theme.primaryPink, 0.55f), value, chevron);
            }
        }
    }

    private void drawRowDimmed(ElytraTheme theme, ImDrawList drawList, int index, float minX, float maxX,
                               float minY, float alpha, String label, String value, boolean enabled) {
        float rowMinX = minX + theme.panelPaddingX();
        float rowMaxX = maxX - theme.panelPaddingX();
        float rowTop = rowTop(theme, minY, index);
        int color = enabled ? rowTextColor(theme, index, false) : theme.disabled;
        float textY = rowTop + theme.s(1f);
        ElytraPanelChrome.labelAt(theme, drawList, rowMinX, textY, alpha, color, label);
        ElytraPanelChrome.valueAt(theme, drawList, rowMaxX, textY, alpha, color, value, false);
    }

    private static boolean isSlider(int index) {
        return index == ROW_SCALE || index == ROW_DENSITY;
    }

    /** Track and knob drawn in the lower half of a tall slider row. */
    private void drawSlider(ElytraTheme theme, ImDrawList drawList, int index, float minX, float maxX,
                            float minY, float alpha, boolean enabled, float fraction) {
        float rowTop = rowTop(theme, minY, index);
        float rowHeight = rowHeight(theme, index);
        float trackMinX = minX + theme.panelPaddingX();
        float trackMaxX = maxX - theme.panelPaddingX();
        float trackTop = rowTop + rowHeight - theme.s(9f);
        float trackBottom = trackTop + theme.s(4.5f);
        float t = clamp01(fraction);
        float fillX = trackMinX + (trackMaxX - trackMinX) * t;

        int trackColor = ColorUtils.withAlpha(theme.mainBackground, 0.85f * alpha);
        int outlineColor = ColorUtils.withAlpha(theme.panelBorder, (enabled ? 0.55f : 0.25f) * alpha);
        int fillColor = ColorUtils.fade(enabled ? theme.primaryPink : theme.disabled, alpha);

        drawList.addRectFilled(trackMinX, trackTop, trackMaxX, trackBottom,
                trackColor, theme.s(2.5f));
        drawList.addRect(trackMinX, trackTop, trackMaxX, trackBottom,
                outlineColor, theme.s(2.5f));
        if (t > 0.001f) {
            drawList.addRectFilled(trackMinX, trackTop, fillX, trackBottom,
                    fillColor, theme.s(2.5f));
        }
        drawList.addCircleFilled(fillX, (trackTop + trackBottom) * 0.5f,
                theme.s(enabled ? 3.4f : 2.6f), fillColor, 8);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

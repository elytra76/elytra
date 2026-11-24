package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.util.ColorUtils;
import com.elytra.client.util.RenderUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.Desktop;
import java.io.File;

/**
 * Configs, built as a panel to match the category columns.
 *
 * The profile row is the highlight row, the three actions write and re-read the
 * real {@code elytra-client.json}, and the status line sits in the panel body
 * under the rows.
 */
public final class ElytraConfigGUI extends ElytraFloatingPanel {

    private static final String POSITION_KEY = "Configs";
    private static final float WIDTH = 170f;
    private static final String PROFILE = "elytra-client.json";

    private static final int ROW_PROFILE = 0;
    private static final int ROW_SAVE = 1;
    private static final int ROW_RELOAD = 2;
    private static final int ROW_FOLDER = 3;
    private static final int ROW_COUNT = 4;

    private static final float STATUS_HEIGHT = 22f;

    private String status = "Ready.";

    public ElytraConfigGUI() {
        super("Configs", POSITION_KEY, WIDTH);
    }

    @Override
    protected float height(ElytraTheme theme) {
        return theme.panelHeaderHeight() + ROW_COUNT * theme.panelRowHeight()
                + theme.s(STATUS_HEIGHT) + theme.panelPaddingBottom();
    }

    @Override
    protected void drawBody(ElytraTheme theme, ImDrawList drawList, float minX, float minY, float maxX,
                            float alpha, boolean interactive, float deltaSeconds) {
        // ---- Profile --------------------------------------------------------
        rowShell(theme, drawList, ROW_PROFILE, minX, maxX, rowTop(theme, minY, ROW_PROFILE),
                alpha, interactive, true, deltaSeconds);
        drawRow(theme, drawList, ROW_PROFILE, minX, maxX, minY, alpha, "Profile", "default", true);

        // ---- Save -----------------------------------------------------------
        int flags = rowShell(theme, drawList, ROW_SAVE, minX, maxX, rowTop(theme, minY, ROW_SAVE),
                alpha, interactive, false, deltaSeconds);
        if ((flags & ElytraPanelChrome.CLICKED) != 0) {
            ElytraClient.config().save();
            status = "Saved " + PROFILE;
        }
        drawRow(theme, drawList, ROW_SAVE, minX, maxX, minY, alpha, "Save", "", false);

        // ---- Reload ---------------------------------------------------------
        flags = rowShell(theme, drawList, ROW_RELOAD, minX, maxX, rowTop(theme, minY, ROW_RELOAD),
                alpha, interactive, false, deltaSeconds);
        if ((flags & ElytraPanelChrome.CLICKED) != 0) {
            ElytraClient.config().load();
            ElytraClient.applyConfig();
            status = "Reloaded " + PROFILE;
        }
        drawRow(theme, drawList, ROW_RELOAD, minX, maxX, minY, alpha, "Reload", "", false);

        // ---- Open folder ----------------------------------------------------
        flags = rowShell(theme, drawList, ROW_FOLDER, minX, maxX, rowTop(theme, minY, ROW_FOLDER),
                alpha, interactive, false, deltaSeconds);
        if ((flags & ElytraPanelChrome.CLICKED) != 0) {
            status = openFolder();
        }
        drawRow(theme, drawList, ROW_FOLDER, minX, maxX, minY, alpha, "Folder", "", true);

        drawStatus(theme, drawList, minX, minY, maxX, alpha);
    }

    private void drawStatus(ElytraTheme theme, ImDrawList drawList, float minX, float minY, float maxX,
                            float alpha) {
        float textX = minX + theme.panelPaddingX();
        float textY = bodyBottom(theme, minY, ROW_COUNT) + theme.s(6f);
        float maxWidth = (maxX - theme.panelPaddingX()) - textX;
        float smallSize = Math.round(ImGuiRenderer.fontSmallSize());
        String text = fit(theme, status, smallSize, maxWidth);

        RenderUtils.text(drawList, ImGuiRenderer.fontSmall(), smallSize, textX, textY,
                ColorUtils.fade(theme.secondaryLavender, alpha), text);
        RenderUtils.text(drawList, ImGuiRenderer.fontSmall(), smallSize, textX,
                textY + smallSize + theme.s(2f),
                ColorUtils.fade(theme.disabled, alpha), "elytra-client.json");
    }

    /** Truncates with an ellipsis so a long path never bleeds out of the panel. */
    private static String fit(ElytraTheme theme, String value, float size, float maxWidth) {
        imgui.ImFont font = ImGuiRenderer.fontSmall();
        if (font == null || value == null) {
            return "";
        }
        if (font.calcTextSizeAX(size, Float.MAX_VALUE, 0f, value) <= maxWidth) {
            return value;
        }
        String trimmed = value;
        while (trimmed.length() > 1
                && font.calcTextSizeAX(size, Float.MAX_VALUE, 0f, trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private void drawRow(ElytraTheme theme, ImDrawList drawList, int index, float minX, float maxX,
                         float minY, float alpha, String label, String value, boolean chevron) {
        float rowMinX = minX + theme.panelPaddingX();
        float rowMaxX = maxX - theme.panelPaddingX();
        float rowTop = rowTop(theme, minY, index);
        boolean highlighted = index == ROW_PROFILE;
        int color = rowTextColor(theme, index, highlighted);

        ElytraPanelChrome.rowLabel(theme, drawList, rowMinX, rowTop, theme.panelRowHeight(),
                alpha, color, label, 0);
        if (!value.isEmpty()) {
            ElytraPanelChrome.rowValue(theme, drawList, rowMaxX, rowTop, theme.panelRowHeight(),
                    alpha, ColorUtils.lerp(color, theme.primaryPink, 0.55f), value, chevron);
        }
    }

    private String openFolder() {
        try {
            File directory = FabricLoader.getInstance().getConfigDir().toFile();
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory);
                return "Opened config folder";
            }
            return directory.getAbsolutePath();
        } catch (Exception exception) {
            return "Could not open folder";
        }
    }
}

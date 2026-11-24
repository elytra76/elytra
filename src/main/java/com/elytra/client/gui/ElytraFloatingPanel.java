package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.util.AnimationUtils;
import com.elytra.client.util.ColorUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

/**
 * Base for the two utility panels (GUI Settings, Configs).
 *
 * These are panels, not windows: they share the category columns' header strip,
 * divider, rounded gradient body, glow and row metrics through
 * {@link ElytraPanelChrome}. Everything a panel needs - drag, clamp, close,
 * position persistence and the right-click theme picker - lives here so both
 * surfaces behave identically.
 */
public abstract class ElytraFloatingPanel {

    protected static final int WINDOW_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoScrollWithMouse
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoNav
            | ImGuiWindowFlags.NoFocusOnAppearing;

    private static final int MAX_ROWS = 32;

    private final String title;
    private final String positionKey;
    private final String windowId;
    private final String themePopupId;
    private final float designWidth;

    private final float[] hover = new float[MAX_ROWS];
    private final ElytraPanelChrome.Drag drag = new ElytraPanelChrome.Drag();

    protected boolean open;
    protected float x;
    protected float y;
    private boolean positioned;

    protected ElytraFloatingPanel(String title, String positionKey, float designWidth) {
        this.title = title;
        this.positionKey = positionKey;
        this.designWidth = designWidth;
        String slug = title.toLowerCase().replace(' ', '_');
        this.windowId = "##elytra_panel_" + slug;
        this.themePopupId = "##elytra_theme_" + slug;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean value) {
        if (open == value) {
            return;
        }
        open = value;
        if (!value) {
            drag.cancel();
        } else {
            resetHover();
        }
    }

    public void toggle() {
        setOpen(!open);
    }

    public void cancelDrag() {
        drag.cancel();
    }

    /** Re-centres the panel the next time it is drawn. */
    public void resetPosition() {
        positioned = false;
        drag.cancel();
    }

    public void resetHover() {
        for (int i = 0; i < hover.length; i++) {
            hover[i] = 0f;
        }
    }

    protected float width(ElytraTheme theme) {
        return theme.s(designWidth);
    }

    /** Per-row height; sliders get extra room for their track. */
    protected float rowHeight(ElytraTheme theme, int index) {
        return theme.panelRowHeight();
    }

    /** Total panel height in screen pixels. */
    protected abstract float height(ElytraTheme theme);

    /** Draws the rows clipped to the panel body. */
    protected abstract void drawBody(ElytraTheme theme, ImDrawList drawList, float minX, float minY,
                                     float maxX, float alpha, boolean interactive, float deltaSeconds);

    public final void render(ElytraTheme theme, ImDrawList backgroundDrawList, float alpha,
                             float screenWidth, float screenHeight, boolean interactive) {
        if (!open || alpha <= 0.05f) {
            return;
        }

        float panelWidth = width(theme);
        float panelHeight = height(theme);

        if (!positioned) {
            float[] saved = ElytraClient.config().panelPosition(positionKey);
            if (saved != null) {
                x = saved[0];
                y = saved[1];
            } else {
                x = (screenWidth - panelWidth) * 0.5f;
                y = (screenHeight - panelHeight) * 0.5f;
            }
            positioned = true;
        }
        clamp(screenWidth, screenHeight, panelWidth, panelHeight);

        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Always);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0f, 0f, 0f, 0f);

        boolean visible = ImGui.begin(windowId, WINDOW_FLAGS);
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(2);

        if (!visible) {
            ImGui.end();
            return;
        }

        float minX = x;
        float minY = y;
        float maxX = x + panelWidth;
        float maxY = y + panelHeight;
        ImDrawList drawList = ImGui.getWindowDrawList();

        ElytraPanelChrome.surface(theme, backgroundDrawList, drawList, minX, minY, maxX, maxY, alpha);
        ElytraPanelChrome.headerTitle(theme, drawList, minX, minY, maxX, alpha, title);

        if (interactive) {
            if (ElytraPanelChrome.closeButton(theme, "##close_" + positionKey, maxX - theme.s(20f),
                    minY + theme.s(9f))) {
                setOpen(false);
                ImGui.end();
                return;
            }
            handleDrag(theme, minX, minY, maxX, screenWidth, screenHeight, panelWidth, panelHeight);
        }

        drawBody(theme, drawList, minX, minY, maxX, alpha, interactive, ImGuiRenderer.deltaSeconds());

        if (interactive && ImGui.beginPopupContextWindow()) {
            drawThemeMenu(theme);
            ImGui.endPopup();
        }

        ImGui.end();
    }

    private void handleDrag(ElytraTheme theme, float minX, float minY, float maxX,
                            float screenWidth, float screenHeight, float panelWidth, float panelHeight) {
        if (!drag.active()) {
            if (!ElytraPanelChrome.Drag.overHeader(theme, minX, minY, maxX, theme.s(28f))
                    || !ImGui.isMouseClicked(0)) {
                return;
            }
            drag.begin(x, y);
        }
        if (!drag.tick()) {
            return;
        }
        x = drag.x();
        y = drag.y();
        clamp(screenWidth, screenHeight, panelWidth, panelHeight);
        ElytraClient.config().setPanelPosition(positionKey, x, y);
        ElytraClient.requestConfigSave();
    }

    private void clamp(float screenWidth, float screenHeight, float panelWidth, float panelHeight) {
        x = Math.max(0f, Math.min(Math.max(0f, screenWidth - panelWidth), x));
        y = Math.max(0f, Math.min(Math.max(0f, screenHeight - panelHeight), y));
    }

    /** Right-click menu: every theme, current one checked. */
    private void drawThemeMenu(ElytraTheme theme) {
        String[] names = theme.themeNames();
        int current = ElytraClient.config().themeIndex();
        for (int index = 0; index < names.length; index++) {
            if (ImGui.menuItem(names[index], null, index == current, true)) {
                ElytraClient.config().setThemeIndex(index);
                theme.applyTheme(index);
                ElytraClient.requestConfigSave();
            }
        }
    }

    /**
     * Draws one row's hit area and backdrop in the reference style.
     *
     * @return bitmask of {@link ElytraPanelChrome#HOVER}, {@link ElytraPanelChrome#ACTIVE}
     *         and {@link ElytraPanelChrome#CLICKED}
     */
    protected final int rowShell(ElytraTheme theme, ImDrawList drawList, int index, float minX, float maxX,
                                 float rowTop, float alpha, boolean interactive, boolean highlighted,
                                 float deltaSeconds) {
        float rowHeight = rowHeight(theme, index);
        float rowMinX = minX + theme.panelPaddingX();
        float rowMaxX = maxX - theme.panelPaddingX();
        float rowWidth = rowMaxX - rowMinX;

        int flags = 0;
        if (interactive) {
            flags = ElytraPanelChrome.rowInteraction("##row_" + positionKey + "_" + index,
                    rowMinX, rowTop, rowWidth, rowHeight);
        }

        boolean hovered = (flags & ElytraPanelChrome.HOVER) != 0;
        hover[index] = AnimationUtils.approach(hover[index], hovered ? 1f : 0f, 18f, deltaSeconds);
        ElytraPanelChrome.rowBackground(theme, drawList, rowMinX, rowTop, rowMaxX, rowHeight,
                alpha, hover[index], highlighted);
        return flags;
    }

    /** Row text colour for the current hover state. */
    protected final int rowTextColor(ElytraTheme theme, int index, boolean highlighted) {
        int base = highlighted ? theme.text : theme.textSecondary;
        return ColorUtils.lerp(base, theme.primaryPink, hover[index]);
    }

    protected final float rowTop(ElytraTheme theme, float minY, int index) {
        float top = minY + theme.panelHeaderHeight();
        for (int i = 0; i < index; i++) {
            top += rowHeight(theme, i);
        }
        return top;
    }

    protected final float bodyBottom(ElytraTheme theme, float minY, int rows) {
        return rowTop(theme, minY, rows);
    }

    protected final String title() {
        return title;
    }
}

package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import com.elytra.client.gui.render.GlowRenderer;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.util.AnimationUtils;
import com.elytra.client.util.ColorUtils;
import com.elytra.client.util.RenderUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * One floating category column.
 *
 * Panels own their position, drag state and hover easing. Rows are display only:
 * {@link Row} carries a label and an "active" tint, nothing else. No module object
 * is ever created from a row, so a placeholder cannot reach game code.
 */
public final class ElytraPanel {

    /** A decorative line of text inside a panel. Never wired to gameplay. */
    public static final class Row {
        private final String label;
        private final int indent;
        private final boolean chevron;
        private final boolean highlight;
        private final Runnable onClick;
        private final java.util.function.BooleanSupplier glowWhen;

        public Row(String label) {
            this(label, 0, false, false, null, null);
        }

        public Row(String label, int indent) {
            this(label, indent, false, false, null, null);
        }

        public Row(String label, int indent, boolean chevron) {
            this(label, indent, chevron, false, null, null);
        }

        public Row(String label, int indent, boolean chevron, boolean highlight) {
            this(label, indent, chevron, highlight, null, null);
        }

        public Row(String label, int indent, boolean chevron, boolean highlight, Runnable onClick) {
            this(label, indent, chevron, highlight, onClick, null);
        }

        public Row(String label, int indent, boolean chevron, boolean highlight, Runnable onClick,
                   java.util.function.BooleanSupplier glowWhen) {
            this.label = label;
            this.indent = indent;
            this.chevron = chevron;
            this.highlight = highlight;
            this.onClick = onClick;
            this.glowWhen = glowWhen;
        }

        public String label() { return label; }
        public int indent() { return indent; }
        public boolean hasChevron() { return chevron; }
        public boolean highlighted() { return highlight; }
        public Runnable onClick() { return onClick; }
        public java.util.function.BooleanSupplier glowWhen() { return glowWhen; }
    }

    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoScrollWithMouse
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoNav
            | ImGuiWindowFlags.NoFocusOnAppearing;

    private final String title;
    private final int column;

    private final List<Row> rows = new ArrayList<>();
    private final float[] hover = new float[32];

    private float x;
    private float y;
    private boolean positioned;

    private boolean dragging;
    private float dragStartMouseX;
    private float dragStartMouseY;
    private float dragStartPanelX;
    private float dragStartPanelY;

    public ElytraPanel(String title, int column) {
        this.title = title;
        this.column = column;
    }

    public String title() {
        return title;
    }

    public int column() {
        return column;
    }

    public ElytraPanel addRow(Row row) {
        if (rows.size() < hover.length) {
            rows.add(row);
        }
        return this;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        this.positioned = true;
    }

    public void setPositioned(boolean value) {
        this.positioned = value;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public boolean isPositioned() {
        return positioned;
    }

    /** Default column position derived from the reference layout. */
    public void applyDefaultPosition(ElytraTheme theme) {
        this.x = theme.panelLeft() + column * (theme.panelWidth() + theme.panelGap());
        this.y = theme.panelTop();
        this.positioned = true;
    }

    public float height(ElytraTheme theme) {
        float h = theme.panelHeaderHeight()
                + rows.size() * theme.panelRowHeight()
                + theme.panelPaddingBottom();
        return Math.max(h, theme.panelHeaderHeight() + theme.panelPaddingBottom() + theme.s(8f));
    }

    public float width(ElytraTheme theme) {
        return theme.panelWidth();
    }

    public boolean isDragging() {
        return dragging;
    }

    /** Clamps the panel inside the viewport so it can never be dragged off screen. */
    public void clampTo(float screenWidth, float screenHeight, ElytraTheme theme) {
        float maxX = Math.max(0f, screenWidth - width(theme));
        float maxY = Math.max(0f, screenHeight - height(theme));
        x = Math.max(0f, Math.min(maxX, x));
        y = Math.max(0f, Math.min(maxY, y));
    }

    /**
     * Draws the panel and processes its interactions.
     *
     * @return true when the panel was dragged this frame, so the caller can mark the
     *         layout dirty without writing the config every frame
     */
    public boolean render(ElytraTheme theme, ImDrawList backgroundDrawList, float deltaSeconds,
                          float alpha, boolean interactive, float screenWidth, float screenHeight) {
        float panelWidth = width(theme);
        float panelHeight = height(theme);

        if (alpha <= 0.002f) {
            return false;
        }

        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Always);

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0f, 0f, 0f, 0f);

        String windowName = "##elytra_panel_" + title.replace(" ", "_");
        boolean open = ImGui.begin(windowName, WINDOW_FLAGS);
        ImGui.popStyleColor();
        ImGui.popStyleVar(2);

        if (!open) {
            ImGui.end();
            return false;
        }

        // Panels are positioned with setNextWindowPos every frame; the window never moves itself.
        ImDrawList drawList = ImGui.getWindowDrawList();
        float minX = x;
        float minY = y;
        float maxX = x + panelWidth;
        float maxY = y + panelHeight;

        ElytraPanelChrome.surface(theme, backgroundDrawList, drawList, minX, minY, maxX, maxY, alpha);
        ElytraPanelChrome.headerTitle(theme, drawList, minX, minY, maxX, alpha, title);

        boolean moved = drawHeader(theme, minX, minY, maxX, interactive);
        drawRows(theme, drawList, minX, minY, maxX, alpha, interactive, deltaSeconds);

        if (moved) {
            clampTo(screenWidth, screenHeight, theme);
        }

        ImGui.end();
        return moved;
    }

    private boolean drawHeader(ElytraTheme theme, float minX, float minY, float maxX, boolean interactive) {
        float headerHeight = theme.panelHeaderHeight();

        if (!interactive) {
            return false;
        }

        // Drag by the header strip only, so row clicks never move the panel.
        boolean headerHovered = ImGui.isMouseHoveringRect(minX, minY, maxX, minY + headerHeight);

        if (headerHovered && !dragging && ImGui.isMouseClicked(0)) {
            dragging = true;
            dragStartMouseX = ImGui.getMousePosX();
            dragStartMouseY = ImGui.getMousePosY();
            dragStartPanelX = x;
            dragStartPanelY = y;
        }

        if (!dragging) {
            return false;
        }
        if (!ImGui.isMouseDown(0)) {
            dragging = false;
            ElytraClient.requestConfigSave();
            return false;
        }

        x = dragStartPanelX + (ImGui.getMousePosX() - dragStartMouseX);
        y = dragStartPanelY + (ImGui.getMousePosY() - dragStartMouseY);
        return true;
    }

    private void drawRows(ElytraTheme theme, ImDrawList drawList, float minX, float minY, float maxX,
                          float alpha, boolean interactive, float deltaSeconds) {
        float rowHeight = theme.panelRowHeight();
        float rowWidth = (maxX - minX) - theme.panelPaddingX() * 2f;
        float top = minY + theme.panelHeaderHeight();

        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            float rowTop = top + index * rowHeight;

            boolean hovered = false;
            if (interactive) {
                ImGui.setCursorScreenPos(minX + theme.panelPaddingX(), rowTop);
                ImGui.invisibleButton("##row_" + title.replace(" ", "_") + "_" + index, rowWidth, rowHeight);
                hovered = ImGui.isItemHovered();
                if (row.onClick() != null && ImGui.isItemClicked()) {
                    row.onClick().run();
                }
            }

            hover[index] = AnimationUtils.approach(hover[index], hovered ? 1f : 0f, 18f, deltaSeconds);
            float eased = hover[index];

            float rowMinX = minX + theme.panelPaddingX();
            float rowMaxX = rowMinX + rowWidth;
            ElytraPanelChrome.rowBackground(theme, drawList, rowMinX, rowTop, rowMaxX, rowHeight, alpha, eased,
                    row.highlighted());

            if (row.glowWhen() != null && row.glowWhen().getAsBoolean()) {
                float inset = theme.s(1f);
                drawList.addRectFilled(rowMinX, rowTop + inset, rowMaxX, rowTop + rowHeight - inset,
                        ColorUtils.withAlpha(theme.primaryPink, 0.22f * alpha), theme.rowRadius());
                for (int g = 2; g >= 1; g--) {
                    float exp = g * 1.6f * theme.uiScale();
                    float a = 0.05f * (1f - (g - 1) / 2f) * alpha;
                    drawList.addRect(rowMinX - exp, rowTop + inset - exp, rowMaxX + exp, rowTop + rowHeight - inset + exp,
                            ColorUtils.withAlpha(theme.primaryPink, a), theme.rowRadius() + exp, 0, 1.5f);
                }
            }

            int baseText = row.hasChevron() || row.highlighted() ? theme.text : theme.textSecondary;
            int rowText = ColorUtils.lerp(baseText, theme.primaryPink, eased);
            ElytraPanelChrome.rowLabel(theme, drawList, rowMinX, rowTop, rowHeight, alpha,
                    rowText, row.label(), row.indent());

            if (row.hasChevron()) {
                float chevronX = rowMaxX - theme.s(9f);
                float chevronY = rowTop + rowHeight * 0.5f;
                int chevronColor = ColorUtils.lerp(
                        ColorUtils.withAlpha(theme.secondaryLavender, 0.35f),
                        theme.primaryPink, eased);
                RenderUtils.chevron(drawList, chevronX, chevronY, theme.s(3.2f),
                        ColorUtils.withAlpha(chevronColor, alpha));
            }
        }
    }

    /** Clears hover easing so reopening the GUI never flashes a stale highlight. */
    public void resetHover() {
        for (int index = 0; index < hover.length; index++) {
            hover[index] = 0f;
        }
    }

    public void cancelDrag() {
        dragging = false;
    }
}

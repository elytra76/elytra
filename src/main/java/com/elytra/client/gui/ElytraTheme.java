package com.elytra.client.gui;

import com.elytra.client.util.ColorUtils;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * Single source of truth for every colour and metric the GUI uses.
 *
 * All sizes are stored in the reference design space (a 1091 x 493 layout) and
 * multiplied by {@link #uiScale()} once, here, so no draw call ever does its own
 * arithmetic. Change a number in this file and the whole client follows.
 */
public final class ElytraTheme {

    // ---- Palette (legacy constants removed; use PRESETS) --------------------

    /** Reference layout width the metrics below were authored against. */
    public static final float DESIGN_WIDTH = 1091f;
    public static final float DESIGN_HEIGHT = 493f;

    // ---- Metrics in reference units -----------------------------------------
    public static final float PANEL_WIDTH = 140f;
    public static final float PANEL_GAP = 4f;
    public static final float PANEL_TOP = 49f;
    public static final float PANEL_LEFT = 6f;
    public static final float PANEL_HEADER_HEIGHT = 30f;
    public static final float PANEL_ROW_HEIGHT = 19.3f;
    public static final float PANEL_PADDING_X = 9f;
    public static final float PANEL_PADDING_BOTTOM = 4f;
    public static final float PANEL_RADIUS = 7f;
    public static final float ROW_RADIUS = 4f;

    public static final float HEADER_LEFT_X = 6f;
    public static final float HEADER_LEFT_Y = 4f;
    public static final float HEADER_LEFT_W = 170f;
    public static final float HEADER_LEFT_H = 30f;

    public static final float HEADER_CENTER_X = 479.5f;
    public static final float HEADER_CENTER_Y = 15f;
    public static final float HEADER_CENTER_W = 132f;
    public static final float HEADER_CENTER_H = 19f;

    public static final float HEADER_RIGHT_X = 935f;
    public static final float HEADER_RIGHT_Y = 5f;
    public static final float HEADER_RIGHT_W = 150f;
    public static final float HEADER_RIGHT_H = 27f;

    public static final float FONT_BODY = 12.5f;
    public static final float FONT_TITLE = 13.5f;
    public static final float FONT_SMALL = 10.5f;
    public static final float FONT_TINY = 9.5f;

    // ---- Presets -------------------------------------------------------------
    public static final class Preset {
        public final String name;
        public final String mainBg;
        public final String panelBg;
        public final String panelBgTop;
        public final String border;
        public final String primary;
        public final String secondary;
        public final String text;
        public final String textSecondary;
        public final String hoverBg;
        public final String disabled;

        public Preset(String name, String mainBg, String panelBg, String panelBgTop, String border,
                      String primary, String secondary, String text, String textSecondary,
                      String hoverBg, String disabled) {
            this.name = name;
            this.mainBg = mainBg;
            this.panelBg = panelBg;
            this.panelBgTop = panelBgTop;
            this.border = border;
            this.primary = primary;
            this.secondary = secondary;
            this.text = text;
            this.textSecondary = textSecondary;
            this.hoverBg = hoverBg;
            this.disabled = disabled;
        }
    }

    public static final Preset[] PRESETS = {
            new Preset("Elytra Pink", "#191820", "#241E2E", "#1E1926", "#B77CA9",
                    "#E69BCB", "#BBA0C5", "#E8DCE9", "#D3C5D6", "#493548", "#827583"),
            new Preset("Midnight Blue", "#141826", "#1C2236", "#161B2C", "#7C8FB7",
                    "#8FB3E8", "#A7B8D8", "#E0E6F2", "#B8C2D9", "#2A3350", "#6B7490"),
            new Preset("Crimson", "#1A1416", "#2A1C20", "#22161A", "#B77C8A",
                    "#E86B8F", "#C9A0A8", "#F0DCE0", "#D6BFC4", "#4A2A32", "#83757A"),
            new Preset("Emerald", "#141A16", "#1C2A22", "#16221C", "#7CB79A",
                    "#6BE8A0", "#A0C9B0", "#DCF0E4", "#BFD6C8", "#2A4A38", "#75837A"),
            new Preset("Amber", "#1A1712", "#2A2218", "#221C14", "#B7A07C",
                    "#E8B86B", "#C9B8A0", "#F0E6D4", "#D6CBBF", "#4A3A28", "#837E75"),
            new Preset("Frost", "#141A20", "#1C2630", "#161E28", "#7CB0C5",
                    "#8FE0F0", "#A0C8D8", "#E0F0F6", "#BFCFD8", "#2A3E4A", "#758088"),
            new Preset("Violet", "#16141E", "#221C30", "#1A1628", "#9A7CB7",
                    "#B86BE8", "#B8A0C9", "#E8DCF0", "#CFC2D6", "#3A2A4A", "#7E7583"),
            new Preset("Rose Gold", "#1A1618", "#2A1E22", "#22181C", "#C9A0A8",
                    "#E8A0B8", "#D8B8C0", "#F0DCE2", "#D6C2C8", "#4A2E36", "#837A7E"),
            new Preset("Neon Cyber", "#10121A", "#161A26", "#121620", "#7C7CB7",
                    "#00E5FF", "#FF00E5", "#E0FFFF", "#C0D8E8", "#2A2A4A", "#757583"),
            new Preset("Slate", "#161618", "#222226", "#1A1A1E", "#8A8A92",
                    "#C0C4CC", "#A8A8B0", "#E8E8EC", "#C8C8CE", "#3A3A40", "#7E7E84"),
            new Preset("Rainbow", "#16141E", "#221C28", "#1A1620", "#B77CA9",
                    "#FF6EC7", "#FFB347", "#FFF066", "#7CFF6B", "#5BC8FF", "#B87CFF")
    };

    // ---- Colours (mutable, follow the active preset) --------------------------
    public int mainBackground;
    public int panelBackground;
    public int panelBackgroundTop;
    public int panelBorder;
    public int primaryPink;
    public int secondaryLavender;
    public int text;
    public int textSecondary;
    public int hoverBackground;
    public int disabled;
    public int rowHighlight;
    public int glow;

    private int themeIndex = 0;
    private float rainbowHue = 0f;

    private float uiScale = 1f;

    /** Advances animated themes (currently: Rainbow). Call once per frame. */
    public void tick(float deltaSeconds) {
        if (!PRESETS[themeIndex].name.equals("Rainbow")) {
            return;
        }
        rainbowHue = (rainbowHue + deltaSeconds * 0.18f) % 1f;
        if (rainbowHue < 0f) {
            rainbowHue += 1f;
        }
        int primary = hslToRgb(rainbowHue, 1f, 0.62f);
        int secondary = hslToRgb((rainbowHue + 0.12f) % 1f, 0.9f, 0.72f);
        int border = hslToRgb((rainbowHue + 0.30f) % 1f, 0.9f, 0.70f);
        int hover = hslToRgb((rainbowHue + 0.55f) % 1f, 0.7f, 0.22f);
        primaryPink = primary;
        secondaryLavender = secondary;
        panelBorder = ColorUtils.withAlpha(border, 0.72f);
        hoverBackground = ColorUtils.withAlpha(hover, 0.85f);
        rowHighlight = ColorUtils.withAlpha(hover, 0.55f);
        glow = primary;
        // panel backgrounds take a faint tint of the current hue
        panelBackground = ColorUtils.withAlpha(hslToRgb(rainbowHue, 0.6f, 0.12f), 0.87f);
        panelBackgroundTop = ColorUtils.withAlpha(hslToRgb(rainbowHue, 0.7f, 0.10f), 0.90f);
    }

    private static int hslToRgb(float h, float s, float l) {
        float r, g, b;
        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
            float p = 2f * l - q;
            r = hueToRgb(p, q, h + 1f / 3f);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1f / 3f);
        }
        int ri = Math.round(r * 255f);
        int gi = Math.round(g * 255f);
        int bi = Math.round(b * 255f);
        return (0xFF << 24) | (ri << 16) | (gi << 8) | bi;
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

    private ElytraTheme() {
        applyPreset(0);
    }

    private static final ElytraTheme INSTANCE = new ElytraTheme();

    public static ElytraTheme get() {
        return INSTANCE;
    }

    public int themeIndex() {
        return themeIndex;
    }

    public String themeName() {
        return PRESETS[themeIndex].name;
    }

    public int themeCount() {
        return PRESETS.length;
    }

    public String[] themeNames() {
        String[] names = new String[PRESETS.length];
        for (int i = 0; i < PRESETS.length; i++) {
            names[i] = PRESETS[i].name;
        }
        return names;
    }

    /** Applies the preset at the given index. Out of range values are clamped. */
    public void applyTheme(int index) {
        int clamped = Math.max(0, Math.min(PRESETS.length - 1, index));
        if (clamped == themeIndex && mainBackground != 0) {
            // still recompute to be safe only if changed
        }
        themeIndex = clamped;
        applyPreset(clamped);
    }

    private void applyPreset(int index) {
        Preset preset = PRESETS[index];
        mainBackground = ColorUtils.hex(preset.mainBg, 0.94f);
        panelBackground = ColorUtils.hex(preset.panelBg, 0.87f);
        panelBackgroundTop = ColorUtils.hex(preset.panelBgTop, 0.90f);
        panelBorder = ColorUtils.hex(preset.border, 0.72f);
        primaryPink = ColorUtils.hex(preset.primary);
        secondaryLavender = ColorUtils.hex(preset.secondary);
        text = ColorUtils.hex(preset.text);
        textSecondary = ColorUtils.hex(preset.textSecondary);
        hoverBackground = ColorUtils.hex(preset.hoverBg, 0.85f);
        disabled = ColorUtils.hex(preset.disabled);
        rowHighlight = ColorUtils.hex(preset.hoverBg, 0.55f);
        glow = ColorUtils.hex(preset.primary);
    }

    // ---- Scale ---------------------------------------------------------------
    public float uiScale() {
        return uiScale;
    }

    /** Sets the layout scale. Returns true when the value actually changed. */
    public boolean setScale(float scale) {
        float clamped = Math.max(0.55f, Math.min(3.2f, scale));
        if (Math.abs(clamped - uiScale) < 0.0005f) {
            return false;
        }
        uiScale = clamped;
        return true;
    }

    public float s(float value) {
        return value * uiScale;
    }

    public float panelWidth() {
        return s(PANEL_WIDTH);
    }

    public float panelGap() {
        return s(PANEL_GAP);
    }

    public float panelTop() {
        return s(PANEL_TOP);
    }

    public float panelLeft() {
        return s(PANEL_LEFT);
    }

    public float panelHeaderHeight() {
        return s(PANEL_HEADER_HEIGHT);
    }

    public float panelRowHeight() {
        return s(PANEL_ROW_HEIGHT);
    }

    public float panelPaddingX() {
        return s(PANEL_PADDING_X);
    }

    public float panelPaddingBottom() {
        return s(PANEL_PADDING_BOTTOM);
    }

    public float panelRadius() {
        return s(PANEL_RADIUS);
    }

    public float rowRadius() {
        return s(ROW_RADIUS);
    }

    public float headerLeftX() {
        return s(HEADER_LEFT_X);
    }

    public float headerLeftY() {
        return s(HEADER_LEFT_Y);
    }

    public float headerLeftW() {
        return s(HEADER_LEFT_W);
    }

    public float headerLeftH() {
        return s(HEADER_LEFT_H);
    }

    public float headerCenterX() {
        return s(HEADER_CENTER_X);
    }

    public float headerCenterY() {
        return s(HEADER_CENTER_Y);
    }

    public float headerCenterW() {
        return s(HEADER_CENTER_W);
    }

    public float headerCenterH() {
        return s(HEADER_CENTER_H);
    }

    public float headerRightX() {
        return s(HEADER_RIGHT_X);
    }

    public float headerRightY() {
        return s(HEADER_RIGHT_Y);
    }

    public float headerRightW() {
        return s(HEADER_RIGHT_W);
    }

    public float headerRightH() {
        return s(HEADER_RIGHT_H);
    }

    public float fontBody() {
        return s(FONT_BODY);
    }

    public float fontTitle() {
        return s(FONT_TITLE);
    }

    public float fontSmall() {
        return s(FONT_SMALL);
    }

    public float fontTiny() {
        return s(FONT_TINY);
    }

    /**
     * Applies the Elytra look to the live ImGui style. Called once per frame while
     * the GUI is open so a scale change takes effect immediately.
     */
    public void applyStyle() {
        ImGuiStyle style = ImGui.getStyle();
        style.setWindowRounding(panelRadius());
        style.setChildRounding(s(6f));
        style.setFrameRounding(s(5f));
        style.setPopupRounding(s(6f));
        style.setScrollbarRounding(s(4f));
        style.setGrabRounding(s(4f));
        style.setWindowBorderSize(1f);
        style.setFrameBorderSize(0f);
        style.setWindowPadding(s(8f), s(7f));
        style.setFramePadding(s(7f), s(4f));
        style.setItemSpacing(s(6f), s(5f));
        style.setScrollbarSize(s(7f));
        style.setWindowTitleAlign(0f, 0.5f);
        style.setWindowMinSize(s(160f), s(60f));

        applyColor(style, ImGuiCol.WindowBg, mainBackground);
        applyColor(style, ImGuiCol.ChildBg, ColorUtils.withAlpha(panelBackground, 0.45f));
        applyColor(style, ImGuiCol.PopupBg, ColorUtils.withAlpha(mainBackground, 0.97f));
        applyColor(style, ImGuiCol.Border, panelBorder);
        style.setColor(ImGuiCol.BorderShadow, 0f, 0f, 0f, 0f);
        applyColor(style, ImGuiCol.Text, text);
        applyColor(style, ImGuiCol.TextDisabled, disabled);
        applyColor(style, ImGuiCol.FrameBg, ColorUtils.hex("#231E2C", 0.85f));
        applyColor(style, ImGuiCol.FrameBgHovered, ColorUtils.withAlpha(hoverBackground, 0.75f));
        applyColor(style, ImGuiCol.FrameBgActive, ColorUtils.withAlpha(hoverBackground, 0.95f));
        applyColor(style, ImGuiCol.TitleBg, ColorUtils.hex("#1E1A26", 0.96f));
        applyColor(style, ImGuiCol.TitleBgActive, ColorUtils.hex("#241E2D", 0.96f));
        applyColor(style, ImGuiCol.TitleBgCollapsed, ColorUtils.hex("#1E1A26", 0.9f));
        applyColor(style, ImGuiCol.MenuBarBg, ColorUtils.hex("#1E1A26", 0.9f));
        applyColor(style, ImGuiCol.ScrollbarBg, ColorUtils.hex("#1A1620", 0.55f));
        applyColor(style, ImGuiCol.ScrollbarGrab, ColorUtils.withAlpha(secondaryLavender, 0.35f));
        applyColor(style, ImGuiCol.ScrollbarGrabHovered, ColorUtils.withAlpha(secondaryLavender, 0.55f));
        applyColor(style, ImGuiCol.ScrollbarGrabActive, ColorUtils.withAlpha(primaryPink, 0.65f));
        applyColor(style, ImGuiCol.CheckMark, primaryPink);
        applyColor(style, ImGuiCol.SliderGrab, ColorUtils.withAlpha(primaryPink, 0.75f));
        applyColor(style, ImGuiCol.SliderGrabActive, primaryPink);
        applyColor(style, ImGuiCol.Button, ColorUtils.withAlpha(hoverBackground, 0.55f));
        applyColor(style, ImGuiCol.ButtonHovered, ColorUtils.withAlpha(hoverBackground, 0.85f));
        applyColor(style, ImGuiCol.ButtonActive, ColorUtils.withAlpha(primaryPink, 0.45f));
        applyColor(style, ImGuiCol.Header, rowHighlight);
        applyColor(style, ImGuiCol.HeaderHovered, hoverBackground);
        applyColor(style, ImGuiCol.HeaderActive, ColorUtils.withAlpha(primaryPink, 0.35f));
        applyColor(style, ImGuiCol.Separator, ColorUtils.withAlpha(panelBorder, 0.35f));
        applyColor(style, ImGuiCol.SeparatorHovered, ColorUtils.withAlpha(primaryPink, 0.55f));
        applyColor(style, ImGuiCol.SeparatorActive, primaryPink);
        applyColor(style, ImGuiCol.ResizeGrip, ColorUtils.withAlpha(panelBorder, 0.25f));
        applyColor(style, ImGuiCol.ResizeGripHovered, ColorUtils.withAlpha(primaryPink, 0.45f));
        applyColor(style, ImGuiCol.ResizeGripActive, primaryPink);
        applyColor(style, ImGuiCol.Tab, ColorUtils.hex("#221D2A", 0.85f));
        applyColor(style, ImGuiCol.TabHovered, ColorUtils.withAlpha(hoverBackground, 0.85f));
        applyColor(style, ImGuiCol.TabActive, ColorUtils.withAlpha(primaryPink, 0.28f));
        applyColor(style, ImGuiCol.TabUnfocused, ColorUtils.hex("#1E1A26", 0.7f));
        applyColor(style, ImGuiCol.TabUnfocusedActive, ColorUtils.withAlpha(primaryPink, 0.18f));
        applyColor(style, ImGuiCol.TextSelectedBg, ColorUtils.withAlpha(primaryPink, 0.30f));
        applyColor(style, ImGuiCol.NavHighlight, ColorUtils.withAlpha(primaryPink, 0.45f));
    }

    /**
     * ImGui's style setter takes components, not a packed integer, so every colour in
     * this file goes through here instead of duplicating the unpack arithmetic.
     */
    private static void applyColor(ImGuiStyle style, int slot, int packed) {
        style.setColor(slot,
                (packed & 0xFF) / 255f,
                ((packed >>> 8) & 0xFF) / 255f,
                ((packed >>> 16) & 0xFF) / 255f,
                ((packed >>> 24) & 0xFF) / 255f);
    }

    /** Pushes the shared window padding pair so a panel body lines up with its header. */
    public void pushPanelPadding() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, panelPaddingX(), panelPaddingX());
    }
}

package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import com.elytra.client.ElytraConfig;
import com.elytra.client.gui.ElytraTheme;
import com.elytra.client.gui.render.ImGuiInputHandler;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.gui.render.EffectRenderer;
import com.elytra.client.util.AnimationUtils;
import com.elytra.client.util.ColorUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * The click GUI: one frame of ImGui, built from the header strip and the five
 * category panels, plus the two utility windows.
 *
 * This class owns the whole ImGui frame lifecycle so there is exactly one place
 * that can call begin/end and exactly one place that owns the input handler.
 */
public final class ElytraClickGUI {

    private static final int ROOT_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoScrollWithMouse
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoBackground
            | ImGuiWindowFlags.NoNav
            | ImGuiWindowFlags.NoFocusOnAppearing
            | ImGuiWindowFlags.NoBringToFrontOnFocus;

    private final List<ElytraPanel> panels = new ArrayList<>();
    private final ElytraHeader header = new ElytraHeader();
    private final ElytraSettingsGUI settingsGUI = new ElytraSettingsGUI();
    private final ElytraConfigGUI configGUI = new ElytraConfigGUI();
    private final ImGuiInputHandler input = new ImGuiInputHandler();
    private final EffectRenderer effect = new EffectRenderer();

    private boolean open;
    private boolean layoutInitialized;
    private float appear;
    private long lastFrameNanos;

    public boolean isOpen() {
        return open;
    }

    public ElytraSettingsGUI settings() {
        return settingsGUI;
    }

    public ElytraConfigGUI configs() {
        return configGUI;
    }

    public ImGuiInputHandler input() {
        return input;
    }

    /** Builds the panels once; positions come from the saved config when present. */
    public void build(ElytraConfig config) {
        panels.clear();
        panels.add(new ElytraPanel("Combat", 0));
        panels.add(new ElytraPanel("Render", 1));
        panels.add(new ElytraPanel("Visuals", 2));
        panels.add(new ElytraPanel("Misc", 3));
        panels.add(new ElytraPanel("Base Finding", 4));
        panels.add(new ElytraPanel("Ac Off", 5));

        restorePositions(config);
        layoutInitialized = false;
    }

    private void restorePositions(ElytraConfig config) {
        for (ElytraPanel panel : panels) {
            float[] saved = config.panelPosition(panel.title());
            if (saved != null) {
                panel.setPosition(saved[0], saved[1]);
            }
        }
    }

    /** Re-applies the reference layout for every panel. */
    public void resetLayout() {
        for (ElytraPanel panel : panels) {
            panel.setPositioned(false);
        }
        layoutInitialized = false;
        settingsGUI.resetPosition();
        configGUI.resetPosition();
        ElytraClient.config().clearPanelPositions();
        ElytraClient.requestConfigSave();
    }

    public void setOpen(boolean value) {
        if (open == value) {
            return;
        }
        open = value;
        if (value) {
            for (ElytraPanel panel : panels) {
                panel.resetHover();
            }
            lastFrameNanos = System.nanoTime();
        } else {
            input.reset();
            settingsGUI.setOpen(false);
            settingsGUI.cancelDrag();
            configGUI.setOpen(false);
            for (ElytraPanel panel : panels) {
                panel.cancelDrag();
            }
        }

    }

    /**
     * Renders one complete ImGui frame into the game's back buffer.
     *
     * @return false when nothing was drawn, so the caller can skip its own bookkeeping
     */
    public boolean renderFrame(int framebufferWidth, int framebufferHeight,
                               int windowWidth, long windowHandle) {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f / 60f
                : Math.min(0.1f, Math.max(0.001f, (now - lastFrameNanos) / 1_000_000_000f));
        lastFrameNanos = now;

        appear = AnimationUtils.approach(appear, open ? 1f : 0f, 15f, delta);
        if (!open && appear < 0.004f) {
            appear = 0f;
            return false;
        }

        ElytraTheme theme = ElytraTheme.get();
        applyScale(theme, framebufferWidth, framebufferHeight);

        if (!layoutInitialized) {
            for (ElytraPanel panel : panels) {
                if (!panel.isPositioned()) {
                    panel.applyDefaultPosition(theme);
                }
            }
            layoutInitialized = true;
        }

        ImGuiRenderer.ensureFonts(theme.uiScale());
        input.update(windowHandle, framebufferWidth, windowWidth);
        ImGuiRenderer.beginFrame(framebufferWidth, framebufferHeight);

        ElytraTheme.get().tick(delta);

        theme.applyStyle();
        if (ImGuiRenderer.fontBody() != null) {
            ImGui.pushFont(ImGuiRenderer.fontBody());
        }

        ImDrawList backgroundDrawList = ImGui.getBackgroundDrawList();
        boolean interactive = open && appear > 0.85f;
        if (ElytraClient.config().rainEnabled()
                && ElytraClient.config().effectMode() != EffectRenderer.NONE) {
            effect.setDensity(ElytraClient.config().rainDensity());
            effect.render(backgroundDrawList, ElytraClient.config().effectMode(),
                    delta, appear, framebufferWidth, framebufferHeight, theme);
        }

        for (ElytraPanel panel : panels) {
            panel.clampTo(framebufferWidth, framebufferHeight, theme);
        }

        int headerAction = renderRoot(theme, backgroundDrawList, interactive, framebufferWidth, framebufferHeight);

        for (ElytraPanel panel : panels) {
            if (panel.render(theme, backgroundDrawList, delta, appear, interactive,
                    framebufferWidth, framebufferHeight)) {
                ElytraClient.config().setPanelPosition(panel.title(), panel.x(), panel.y());
            }
        }

        settingsGUI.render(theme, backgroundDrawList, appear, framebufferWidth, framebufferHeight, interactive);
        configGUI.render(theme, backgroundDrawList, appear, framebufferWidth, framebufferHeight, interactive);

        if (ImGuiRenderer.fontBody() != null) {
            ImGui.popFont();
        }

        ImGuiRenderer.endFrame();

        handleHeaderAction(headerAction);
        return true;
    }

    private void handleHeaderAction(int action) {
        switch (action) {
            case ElytraHeader.ACTION_CLOSE:
                ElytraClient.closeGui();
                break;
            case ElytraHeader.ACTION_SETTINGS:
                settingsGUI.toggle();
                configGUI.setOpen(false);
                break;
            case ElytraHeader.ACTION_CONFIGS:
                configGUI.toggle();
                settingsGUI.setOpen(false);
                break;
            default:
                break;
        }
    }

    private int renderRoot(ElytraTheme theme, ImDrawList backgroundDrawList, boolean interactive,
                           int framebufferWidth, int framebufferHeight) {
        float bandHeight = theme.s(48f);

        ImGui.setNextWindowPos(0f, 0f, ImGuiCond.Always);
        ImGui.setNextWindowSize(framebufferWidth, bandHeight, ImGuiCond.Always);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0f, 0f, 0f, 0f);

        boolean visible = ImGui.begin("##elytra_root", ROOT_FLAGS);
        ImGui.popStyleColor();
        ImGui.popStyleVar(2);

        int action = ElytraHeader.ACTION_NONE;
        if (visible) {
            header.tick(ImGuiRenderer.deltaSeconds());
            action = header.render(theme, backgroundDrawList, appear, interactive,
                    framebufferWidth, framebufferHeight);
        }
        ImGui.end();

        return action;
    }

    public static void applyScale(ElytraTheme theme, int framebufferWidth, int framebufferHeight) {
        float fit = Math.min(framebufferWidth / ElytraTheme.DESIGN_WIDTH,
                framebufferHeight / ElytraTheme.DESIGN_HEIGHT);
        float scale = fit * ElytraClient.config().guiScale();
        theme.setScale(scale);
    }

    /** Alpha of the whole GUI, for callers that want to know if it is on screen. */
    public float appear() {
        return appear;
    }

    public boolean hasVisibleContent() {
        return open || appear > 0.004f;
    }

    /** Colours used by the window decorations; exposed for the config preview. */
    public static int accentColor() {
        return ColorUtils.withAlpha(ElytraTheme.get().primaryPink, 0.6f);
    }
}

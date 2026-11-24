package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import com.elytra.client.ElytraConfig;
import com.elytra.client.gui.render.ImGuiInputHandler;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.gui.render.EffectRenderer;
import com.elytra.client.util.ColorUtils;
import com.elytra.client.util.RenderUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;

import java.util.Random;

/**
 * Elytra title screen. Layout and motion follow the referenced custom client:
 * full-screen vertical gradient, drifting dust, a short intro (logo + spaced
 * wordmark + progress bar), then a left-aligned menu with eased hover, a sliding
 * rounded highlight and an accent bar. Every colour comes from the active theme,
 * rain follows the saved rain preference.
 */
public final class ElytraMainMenu {

    private static final String[] LABELS = {
            "Singleplayer", "Multiplayer", "Minecraft Realms", "Minecraft Settings",
            "Elytra Client", "Quit Game"
    };
    private static final String WORDMARK = "ELYTRA";

    private static final double INTRO_SECONDS = 2.8;
    private static final double FADE_SECONDS = 0.7;
    private static final int DUST_COUNT = 22;

    private final EffectRenderer effect = new EffectRenderer();
    private final AccountChip chip = new AccountChip();
    private final double[] hover = new double[LABELS.length];

    private final double[] dx = new double[DUST_COUNT];
    private final double[] dy = new double[DUST_COUNT];
    private final double[] dr = new double[DUST_COUNT];
    private final double[] dv = new double[DUST_COUNT];
    private final double[] da = new double[DUST_COUNT];
    private final double[] dp = new double[DUST_COUNT];

    private Runnable pendingAction;
    private boolean introDone;
    private long start;
    private long last;
    private boolean playIntro;
    private double skinTime;
    private boolean wasVisible;
    private boolean previewFailureLogged;

    /** Called by the client when the title screen is (re)entered after another screen. */
    public void onReturn() {
        start = 0L;
        last = 0L;
        playIntro = false;
        introDone = true;
        PlayerPreview.reset();
    }

    public boolean isWasVisible() {
        return wasVisible;
    }

    public void setWasVisible(boolean value) {
        wasVisible = value;
    }

    private boolean rendered;

    public boolean hasRendered() {
        return rendered;
    }

    public void flushPendingAction() {
        Runnable action = pendingAction;
        pendingAction = null;
        if (action != null) {
            action.run();
        }
    }

    public void render(ElytraTheme theme, ImGuiInputHandler input,
                       int framebufferWidth, int framebufferHeight, int windowWidth, long windowHandle) {
        long now = System.nanoTime();
        rendered = true;
        if (start == 0L) {
            start = now;
            last = now;
            playIntro = !introDone;
            initDust();
        }
        float delta = last == 0L ? 1f / 60f
                : Math.min(0.1f, Math.max(0.001f, (now - last) / 1_000_000_000f));
        last = now;
        double t = (now - start) / 1_000_000_000.0;

        if (playIntro && t > INTRO_SECONDS) {
            introDone = true;
        }
        double menuAlpha = playIntro ? clamp01((t - INTRO_SECONDS) / FADE_SECONDS) : 1.0;

        ElytraClickGUI.applyScale(theme, framebufferWidth, framebufferHeight);
        ImGuiRenderer.ensureFonts(theme.uiScale());
        input.update(windowHandle, framebufferWidth, windowWidth);
        ImGuiRenderer.beginFrame(framebufferWidth, framebufferHeight);
        ElytraTheme.get().tick(delta);
        theme.applyStyle();

        ImDrawList dl = ImGui.getBackgroundDrawList();
        float w = framebufferWidth;
        float h = framebufferHeight;

        drawBackdrop(theme, dl, w, h, t, menuAlpha);

        if (playIntro && t < INTRO_SECONDS) {
            drawIntro(theme, dl, w, h, t);
            ImGuiRenderer.endFrame();
            return;
        }

        ElytraConfig config = ElytraClient.config();
        if (config != null && config.rainEnabled()
                && config.effectMode() != EffectRenderer.NONE) {
            effect.setDensity(config.rainDensity());
            effect.render(dl, config.effectMode(), (float) delta, (float) menuAlpha,
                    framebufferWidth, framebufferHeight, theme);
        }

        drawMenu(theme, dl, w, h, t, (float) menuAlpha, delta);
        ImGuiRenderer.endFrame();
    }

    private void initDust() {
        Random r = new Random(77L);
        for (int i = 0; i < DUST_COUNT; i++) {
            dx[i] = r.nextDouble();
            dy[i] = r.nextDouble();
            dr[i] = 1.6 + r.nextDouble() * 3.6;
            dv[i] = 0.006 + r.nextDouble() * 0.012;
            da[i] = 0.1 + r.nextDouble() * 0.28;
            dp[i] = r.nextDouble() * Math.PI * 2.0;
        }
    }

    private void drawBackdrop(ElytraTheme theme, ImDrawList dl, float w, float h, double t, double alpha) {
        // Vertical gradient, top slightly lighter than the bottom.
        int top = ColorUtils.fade(theme.panelBackgroundTop, (float) (alpha * 0.85f));
        int bottom = ColorUtils.fade(theme.mainBackground, (float) alpha);
        dl.addRectFilledMultiColor(0f, 0f, w, h, top, top, bottom, bottom);

        // Low accent wash across the middle band.
        int wash = ColorUtils.fade(theme.primaryPink, (float) (alpha * 0.07f));
        dl.addRectFilled(0f, h * 0.42f, w, h * 0.56f, wash);

        // Drifting dust motes.
        for (int i = 0; i < DUST_COUNT; i++) {
            dy[i] -= dv[i] * 0.016;
            if (dy[i] < -0.02) {
                dy[i] = 1.02;
                dx[i] = ((i * 37) % 100) / 100.0;
            }
            float px = (float) (dx[i] * w + Math.sin(t * 0.35 + dp[i]) * theme.s(10f));
            float py = (float) (dy[i] * h);
            int c = ColorUtils.fade(theme.secondaryLavender, (float) (da[i] * alpha));
            float radius = theme.s((float) dr[i]);
            dl.addCircleFilled(px, py, radius, c, 8);
        }
    }

    private void drawIntro(ElytraTheme theme, ImDrawList dl, float w, float h, double t) {
        double introT = clamp01(t / 0.5) * clamp01((INTRO_SECONDS - t) / 0.45);
        float a = (float) introT;
        float cx = w * 0.5f;
        float cy = h * 0.5f - theme.s(120f);
        float pulse = (float) (1f + 0.05f * Math.sin(t * 3.6f));

        drawLogo(theme, dl, cx, cy, theme.s(250f * pulse), a);

        float wide = spacedWidth(ImGuiRenderer.fontHuge(), ImGuiRenderer.fontHugeSize(), WORDMARK, theme.s(8f));
        spacedText(dl, ImGuiRenderer.fontHuge(), ImGuiRenderer.fontHugeSize(), WORDMARK,
                cx - wide * 0.5f, cy + theme.s(160f), theme.s(8f), theme.text, a);

        float barW = theme.s(280f);
        float barX = cx - barW * 0.5f;
        float barY = cy + theme.s(236f);
        dl.addRectFilled(barX, barY, barX + barW, barY + theme.s(5f),
                ColorUtils.fade(theme.panelBorder, 0.16f * a), theme.s(2.5f));
        double prog = clamp01(t / 2.45);
        float fill = (float) Math.floor(prog * (barW / theme.s(6f))) * theme.s(6f);
        dl.addRectFilled(barX, barY, barX + fill, barY + theme.s(5f),
                ColorUtils.fade(theme.primaryPink, 0.9f * a), theme.s(2.5f));
    }

    private void drawMenu(ElytraTheme theme, ImDrawList dl, float w, float h, double t,
                          float alpha, float delta) {
        ImGuiIO io = ImGui.getIO();
        float mouseX = io.getMousePosX();
        float mouseY = io.getMousePosY();
        boolean clicked = ImGui.isMouseClicked(0);

        float menuX = w * 0.5f - theme.s(360f);
        float top = h * 0.2f;

        // Logo + wordmark as one block, centered on the Singleplayer button.
        float columnCenterX = menuX + theme.s(147f);
        float logoSize = theme.s(80f);
        float logoY = top + theme.s(28f);
        drawLogo(theme, dl, columnCenterX, logoY, logoSize, alpha);
        float wordY = logoY + logoSize * 0.5f + theme.s(20f);
        float wordW = spacedWidth(ImGuiRenderer.fontHuge(), ImGuiRenderer.fontHugeSize(), WORDMARK, theme.s(3f));
        spacedText(dl, ImGuiRenderer.fontHuge(), ImGuiRenderer.fontHugeSize(), WORDMARK,
                columnCenterX - wordW * 0.5f, wordY, theme.s(3f), theme.text, alpha);

        for (int i = 0; i < LABELS.length; i++) {
            boolean over = alpha > 0.95f && overItem(theme, i, menuX, top, mouseX, mouseY);
            double target = over ? 1.0 : 0.0;
            hover[i] += (target - hover[i]) * (1.0 - Math.exp(-delta * 14.0));
            float hv = (float) hover[i];
            if (hv <= 0.01f) {
                continue;
            }

            float iy = itemY(theme, top, i);
            float shift = theme.s(10f) * hv;

            float rx = menuX - theme.s(18f);
            float ry = iy - theme.s(22f);
            float rw = theme.s(330f);
            float rh = theme.s(44f);
            dl.addRectFilled(rx, ry, rx + rw, ry + rh,
                    ColorUtils.fade(theme.primaryPink, alpha * 0.1f * hv), theme.s(12f));
            dl.addRectFilled(rx, iy - theme.s(11f), rx + theme.s(4f), iy + theme.s(11f),
                    ColorUtils.fade(theme.primaryPink, alpha * hv), theme.s(2f));

        }

        // Labels drawn after highlights so text always sits on top.
        float fontSize = ImGuiRenderer.fontBodySize();
        for (int i = 0; i < LABELS.length; i++) {
            float hv = (float) hover[i];
            float iy = itemY(theme, top, i);
            int labelColor = ColorUtils.lerp(theme.text, theme.primaryPink, hv);
            RenderUtils.text(dl, ImGuiRenderer.fontBody(), fontSize,
                    menuX + theme.s(10f) * hv, iy - fontSize * 0.5f,
                    ColorUtils.fade(labelColor, alpha), LABELS[i]);
        }

        // Player skin on a pedestal, on the right side of the screen.
        PlayerPreview.ensureLoaded();
        // Continuous animation clock (never resets on navigation) keeps the model
        // in a stable idle pose; only the horizontal slide-in uses the intro ease.
        skinTime += delta;
        double introP = Math.max(0.0, Math.min(1.0, (t - (playIntro ? INTRO_SECONDS : 0.0) + 0.1) / 2.4));
        double eased = 1.0 - Math.pow(1.0 - introP, 3.0);
        float px = (float) (w * 0.73);
        float py = h * 0.86f;
        float sp = Math.max(7f, (float) Math.round(h * 0.46 / 32.0));
        float charX = w + 18f * sp + (px - (w + 18f * sp)) * (float) eased;
        dl.addRectFilled(charX - 7f * sp, py - 3f, charX + 7f * sp, py + 9f,
                ColorUtils.fade(theme.panelBorder, 0.35f), theme.s(6f));
        try {
            PlayerPreview.draw(theme, dl, charX, py, sp, alpha, skinTime, 1.0, (int) mouseX, (int) mouseY);
        } catch (Throwable failure) {
            // A preview failure must never take the client down; log once and
            // rebuild the raster on the next frame.
            if (!previewFailureLogged) {
                previewFailureLogged = true;
                ElytraClient.LOGGER.warn("Player preview failed to render", failure);
            }
            PlayerPreview.reset();
        }

        // Compact account chip (picture, name, gear) hovering above the character's head.
        float headTop = py - 32f * sp;
        chip.draw(theme, dl, charX, headTop - theme.s(16f), alpha, mouseX, mouseY, delta);
        if (clicked && alpha > 0.6f && chip.contains(mouseX, mouseY)) {
            queueAccountSettings();
            return;
        }

        if (clicked && alpha > 0.6f) {
            for (int i = 0; i < LABELS.length; i++) {
                if (overItem(theme, i, menuX, top, mouseX, mouseY)) {
                    queueAction(i);
                    break;
                }
            }
        }
    }

    private float itemY(ElytraTheme theme, float top, int index) {
        return top + theme.s(200f) + index * theme.s(42f);
    }

    private boolean overItem(ElytraTheme theme, int index, float menuX, float top, float mx, float my) {
        float y = itemY(theme, top, index);
        return mx >= menuX - theme.s(18f) && mx <= menuX + theme.s(312f)
                && my >= y - theme.s(22f) && my <= y + theme.s(22f);
    }

    private void drawLogo(ElytraTheme theme, ImDrawList dl, float cx, float cy, float size, float alpha) {
        int tex = ImGuiRenderer.logoTexture();
        if (tex == 0 || ImGuiRenderer.logoWidth() <= 0 || alpha <= 0.01f) {
            return;
        }
        float half = size * 0.5f;
        float ratio = ImGuiRenderer.logoWidth() / (float) ImGuiRenderer.logoHeight();
        float w = size;
        float h = size / ratio;
        dl.addImageRounded(tex, cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f,
                0f, 0f, 1f, 1f, ColorUtils.fade(ColorUtils.hex("#FFFFFF"), alpha), theme.s(10f), 0);
    }

    private static float spacedWidth(imgui.ImFont font, float size, String text, float spacing) {
        return font.calcTextSizeAX(size, 4096f, 0f, text) + spacing * Math.max(0, text.length() - 1);
    }

    private static void spacedText(ImDrawList dl, imgui.ImFont font, float size, String text,
                                   float x, float y, float spacing, int color, float alpha) {
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            RenderUtils.text(dl, font, size, cursor, y, ColorUtils.fade(color, alpha), ch);
            cursor += font.calcTextSizeAX(size, 4096f, 0f, ch) + spacing;
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private void queueAccountSettings() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || pendingAction != null) {
            return;
        }
        Screen parent = client.currentScreen;
        pendingAction = () -> {
            ElytraAccountScreen screen = new ElytraAccountScreen(parent);
            ElytraClient.accountUi().open(screen, ElytraAccountUi.Mode.SETTINGS);
            client.setScreen(screen);
        };
    }

    private void queueAction(int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        Screen parent = client.currentScreen;
        pendingAction = switch (index) {
            case 0 -> () -> client.setScreen(new SelectWorldScreen(parent));
            case 1 -> () -> client.setScreen(new MultiplayerScreen(parent));
            case 2 -> () -> client.setScreen(new RealmsMainScreen(parent));
            case 3 -> () -> client.setScreen(new OptionsScreen(parent, client.options));
            case 4 -> () -> ElytraClient.openGui();
            case 5 -> client::scheduleStop;
            default -> null;
        };
    }
}

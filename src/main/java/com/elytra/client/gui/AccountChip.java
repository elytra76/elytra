package com.elytra.client.gui;

import com.elytra.client.account.Account;
import com.elytra.client.account.AccountManager;
import com.elytra.client.gui.render.AvatarTextures;
import com.elytra.client.gui.render.GlowRenderer;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.util.ColorUtils;
import com.elytra.client.util.RenderUtils;
import imgui.ImDrawList;

/**
 * Small pill shown above the title-screen character: profile picture, username and a
 * settings gear. Clicking anywhere on it opens the account settings.
 */
public final class AccountChip {

    private float x1;
    private float y1;
    private float x2;
    private float y2;
    private float hover;

    public boolean contains(float mx, float my) {
        return x2 > x1 && mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    /** Draws the chip centred on {@code cx} with its bottom edge at {@code bottomY}. */
    public void draw(ElytraTheme theme, ImDrawList dl, float cx, float bottomY, float alpha,
                     float mouseX, float mouseY, float delta) {
        Account account = AccountManager.get().active();
        if (account == null || ImGuiRenderer.fontSmall() == null || alpha <= 0.01f) {
            x1 = x2 = 0f;
            return;
        }

        float height = theme.s(28f);
        float pad = theme.s(4f);
        float radius = height * 0.5f - pad;
        float fontSize = ImGuiRenderer.fontSmallSize();
        float gear = theme.s(7f);
        float gap = theme.s(7f);

        String name = fit(account.username(), fontSize, theme.s(120f));
        float textW = ImGuiRenderer.fontSmall().calcTextSizeAX(fontSize, 4096f, 0f, name);
        float width = pad + radius * 2f + gap + textW + gap + gear * 2f + gap;

        x1 = cx - width * 0.5f;
        x2 = cx + width * 0.5f;
        y2 = bottomY;
        y1 = bottomY - height;

        boolean over = contains(mouseX, mouseY);
        hover += ((over ? 1f : 0f) - hover) * (1f - (float) Math.exp(-delta * 14f));

        float round = height * 0.5f;
        if (hover > 0.05f) {
            GlowRenderer.pillGlow(dl, x1, y1, x2, y2, round, hover * alpha);
        }
        dl.addRectFilled(x1, y1, x2, y2,
                ColorUtils.fade(ColorUtils.lerp(theme.panelBackground, theme.hoverBackground, hover), alpha * 0.94f), round);
        dl.addRect(x1, y1, x2, y2,
                ColorUtils.fade(ColorUtils.lerp(theme.panelBorder, theme.primaryPink, hover), alpha * 0.8f), round);

        float midY = (y1 + y2) * 0.5f;
        drawAvatar(theme, dl, x1 + pad + radius, midY, radius, account, alpha);

        float tx = x1 + pad + radius * 2f + gap;
        RenderUtils.text(dl, ImGuiRenderer.fontSmall(), fontSize, tx, midY - fontSize * 0.5f,
                ColorUtils.fade(ColorUtils.lerp(theme.text, theme.primaryPink, hover), alpha), name);

        drawGear(dl, x2 - gap - gear, midY, gear,
                ColorUtils.fade(ColorUtils.lerp(theme.textSecondary, theme.primaryPink, hover), alpha));
    }

    /** Round avatar: the account's picture, or its colour with an initial. */
    public static void drawAvatar(ElytraTheme theme, ImDrawList dl, float cx, float cy, float radius,
                                  Account account, float alpha) {
        int texture = AvatarTextures.textureFor(account);
        drawAvatar(theme, dl, cx, cy, radius, texture, account.avatarColor(), account.username(), alpha);
    }

    public static void drawAvatar(ElytraTheme theme, ImDrawList dl, float cx, float cy, float radius,
                                  int texture, int colorIndex, String name, float alpha) {
        int ring = ColorUtils.fade(theme.primaryPink, alpha * 0.9f);
        if (texture != 0) {
            dl.addImageRounded(texture, cx - radius, cy - radius, cx + radius, cy + radius,
                    0f, 0f, 1f, 1f, ColorUtils.fade(ColorUtils.hex("#FFFFFF"), alpha), radius, 0);
        } else {
            int[] colors = AccountManager.AVATAR_COLORS;
            int fill = colors[Math.floorMod(colorIndex, colors.length)];
            dl.addCircleFilled(cx, cy, radius, ColorUtils.fade(fill, alpha), 28);
            if (name != null && !name.isEmpty() && ImGuiRenderer.fontTitle() != null) {
                String letter = name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
                float size = Math.max(8f, radius * 1.15f);
                float w = ImGuiRenderer.fontTitle().calcTextSizeAX(size, 4096f, 0f, letter);
                RenderUtils.text(dl, ImGuiRenderer.fontTitle(), size, cx - w * 0.5f, cy - size * 0.5f,
                        ColorUtils.fade(theme.mainBackground, alpha), letter);
            }
        }
        dl.addCircle(cx, cy, radius, ring, 28, 1.3f);
    }

    /** A plain gear: ring, hub and eight teeth. */
    private static void drawGear(ImDrawList dl, float cx, float cy, float r, int color) {
        dl.addCircle(cx, cy, r * 0.62f, color, 16, 1.4f);
        dl.addCircleFilled(cx, cy, r * 0.22f, color, 8);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            float dx = (float) Math.cos(a);
            float dy = (float) Math.sin(a);
            dl.addLine(cx + dx * r * 0.7f, cy + dy * r * 0.7f, cx + dx * r * 1.05f, cy + dy * r * 1.05f, color, 1.8f);
        }
    }

    private static String fit(String value, float size, float maxWidth) {
        if (ImGuiRenderer.fontSmall().calcTextSizeAX(size, 4096f, 0f, value) <= maxWidth) {
            return value;
        }
        String trimmed = value;
        while (trimmed.length() > 1
                && ImGuiRenderer.fontSmall().calcTextSizeAX(size, 4096f, 0f, trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }
}

package com.elytra.client.util;

import imgui.ImVec4;

/**
 * Colour helpers. All packed values follow ImGui's native layout (0xAABBGGRR).
 * Keeping every conversion here means the theme only ever deals in hex strings.
 */
public final class ColorUtils {

    private ColorUtils() {
    }

    /** Packs 0-255 components into an ImGui colour integer. */
    public static int rgba(int r, int g, int b, int a) {
        return (clamp(a) << 24) | (clamp(b) << 16) | (clamp(g) << 8) | clamp(r);
    }

    /** Packs a hex string (#RRGGBB or #AARRGGBB) into an ImGui colour integer. */
    public static int hex(String hex, float alpha) {
        return rgba(red(hex), green(hex), blue(hex), Math.round(alpha * 255f));
    }

    /** Packs a hex string at full opacity. */
    public static int hex(String hex) {
        return hex(hex, 1f);
    }

    public static int red(String hex) {
        return component(hex, 0);
    }

    public static int green(String hex) {
        return component(hex, 1);
    }

    public static int blue(String hex) {
        return component(hex, 2);
    }

    private static int component(String hex, int index) {
        String clean = normalise(hex);
        int offset = index * 2;
        return Integer.parseInt(clean.substring(offset, offset + 2), 16);
    }

    private static String normalise(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        if (clean.length() == 3) {
            StringBuilder expanded = new StringBuilder(6);
            for (int i = 0; i < 3; i++) {
                expanded.append(clean.charAt(i)).append(clean.charAt(i));
            }
            clean = expanded.toString();
        }
        if (clean.length() == 8) {
            // Input is AARRGGBB - drop the alpha channel, callers pass alpha explicitly.
            clean = clean.substring(2);
        }
        if (clean.length() != 6) {
            throw new IllegalArgumentException("Unsupported colour literal: " + hex);
        }
        return clean;
    }

    /** Returns the same colour with a different alpha (0-1). */
    public static int withAlpha(int packed, float alpha) {
        int a = clamp(Math.round(alpha * 255f));
        return (packed & 0x00FFFFFF) | (a << 24);
    }

    /** Multiplies an existing alpha channel by a factor. */
    public static int fade(int packed, float factor) {
        int a = (packed >>> 24) & 0xFF;
        return withAlpha(packed, (a / 255f) * factor);
    }

    /** Linearly interpolates two packed colours. */
    public static int lerp(int from, int to, float t) {
        float k = clampF(t);
        int a = lerpChannel(from >>> 24, to >>> 24, k);
        int b = lerpChannel(from >>> 16, to >>> 16, k);
        int g = lerpChannel(from >>> 8, to >>> 8, k);
        int r = lerpChannel(from, to, k);
        return rgba(r, g, b, a);
    }

    private static int lerpChannel(int from, int to, float t) {
        int f = from & 0xFF;
        int d = to & 0xFF;
        return clamp(Math.round(f + (d - f) * t));
    }

    public static ImVec4 vec(int packed) {
        return new ImVec4(
                (packed & 0xFF) / 255f,
                ((packed >>> 8) & 0xFF) / 255f,
                ((packed >>> 16) & 0xFF) / 255f,
                ((packed >>> 24) & 0xFF) / 255f);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static float clampF(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

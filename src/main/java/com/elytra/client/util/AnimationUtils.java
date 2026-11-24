package com.elytra.client.util;

/**
 * Frame-rate independent smoothing.
 *
 * Every animated value in the GUI goes through here so a 30 fps dip and a 300 fps
 * sprint produce the same visual duration.
 */
public final class AnimationUtils {

    private AnimationUtils() {
    }

    /**
     * Exponential approach towards a target.
     *
     * @param current  the value being animated
     * @param target   the destination
     * @param speed    higher is snappier; ~14 is a crisp 120 ms feel
     * @param delta    frame delta in seconds
     */
    public static float approach(float current, float target, float speed, float delta) {
        if (delta <= 0f) {
            return current;
        }
        float factor = 1f - (float) Math.exp(-speed * delta);
        float next = current + (target - current) * factor;
        // Snap once the remaining distance is sub-pixel so text stops shimmering.
        if (Math.abs(target - next) < 0.0015f) {
            return target;
        }
        return next;
    }

    public static float approach(float current, float target, float speed) {
        return approach(current, target, speed, 1f / 60f);
    }

    /** Ease-out cubic, for one-shot entrances. */
    public static float easeOutCubic(float t) {
        float clamped = ColorUtils.clampF(t);
        float inverse = 1f - clamped;
        return 1f - inverse * inverse * inverse;
    }

    /** Ease-in-out quad, used by the open/close fade. */
    public static float easeInOutQuad(float t) {
        float clamped = ColorUtils.clampF(t);
        return clamped < 0.5f
                ? 2f * clamped * clamped
                : 1f - (float) Math.pow(-2f * clamped + 2f, 2f) / 2f;
    }
}

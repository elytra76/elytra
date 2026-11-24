package com.elytra.client.gui.render;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-element animation state.
 *
 * Keyed by a stable string so panels, rows and header sections keep their own
 * easing without every class owning a field. Values are advanced once per frame by
 * {@link #tick(float)}.
 */
public final class GuiAnimations {

    private static final float HOVER_SPEED = 16f;
    private static final float FADE_SPEED = 11f;

    private final Map<String, Float> values = new HashMap<>();
    private final Map<String, Float> targets = new HashMap<>();

    /** Registers or updates the target for a key. */
    public void target(String key, float value) {
        targets.put(key, value);
    }

    /** Advances every tracked value towards its target. */
    public void tick(float deltaSeconds) {
        for (Map.Entry<String, Float> entry : targets.entrySet()) {
            String key = entry.getKey();
            float target = entry.getValue();
            float current = values.getOrDefault(key, target);
            float speed = Math.abs(target - current) > 0.4f ? FADE_SPEED : HOVER_SPEED;
            values.put(key, com.elytra.client.util.AnimationUtils.approach(current, target, speed, deltaSeconds));
        }
    }

    public float get(String key) {
        return values.getOrDefault(key, 0f);
    }

    public float get(String key, float fallback) {
        return values.getOrDefault(key, fallback);
    }

    /** Drops state for keys that were not touched this frame (panel removed, category empty). */
    public void prune(int maxKeys) {
        if (values.size() <= maxKeys) {
            return;
        }
        values.keySet().retainAll(targets.keySet());
    }

    public void clear() {
        values.clear();
        targets.clear();
    }
}

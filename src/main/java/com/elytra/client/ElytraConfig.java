package com.elytra.client;

import com.elytra.client.account.AccountManager;
import com.elytra.client.gui.ElytraTheme;
import com.elytra.client.util.AtomicFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * On-disk state for the client: open key, UI scale and where each panel was left.
 *
 * Plain JSON through the Gson that ships with Minecraft - no extra dependency and
 * no reflection over mutable game state.
 */
public final class ElytraConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "elytra-client.json";

    private float guiScale = 1.0f;
    private int openKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private int themeIndex = 0;
    private boolean rainEnabled = true;
    private float rainDensity = 1.0f;
    private int effectMode = 1;
    private final Map<String, float[]> panelPositions = new LinkedHashMap<>();

    private boolean dirty;

    public float guiScale() {
        return guiScale;
    }

    public void setGuiScale(float value) {
        float clamped = Math.max(0.55f, Math.min(2.4f, value));
        if (Math.abs(clamped - guiScale) > 0.0005f) {
            guiScale = clamped;
            dirty = true;
        }
    }

    public int openKey() {
        return openKey;
    }

    public void setOpenKey(int key) {
        if (openKey != key) {
            openKey = key;
            dirty = true;
        }
    }

    public int themeIndex() {
        return themeIndex;
    }

    public void setThemeIndex(int index) {
        int clamped = Math.max(0, Math.min(ElytraTheme.PRESETS.length - 1, index));
        if (themeIndex != clamped) {
            themeIndex = clamped;
            dirty = true;
        }
    }

    public boolean rainEnabled() {
        return rainEnabled;
    }

    public void setRainEnabled(boolean value) {
        if (rainEnabled != value) {
            rainEnabled = value;
            dirty = true;
        }
    }

    public float rainDensity() {
        return rainDensity;
    }

    public void setRainDensity(float value) {
        float clamped = Math.max(0.15f, Math.min(1.0f, value));
        if (Math.abs(clamped - rainDensity) > 0.0005f) {
            rainDensity = clamped;
            dirty = true;
        }
    }

    /** 0 = None, 1 = Rain, 2 = Snow, 3 = Leaves. */
    public int effectMode() {
        return effectMode;
    }

    public void setEffectMode(int mode) {
        int clamped = Math.max(0, Math.min(3, mode));
        if (effectMode != clamped) {
            effectMode = clamped;
            dirty = true;
        }
        // "None" is the master off switch now; every other mode implies on.
        boolean enabled = clamped != 0;
        if (rainEnabled != enabled) {
            rainEnabled = enabled;
            dirty = true;
        }
    }

    public float[] panelPosition(String category) {
        return panelPositions.get(category);
    }

    public void setPanelPosition(String category, float x, float y) {
        float[] existing = panelPositions.get(category);
        if (existing != null && Math.abs(existing[0] - x) < 0.5f && Math.abs(existing[1] - y) < 0.5f) {
            return;
        }
        panelPositions.put(category, new float[]{x, y});
        dirty = true;
    }

    public void clearPanelPositions() {
        if (!panelPositions.isEmpty()) {
            panelPositions.clear();
            dirty = true;
        }
    }

    private Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** Reads the file if present. A malformed file is ignored rather than fatal. */
    public void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("guiScale")) {
                guiScale = Math.max(0.55f, Math.min(2.4f, root.get("guiScale").getAsFloat()));
            }
            if (root.has("openKey")) {
                openKey = root.get("openKey").getAsInt();
            }
            if (root.has("theme")) {
                themeIndex = Math.max(0, Math.min(ElytraTheme.PRESETS.length - 1,
                        root.get("theme").getAsInt()));
            }
            if (root.has("rainEnabled")) {
                rainEnabled = root.get("rainEnabled").getAsBoolean();
            }
            if (root.has("rainDensity")) {
                rainDensity = Math.max(0.15f, Math.min(1.0f, root.get("rainDensity").getAsFloat()));
            }
            if (root.has("effectMode")) {
                int stored = root.get("effectMode").getAsInt();
                // Pre-"None" configs stored 0=rain, 1=snow, 2=leaves.
                if (!root.has("effectVersion") || root.get("effectVersion").getAsInt() < 1) {
                    stored += 1;
                }
                effectMode = Math.max(0, Math.min(3, stored));
            }
            if (root.has("panels") && root.get("panels").isJsonObject()) {
                JsonObject panels = root.getAsJsonObject("panels");
                for (String key : panels.keySet()) {
                    JsonObject entry = panels.getAsJsonObject(key);
                    if (entry != null && entry.has("x") && entry.has("y")) {
                        panelPositions.put(key, new float[]{entry.get("x").getAsFloat(), entry.get("y").getAsFloat()});
                    }
                }
            }
            dirty = false;
        } catch (IOException | RuntimeException exception) {
            ElytraClient.LOGGER.warn("Could not read {}, using defaults", path, exception);
        }
    }

    public void saveIfDirty() {
        if (!dirty) {
            return;
        }
        save();
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("guiScale", guiScale);
        root.addProperty("openKey", openKey);
        root.addProperty("theme", themeIndex);
        root.addProperty("rainEnabled", rainEnabled);
        root.addProperty("rainDensity", rainDensity);
        root.addProperty("effectMode", effectMode);
        root.addProperty("effectVersion", 1);

        JsonObject panels = new JsonObject();
        panelPositions.forEach((key, value) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("x", value[0]);
            entry.addProperty("y", value[1]);
            panels.add(key, entry);
        });
        root.add("panels", panels);

        Path path = file();
        try {
            AtomicFiles.writeString(path, GSON.toJson(root));
            dirty = false;
            AccountManager.get().captureSettingsFrom(this);
        } catch (IOException exception) {
            ElytraClient.LOGGER.warn("Could not write {}", path, exception);
        }
    }
}

package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import com.elytra.client.gui.skin.SkinModel;
import com.elytra.client.gui.skin.SoftRaster;
import com.elytra.client.util.AtomicFiles;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import imgui.ImDrawList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Player preview: the {@link SoftRaster} + {@link SkinModel} render the skin as a
 * mouse-following 3D figure into an ARGB buffer, which is painted onto the ImGui draw
 * list as rectangles.
 *
 * <p>There is deliberately no GL texture in this path: a filled rectangle is the one
 * primitive that stays reliable across screen transitions.
 *
 * <p>Performance: the raster is converted into a small set of merged rectangles (runs of
 * identical pixels joined horizontally and then vertically) once per raster update, so
 * a frame replays a few hundred rectangles instead of one per pixel. Rasterising is
 * also capped to ~80 updates a second; faster displays reuse the previous result.
 */
public final class PlayerPreview {

    private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("elytra-skin.png");
    private static final Path META = FabricLoader.getInstance().getConfigDir().resolve("elytra-skin.txt");
    private static final AtomicReference<BufferedImage> IMAGE = new AtomicReference<>();
    private static volatile boolean started;
    private static volatile boolean slim;

    /** Widest raster the renderer will produce. */
    private static final int MAX_COLUMNS = 160;
    private static final long RASTER_INTERVAL_NANOS = 12_000_000L;
    /** Pixels fainter than this alpha are skipped entirely. */
    private static final int MIN_ALPHA = 6;

    /** Skins are only ever fetched from Mojang's texture host. */
    private static final String SKIN_HOST = "textures.minecraft.net";
    private static final int MAX_SKIN_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SKIN_SIDE = 1024;

    private static SoftRaster raster;
    private static int[] pixels;
    private static int curW = -1;
    private static int curH = -1;
    private static long lastRasterNanos;
    private static boolean blankLogged;
    private static boolean missingLogged;
    private static int missingFrames;

    // Merged rectangles in raster space; rebuilt whenever the raster is.
    private static int[] runX = new int[512];
    private static int[] runY = new int[512];
    private static int[] runW = new int[512];
    private static int[] runH = new int[512];
    private static int[] runC = new int[512];
    private static int runCount;
    private static int[] openPrev = new int[0];
    private static int[] openCur = new int[0];

    private PlayerPreview() {
    }

    /** Drops the cached raster so it is rebuilt at the next size. */
    public static void reset() {
        raster = null;
        pixels = null;
        curW = -1;
        curH = -1;
        runCount = 0;
        lastRasterNanos = 0L;
    }

    public static void ensureLoaded() {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(() -> {
            // Disk cache first so the figure appears immediately, then refresh from Mojang.
            BufferedImage cached = readCache();
            if (cached != null) {
                IMAGE.set(cached);
            }
            BufferedImage fresh = fetch();
            if (fresh != null) {
                IMAGE.set(fresh);
            }
        }, "Elytra-Skin");
        thread.setDaemon(true);
        thread.start();
    }

    private static BufferedImage readCache() {
        try {
            if (!Files.isRegularFile(CACHE) || Files.size(CACHE) > MAX_SKIN_BYTES) {
                return null;
            }
            BufferedImage image = ImageIO.read(CACHE.toFile());
            if (!validSkin(image)) {
                return null;
            }
            slim = Files.isRegularFile(META) && "slim".equals(Files.readString(META).trim());
            return image;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean validSkin(BufferedImage image) {
        return image != null
                && image.getWidth() >= 64 && image.getWidth() <= MAX_SKIN_SIDE
                && image.getHeight() >= 32 && image.getHeight() <= MAX_SKIN_SIDE;
    }

    private static BufferedImage fetch() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getGameProfile() == null) {
                return null;
            }
            var props = mc.getGameProfile().properties().get("textures");
            if (props == null || props.isEmpty()) {
                return null;
            }
            String value = props.iterator().next().value();
            String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            JsonObject skin = JsonParser.parseString(json).getAsJsonObject()
                    .getAsJsonObject("textures").getAsJsonObject("SKIN");
            boolean isSlim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
                    && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());

            String raw = skin.get("url").getAsString();
            if (raw.startsWith("http://")) {
                raw = "https://" + raw.substring(7);
            }
            URL url = java.net.URI.create(raw).toURL();
            if (!"https".equals(url.getProtocol()) || !SKIN_HOST.equalsIgnoreCase(url.getHost())) {
                ElytraClient.LOGGER.warn("Ignoring skin URL from unexpected host: {}", url.getHost());
                return null;
            }

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(8_000);
            connection.setInstanceFollowRedirects(false);
            try (InputStream in = connection.getInputStream()) {
                byte[] data = in.readNBytes(MAX_SKIN_BYTES + 1);
                if (data.length > MAX_SKIN_BYTES) {
                    return null;
                }
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                if (!validSkin(image)) {
                    return null;
                }
                slim = isSlim;
                ByteArrayOutputStream png = new ByteArrayOutputStream(data.length);
                ImageIO.write(image, "png", png);
                AtomicFiles.write(CACHE, png.toByteArray());
                AtomicFiles.writeString(META, isSlim ? "slim" : "classic");
                return image;
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * (cx, feetY) is the feet position; s is the on-screen size; t is elapsed time; p is
     * the intro ease; mouse drives yaw/pitch.
     */
    public static boolean draw(ElytraTheme theme, ImDrawList dl, float cx, float feetY, float s,
                               float alpha, double t, double p, int mouseX, int mouseY) {
        BufferedImage image = IMAGE.get();
        if (image == null || image.getWidth() < 64 || image.getHeight() < 32) {
            if (++missingFrames > 300 && !missingLogged) {
                missingLogged = true;
                ElytraClient.LOGGER.warn(
                        "Player preview has no usable skin image after {} frames (started={}, slim={})",
                        missingFrames, started, slim);
            }
            return false;
        }

        double scale = theme.uiScale();
        double rs = Math.max(1.5, Math.min(s * scale, MAX_COLUMNS / 34.0));
        int w = (int) Math.ceil(34.0 * rs);
        int h = (int) Math.ceil(42.0 * rs);
        boolean resized = raster == null || w != curW || h != curH || pixels == null || pixels.length != w * h;
        if (resized) {
            raster = new SoftRaster(w, h, 2);
            pixels = new int[w * h];
            curW = w;
            curH = h;
            lastRasterNanos = 0L;
        }

        long now = System.nanoTime();
        if (resized || now - lastRasterNanos >= RASTER_INTERVAL_NANOS) {
            double dxm = Math.max(-1.0, Math.min(1.0, (mouseX - cx) / 170.0));
            double dym = Math.max(-1.0, Math.min(1.0, (mouseY - (feetY - 28.0 * s)) / 150.0));
            SkinModel.render(raster, image, slim, rs, w / 2.0, h - 3.0 * rs, t, p, dxm, dym);
            raster.resolve(pixels);
            buildRuns(pixels, w, h);
            lastRasterNanos = now;
        }

        double k = s / rs;
        float dx = (float) (cx - w * k / 2.0);
        float dy = (float) (feetY - (h - 3.0 * rs) * k);
        paint(dl, w, h, dx, dy, (float) (w * k), (float) (h * k), alpha);
        return true;
    }

    private static int addRun(int x, int y, int width, int color) {
        if (runCount == runX.length) {
            int capacity = runCount * 2;
            runX = java.util.Arrays.copyOf(runX, capacity);
            runY = java.util.Arrays.copyOf(runY, capacity);
            runW = java.util.Arrays.copyOf(runW, capacity);
            runH = java.util.Arrays.copyOf(runH, capacity);
            runC = java.util.Arrays.copyOf(runC, capacity);
        }
        int index = runCount++;
        runX[index] = x;
        runY[index] = y;
        runW[index] = width;
        runH[index] = 1;
        runC[index] = color;
        return index;
    }

    /**
     * Greedy rectangle merge: identical neighbouring pixels join into a horizontal run, and
     * a run directly below an identical run (same start, width and colour) extends it down.
     */
    private static void buildRuns(int[] px, int w, int h) {
        runCount = 0;
        if (openPrev.length < w) {
            openPrev = new int[w];
            openCur = new int[w];
        }
        int prevCount = 0;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int curCount = 0;
            int x = 0;
            while (x < w) {
                int c = px[row + x];
                if ((c >>> 24) < MIN_ALPHA) {
                    x++;
                    continue;
                }
                int end = x + 1;
                while (end < w && px[row + end] == c) {
                    end++;
                }
                int length = end - x;
                int match = -1;
                for (int j = 0; j < prevCount; j++) {
                    int candidate = openPrev[j];
                    if (runX[candidate] > x) {
                        break;
                    }
                    if (runX[candidate] == x && runW[candidate] == length && runC[candidate] == c) {
                        match = candidate;
                        break;
                    }
                }
                if (match >= 0) {
                    runH[match]++;
                } else {
                    match = addRun(x, y, length, c);
                }
                openCur[curCount++] = match;
                x = end;
            }
            int[] swap = openPrev;
            openPrev = openCur;
            openCur = swap;
            prevCount = curCount;
        }
    }

    private static void paint(ImDrawList dl, int w, int h, float x0, float y0, float dw, float dh, float alpha) {
        float cw = dw / w;
        float ch = dh / h;
        for (int i = 0; i < runCount; i++) {
            int c = runC[i];
            int a = Math.min(255, Math.round((c >>> 24) * alpha));
            if (a <= 0) {
                continue;
            }
            // Raster pixels are ARGB; ImGui wants ABGR.
            int color = (a << 24) | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
            dl.addRectFilled(x0 + runX[i] * cw, y0 + runY[i] * ch,
                    x0 + (runX[i] + runW[i]) * cw, y0 + (runY[i] + runH[i]) * ch, color);
        }
        if (runCount == 0 && !blankLogged) {
            blankLogged = true;
            ElytraClient.LOGGER.warn("Player preview raster came out empty ({}x{})", w, h);
        }
    }
}

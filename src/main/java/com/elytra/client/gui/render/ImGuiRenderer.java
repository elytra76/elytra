package com.elytra.client.gui.render;

import com.elytra.client.ElytraClient;
import com.elytra.client.gui.ElytraTheme;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Owns the Dear ImGui context, the font atlas and the per-frame lifecycle.
 *
 * Exactly one context is created, lazily, on the render thread with a live GL
 * context. The GL3 backend is the only backend used - the GLFW backend is
 * deliberately skipped because it installs global callbacks that would fight with
 * Minecraft's own input handling.
 */
public final class ImGuiRenderer {

    private static final String FONT_RESOURCE = "/com/elytra/client/font/mochi-boom.ttf";
    /** Mochi Boom's demo build draws digits as watermark sprites; Ubuntu handles numerics. */
    private static final String FALLBACK_RESOURCE = "/com/elytra/client/font/ubuntu-medium.ttf";

    /** Latin ranges with the digit block removed, so merged Ubuntu digits take effect. */
    private static final short[] LATIN_WITHOUT_DIGITS = {
            0x0020, 0x002F,
            0x003A, 0x00FF,
            0x0100, 0x017F,
            0x2000, 0x206F,
            0x2190, 0x21FF,
            0
    };

    /** Reference body size in design units, before the UI scale is applied. */
    private static final float BODY_PX = 12.5f;
    private static final float TITLE_PX = 13.5f;
    private static final float SMALL_PX = 10.5f;
    private static final float DISPLAY_PX = 32f;
    private static final float HUGE_PX = 42f;

    private static final ImGuiImplGl3 GL3 = new ImGuiImplGl3();
    private static final GlStateSnapshot STATE = new GlStateSnapshot();

    private static boolean initialized;
    /** Set when ImGui's native library could not be loaded; the client then falls back to vanilla menus. */
    private static boolean failed;
    private static byte[] fontBytes;
    private static byte[] fallbackBytes;

    private static ImFont bodyFont;
    private static ImFont titleFont;
    private static ImFont smallFont;
    private static ImFont displayFont;
    private static ImFont hugeFont;
    private static ImFont digitFont;

    private static float bodySize;
    private static float titleSize;
    private static float smallSize;
    private static float displaySize;
    private static float hugeSize;
    private static float atlasScale = -1f;

    private static int logoTexture = -1;
    private static int logoWidth;
    private static int logoHeight;
    private static boolean logoLoaded;

    private static long lastFrameNanos;
    private static float deltaSeconds = 1f / 60f;

    private ImGuiRenderer() {
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Creates the context and the GL3 backend. Must run on the render thread with
     * the game's GL context current. Safe to call repeatedly.
     */
    public static boolean init() {
        if (initialized) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            doInit();
            return true;
        } catch (Throwable failure) {
            // Typically UnsatisfiedLinkError for imgui-java64. Never take the game down for a UI.
            failed = true;
            initialized = false;
            ElytraClient.LOGGER.error("Dear ImGui could not start; Elytra menus are disabled this session", failure);
            return false;
        }
    }

    /** True once startup failed; menus, themes and the account gate then stay out of the way. */
    public static boolean isUnavailable() {
        return failed;
    }

    private static void doInit() {
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.setLogFilename(null);
        io.setMouseDrawCursor(false);

        // No GLFW backend is installed, so wire the clipboard by hand (paste into text fields).
        io.setSetClipboardTextFn(new imgui.callback.ImStrConsumer() {
            @Override
            public void accept(String text) {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.getWindow() != null) {
                    org.lwjgl.glfw.GLFW.glfwSetClipboardString(client.getWindow().getHandle(), text);
                }
            }
        });
        io.setGetClipboardTextFn(new imgui.callback.ImStrSupplier() {
            @Override
            public String get() {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client == null || client.getWindow() == null) {
                    return "";
                }
                String text = org.lwjgl.glfw.GLFW.glfwGetClipboardString(client.getWindow().getHandle());
                return text == null ? "" : text;
            }
        });

        // No keyboard navigation: Minecraft owns the cursor and the focus model.
        io.setConfigFlags(0);

        if (!GL3.init("#version 150")) {
            ElytraClient.LOGGER.error("Dear ImGui GL3 backend failed to initialise");
        }

        fontBytes = readFont(FONT_RESOURCE);
        fallbackBytes = readFont(FALLBACK_RESOURCE);
        initialized = true;
        atlasScale = -1f;
        ensureFonts(ElytraTheme.get().uiScale());

        lastFrameNanos = System.nanoTime();
    }

    private static byte[] readFont(String resource) {
        try (InputStream stream = ImGuiRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                ElytraClient.LOGGER.warn("Font {} missing from the jar", resource);
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            ElytraClient.LOGGER.warn("Could not read bundled font {}", resource, exception);
            return null;
        }
    }

    /**
     * Rebuilds the atlas when the UI scale changed enough to matter. Font sizes are
     * rasterised at their final pixel height, which is what keeps the small type
     * crisp instead of upscaled.
     */
    public static void ensureFonts(float uiScale) {
        if (!initialized) {
            return;
        }
        if (atlasScale > 0f && Math.abs(uiScale - atlasScale) / atlasScale < 0.02f) {
            return;
        }

        ImFontAtlas atlas = ImGui.getIO().getFonts();
        atlas.clear();

        bodySize = Math.max(8f, BODY_PX * uiScale);
        titleSize = Math.max(8f, TITLE_PX * uiScale);
        smallSize = Math.max(7f, SMALL_PX * uiScale);
        displaySize = Math.max(16f, DISPLAY_PX * uiScale);
        hugeSize = Math.max(20f, HUGE_PX * uiScale);

        bodyFont = addFont(atlas, bodySize, false);
        titleFont = addFont(atlas, titleSize, true);
        smallFont = addFont(atlas, smallSize, false);
        displayFont = addFont(atlas, displaySize, true);
        hugeFont = addFont(atlas, hugeSize, true);
        digitFont = addFontFromBytes(atlas, bodySize, fallbackBytes);

        if (bodyFont == null) {
            bodyFont = atlas.addFontDefault();
            titleFont = bodyFont;
            smallFont = bodyFont;
            displayFont = bodyFont;
            hugeFont = bodyFont;
            digitFont = bodyFont;
            bodySize = titleSize = smallSize = displaySize = hugeSize = bodyFont.getFontSize();
        }

        atlas.build();

        GL3.destroyFontsTexture();
        GL3.createFontsTexture();

        atlasScale = uiScale;
    }

    private static ImFont addFont(ImFontAtlas atlas, float pixelSize, boolean bold) {
        if (fontBytes == null) {
            return null;
        }
        ImFontConfig config = new ImFontConfig();
        config.setSizePixels(pixelSize);
        config.setOversampleH(2);
        config.setOversampleV(2);
        config.setPixelSnapH(false);
        // A hair of tracking keeps the small type from turning into a grey smear.
        config.setGlyphExtraSpacing(bold ? 0.15f : 0.05f, 0f);
        config.setRasterizerMultiply(bold ? 1.12f : 1f);
        // Exclude 0-9 from Mochi: its digit slots are all the same watermark sprite.
        // Ubuntu supplies those glyphs through the merge below.
        config.setGlyphRanges(LATIN_WITHOUT_DIGITS);

        ImFont font = atlas.addFontFromMemoryTTF(fontBytes, pixelSize, config);
        config.destroy();

        // Mochi Boom is a display face: its Latin glyphs render, but its digits are
        // watermark art. Merge only 0-9 from Ubuntu into every size so numerals show
        // up everywhere without changing the rest of the typeface.
        if (fallbackBytes != null) {
            ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
            builder.addText("0123456789");
            short[] digitRanges = builder.buildRanges();

            ImFontConfig overlay = new ImFontConfig();
            overlay.setSizePixels(pixelSize);
            overlay.setMergeMode(true);
            overlay.setGlyphRanges(digitRanges);
            atlas.addFontFromMemoryTTF(fallbackBytes, pixelSize, overlay);
            overlay.destroy();
        }
        return font;
    }

    /** Separate plain font for numeric widget text. */
    private static ImFont addFontFromBytes(ImFontAtlas atlas, float pixelSize, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ImFontConfig config = new ImFontConfig();
        config.setSizePixels(pixelSize);
        config.setPixelSnapH(false);
        ImFont font = atlas.addFontFromMemoryTTF(bytes, pixelSize, config);
        config.destroy();
        return font;
    }

    /** Begins an ImGui frame sized to the game's framebuffer. */
    public static void beginFrame(int framebufferWidth, int framebufferHeight) {
        if (!initialized) {
            return;
        }

        long now = System.nanoTime();
        deltaSeconds = Math.min(0.1f, Math.max(0.001f, (now - lastFrameNanos) / 1_000_000_000f));
        lastFrameNanos = now;

        ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(framebufferWidth, framebufferHeight);
        io.setDeltaTime(deltaSeconds);

        // The GL3 backend lazily builds its shader and buffers on the first newFrame,
        // so this call is what makes the first draw work at all.
        GL3.newFrame();
        ImGui.newFrame();
    }


    public static void endFrame() {
        if (!initialized) {
            return;
        }
        ImGui.render();
        STATE.capture();
        GL3.renderDrawData(ImGui.getDrawData());
        STATE.restore();
    }


    public static float deltaSeconds() {
        return deltaSeconds;
    }

    public static ImFont fontBody() {
        return bodyFont;
    }

    public static ImFont fontTitle() {
        return titleFont;
    }

    public static ImFont fontSmall() {
        return smallFont;
    }

    public static float fontBodySize() {
        return bodySize;
    }

    public static float fontTitleSize() {
        return titleSize;
    }

    public static float fontSmallSize() {
        return smallSize;
    }

    public static ImFont fontDisplay() {
        return displayFont;
    }

    public static float fontDisplaySize() {
        return displaySize;
    }

    public static ImFont fontHuge() {
        return hugeFont;
    }

    public static float fontHugeSize() {
        return hugeSize;
    }

    public static ImFont fontDigits() {
        return digitFont;
    }

    /**
     * Lazily uploads the bundled logo PNG to a GL texture. Returns 0 if it could
     * not be loaded, so callers can fall back to the procedural mark.
     */
    public static int logoTexture() {
        if (logoLoaded) {
            return logoTexture;
        }
        logoLoaded = true;
        String resource = "/com/elytra/client/icon/logo.png";
        try (InputStream stream = ImGuiRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return 0;
            }
            BufferedImage image = javax.imageio.ImageIO.read(stream);
            if (image == null) {
                return 0;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

            ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
            try {
                for (int pixel : pixels) {
                    buffer.put((byte) ((pixel >> 16) & 0xFF));
                    buffer.put((byte) ((pixel >> 8) & 0xFF));
                    buffer.put((byte) (pixel & 0xFF));
                    buffer.put((byte) ((pixel >> 24) & 0xFF));
                }
                buffer.flip();

                // Save the currently bound texture so we don't desynchronise Blaze3D.
                int previous = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
                int texture = org.lwjgl.opengl.GL11.glGenTextures();
                org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texture);
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                        org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                        org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
                org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0,
                        org.lwjgl.opengl.GL11.GL_RGBA, width, height, 0,
                        org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buffer);
                org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, previous);

                logoTexture = texture;
                logoWidth = width;
                logoHeight = height;
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(buffer);
            }
        } catch (IOException exception) {
            ElytraClient.LOGGER.warn("Could not load logo texture", exception);
        }
        return logoTexture;
    }

    /** Uploads an arbitrary image as a GL texture (nearest filtering). 0 on failure. */
    public static int uploadTexture(BufferedImage image, boolean nearest) {
        if (image == null) {
            return 0;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
        try {
            for (int pixel : pixels) {
                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >>> 24) & 0xFF));
            }
            buffer.flip();

            int previous = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            int texture = org.lwjgl.opengl.GL11.glGenTextures();
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texture);
            int filter = nearest ? org.lwjgl.opengl.GL11.GL_NEAREST : org.lwjgl.opengl.GL11.GL_LINEAR;
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, filter);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, filter);
            org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0,
                    org.lwjgl.opengl.GL11.GL_RGBA, width, height, 0,
                    org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buffer);
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, previous);
            return texture;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(buffer);
        }
    }

    public static int logoWidth() {
        return logoWidth;
    }

    public static int logoHeight() {
        return logoHeight;
    }

    /** Releases native resources. Called from the client shutdown hook. */
    public static void shutdown() {
        if (!initialized) {
            return;
        }
        AvatarTextures.shutdown();
        GL3.shutdown();
        ImGui.destroyContext();
        initialized = false;
        bodyFont = null;
        titleFont = null;
        smallFont = null;
        displayFont = null;
        hugeFont = null;
        digitFont = null;
        atlasScale = -1f;
    }
}

package com.elytra.client;

import com.elytra.client.account.AccountManager;
import com.elytra.client.gui.ElytraAccountScreen;
import com.elytra.client.gui.ElytraAccountUi;
import com.elytra.client.gui.ElytraClickGUI;
import com.elytra.client.gui.ElytraMainMenu;
import com.elytra.client.gui.ElytraMenuOverlay;
import com.elytra.client.gui.ElytraScreen;
import com.elytra.client.gui.ElytraScreenTheme;
import com.elytra.client.gui.ElytraTheme;
import net.minecraft.client.gui.screen.TitleScreen;
import com.elytra.client.gui.render.ImGuiRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point.
 *
 * Owns the config, the click GUI and the toggle keybinding. The only rendering
 * hook is {@link #renderGui()}, called from {@code Window#swapBuffers} once the
 * frame has been composed and before it is presented - the one point in the frame
 * where the back buffer is complete and nothing else is going to touch it.
 */
public final class ElytraClient implements ClientModInitializer {

    public static final String MOD_ID = "elytra";
    public static final Logger LOGGER = LoggerFactory.getLogger("Elytra Client");

    /** Guards against the same physical keypress reopening the GUI it just closed. */
    private static final long TOGGLE_COOLDOWN_MS = 250L;

    private static ElytraClient instance;

    private static ElytraConfig config;
    private static ElytraClickGUI gui;
    private static ElytraMainMenu mainMenu;
    private static ElytraAccountUi accountUi;
    private static KeyBinding openKey;

    private static boolean savePending;
    private static long lastToggleMs;
    private static boolean lastOpenKeyDown;


    public static ElytraClient get() {
        return instance;
    }

    public static ElytraConfig config() {
        return config;
    }

    public static ElytraClickGUI gui() {
        return gui;
    }

    public static ElytraAccountUi accountUi() {
        return accountUi;
    }

    public static net.minecraft.client.option.KeyBinding openKey() {
        return openKey;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        config = new ElytraConfig();
        config.load();

        ElytraTheme.get().applyTheme(config.themeIndex());

        AccountManager.get().load();
        AccountManager.get().applySettingsTo(config);
        ElytraTheme.get().applyTheme(config.themeIndex());
        accountUi = new ElytraAccountUi();

        gui = new ElytraClickGUI();
        gui.build(config);
        mainMenu = new ElytraMainMenu();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elytra.open",
                InputUtil.Type.KEYSYM,
                config.openKey(),
                KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::onClientStopping);

        LOGGER.info("Elytra Client ready - {} local account(s)", AccountManager.get().accounts().size());
    }

    private void onClientTick(MinecraftClient client) {
        if (mainMenu != null) {
            mainMenu.flushPendingAction();
        }
        if (accountUi != null) {
            accountUi.flushPending();
        }

        // An account is required: whenever the title screen is up with nobody signed in,
        // put the sign-in screen in front of it. Skipped if ImGui could not start, so a
        // broken UI can never lock the player out of the game.
        if (client != null && !AccountManager.get().isSignedIn() && !ImGuiRenderer.isUnavailable()
                && client.currentScreen instanceof TitleScreen title) {
            ElytraAccountScreen gate = new ElytraAccountScreen(title);
            accountUi.open(gate, ElytraAccountUi.Mode.GATE);
            client.setScreen(gate);
        }

        if (savePending) {
            savePending = false;
            config.saveIfDirty();
        }

        // Manual toggle-key polling (more reliable than the registered KeyBinding,
        // which does not always pick up runtime setBoundKey changes).
        if (client != null && client.getWindow() != null) {
            boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), config.openKey())
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (down && !lastOpenKeyDown) {
                if (gui != null && gui.isOpen()) {
                    closeGui();
                } else if (gui != null && client.currentScreen == null) {
                    openGui();
                }
            }
            lastOpenKeyDown = down;
        }

        // sync theme from config if changed externally
    }

    private void onClientStopping(MinecraftClient client) {
        config.saveIfDirty();
        ImGuiRenderer.shutdown();
    }

    /** Opens the GUI. Refused while another screen owns the game, so menus stay usable. */
    public static void openGui() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || gui == null || gui.isOpen() || !AccountManager.get().isSignedIn()) {
            return;
        }
        if (System.currentTimeMillis() - lastToggleMs < TOGGLE_COOLDOWN_MS) {
            return;
        }
        if (client.currentScreen != null) {
            return;
        }
        gui.setOpen(true);
        client.setScreen(new ElytraScreen());
    }

    /** Closes the GUI and returns control to the world. */
    public static void closeGui() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (gui == null || !gui.isOpen()) {
            return;
        }
        gui.setOpen(false);
        lastToggleMs = System.currentTimeMillis();
        if (client != null && client.currentScreen instanceof ElytraScreen) {
            client.setScreen(null);
        }
    }

    /** Called from the screen's teardown path, which must never re-enter setScreen. */
    public static void onScreenRemoved() {
        if (gui == null) {
            return;
        }
        if (gui.isOpen()) {
            gui.setOpen(false);
        }
        lastToggleMs = System.currentTimeMillis();
    }

    /** Marks the config dirty; the write itself happens on the next client tick. */
    public static void requestConfigSave() {
        savePending = true;
    }

    public static void resetKeyState() {
        lastOpenKeyDown = true;
    }

    /** Re-reads the config and rebuilds the layout from it. */
    public static void applyConfig() {
        ElytraTheme.get().applyTheme(config().themeIndex());
        if (gui != null) {
            gui.build(config);
        }
    }

    public static void resetPanelLayout() {
        if (gui != null) {
            gui.resetLayout();
        }
    }

    /**
     * Draws one ImGui frame into the game's back buffer.
     *
     * Called from the swap hook on the render thread with the GL context current.
     */
    public static void renderGui() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        Window window = client.getWindow();
        int framebufferWidth = window.getFramebufferWidth();
        int framebufferHeight = window.getFramebufferHeight();
        if (framebufferWidth <= 0 || framebufferHeight <= 0 || window.getWidth() <= 0) {
            return;
        }

        boolean onTitle = client.currentScreen instanceof TitleScreen && mainMenu != null;
        boolean onMenu = ElytraScreenTheme.shouldTheme(client.currentScreen);
        boolean onAccount = client.currentScreen instanceof ElytraAccountScreen;
        if (onTitle && mainMenu.isWasVisible() == false && mainMenu.hasRendered()) {
            mainMenu.onReturn();
        }
        mainMenu.setWasVisible(onTitle);
        if (!onTitle && !onMenu && !onAccount && (gui == null || !gui.hasVisibleContent())) {
            return;
        }

        // Set the scale before init so the first atlas is built at the final size
        // instead of being torn down and rebuilt on the very first menu frame.
        if (onTitle || onMenu || onAccount) {
            ElytraClickGUI.applyScale(ElytraTheme.get(), framebufferWidth, framebufferHeight);
        }

        if (!ImGuiRenderer.init()) {
            return;
        }

        if (onAccount) {
            accountUi.render(ElytraTheme.get(), gui.input(),
                    framebufferWidth, framebufferHeight, window.getWidth(), window.getHandle());
            return;
        }
        if (onTitle) {
            mainMenu.render(ElytraTheme.get(), gui.input(),
                    framebufferWidth, framebufferHeight, window.getWidth(), window.getHandle());
            return;
        }
        if (onMenu) {
            // Feed the live cursor before the ImGui frame so button hover is accurate.
            gui.input().update(window.getHandle(), framebufferWidth, window.getWidth());
            ElytraMenuOverlay.render(ElytraTheme.get(), framebufferWidth, framebufferHeight);
            return;
        }

        gui.renderFrame(framebufferWidth, framebufferHeight, window.getWidth(), window.getHandle());
    }

    /** Default toggle key, exposed for the settings window. */
    public static int defaultOpenKey() {
        return GLFW.GLFW_KEY_RIGHT_SHIFT;
    }
}

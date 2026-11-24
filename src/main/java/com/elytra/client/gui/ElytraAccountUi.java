package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import com.elytra.client.ElytraConfig;
import com.elytra.client.account.Account;
import com.elytra.client.account.AccountManager;
import com.elytra.client.account.AvatarStore;
import com.elytra.client.account.PasswordHasher;
import com.elytra.client.gui.render.AvatarTextures;
import com.elytra.client.gui.render.EffectRenderer;
import com.elytra.client.gui.render.GlowRenderer;
import com.elytra.client.gui.render.ImGuiInputHandler;
import com.elytra.client.gui.render.ImGuiRenderer;
import com.elytra.client.util.ColorUtils;
import com.elytra.client.util.RenderUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The account screen's ImGui content.
 *
 * <ul>
 *   <li>{@link Mode#GATE}: required sign-in / account creation, shown on the title screen
 *       until someone is signed in.</li>
 *   <li>{@link Mode#SETTINGS}: profile picture, username, preferences, password and
 *       sign-out for the signed-in account, opened from the chip above the character.</li>
 * </ul>
 *
 * Password hashing and image decoding happen on worker threads; results are picked up
 * at the start of the next frame, so the render thread never stalls.
 */
public final class ElytraAccountUi {

    public enum Mode { GATE, SETTINGS }

    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoNav;

    private static final String[] EFFECT_NAMES = {"None", "Rain", "Snow", "Leaves"};
    private static final int ERROR_COLOR = ColorUtils.hex("#FF8A9A");
    private static final int OK_COLOR = ColorUtils.hex("#8CEBB0");

    private final AccountManager accounts = AccountManager.get();
    private final EffectRenderer effect = new EffectRenderer();

    private Mode mode = Mode.GATE;
    private ElytraAccountScreen screen;
    private ElytraTheme t;
    private boolean createTab;

    private final ImString user = new ImString(40);
    private final ImString pass = new ImString(100);
    private final ImString confirm = new ImString(100);
    private final ImString current = new ImString(100);
    private final ImString rename = new ImString(40);
    private final ImString pathField = new ImString(520);
    private final ImString deletePass = new ImString(100);
    private final ImBoolean remember = new ImBoolean(true);
    private final ImInt themeSel = new ImInt();
    private final ImInt effectSel = new ImInt();
    private final float[] density = {1f};
    private final float[] scaleValue = {1f};

    private int regColor;
    private BufferedImage regImage;
    private int regTexture;

    private String error = "";
    private String info = "";
    private boolean confirmDelete;

    private CompletableFuture<byte[]> pendingHash;
    private Consumer<byte[]> pendingDone;
    private volatile BufferedImage pickedImage;
    private volatile String pickError;
    private Thread picker;
    private Runnable pendingAction;

    private int focusRequest = -1;
    private int activeField = -1;
    private int fieldCounter;
    private int fieldCount;
    private long lastNanos;

    // ---- Lifecycle -------------------------------------------------------------

    /** Prepares the UI for a freshly opened {@link ElytraAccountScreen}. */
    public void open(ElytraAccountScreen newScreen, Mode newMode) {
        screen = newScreen;
        mode = newMode;
        createTab = !accounts.hasAccounts();
        clearFields();
        error = "";
        info = "";
        confirmDelete = false;
        ElytraConfig config = ElytraClient.config();
        themeSel.set(config.themeIndex());
        effectSel.set(config.effectMode());
        density[0] = config.rainDensity();
        scaleValue[0] = config.guiScale();
        Account active = accounts.active();
        if (mode == Mode.SETTINGS && active != null) {
            rename.set(active.username());
        }
        discardPreview();
        regColor = 0;
    }

    public boolean canClose() {
        return mode == Mode.SETTINGS;
    }

    public void requestClose() {
        if (pendingAction == null && screen != null) {
            ElytraAccountScreen target = screen;
            pendingAction = target::closeToParent;
        }
    }

    /** Tab / Shift+Tab between text fields. */
    public void requestTab(boolean backwards) {
        if (fieldCount <= 0) {
            return;
        }
        int from = activeField < 0 ? (backwards ? 0 : -1) : activeField;
        focusRequest = Math.floorMod(from + (backwards ? -1 : 1), fieldCount);
    }

    public void onScreenRemoved() {
        clearFields();
        discardPreview();
        pendingHash = null;
        pendingDone = null;
        screen = null;
    }

    /** Runs actions that must happen outside the render call (screen changes, shutdown). */
    public void flushPending() {
        Runnable action = pendingAction;
        pendingAction = null;
        if (action != null) {
            action.run();
        }
    }

    private void clearFields() {
        user.set("");
        pass.set("");
        confirm.set("");
        current.set("");
        rename.set("");
        pathField.set("");
        deletePass.set("");
    }

    private void discardPreview() {
        regImage = null;
        AvatarTextures.delete(regTexture);
        regTexture = 0;
    }

    // ---- Frame -----------------------------------------------------------------

    public void render(ElytraTheme theme, ImGuiInputHandler input,
                       int fbW, int fbH, int winW, long windowHandle) {
        this.t = theme;
        long now = System.nanoTime();
        float delta = lastNanos == 0L ? 1f / 60f
                : Math.min(0.1f, Math.max(0.001f, (now - lastNanos) / 1_000_000_000f));
        lastNanos = now;

        ImGuiRenderer.ensureFonts(theme.uiScale());
        input.update(windowHandle, fbW, winW);
        ImGuiRenderer.beginFrame(fbW, fbH);
        theme.tick(delta);
        theme.applyStyle();
        pumpAsync();

        drawBackdrop(ImGui.getBackgroundDrawList(), fbW, fbH, delta);

        if (ImGuiRenderer.fontBody() != null) {
            ImGui.pushFont(ImGuiRenderer.fontBody());
        }
        drawCard(fbW, fbH);
        if (ImGuiRenderer.fontBody() != null) {
            ImGui.popFont();
        }
        ImGuiRenderer.endFrame();
    }

    private void drawBackdrop(ImDrawList dl, int w, int h, float delta) {
        int top = ColorUtils.fade(t.panelBackgroundTop, 0.97f);
        int bottom = t.mainBackground;
        dl.addRectFilledMultiColor(0f, 0f, w, h, top, top, bottom, bottom);
        ElytraConfig config = ElytraClient.config();
        if (config != null && config.rainEnabled() && config.effectMode() != EffectRenderer.NONE) {
            effect.setDensity(config.rainDensity());
            effect.render(dl, config.effectMode(), delta, 1f, w, h, t);
        }
    }

    private void pumpAsync() {
        if (pendingHash != null && pendingHash.isDone()) {
            byte[] result = null;
            try {
                result = pendingHash.join();
            } catch (RuntimeException failure) {
                ElytraClient.LOGGER.warn("Password hashing failed", failure);
            }
            Consumer<byte[]> done = pendingDone;
            pendingHash = null;
            pendingDone = null;
            if (done != null) {
                done.accept(result);
            }
        }
        BufferedImage image = pickedImage;
        if (image != null) {
            pickedImage = null;
            onAvatarPicked(image);
        }
        String problem = pickError;
        if (problem != null) {
            pickError = null;
            error = problem;
            info = "";
        }
    }

    private boolean busy() {
        return pendingHash != null || pendingAction != null;
    }

    // ---- Card ------------------------------------------------------------------

    private void drawCard(int fbW, int fbH) {
        boolean settings = mode == Mode.SETTINGS;
        float width = t.s(390f);
        int flags = WINDOW_FLAGS;
        if (settings) {
            ImGui.setNextWindowSize(width, Math.min(t.s(600f), fbH - t.s(40f)), ImGuiCond.Always);
        } else {
            flags |= ImGuiWindowFlags.AlwaysAutoResize;
            ImGui.setNextWindowSize(width, 0f, ImGuiCond.Always);
        }
        ImGui.setNextWindowPos(fbW * 0.5f, fbH * 0.5f, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, t.s(20f), t.s(18f));
        boolean visible = ImGui.begin("##elytra_account", flags);
        ImGui.popStyleVar();

        fieldCounter = 0;
        if (visible) {
            float x = ImGui.getWindowPosX();
            float y = ImGui.getWindowPosY();
            GlowRenderer.panelGlow(ImGui.getBackgroundDrawList(), x, y,
                    x + ImGui.getWindowWidth(), y + ImGui.getWindowHeight(), t.panelRadius(), 1f);
            drawHeader();
            if (settings) {
                drawSettings();
            } else {
                drawGate();
            }
        }
        fieldCount = fieldCounter;
        ImGui.end();
    }

    private void drawHeader() {
        ImDrawList dl = ImGui.getWindowDrawList();
        float inner = ImGui.getContentRegionAvailX();
        int logo = ImGuiRenderer.logoTexture();
        float size = t.s(46f);
        if (logo != 0 && ImGuiRenderer.logoWidth() > 0) {
            float ratio = ImGuiRenderer.logoWidth() / (float) ImGuiRenderer.logoHeight();
            float w = size;
            float h = size / ratio;
            float sx = ImGui.getCursorScreenPosX() + (inner - w) * 0.5f;
            float sy = ImGui.getCursorScreenPosY();
            dl.addImageRounded(logo, sx, sy, sx + w, sy + h, 0f, 0f, 1f, 1f,
                    ColorUtils.hex("#FFFFFF"), t.s(8f), 0);
            ImGui.dummy(inner, h);
        }
        centered("ELYTRA", ImGuiRenderer.fontHuge(), ImGuiRenderer.fontHugeSize() * 0.62f, t.text);
        ImGui.dummy(0f, t.s(2f));
    }

    private void centered(String text, imgui.ImFont font, float size, int color) {
        if (font == null) {
            return;
        }
        float inner = ImGui.getContentRegionAvailX();
        ImDrawList dl = ImGui.getWindowDrawList();
        float w = font.calcTextSizeAX(size, 4096f, 0f, text);
        float sx = ImGui.getCursorScreenPosX() + (inner - w) * 0.5f;
        float sy = ImGui.getCursorScreenPosY();
        RenderUtils.text(dl, font, size, sx, sy, color, text);
        ImGui.dummy(inner, size + t.s(4f));
    }

    private void caption(String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, t.textSecondary);
        ImGui.text(text);
        ImGui.popStyleColor();
    }

    private void section(String text) {
        ImGui.dummy(0f, t.s(4f));
        ImGui.pushStyleColor(ImGuiCol.Text, t.primaryPink);
        ImGui.text(text);
        ImGui.popStyleColor();
        ImGui.separator();
        ImGui.dummy(0f, t.s(2f));
    }

    private void status() {
        if (!error.isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, ERROR_COLOR);
            ImGui.textWrapped(error);
            ImGui.popStyleColor();
        } else if (!info.isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, OK_COLOR);
            ImGui.textWrapped(info);
            ImGui.popStyleColor();
        }
    }

    // ---- Widgets ---------------------------------------------------------------

    /** Labelled text field. Returns true when Enter was pressed inside it. */
    private boolean field(String id, String label, ImString buffer, boolean password) {
        int index = fieldCounter++;
        caption(label);
        if (focusRequest == index) {
            ImGui.setKeyboardFocusHere();
            focusRequest = -1;
        }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        int flags = ImGuiInputTextFlags.EnterReturnsTrue | (password ? ImGuiInputTextFlags.Password : 0);
        boolean enter = ImGui.inputText("##" + id, buffer, flags);
        if (ImGui.isItemActive()) {
            activeField = index;
        }
        return enter;
    }

    private boolean button(String label, float width, boolean primary) {
        if (primary) {
            ImGui.pushStyleColor(ImGuiCol.Button, ColorUtils.withAlpha(t.primaryPink, 0.5f));
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ColorUtils.withAlpha(t.primaryPink, 0.72f));
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, ColorUtils.withAlpha(t.primaryPink, 0.92f));
        }
        boolean clicked = ImGui.button(busy() ? "Working..." : label, width, t.s(30f));
        if (primary) {
            ImGui.popStyleColor(3);
        }
        return clicked && !busy();
    }

    private void themeAndEffectCombos() {
        caption("Theme");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##theme", themeSel, t.themeNames())) {
            int index = themeSel.get();
            ElytraClient.config().setThemeIndex(index);
            t.applyTheme(index);
            ElytraClient.requestConfigSave();
        }
        caption("Background effect");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##effect", effectSel, EFFECT_NAMES)) {
            ElytraClient.config().setEffectMode(effectSel.get());
            ElytraClient.requestConfigSave();
        }
    }

    private void swatches(int selected, Consumer<Integer> onPick) {
        int[] colors = AccountManager.AVATAR_COLORS;
        float d = t.s(20f);
        ImDrawList dl = ImGui.getWindowDrawList();
        for (int i = 0; i < colors.length; i++) {
            if (i > 0) {
                ImGui.sameLine(0f, t.s(7f));
            }
            float sx = ImGui.getCursorScreenPosX();
            float sy = ImGui.getCursorScreenPosY();
            if (ImGui.invisibleButton("##swatch" + i, d, d)) {
                onPick.accept(i);
            }
            dl.addCircleFilled(sx + d * 0.5f, sy + d * 0.5f, d * 0.5f, colors[i], 20);
            if (i == selected) {
                dl.addCircle(sx + d * 0.5f, sy + d * 0.5f, d * 0.5f + t.s(2.5f), t.text, 24, 1.6f);
            }
        }
    }

    // ---- Gate ------------------------------------------------------------------

    private void drawGate() {
        centered(createTab ? "Create your account" : "Welcome back",
                ImGuiRenderer.fontTitle(), ImGuiRenderer.fontTitleSize() * 1.15f, t.primaryPink);
        ImGui.pushStyleColor(ImGuiCol.Text, t.textSecondary);
        ImGui.textWrapped("An account is required to use Elytra Client. It is stored on this computer only.");
        ImGui.popStyleColor();
        ImGui.dummy(0f, t.s(4f));

        float half = (ImGui.getContentRegionAvailX() - t.s(6f)) * 0.5f;
        if (tabButton("Sign in", !createTab, half)) {
            switchTab(false);
        }
        ImGui.sameLine(0f, t.s(6f));
        if (tabButton("Create account", createTab, half)) {
            switchTab(true);
        }
        ImGui.dummy(0f, t.s(4f));

        if (createTab) {
            drawRegister();
        } else {
            drawLogin();
        }

        ImGui.dummy(0f, t.s(4f));
        status();
        ImGui.dummy(0f, t.s(2f));
        if (ImGui.smallButton("Quit game") && pendingAction == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            pendingAction = client::scheduleStop;
        }
    }

    private boolean tabButton(String label, boolean selected, float width) {
        if (selected) {
            ImGui.pushStyleColor(ImGuiCol.Button, ColorUtils.withAlpha(t.primaryPink, 0.3f));
        }
        boolean clicked = ImGui.button(label, width, t.s(26f));
        if (selected) {
            ImGui.popStyleColor();
        }
        return clicked;
    }

    private void switchTab(boolean toCreate) {
        if (createTab == toCreate) {
            return;
        }
        createTab = toCreate;
        error = "";
        info = "";
        pass.set("");
        confirm.set("");
    }

    private void drawLogin() {
        boolean enter = field("user", "Username", user, false);
        enter |= field("pass", "Password", pass, true);
        ImGui.checkbox("Keep me signed in", remember);
        ImGui.dummy(0f, t.s(2f));
        if (button("Sign in", ImGui.getContentRegionAvailX(), true) || (enter && !busy())) {
            submitLogin();
        }

        List<Account> known = accounts.accounts();
        if (!known.isEmpty()) {
            ImGui.dummy(0f, t.s(4f));
            caption("Accounts on this computer");
            ImDrawList dl = ImGui.getWindowDrawList();
            float radius = t.s(15f);
            int shown = Math.min(known.size(), 8);
            for (int i = 0; i < shown; i++) {
                Account account = known.get(i);
                if (i > 0) {
                    ImGui.sameLine(0f, t.s(8f));
                }
                float sx = ImGui.getCursorScreenPosX();
                float sy = ImGui.getCursorScreenPosY();
                if (ImGui.invisibleButton("##known" + i, radius * 2f, radius * 2f)) {
                    user.set(account.username());
                    focusRequest = 1;
                }
                AccountChip.drawAvatar(t, dl, sx + radius, sy + radius, radius, account, 1f);
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(account.username());
                }
            }
        }
    }

    private void drawRegister() {
        boolean enter = field("ruser", "Username (3-16: letters, numbers, _)", user, false);
        enter |= field("rpass", "Password (at least 6 characters)", pass, true);
        enter |= field("rconf", "Confirm password", confirm, true);

        section("Profile picture");
        drawPictureControls(regColor, regTexture, user.get().trim(), picked -> regColor = picked);

        section("Settings");
        themeAndEffectCombos();
        ImGui.dummy(0f, t.s(2f));
        ImGui.checkbox("Keep me signed in", remember);
        ImGui.dummy(0f, t.s(2f));
        if (button("Create account", ImGui.getContentRegionAvailX(), true) || (enter && !busy())) {
            submitRegister();
        }
    }

    /** Preview, "Choose picture" button, path box and colour swatches (shared by both modes). */
    private void drawPictureControls(int colorIndex, int texture, String name, Consumer<Integer> onColor) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float radius = t.s(26f);
        float sx = ImGui.getCursorScreenPosX();
        float sy = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##avatarPreview", radius * 2f, radius * 2f);
        AccountChip.drawAvatar(t, dl, sx + radius, sy + radius, radius, texture, colorIndex, name, 1f);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Choose a picture");
        }
        if (clicked) {
            startPicker();
        }
        ImGui.sameLine(0f, t.s(12f));
        ImGui.beginGroup();
        if (ImGui.button("Choose picture...", 0f, t.s(26f))) {
            startPicker();
        }
        caption("PNG, JPG, GIF or BMP");
        ImGui.endGroup();

        ImGui.dummy(0f, t.s(2f));
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - t.s(52f));
        ImGui.inputText("##path", pathField);
        ImGui.sameLine(0f, t.s(6f));
        if (ImGui.button("Use", t.s(46f), 0f)) {
            startPathLoad(pathField.get());
        }
        caption("Or paste an image file path above");
        ImGui.dummy(0f, t.s(2f));
        caption("Default avatar colour");
        swatches(colorIndex, onColor);
    }

    // ---- Settings mode -----------------------------------------------------------

    private void drawSettings() {
        Account account = accounts.active();
        if (account == null) {
            mode = Mode.GATE;
            return;
        }
        centered("Account", ImGuiRenderer.fontTitle(), ImGuiRenderer.fontTitleSize() * 1.15f, t.primaryPink);

        // Profile
        ImDrawList dl = ImGui.getWindowDrawList();
        float radius = t.s(30f);
        float sx = ImGui.getCursorScreenPosX();
        float sy = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##avatar", radius * 2f, radius * 2f);
        AccountChip.drawAvatar(t, dl, sx + radius, sy + radius, radius, account, 1f);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Change picture");
        }
        if (clicked) {
            startPicker();
        }
        ImGui.sameLine(0f, t.s(14f));
        ImGui.beginGroup();
        centeredLeft(account.username(), ImGuiRenderer.fontTitle(), ImGuiRenderer.fontTitleSize() * 1.2f, t.text);
        caption("Created " + Instant.ofEpochMilli(account.createdAt()).atZone(ZoneId.systemDefault()).toLocalDate());
        ImGui.endGroup();

        section("Profile picture");
        if (ImGui.button("Choose picture...", 0f, t.s(26f))) {
            startPicker();
        }
        if (account.hasAvatar()) {
            ImGui.sameLine(0f, t.s(6f));
            if (ImGui.button("Remove picture", 0f, t.s(26f))) {
                accounts.clearAvatar(account);
                AvatarTextures.release(account.id);
                info = "Picture removed.";
                error = "";
            }
        }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - t.s(52f));
        ImGui.inputText("##path", pathField);
        ImGui.sameLine(0f, t.s(6f));
        if (ImGui.button("Use", t.s(46f), 0f)) {
            startPathLoad(pathField.get());
        }
        caption("Default avatar colour");
        swatches(account.avatarColor(), picked -> accounts.setAvatarColor(account, picked));

        section("Username");
        boolean enter = field("rename", "Username", rename, false);
        if (button("Save username", ImGui.getContentRegionAvailX(), false) || (enter && !busy())) {
            String problem = accounts.rename(account, rename.get());
            error = problem == null ? "" : problem;
            info = problem == null ? "Username updated." : "";
        }

        section("Preferences");
        themeAndEffectCombos();
        if (effectSel.get() != EffectRenderer.NONE) {
            caption("Effect density");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.sliderFloat("##density", density, 0.15f, 1.0f, "%.2f")) {
                ElytraClient.config().setRainDensity(density[0]);
                ElytraClient.requestConfigSave();
            }
        }
        caption("Interface scale");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.sliderFloat("##scale", scaleValue, 0.7f, 1.6f, "%.2f");
        // Applied on release: rescaling rebuilds the font atlas, which is too heavy to do mid-drag.
        if (ImGui.isItemDeactivatedAfterEdit()) {
            ElytraClient.config().setGuiScale(scaleValue[0]);
            ElytraClient.requestConfigSave();
        }

        section("Password");
        field("cur", "Current password", current, true);
        field("new", "New password", pass, true);
        field("new2", "Confirm new password", confirm, true);
        if (button("Update password", ImGui.getContentRegionAvailX(), false)) {
            submitPasswordChange(account);
        }

        section("Session");
        float half = (ImGui.getContentRegionAvailX() - t.s(6f)) * 0.5f;
        if (button("Sign out", half, false)) {
            accounts.signOut();
            switchToGate();
        }
        ImGui.sameLine(0f, t.s(6f));
        if (ImGui.button(confirmDelete ? "Cancel delete" : "Delete account", half, t.s(30f))) {
            confirmDelete = !confirmDelete;
            deletePass.set("");
            error = "";
        }
        if (confirmDelete) {
            ImGui.pushStyleColor(ImGuiCol.Text, ERROR_COLOR);
            ImGui.textWrapped("This permanently deletes the account and its picture. Enter your password to confirm.");
            ImGui.popStyleColor();
            boolean confirmEnter = field("delpass", "Password", deletePass, true);
            if (button("Delete forever", ImGui.getContentRegionAvailX(), false) || (confirmEnter && !busy())) {
                submitDelete(account);
            }
        }

        ImGui.dummy(0f, t.s(4f));
        status();
        ImGui.dummy(0f, t.s(2f));
        if (button("Done", ImGui.getContentRegionAvailX(), true)) {
            requestClose();
        }
    }

    private void centeredLeft(String text, imgui.ImFont font, float size, int color) {
        if (font == null) {
            return;
        }
        RenderUtils.text(ImGui.getWindowDrawList(), font, size,
                ImGui.getCursorScreenPosX(), ImGui.getCursorScreenPosY(), color, text);
        ImGui.dummy(font.calcTextSizeAX(size, 4096f, 0f, text), size + t.s(2f));
    }

    private void switchToGate() {
        mode = Mode.GATE;
        createTab = false;
        clearFields();
        error = "";
        info = "";
        confirmDelete = false;
        discardPreview();
    }

    // ---- Actions ---------------------------------------------------------------

    private void startHash(String password, byte[] salt, int iterations, Consumer<byte[]> done) {
        pendingDone = done;
        pendingHash = accounts.hashAsync(password, salt, iterations);
    }

    private void submitLogin() {
        String name = user.get().trim();
        String password = pass.get();
        pass.set("");
        error = "";
        info = "";
        if (name.isEmpty() || password.isEmpty()) {
            error = "Enter your username and password.";
            return;
        }
        long locked = accounts.lockedForMillis(name);
        if (locked > 0) {
            error = "Too many failed attempts. Try again in " + (locked / 1000 + 1) + "s.";
            return;
        }
        Account account = accounts.find(name);
        if (account == null) {
            accounts.recordFailure(name);
            error = "Incorrect username or password.";
            return;
        }
        boolean keep = remember.get();
        startHash(password, accounts.saltOf(account), accounts.iterationsOf(account), derived -> {
            if (derived != null && accounts.verify(account, derived)) {
                accounts.clearFailures(name);
                accounts.signIn(account, keep);
                finishAuth();
            } else {
                accounts.recordFailure(name);
                error = "Incorrect username or password.";
            }
        });
    }

    private void submitRegister() {
        String name = user.get().trim();
        String password = pass.get();
        String again = confirm.get();
        error = "";
        info = "";
        String problem = AccountManager.checkUsername(name);
        if (problem == null) {
            problem = AccountManager.checkPassword(password);
        }
        if (problem == null && !password.equals(again)) {
            problem = "Passwords do not match.";
        }
        if (problem == null && accounts.isUsernameTaken(name, null)) {
            problem = "That username is already taken.";
        }
        if (problem != null) {
            error = problem;
            return;
        }
        pass.set("");
        confirm.set("");
        byte[] salt = PasswordHasher.newSalt();
        boolean keep = remember.get();
        int color = regColor;
        BufferedImage picture = regImage;
        startHash(password, salt, PasswordHasher.ITERATIONS, derived -> {
            if (derived == null) {
                error = "Could not create the account. Try again.";
                return;
            }
            Account created = accounts.create(name, salt, derived, color, ElytraClient.config());
            if (created == null) {
                error = "That username is already taken.";
                return;
            }
            if (picture != null) {
                accounts.setAvatar(created, picture);
            }
            accounts.signIn(created, keep);
            finishAuth();
        });
    }

    private void submitPasswordChange(Account account) {
        String old = current.get();
        String fresh = pass.get();
        String again = confirm.get();
        error = "";
        info = "";
        String problem = AccountManager.checkPassword(fresh);
        if (old.isEmpty()) {
            problem = "Enter your current password.";
        } else if (problem == null && !fresh.equals(again)) {
            problem = "New passwords do not match.";
        }
        if (problem != null) {
            error = problem;
            return;
        }
        current.set("");
        pass.set("");
        confirm.set("");
        startHash(old, accounts.saltOf(account), accounts.iterationsOf(account), derived -> {
            if (derived == null || !accounts.verify(account, derived)) {
                error = "Current password is incorrect.";
                return;
            }
            byte[] salt = PasswordHasher.newSalt();
            startHash(fresh, salt, PasswordHasher.ITERATIONS, newKey -> {
                if (newKey == null) {
                    error = "Could not update the password.";
                    return;
                }
                accounts.setPassword(account, salt, newKey);
                info = "Password updated.";
            });
        });
    }

    private void submitDelete(Account account) {
        String password = deletePass.get();
        deletePass.set("");
        error = "";
        if (password.isEmpty()) {
            error = "Enter your password to delete the account.";
            return;
        }
        startHash(password, accounts.saltOf(account), accounts.iterationsOf(account), derived -> {
            if (derived == null || !accounts.verify(account, derived)) {
                error = "Password is incorrect.";
                return;
            }
            AvatarTextures.release(account.id);
            accounts.delete(account);
            switchToGate();
        });
    }

    /** Signed in: apply this account's settings and return to the title screen. */
    private void finishAuth() {
        ElytraAccountScreen target = screen;
        clearFields();
        discardPreview();
        pendingAction = () -> {
            accounts.applySettingsTo(ElytraClient.config());
            ElytraClient.applyConfig();
            ElytraClient.requestConfigSave();
            if (target != null) {
                target.closeToParent();
            }
        };
    }

    // ---- Avatar picking --------------------------------------------------------

    private void startPicker() {
        if (picker != null && picker.isAlive()) {
            return;
        }
        picker = new Thread(() -> {
            try {
                if (GraphicsEnvironment.isHeadless()) {
                    pickError = "No file dialog is available. Paste an image path instead.";
                    return;
                }
                FileDialog dialog = new FileDialog((Frame) null, "Choose a profile picture", FileDialog.LOAD);
                dialog.setFile("*.png;*.jpg;*.jpeg;*.gif;*.bmp");
                dialog.setFilenameFilter((dir, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                            || lower.endsWith(".gif") || lower.endsWith(".bmp");
                });
                dialog.setVisible(true);
                String file = dialog.getFile();
                String directory = dialog.getDirectory();
                dialog.dispose();
                if (file != null && directory != null) {
                    loadAvatarFile(Path.of(directory, file));
                }
            } catch (Throwable failure) {
                pickError = "Could not open the file dialog. Paste an image path instead.";
            }
        }, "Elytra-AvatarPicker");
        picker.setDaemon(true);
        picker.start();
    }

    private void startPathLoad(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.isEmpty()) {
            error = "Paste the path of an image file first.";
            return;
        }
        String path = cleaned;
        Thread thread = new Thread(() -> {
            try {
                loadAvatarFile(Path.of(path));
            } catch (RuntimeException invalid) {
                pickError = "That is not a valid file path.";
            }
        }, "Elytra-AvatarLoad");
        thread.setDaemon(true);
        thread.start();
    }

    /** Worker thread: decode and square the image, then hand it to the render thread. */
    private void loadAvatarFile(Path file) {
        try {
            pickedImage = AvatarStore.readSquare(file);
        } catch (java.io.IOException | RuntimeException failure) {
            String message = failure.getMessage();
            pickError = message == null || message.isEmpty() ? "Could not read that image." : message;
        }
    }

    private void onAvatarPicked(BufferedImage image) {
        error = "";
        if (mode == Mode.SETTINGS && accounts.active() != null) {
            Account account = accounts.active();
            String problem = accounts.setAvatar(account, image);
            AvatarTextures.release(account.id);
            error = problem == null ? "" : problem;
            info = problem == null ? "Picture updated." : "";
            pathField.set("");
        } else {
            AvatarTextures.delete(regTexture);
            regImage = image;
            regTexture = ImGuiRenderer.uploadTexture(image, false);
            pathField.set("");
        }
    }
}

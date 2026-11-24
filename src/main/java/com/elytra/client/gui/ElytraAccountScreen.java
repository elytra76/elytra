package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Owns keyboard and mouse input while the account UI is up, exactly like
 * {@link ElytraScreen} does for the click GUI. It draws nothing itself; the ImGui
 * frame is painted by {@link ElytraAccountUi} at the end of the frame.
 */
public final class ElytraAccountScreen extends Screen {

    private final Screen parent;

    public ElytraAccountScreen(Screen parent) {
        super(Text.literal("Elytra Account"));
        this.parent = parent;
    }

    /** Returns to the screen that was showing before (the title screen). */
    public void closeToParent() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Escape is handled by hand so a required sign-in can never be dismissed. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally empty; ImGui paints over the finished frame.
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ElytraClient.gui().input().onScroll(horizontalAmount, verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.getKeycode();
        ElytraAccountUi ui = ElytraClient.accountUi();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (ui.canClose()) {
                ui.requestClose();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            ui.requestTab((input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        ElytraClient.gui().input().onKey(key, true);
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        ElytraClient.gui().input().onKey(input.getKeycode(), false);
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.isValidChar()) {
            ElytraClient.gui().input().onChar(input.codepoint());
        }
        return true;
    }

    @Override
    public void removed() {
        ElytraClient.accountUi().onScreenRemoved();
        ElytraClient.gui().input().reset();
    }
}

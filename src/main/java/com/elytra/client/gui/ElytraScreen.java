package com.elytra.client.gui;

import com.elytra.client.ElytraClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The screen that exists only to own input while the ImGui GUI is open.
 *
 * It draws nothing: the world stays visible and ImGui paints itself at the end of
 * the frame, over the finished back buffer. Minecraft still routes key, char and
 * scroll events here, which is exactly why this class exists - it lets the GUI
 * receive input without installing a single GLFW callback.
 */
public final class ElytraScreen extends Screen {

    public ElytraScreen() {
        super(Text.literal("Elytra Client"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally empty. ImGui draws over the finished frame; the vanilla
        // background dimmer is skipped so the world stays readable through the GUI.
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Buttons are polled from GLFW each frame, so the click itself only has to be
        // swallowed to keep it away from the world.
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
        if (ElytraClient.gui() != null && ElytraClient.gui().settings().isListening()) {
            ElytraClient.gui().settings().onKey(key);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == ElytraClient.config().openKey()) {
            ElytraClient.closeGui();
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
        ElytraClient.onScreenRemoved();
    }
}

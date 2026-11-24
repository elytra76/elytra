package com.elytra.client.gui.render;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import org.lwjgl.glfw.GLFW;

/**
 * Feeds mouse and keyboard state into ImGui.
 *
 * Two sources, on purpose:
 *
 * <ul>
 *   <li>Mouse position and buttons are polled from GLFW every frame. Minecraft's
 *       click records hand us coordinates only through obfuscated accessors and
 *       the polled values are always current, even during a drag.</li>
 *   <li>Keys and characters arrive through {@code ElytraScreen}, which Minecraft
 *       already routes for us. No GLFW callback is ever installed, so the game's
 *       own input handling is untouched.</li>
 * </ul>
 */
public final class ImGuiInputHandler {

    private static final int POLLED_BUTTONS = 5;

    private final double[] cursorX = new double[1];
    private final double[] cursorY = new double[1];
    private final boolean[] buttonState = new boolean[POLLED_BUTTONS];
    private final boolean[] lastButtonState = new boolean[POLLED_BUTTONS];

    private float pendingScrollX;
    private float pendingScrollY;

    private boolean feedInput = true;

    /** Drops every held key/button so nothing sticks after the GUI closes. */
    public void reset() {
        if (!ImGuiRenderer.isInitialized()) {
            return;
        }
        ImGuiIO io = ImGui.getIO();
        for (int button = 0; button < POLLED_BUTTONS; button++) {
            io.addMouseButtonEvent(button, false);
            lastButtonState[button] = false;
            buttonState[button] = false;
        }
        releaseModifiers(io);
        pendingScrollX = 0f;
        pendingScrollY = 0f;
    }

    /** Called once per frame, after {@link ImGuiRenderer#beginFrame}. */
    public void update(long windowHandle, int framebufferWidth, int windowWidth) {
        ImGuiIO io = ImGui.getIO();
        if (!feedInput) {
            reset();
            return;
        }

        float ratio = windowWidth > 0 ? framebufferWidth / (float) windowWidth : 1f;

        GLFW.glfwGetCursorPos(windowHandle, cursorX, cursorY);
        io.addMousePosEvent((float) (cursorX[0] * ratio), (float) (cursorY[0] * ratio));

        for (int button = 0; button < POLLED_BUTTONS; button++) {
            boolean down = GLFW.glfwGetMouseButton(windowHandle, button) == GLFW.GLFW_PRESS;
            buttonState[button] = down;
            if (down != lastButtonState[button]) {
                io.addMouseButtonEvent(button, down);
                lastButtonState[button] = down;
            }
        }

        if (pendingScrollX != 0f || pendingScrollY != 0f) {
            io.addMouseWheelEvent(pendingScrollX, pendingScrollY);
            pendingScrollX = 0f;
            pendingScrollY = 0f;
        }

        pollModifiers(io, windowHandle);
    }

    private void pollModifiers(ImGuiIO io, long windowHandle) {
        apply(io, ImGuiKey.LeftShift, windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT);
        apply(io, ImGuiKey.RightShift, windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT);
        apply(io, ImGuiKey.LeftCtrl, windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL);
        apply(io, ImGuiKey.RightCtrl, windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL);
        apply(io, ImGuiKey.LeftAlt, windowHandle, GLFW.GLFW_KEY_LEFT_ALT);
        apply(io, ImGuiKey.RightAlt, windowHandle, GLFW.GLFW_KEY_RIGHT_ALT);
        apply(io, ImGuiKey.LeftSuper, windowHandle, GLFW.GLFW_KEY_LEFT_SUPER);
        apply(io, ImGuiKey.RightSuper, windowHandle, GLFW.GLFW_KEY_RIGHT_SUPER);
    }

    private static void apply(ImGuiIO io, int imguiKey, long windowHandle, int glfwKey) {
        io.addKeyEvent(imguiKey, GLFW.glfwGetKey(windowHandle, glfwKey) == GLFW.GLFW_PRESS);
    }

    private static void releaseModifiers(ImGuiIO io) {
        io.addKeyEvent(ImGuiKey.LeftShift, false);
        io.addKeyEvent(ImGuiKey.RightShift, false);
        io.addKeyEvent(ImGuiKey.LeftCtrl, false);
        io.addKeyEvent(ImGuiKey.RightCtrl, false);
        io.addKeyEvent(ImGuiKey.LeftAlt, false);
        io.addKeyEvent(ImGuiKey.RightAlt, false);
        io.addKeyEvent(ImGuiKey.LeftSuper, false);
        io.addKeyEvent(ImGuiKey.RightSuper, false);
    }

    /** Forwarded from the screen. {@code glfwKey} is a GLFW key code. */
    public void onKey(int glfwKey, boolean pressed) {
        if (!ImGuiRenderer.isInitialized()) {
            return;
        }
        int mapped = mapKey(glfwKey);
        if (mapped == ImGuiKey.None) {
            return;
        }
        ImGui.getIO().addKeyEvent(mapped, pressed);
    }

    public void onChar(int codepoint) {
        if (!ImGuiRenderer.isInitialized() || codepoint <= 0 || codepoint > 0x10FFFF) {
            return;
        }
        ImGui.getIO().addInputCharacter(codepoint);
    }

    public void onScroll(double horizontal, double vertical) {
        pendingScrollX += (float) horizontal;
        pendingScrollY += (float) vertical;
    }

    /** GLFW key code to ImGui key. Unknown codes map to {@code ImGuiKey.None}. */
    public static int mapKey(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return ImGuiKey.A + (key - GLFW.GLFW_KEY_A);
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return ImGuiKey._0 + (key - GLFW.GLFW_KEY_0);
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) {
            return ImGuiKey.F1 + (key - GLFW.GLFW_KEY_F1);
        }
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return ImGuiKey.Keypad0 + (key - GLFW.GLFW_KEY_KP_0);
        }

        switch (key) {
            case GLFW.GLFW_KEY_TAB:
                return ImGuiKey.Tab;
            case GLFW.GLFW_KEY_LEFT:
                return ImGuiKey.LeftArrow;
            case GLFW.GLFW_KEY_RIGHT:
                return ImGuiKey.RightArrow;
            case GLFW.GLFW_KEY_UP:
                return ImGuiKey.UpArrow;
            case GLFW.GLFW_KEY_DOWN:
                return ImGuiKey.DownArrow;
            case GLFW.GLFW_KEY_PAGE_UP:
                return ImGuiKey.PageUp;
            case GLFW.GLFW_KEY_PAGE_DOWN:
                return ImGuiKey.PageDown;
            case GLFW.GLFW_KEY_HOME:
                return ImGuiKey.Home;
            case GLFW.GLFW_KEY_END:
                return ImGuiKey.End;
            case GLFW.GLFW_KEY_INSERT:
                return ImGuiKey.Insert;
            case GLFW.GLFW_KEY_DELETE:
                return ImGuiKey.Delete;
            case GLFW.GLFW_KEY_BACKSPACE:
                return ImGuiKey.Backspace;
            case GLFW.GLFW_KEY_SPACE:
                return ImGuiKey.Space;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                return ImGuiKey.Enter;
            case GLFW.GLFW_KEY_ESCAPE:
                return ImGuiKey.Escape;
            case GLFW.GLFW_KEY_APOSTROPHE:
                return ImGuiKey.Apostrophe;
            case GLFW.GLFW_KEY_COMMA:
                return ImGuiKey.Comma;
            case GLFW.GLFW_KEY_MINUS:
            case GLFW.GLFW_KEY_KP_SUBTRACT:
                return ImGuiKey.Minus;
            case GLFW.GLFW_KEY_PERIOD:
            case GLFW.GLFW_KEY_KP_DECIMAL:
                return ImGuiKey.Period;
            case GLFW.GLFW_KEY_SLASH:
            case GLFW.GLFW_KEY_KP_DIVIDE:
                return ImGuiKey.Slash;
            case GLFW.GLFW_KEY_SEMICOLON:
                return ImGuiKey.Semicolon;
            case GLFW.GLFW_KEY_EQUAL:
                return ImGuiKey.Equal;
            case GLFW.GLFW_KEY_LEFT_BRACKET:
                return ImGuiKey.LeftBracket;
            case GLFW.GLFW_KEY_BACKSLASH:
                return ImGuiKey.Backslash;
            case GLFW.GLFW_KEY_RIGHT_BRACKET:
                return ImGuiKey.RightBracket;
            case GLFW.GLFW_KEY_GRAVE_ACCENT:
                return ImGuiKey.GraveAccent;
            case GLFW.GLFW_KEY_CAPS_LOCK:
                return ImGuiKey.CapsLock;
            case GLFW.GLFW_KEY_SCROLL_LOCK:
                return ImGuiKey.ScrollLock;
            case GLFW.GLFW_KEY_NUM_LOCK:
                return ImGuiKey.NumLock;
            case GLFW.GLFW_KEY_PRINT_SCREEN:
                return ImGuiKey.PrintScreen;
            case GLFW.GLFW_KEY_PAUSE:
                return ImGuiKey.Pause;
            case GLFW.GLFW_KEY_KP_ADD:
                return ImGuiKey.KeypadAdd;
            case GLFW.GLFW_KEY_KP_MULTIPLY:
                return ImGuiKey.KeypadMultiply;
            case GLFW.GLFW_KEY_LEFT_SHIFT:
                return ImGuiKey.LeftShift;
            case GLFW.GLFW_KEY_RIGHT_SHIFT:
                return ImGuiKey.RightShift;
            case GLFW.GLFW_KEY_LEFT_CONTROL:
                return ImGuiKey.LeftCtrl;
            case GLFW.GLFW_KEY_RIGHT_CONTROL:
                return ImGuiKey.RightCtrl;
            case GLFW.GLFW_KEY_LEFT_ALT:
                return ImGuiKey.LeftAlt;
            case GLFW.GLFW_KEY_RIGHT_ALT:
                return ImGuiKey.RightAlt;
            case GLFW.GLFW_KEY_LEFT_SUPER:
                return ImGuiKey.LeftSuper;
            case GLFW.GLFW_KEY_RIGHT_SUPER:
                return ImGuiKey.RightSuper;
            case GLFW.GLFW_KEY_MENU:
                return ImGuiKey.Menu;
            default:
                return ImGuiKey.None;
        }
    }

    /** Human readable name for the config screen. */
    public static String keyName(int glfwKey) {
        String name = GLFW.glfwGetKeyName(glfwKey, 0);
        if (name != null) {
            return name.toUpperCase();
        }
        switch (glfwKey) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT:
                return "Right Shift";
            case GLFW.GLFW_KEY_LEFT_SHIFT:
                return "Left Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL:
                return "Left Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL:
                return "Right Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT:
                return "Left Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT:
                return "Right Alt";
            case GLFW.GLFW_KEY_TAB:
                return "Tab";
            case GLFW.GLFW_KEY_ESCAPE:
                return "Escape";
            case GLFW.GLFW_KEY_SPACE:
                return "Space";
            default:
                return "Key " + glfwKey;
        }
    }
}

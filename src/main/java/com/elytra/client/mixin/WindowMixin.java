package com.elytra.client.mixin;

import com.elytra.client.ElytraClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.tracy.TracyFrameCapturer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the ImGui overlay at the last possible moment of the frame.
 *
 * {@code MinecraftClient#render} blits the world framebuffer to the window and then
 * calls {@code Window#swapBuffers}. Hooking the head of the swap means the back
 * buffer already holds the complete frame, the default framebuffer is the draw
 * target, and nothing else will overwrite our pixels before they are presented.
 */
@Mixin(Window.class)
public class WindowMixin {

    @Inject(method = "swapBuffers", at = @At("HEAD"))
    private void elytra$renderOverlay(TracyFrameCapturer capturer, CallbackInfo info) {
        ElytraClient.renderGui();
    }
}

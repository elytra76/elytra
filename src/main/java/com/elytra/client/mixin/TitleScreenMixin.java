package com.elytra.client.mixin;

import com.elytra.client.gui.render.ImGuiRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides the vanilla title screen so the Elytra themed menu can own the whole
 * screen. Drawing and click handling are replaced; the ImGui menu polls its own
 * input from GLFW, so swallowing the vanilla click does not disable our buttons.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void elytra$hideVanillaMenu(DrawContext context, int mouseX, int mouseY, float delta,
                                        CallbackInfo info) {
        if (ImGuiRenderer.isUnavailable()) {
            return;
        }
        info.cancel();
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void elytra$swallowVanillaClicks(Click click, boolean doubled,
                                             CallbackInfoReturnable<Boolean> info) {
        if (ImGuiRenderer.isUnavailable()) {
            return;
        }
        info.setReturnValue(true);
    }
}

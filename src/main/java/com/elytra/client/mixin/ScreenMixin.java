package com.elytra.client.mixin;

import com.elytra.client.gui.ElytraScreenTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paints the Elytra themed backdrop behind vanilla menu widgets. */
@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"))
    private void elytra$renderMenuBackdrop(DrawContext context, int mouseX, int mouseY,
                                           float delta, CallbackInfo info) {
        if (ElytraScreenTheme.shouldTheme((Screen) (Object) this)) {
            ElytraScreenTheme.renderBackdrop(context);
        }
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void elytra$skipVanillaBackground(DrawContext context, int mouseX, int mouseY,
                                              float delta, CallbackInfo info) {
        if (ElytraScreenTheme.shouldTheme((Screen) (Object) this)) {
            info.cancel();
        }
    }
}

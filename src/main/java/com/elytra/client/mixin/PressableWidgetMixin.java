package com.elytra.client.mixin;

import com.elytra.client.gui.ElytraScreenTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides vanilla button visuals on themed menus. The widgets still receive clicks;
 * ElytraMenuOverlay paints their rounded, theme-matched replacements.
 */
@Mixin(PressableWidget.class)
public class PressableWidgetMixin {

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void elytra$hideVanillaButton(DrawContext context, int mouseX, int mouseY,
                                          float delta, CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            Screen screen = client.currentScreen;
            if (ElytraScreenTheme.shouldTheme(screen)) {
                info.cancel();
            }
        }
    }
}

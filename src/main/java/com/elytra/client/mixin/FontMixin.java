package com.elytra.client.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Redirects the vanilla default font to Elytra's Mochi-based font. */
@Mixin(TextRenderer.class)
public class FontMixin {

    private static final Identifier DEFAULT_FONT = Identifier.of("minecraft", "default");
    private static final StyleSpriteSource.Font ELYTRA_SOURCE =
            new StyleSpriteSource.Font(Identifier.of("elytra", "default"));

    @ModifyVariable(method = "getGlyphs(Lnet/minecraft/text/StyleSpriteSource;)Lnet/minecraft/client/font/GlyphProvider;",
            at = @At("HEAD"), argsOnly = true)
    private StyleSpriteSource elytra$redirectFont(StyleSpriteSource source) {
        if (source instanceof StyleSpriteSource.Font font
                && (source == StyleSpriteSource.DEFAULT || font.id().equals(DEFAULT_FONT))) {
            return ELYTRA_SOURCE;
        }
        return source;
    }
}

package com.elytra.client.gui.render;

import com.elytra.client.account.Account;
import com.elytra.client.account.AccountManager;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * GL texture cache for profile pictures. Textures are uploaded once, on the render
 * thread, the first time an account's avatar is drawn, and reloaded only when the
 * picture changes.
 */
public final class AvatarTextures {

    private static final class Entry {
        final int texture;
        final int version;

        Entry(int texture, int version) {
            this.texture = texture;
            this.version = version;
        }
    }

    private static final Map<String, Entry> CACHE = new HashMap<>();

    private AvatarTextures() {
    }

    /** GL texture id for the account's picture, or 0 when it uses the default avatar. */
    public static int textureFor(Account account) {
        if (account == null) {
            return 0;
        }
        Entry entry = CACHE.get(account.id);
        if (!account.hasAvatar()) {
            if (entry != null) {
                release(account.id);
            }
            return 0;
        }
        if (entry != null && entry.version == account.avatarVersion()) {
            return entry.texture;
        }
        BufferedImage image = AccountManager.get().readAvatar(account);
        int texture = image == null ? 0 : ImGuiRenderer.uploadTexture(image, false);
        if (entry != null) {
            delete(entry.texture);
        }
        // A failed load is cached too (texture 0), so it is not retried every frame.
        CACHE.put(account.id, new Entry(texture, account.avatarVersion()));
        return texture;
    }

    public static void release(String accountId) {
        Entry entry = CACHE.remove(accountId);
        if (entry != null) {
            delete(entry.texture);
        }
    }

    public static void delete(int texture) {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
        }
    }

    public static void shutdown() {
        for (Entry entry : CACHE.values()) {
            delete(entry.texture);
        }
        CACHE.clear();
    }
}

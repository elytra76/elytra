package com.elytra.client.account;

/** One local Elytra account: identity, credentials, avatar choice and personal settings. */
public final class Account {

    public final String id;
    private String username;

    byte[] salt;
    byte[] hash;
    int iterations;

    /** Index into {@link AccountManager#AVATAR_COLORS}; used when there is no custom picture. */
    private int avatarColor;
    private boolean hasAvatar;
    /** Bumped whenever the picture changes so the GL texture cache knows to reload. Not persisted. */
    private int avatarVersion;
    private final long createdAt;

    // Personal settings, applied to the client on sign-in.
    int themeIndex;
    int effectMode = 1;
    float rainDensity = 1.0f;
    float guiScale = 1.0f;

    Account(String id, String username, long createdAt) {
        this.id = id;
        this.username = username;
        this.createdAt = createdAt;
    }

    public String username() {
        return username;
    }

    void setUsername(String value) {
        this.username = value;
    }

    public int avatarColor() {
        return avatarColor;
    }

    void setAvatarColor(int value) {
        this.avatarColor = Math.floorMod(value, AccountManager.AVATAR_COLORS.length);
    }

    public boolean hasAvatar() {
        return hasAvatar;
    }

    void setHasAvatar(boolean value) {
        this.hasAvatar = value;
        this.avatarVersion++;
    }

    public int avatarVersion() {
        return avatarVersion;
    }

    public long createdAt() {
        return createdAt;
    }

    public int themeIndex() {
        return themeIndex;
    }

    public int effectMode() {
        return effectMode;
    }

    public float rainDensity() {
        return rainDensity;
    }

    public float guiScale() {
        return guiScale;
    }

    boolean passwordMatches(byte[] candidate) {
        return PasswordHasher.matches(hash, candidate);
    }
}

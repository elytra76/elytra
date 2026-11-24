package com.elytra.client.account;

import com.elytra.client.ElytraClient;
import com.elytra.client.ElytraConfig;
import com.elytra.client.util.AtomicFiles;
import com.elytra.client.util.ColorUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Local account store.
 *
 * Elytra has no server, so accounts live on this machine: {@code config/elytra-accounts.json}
 * holds usernames, salted PBKDF2 password hashes, avatar choices and per-account settings,
 * and {@code config/elytra-accounts/avatars/} holds the profile pictures. A password keeps
 * other people at the keyboard out of your profile; it is not protection against someone who
 * can read your files.
 *
 * Every method here runs on the client thread. Only the slow key derivation is pushed to a
 * worker ({@link #hashAsync}); the result is applied back on the client thread.
 */
public final class AccountManager {

    /** Default-avatar swatches, packed in ImGui's 0xAABBGGRR layout. */
    public static final int[] AVATAR_COLORS = {
            ColorUtils.hex("#E69BCB"), ColorUtils.hex("#8FB3E8"), ColorUtils.hex("#6BE8A0"),
            ColorUtils.hex("#E8B86B"), ColorUtils.hex("#B86BE8"), ColorUtils.hex("#8FE0F0"),
            ColorUtils.hex("#E86B8F"), ColorUtils.hex("#C0C4CC")
    };

    public static final int MAX_USERNAME = 16;
    public static final int MAX_PASSWORD = 64;

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AccountManager INSTANCE = new AccountManager();

    private static final ExecutorService HASHER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Elytra-Hash");
        thread.setDaemon(true);
        return thread;
    });

    private final List<Account> accounts = new ArrayList<>();
    private final Map<String, Throttle> throttles = new HashMap<>();
    private Account active;
    private String rememberedId = "";

    private static final class Throttle {
        int failures;
        long lockedUntilMs;
    }

    private AccountManager() {
    }

    public static AccountManager get() {
        return INSTANCE;
    }

    // ---- State ---------------------------------------------------------------

    public List<Account> accounts() {
        return Collections.unmodifiableList(accounts);
    }

    public boolean hasAccounts() {
        return !accounts.isEmpty();
    }

    public Account active() {
        return active;
    }

    public boolean isSignedIn() {
        return active != null;
    }

    public Account find(String username) {
        if (username == null) {
            return null;
        }
        String key = key(username);
        for (Account account : accounts) {
            if (key(account.username()).equals(key)) {
                return account;
            }
        }
        return null;
    }

    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    // ---- Validation ------------------------------------------------------------

    /** @return an error message, or {@code null} when the name is acceptable. */
    public static String checkUsername(String username) {
        String name = username == null ? "" : username.trim();
        if (name.length() < 3) {
            return "Username must be at least 3 characters.";
        }
        if (!USERNAME.matcher(name).matches()) {
            return "Username can use letters, numbers and _ only (3-16 characters).";
        }
        return null;
    }

    /** @return an error message, or {@code null} when the password is acceptable. */
    public static String checkPassword(String password) {
        if (password == null || password.length() < 6) {
            return "Password must be at least 6 characters.";
        }
        if (password.length() > MAX_PASSWORD) {
            return "Password can be at most " + MAX_PASSWORD + " characters.";
        }
        return null;
    }

    public boolean isUsernameTaken(String username, Account except) {
        Account existing = find(username);
        return existing != null && existing != except;
    }

    // ---- Brute-force throttle (in memory) ------------------------------------

    /** Milliseconds this username is locked out for, or 0. */
    public long lockedForMillis(String username) {
        Throttle throttle = throttles.get(key(username));
        return throttle == null ? 0L : Math.max(0L, throttle.lockedUntilMs - System.currentTimeMillis());
    }

    public void recordFailure(String username) {
        Throttle throttle = throttles.computeIfAbsent(key(username), k -> new Throttle());
        throttle.failures++;
        if (throttle.failures >= 5) {
            long delay = Math.min(300_000L, 15_000L << Math.min(4, throttle.failures - 5));
            throttle.lockedUntilMs = System.currentTimeMillis() + delay;
        }
    }

    public void clearFailures(String username) {
        throttles.remove(key(username));
    }

    // ---- Hashing ---------------------------------------------------------------

    /** Derives a password key off-thread. Poll the future from the render loop. */
    public CompletableFuture<byte[]> hashAsync(String password, byte[] salt, int iterations) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return PasswordHasher.hash(password, salt, iterations);
            } catch (GeneralSecurityException exception) {
                throw new CompletionException(exception);
            }
        }, HASHER);
    }

    public boolean verify(Account account, byte[] derivedKey) {
        return account.passwordMatches(derivedKey);
    }

    public byte[] saltOf(Account account) {
        return account.salt;
    }

    public int iterationsOf(Account account) {
        return account.iterations;
    }

    // ---- Mutations -------------------------------------------------------------

    /**
     * Adds an account whose personal settings start as the client's current ones.
     *
     * @return the new account, or {@code null} if the username is invalid or already used
     */
    public Account create(String username, byte[] salt, byte[] hash, int avatarColor, ElytraConfig config) {
        if (checkUsername(username) != null || isUsernameTaken(username, null)) {
            return null;
        }
        Account account = new Account(newId(), username.trim(), System.currentTimeMillis());
        account.salt = salt;
        account.hash = hash;
        account.iterations = PasswordHasher.ITERATIONS;
        account.setAvatarColor(avatarColor);
        if (config != null) {
            account.themeIndex = config.themeIndex();
            account.effectMode = config.effectMode();
            account.rainDensity = config.rainDensity();
            account.guiScale = config.guiScale();
        }
        accounts.add(account);
        save();
        return account;
    }

    public void signIn(Account account, boolean remember) {
        this.active = account;
        this.rememberedId = remember ? account.id : "";
        save();
    }

    /** Signs out and forgets the remembered login, so the next launch asks again. */
    public void signOut() {
        this.active = null;
        this.rememberedId = "";
        save();
    }

    public void delete(Account account) {
        accounts.remove(account);
        try {
            Files.deleteIfExists(avatarPath(account));
        } catch (IOException exception) {
            ElytraClient.LOGGER.warn("Could not delete avatar for {}", account.username(), exception);
        }
        if (active == account) {
            active = null;
        }
        if (account.id.equals(rememberedId)) {
            rememberedId = "";
        }
        save();
    }

    /** @return an error message, or {@code null} on success */
    public String rename(Account account, String newName) {
        String error = checkUsername(newName);
        if (error != null) {
            return error;
        }
        if (isUsernameTaken(newName, account)) {
            return "That username is already taken.";
        }
        account.setUsername(newName.trim());
        save();
        return null;
    }

    public void setPassword(Account account, byte[] salt, byte[] hash) {
        account.salt = salt;
        account.hash = hash;
        account.iterations = PasswordHasher.ITERATIONS;
        save();
    }

    public void setAvatarColor(Account account, int color) {
        account.setAvatarColor(color);
        save();
    }

    /** Stores a profile picture (already squared by {@link AvatarStore}). */
    public String setAvatar(Account account, BufferedImage square) {
        try {
            AtomicFiles.write(avatarPath(account), AvatarStore.encodePng(square));
            account.setHasAvatar(true);
            save();
            return null;
        } catch (IOException exception) {
            ElytraClient.LOGGER.warn("Could not save avatar", exception);
            return "Could not save the picture.";
        }
    }

    public void clearAvatar(Account account) {
        try {
            Files.deleteIfExists(avatarPath(account));
        } catch (IOException exception) {
            ElytraClient.LOGGER.warn("Could not remove avatar", exception);
        }
        account.setHasAvatar(false);
        save();
    }

    public BufferedImage readAvatar(Account account) {
        Path path = avatarPath(account);
        if (!account.hasAvatar() || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            ElytraClient.LOGGER.warn("Could not read avatar for {}", account.username(), exception);
            return null;
        }
    }

    // ---- Personal settings -----------------------------------------------------

    /** Pushes the signed-in account's settings into the live client config. */
    public void applySettingsTo(ElytraConfig config) {
        if (active == null || config == null) {
            return;
        }
        config.setThemeIndex(active.themeIndex);
        config.setEffectMode(active.effectMode);
        config.setRainDensity(active.rainDensity);
        config.setGuiScale(active.guiScale);
    }

    /** Called after every config save so the account always mirrors what the player set. */
    public void captureSettingsFrom(ElytraConfig config) {
        if (active == null || config == null) {
            return;
        }
        boolean changed = active.themeIndex != config.themeIndex()
                || active.effectMode != config.effectMode()
                || Math.abs(active.rainDensity - config.rainDensity()) > 0.0005f
                || Math.abs(active.guiScale - config.guiScale()) > 0.0005f;
        if (!changed) {
            return;
        }
        active.themeIndex = config.themeIndex();
        active.effectMode = config.effectMode();
        active.rainDensity = config.rainDensity();
        active.guiScale = config.guiScale();
        save();
    }

    // ---- Persistence -----------------------------------------------------------

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("elytra-accounts.json");
    }

    private static Path avatarDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("elytra-accounts").resolve("avatars");
    }

    private Path avatarPath(Account account) {
        // Ids are generated hex, never user text, so this cannot escape the folder.
        return avatarDirectory().resolve(account.id + ".png");
    }

    public void load() {
        accounts.clear();
        active = null;
        rememberedId = "";
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("remembered") && root.get("remembered").isJsonPrimitive()) {
                rememberedId = root.get("remembered").getAsString();
            }
            JsonArray array = root.has("accounts") ? root.getAsJsonArray("accounts") : new JsonArray();
            for (JsonElement element : array) {
                Account account = parse(element.getAsJsonObject());
                if (account != null && find(account.username()) == null) {
                    accounts.add(account);
                }
            }
        } catch (IOException | RuntimeException exception) {
            ElytraClient.LOGGER.warn("Could not read {}; keeping a backup and starting fresh", path, exception);
            backUpCorruptFile(path);
            accounts.clear();
        }
        for (Account account : accounts) {
            if (account.id.equals(rememberedId)) {
                active = account;
            }
        }
    }

    private static void backUpCorruptFile(Path path) {
        try {
            Files.move(path, path.resolveSibling("elytra-accounts.json.corrupt"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Nothing more we can do; the next save overwrites it.
        }
    }

    private static Account parse(JsonObject node) {
        try {
            String id = node.get("id").getAsString();
            String username = node.get("username").getAsString();
            if (!id.matches("[0-9a-f]{8,32}") || checkUsername(username) != null) {
                return null;
            }
            Account account = new Account(id, username, node.has("created") ? node.get("created").getAsLong() : 0L);
            account.salt = Base64.getDecoder().decode(node.get("salt").getAsString());
            account.hash = Base64.getDecoder().decode(node.get("hash").getAsString());
            account.iterations = Math.max(10_000, node.get("iterations").getAsInt());
            account.setAvatarColor(node.has("avatarColor") ? node.get("avatarColor").getAsInt() : 0);
            account.setHasAvatar(node.has("hasAvatar") && node.get("hasAvatar").getAsBoolean());
            if (node.has("settings") && node.get("settings").isJsonObject()) {
                JsonObject settings = node.getAsJsonObject("settings");
                account.themeIndex = settings.has("theme") ? settings.get("theme").getAsInt() : 0;
                account.effectMode = settings.has("effectMode") ? settings.get("effectMode").getAsInt() : 1;
                account.rainDensity = settings.has("rainDensity") ? settings.get("rainDensity").getAsFloat() : 1f;
                account.guiScale = settings.has("guiScale") ? settings.get("guiScale").getAsFloat() : 1f;
            }
            return account;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("remembered", rememberedId);
        JsonArray array = new JsonArray();
        for (Account account : accounts) {
            JsonObject node = new JsonObject();
            node.addProperty("id", account.id);
            node.addProperty("username", account.username());
            node.addProperty("salt", Base64.getEncoder().encodeToString(account.salt));
            node.addProperty("hash", Base64.getEncoder().encodeToString(account.hash));
            node.addProperty("iterations", account.iterations);
            node.addProperty("avatarColor", account.avatarColor());
            node.addProperty("hasAvatar", account.hasAvatar());
            node.addProperty("created", account.createdAt());
            JsonObject settings = new JsonObject();
            settings.addProperty("theme", account.themeIndex);
            settings.addProperty("effectMode", account.effectMode);
            settings.addProperty("rainDensity", account.rainDensity);
            settings.addProperty("guiScale", account.guiScale);
            node.add("settings", settings);
            array.add(node);
        }
        root.add("accounts", array);
        try {
            AtomicFiles.writeString(file(), GSON.toJson(root));
        } catch (IOException exception) {
            ElytraClient.LOGGER.warn("Could not write {}", file(), exception);
        }
    }

    private static String newId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(16);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}

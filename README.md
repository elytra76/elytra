# Elytra Client

A Minecraft 1.21.11 Fabric client-side mod. This build ships the **click GUI only** —
no module system, no cheat modules.

## Building

Requires a JDK 25 (Temurin recommended) and the Gradle wrapper shipped in this repo.

```powershell
.\gradlew.bat build --console=plain
```

The compiled jar lands at:

```
build/libs/elytra-client-1.0.0.jar
```

Install by copying that jar into your `.minecraft/mods` folder alongside Fabric
Loader `0.20.1`, Fabric API `0.141.6+1.21.11` and Minecraft `1.21.11`.

## Running in dev

```powershell
.\gradlew.bat runClient --console=plain
```

## Using the GUI

- **Right Shift** toggles the GUI (configurable in GUI Settings).
- Drag any panel by its header; positions persist in `config/elytra-client.json`.
- The header's three sections open the client status, GUI Settings and Configs.
- Panels are empty containers in this build — no module rows.

## Accounts

An account is required. On first launch (and after signing out) the title screen is
covered by a sign-in / create-account screen. Accounts are **local to this computer**:

- `config/elytra-accounts.json` - usernames, salted PBKDF2-HMAC-SHA256 password hashes
  (210,000 iterations), avatar colour and per-account settings (theme, effect, density, scale)
- `config/elytra-accounts/avatars/` - profile pictures, re-encoded to 128x128 PNG

A small chip (picture, username, gear) floats above the character on the title screen;
click it for profile picture, username, preferences, password, sign out and delete.
There is no server, so a password keeps other people at your keyboard out of your profile -
it does not protect against someone who can read your files. If Dear ImGui's native library
fails to load, the account gate and themed menus switch themselves off instead of crashing.

## Project layout

See `src/main/java/com/elytra/client/...` — rendering, input, theme, panels and
configuration are split into separate packages. No module registry is present.

# Changes in 1.1.0

## Removed
- `render/Render3D.java` - an ESP (see-through-walls entity box) renderer.
- `module/` framework and the `Fly`, `Speed`, `Hitbox`, `PlayerESP`, `SkeletonESP`, `Tracers`
  registrations in `ElytraClient` (the classes were already gone, so 1.0.0 did not compile).
- Not included in this package: `reference/` (copies of other cheat clients), `AGENTS.md`
  (a prompt file for AI agents), `.gradle/`, `build/`, `run/` logs and caches.

## Added
- Required local account system (see README): sign in / create, profile picture, per-account settings.
- Account chip above the title-screen character, opening an account settings screen.

## Fixed / optimized
- ImGui native-load failure (`UnsatisfiedLinkError`) no longer crashes the game; menus fall back to vanilla.
- Skin preview: rectangles merged into runs (~7x fewer draw calls), raster capped to ~80 Hz,
  skin cache read off the render thread.
- Skin download: https + textures.minecraft.net only, no redirects, timeouts, 2 MB / 1024 px limits,
  real JSON parsing instead of string slicing.
- Config and accounts are written atomically (no half-written files after a crash).
- Clipboard paste works in text fields.

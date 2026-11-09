# ADR 0006: Alloy — NexAlloy-style DexKit fingerprints (rooted)

- **Status:** Accepted (implementation started — AlloyDexKit + Favorites pilot)
- **Date:** 2026-10-05
- **Related:** [0005-dual-apk-morphe-alloy.md](0005-dual-apk-morphe-alloy.md), [vision.md](../vision.md)

## Context

The `alloy` APK (`dev.fatter.gpp.alloy`) is the rooted Vector channel. Residual tip-DEX sites are fragile under R8 churn. [NexAlloy](https://github.com/NexAlloy/NexAlloy) maps Morphe/ReVanced-style fingerprints to DexKit + Xposed hooks at runtime.

## Decision

1. Depend on `org.luckypray:dexkit` for **alloy** (`alloyImplementation`); other flavors `compileOnly` so shared hooks compile.
2. Façade: `dev.fatter.gpp.alloy.AlloyDexKit` (`ensureInitialized`, `findClassByStrings`, `findMethod` with string/number/signature filters).
3. Pilot: [Favorites.kt](../../app/src/module/java/dev/fatter/gpp/hooks/Favorites.kt) falls back to DexKit string search when pack remap class is missing (Alloy only). [OnlineIndicator.kt](../../app/src/module/java/dev/fatter/gpp/hooks/OnlineIndicator.kt) now recovers the `600000ms` boolean gate via `findMethod(numbers = [600000], returnType = "Z", paramTypes = ["J"])` when the pack class is absent.
4. Init: `GrindrPlus.initializeCore` calls `AlloyDexKit.ensureInitialized` before `HookManager.init` on Alloy.

## Non-goals

- Replacing HookManager wholesale.
- Shipping DexKit as the primary discovery path on Morphe (Morphe B bytecode covers rootless static sites).

## Acceptance

- [x] Compile: alloy + morphe green with DexKit wiring.
- [x] Offline tip DEX (26.16.1): `FavoritesFragment` / `fragment_favorite_recycler_view` **miss**; `CascadeFavoritesItemUiModel` / `FavoritesHeaderData` **hit** — Favorites DexKit needles updated accordingly.
- [x] Numeric/signature method fallback: `findMethod(numbers = [600000], returnType = "Z", paramTypes = ["J"])` wired into `OnlineIndicator` for Alloy.
- [ ] Device (lab): tip Grindr recovers Favorites/OnlineIndicator when pack class absent but fingerprint hits — **blocked**: the lab account is currently “Account Restricted / Additional Review Required”, so no login session; 194910 stock also SIGSEGVs in `libpairipcore.so` on the x86_64 emulator.

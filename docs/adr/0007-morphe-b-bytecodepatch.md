# ADR 0007: Morphe B — MorpheApp bytecodePatch as STATIC_RESOURCE

- **Status:** Accepted (dexlib2 rewriter scaffold landed; feature instruction recipes TBD)
- **Date:** 2026-10-05
- **Related:** [0004-morphe-b.md](0004-morphe-b.md), [0005-dual-apk-morphe-alloy.md](0005-dual-apk-morphe-alloy.md)

> **Update (2026-11-12):** LSPatch in the pipeline runs **`-l 1`** (see the ADR 0003 update).

## Context

Morphe B today embeds a JSON marker and uses RUNTIME_REMAP. Tip-DEX residuals need real bytecode mutation. Upstream MorpheApp provides `bytecodePatch` / fingerprints / `.mpp` bundles ([morphe-patches-template](https://github.com/MorpheApp/morphe-patches-template)).

## Decision

1. Integrate MorpheApp patcher **inside** `MorpheBPatchEngine` via [MorpheBytecodeBackend](../../app/src/morphe-b/java/dev/fatter/gpp/morphe/b/MorpheBytecodeBackend.kt) (not as Morphe Manager product).
2. Pipeline: **B (bytecode) → A (LSPatch `-l 2` + slim embed) → SessionInstaller** on `dev.fatter.gpp.morphe`.
3. Fail-soft per patch; Settings stay honest on miss.
4. Pilot Chat terminal and/or Favorites Cascade residual after patcher dependency lands.

## Current state

Default backend is [DexlibBytecodeBackend](../../app/src/morphe-b/java/dev/fatter/gpp/morphe/b/DexlibBytecodeBackend.kt): parses `classes*.dex` with `smali-dexlib2` (same writer as MorpheApp `bytecodePatch` / patches-template), locating [BytecodePilotCatalog](../../app/src/morphe-b/java/dev/fatter/gpp/morphe/b/BytecodePilotCatalog.kt) needles to class types, then executing in-process `MorpheBytecodePatch` blocks (`addInstructions`-style prepend + marker class inject). Invalid DEX falls back to `FingerprintScanBytecodeBackend`. Desktop `app.morphe:morphe-patcher` is **not** embedded (apktool/resource decode is too heavy for on-device Manager). Slim embed stays `compileOnly` for dexlib2.

Pilot: marker class `Lcom/grindrplus/morphe/b/BytecodeApplied;` plus boolean-gate prepend and void-stub primitives. A feature recipe stubs the forced-update deprecation dialog: the class that builds `market://details?id=com.grindrapp.android` has its `showDeprecatedDialog(AppCompatActivity)` replaced with `return-void` (`disable-updates-dialog`). **The clone default is now no DEX rewrite**: dexlib2 `DexPool` corrupts Compose interface static methods (verified `DrawScope.drawRect-n-J9OG0$default` -> `IncompatibleClassChangeError`), so the recipe is lab/unit-test only and the runtime `Disable updates` hook is the production path. Chat terminal / Favorites remain locate-only - JADX tip `FavoritesHeaderData` is a Cascade data class, not the layout Fragment hook (Settings stay RUNTIME_REMAP / honest skip).

Next: a verified feature recipe (Chat inbound processor or Favorites Fragment) once a safe boolean/const site is mapped.

**PairIP (Play 26.19+):** Alloy runtime bypass is a dead end (SIGILL in `libsqliteJni` after stubbing PairIP — see [0009](0009-pairip-static-decrypt.md) and [lab notes](../lab/pairip/)). Static decrypt / rewrite of PairIP-protected sites is an additional Morphe B workstream before PairIP Play hosts are “green”.

## Acceptance

- [x] ≥1 static bytecode path applied in dry-run (fingerprint scan + report asset).
- [x] Real instruction rewrite scaffold (dexlib2 mutate + marker class; MorpheApp-shaped `bytecodePatch` execute inside orchestrator).
- [x] Feature-specific instruction recipe — `disable-updates-dialog` void-stub (verified by unit test); **disabled in the clone default** because `DexPool` corrupts Compose interface static methods; runtime `Disable updates` hook is the production path. Chat/Favorites remain deferred until a safe tip-DEX target exists.
- Slim embed still used; Settings truthfulness preserved.

# Ver.5 green checklist

Date: 2026-10-05  
Sequence: **Morphe A (in Ver.5) → green → Morphe B**

| Requirement | Status | Evidence |
| --- | --- | --- |
| Init pack-driven, no hard abort on version tip | Done | `GrindrPlus.init` continues; soft dialogs only |
| Schema v2 + migrator + `index.json` | Done | `MappingDictionary` SCHEMA_VERSION=2; packs/index |
| Fingerprint soft-validate + sanitizer | Done | `TargetFingerprint`, `StacktraceSanitizer`, `writeThrowable` |
| Platform concurrency / backoff / circuit-breaker | Done | CHM maps, `TaskScheduler` backoff, `HookCircuitBreaker` |
| Settings ↔ runtime truthfulness | Done | `runtimeStatus` + Settings subtitles; pack `hooks.status` consumed |
| E2E Settings↔runtime | Device Done (lab) | [e2e-wave4-results.md](e2e-wave4-results.md); session `5dd9df` on Mi A2 lite |
| Morphe A path rootless | Done + E2E (lab) | `PatchBackend`, LSPatch **v1.2** pinned; Create/Update Grindr++ installs and launches on emulator ([e2e-wave4-results.md](e2e-wave4-results.md)) |
| Anonymous opt-in telemetry | Done | `AnonymousTelemetry` HTTP flush when `analytics_endpoint` set |
| Hot-reload packs | Done | `MappingDictionary.reloadForVersion` |
| `vision.md` + README aligned | Done | [vision.md](vision.md), docs/README links |
| Morphe B started | Done | `MorpheBPatchEngine` in `ApplyMorpheBStep` (before signing); runtime remaps; [morphe-b.md](morphe-b.md) |
| Dual APK Morphe / Alloy | Phase 1 Done | Flavors `morphe` / `alloy` / `embed`; [ADR 0005](adr/0005-dual-apk-morphe-alloy.md) |
| Alloy DexKit | Started | [ADR 0006](adr/0006-alloy-dexkit-fingerprints.md); `AlloyDexKit` + Favorites pilot |
| Morphe B bytecodePatch | Scaffold Done | [ADR 0007](adr/0007-morphe-b-bytecodepatch.md); dexlib2 rewrite in `ApplyMorpheBStep` (before signing) |

## Residual product debt (honest skips)

1. Remap Chat terminal (`ChatTerminal.HANDLER`) when inbound processor confirmed
2. Cascade V2 Favorites UI rewrite (`Favorites.FRAGMENT`)
3. Disable shuffle / Notification Alerts — product surface absent on tip DEX
4. Optional: full SessionInstaller E2E of A+B patched APK on device
5. Play tip **26.23.0 / 194910** has no mapping pack yet; catalog newest is 26.19.0 / 185656 (soft-fail documented in [remote-mapping-packs.md](remote-mapping-packs.md))

## Morphe B

In progress / landed scaffold+engine+remaps. See [adr/0004-morphe-b.md](adr/0004-morphe-b.md).

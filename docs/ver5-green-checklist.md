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
| Play tip pack 26.23.0 / 194910 | Pack + PairIP dump Done | `mapping-packs/194910.json` + assets mirror; 43/83 symbols soft-skipped honestly; `validate_mapping_packs.py --require-tip` passes; `pairip-decrypt/194910/` produced from Play-signed ARM64 stock; Morphe B applies it (`splice libsqliteJni.so`, `DEX integrity stubs=2`). Clone launch still blocked by the lab ROM package-installer UI |
| Dual APK Morphe / Alloy | Phase 1 Done | Flavors `morphe` / `alloy` / `embed`; [ADR 0005](adr/0005-dual-apk-morphe-alloy.md) |
| Alloy DexKit | Implemented (device blocked) | [ADR 0006](adr/0006-alloy-dexkit-fingerprints.md); `AlloyDexKit` class + method fingerprint fallbacks; Favorites/OnlineIndicator wired; device login blocked by account restriction |
| Morphe B bytecodePatch | Recipe landed (default off) | [ADR 0007](adr/0007-morphe-b-bytecodepatch.md); `disable-updates-dialog` void-stub implemented/unit-tested but disabled in the clone default because `DexPool` corrupts Compose interface static methods (`DrawScope`); runtime `Disable updates` hook is the path. Chat/Favorites deferred (surface absent) |

## Residual product debt (honest skips)

1. Remap Chat terminal (`ChatTerminal.HANDLER`) when inbound processor confirmed
2. Cascade V2 Favorites UI rewrite (`Favorites.FRAGMENT`)
3. Disable shuffle / Notification Alerts — product surface absent on tip DEX
4. Optional: full SessionInstaller E2E of A+B patched APK on device
5. ~~Play tip 26.23.0 / 194910 has no mapping pack~~ — pack added 2026-11-09; remaining 26.23.0 gaps are the explicit soft-skips in `mapping-packs/194910.json` (see [remote-mapping-packs.md](remote-mapping-packs.md))
6. Morphe-clone Settings↔runtime matrix is blocked on the lab A2 Lite by the LineageOS package-installer dialog being `NOT_TOUCHABLE` (`INSTALL_FAILED_ABORTED: User rejected permissions`); manual `adb install-multiple` bypasses it. The supplied account now logs in on stock 194910. 194910 stock/clone still SIGSEGVs on the x86_64 emulator in `libpairipcore.so`. See [e2e-wave4-results.md](e2e-wave4-results.md)

## Morphe B

In progress / landed scaffold+engine+remaps. See [adr/0004-morphe-b.md](adr/0004-morphe-b.md).

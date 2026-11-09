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
| E2E Settings↔runtime | Device Done (lab) | [e2e-wave4-results.md](e2e-wave4-results.md); session `5dd9df` on Mi A2 lite + 2026-11-09 Morphe clone 26.16.1 on the emulator (toggle OFF/ON verified) |
| UI branding / News / clone icon | Done + E2E (lab) | Clone icon: adaptive XML remapped to brand layers, no duplicate ZIP entries, verified on launcher. News: HTML/entities sanitized in releases and `news.json`; user-facing “GrindrPlus” removed (upstream attribution kept) |
| Morphe A path rootless | Done + E2E (lab) | `PatchBackend`, LSPatch **v1.2** pinned at `-l 1` (`-l 2` DEX rewrite corrupts Compose interface static methods); Create/Update Grindr++ installs and launches on emulator ([e2e-wave4-results.md](e2e-wave4-results.md)) |
| Razr rootless 26.16.1 clone | Done + E2E (lab) | Stock 194910; 26.16.1 patched APKs (`-l 1`) installed on the Razr; no Compose/PairIP crash; login reaches AI consent/profile |
| Rooted Alloy 26.16.1 (A2 Lite) | Done + E2E (lab) | Stock 26.16.1 Play-signed + Alloy enabled/scoped via Vector CLI; login reaches Browse; `enabled=20, partial=3, skipped=5, disabled=1, failed=0` |
| Anonymous opt-in telemetry | Done | `AnonymousTelemetry` HTTP flush when `analytics_endpoint` set |
| Hot-reload packs | Done | `MappingDictionary.reloadForVersion` |
| `vision.md` + README aligned | Done | [vision.md](vision.md), docs/README links |
| Morphe B started | Done | `MorpheBPatchEngine` in `ApplyMorpheBStep` (before signing); runtime remaps; [morphe-b.md](morphe-b.md) |
| Play tip pack 26.23.0 / 194910 | Pack + PairIP dump Done | `mapping-packs/194910.json` + assets mirror; 43/83 symbols soft-skipped honestly; `validate_mapping_packs.py --require-tip` passes; `pairip-decrypt/194910/` produced from Play-signed ARM64 stock; Morphe B applies it (`splice libsqliteJni.so`, `DEX integrity stubs=2`). Clone install is blocked by the lab ROM package-installer UI (manual `adb install-multiple` works); launch is blocked by the native PairIP anti-tamper (`SIGSEGV` in `libpairipcore.so`) |
| Dual APK Morphe / Alloy | Phase 1 Done | Flavors `morphe` / `alloy` / `embed`; [ADR 0005](adr/0005-dual-apk-morphe-alloy.md) |
| Alloy DexKit | Implemented + login (lab) | [ADR 0006](adr/0006-alloy-dexkit-fingerprints.md); `AlloyDexKit` class + method fingerprint fallbacks; Favorites/OnlineIndicator wired; stock 26.16.1 + Alloy login reached Browse on the A2 Lite; DexKit device checks still pending |
| Morphe B bytecodePatch | Recipe landed (default off) | [ADR 0007](adr/0007-morphe-b-bytecodepatch.md); `disable-updates-dialog` void-stub implemented/unit-tested but disabled in the clone default because `DexPool` corrupts Compose interface static methods (`DrawScope`); runtime `Disable updates` hook is the path. Chat/Favorites deferred (surface absent) |

## Residual product debt (honest skips)

1. Remap Chat terminal (`ChatTerminal.HANDLER`) when inbound processor confirmed
2. Cascade V2 Favorites UI rewrite (`Favorites.FRAGMENT`)
3. Disable shuffle / Notification Alerts — product surface absent on tip DEX
4. Optional: full SessionInstaller E2E of A+B patched APK on device
5. ~~Play tip 26.23.0 / 194910 has no mapping pack~~ — pack added 2026-11-09; remaining 26.23.0 gaps are the explicit soft-skips in `mapping-packs/194910.json` (see [remote-mapping-packs.md](remote-mapping-packs.md))
6. ~~Morphe-clone Settings↔runtime matrix blocked~~ — passed on the 26.16.1 / 179451 clone (emulator, LSPatch `-l 1`): login reached Browse, toggle OFF → `enabled=18, disabled=2`, restore → `enabled=19, disabled=1`. The lab A2 Lite still needs `adb install-multiple` because its package-installer dialog is `NOT_TOUCHABLE`. The 194910 re-signed clone still crashes in the native PairIP anti-tamper (`libpairipcore.so` `SIGSEGV`) after the decrypted `libsqliteJni.so` splice; 26.16.1 / 179451 (no PairIP) is the working clone target. See [e2e-wave4-results.md](e2e-wave4-results.md)
7. Server-enforced profile-view paywall: `EnableUnlimited` suppresses the upsell and the profile opens, but the limit cannot be bypassed safely; the blocking “Paywalled Feature Detected” dialog was replaced by a one-time toast + log in `567c8d3`. Do not spoof roles in HTTP headers (account-ban risk)
8. Alloy on 194910 remains blocked (native PairIP under Vector, or Firebase `ExceptionInInitializerError` with the early bypass); Alloy stays on 26.16.1. The `ENABLE_PAIRIP_EARLY_BYPASS` product gate is off by default

## Morphe B

In progress / landed scaffold+engine+remaps. See [adr/0004-morphe-b.md](adr/0004-morphe-b.md).

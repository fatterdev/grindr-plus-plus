# Wave 4 E2E results — Settings ↔ runtime

| Field | Value |
| --- | --- |
| Date | 2026-10-05 |
| Target | Grindr `26.16.1` / `versionCode` `179451` |
| Pack | `mapping-packs/179451.json` (+ assets mirror) |
| Module | `4.7.2-26.16.1_f73aff5-debug` on Mi A2 lite (Lineage 15, Magisk + JingMatrix LSPosed) |
| Status | **device E2E matrix + toggle path passed** (session `5dd9df`) |
| Runbook | [e2e-settings-runtime-runbook.md](e2e-settings-runtime-runbook.md) |

## Device evidence (2026-10-05)

| Check | Result | Evidence |
| --- | --- | --- |
| Pack load schema v2 | Pass | DBG: `hasPack=true`, `packSymbols=76`, `packSchema=2`, `versionCode=179451` |
| Hook init summary | Pass | `enabled=18, partial=3, skipped=4` |
| Pack skips | Pass | Chat terminal / Video calls / Disable shuffle / Notification Alerts → `runtimeStatus=skipped` with pack reasons in `grindrplus.json` |
| Pack partials | Pass | Ban management / Disable boosting / Profile details → `partial` |
| Toggle OFF → runtime | Pass | Disabled “Allow screenshots” → log `Hook Allow screenshots is disabled.` + config `runtimeStatus=disabled`; summary `disabled=1` |
| Toggle restore | Pass | Re-enabled → `enabled=18` again |
| Post-login session | Pass | `HomeActivityOriginal`; `Failed to fetch own profile` count **0** after login + relaunch |
| Unit tests | Pass | `gradlew testDebugUnitTest` BUILD SUCCESSFUL (Studio JBR 21 + `C:\devbin\android-sdk`) |

Log sink: workspace `debug-5dd9df.log` (NDJSON, session `5dd9df`).

## Morphe A rootless E2E (2026-11-08)

| Field | Value |
| --- | --- |
| Host | Grindr `26.16.1` / `versionCode` `179451` installed from Play |
| Manager | `dev.fatter.gpp.morphe` `5.0.0-dev-83b7261` |
| Patcher | JingMatrix LSPatch **v1.2** (`-l 2` + slim embed) |
| Pack | `mapping-packs/179451.json` |
| Device | `Medium_Phone_API_36.1` emulator (x86_64, Play image, ARM translation) |

| Check | Result | Evidence |
| --- | --- | --- |
| Create/Update Grindr++ pipeline | Pass | Export installed splits → clone `com.grindrapp.android.plus` → icon → Morphe B → sign → LSPatch → PackageInstaller |
| `resources.arsc` install rule | Pass | Patched base + `config.xxhdpi` have `resources.arsc` **STORED** and data offset `% 4 == 0`; `adb install-multiple` succeeds |
| Clone launch | Pass | `com.grindrapp.android.plus` reaches the Grindr login screen; no `ExceptionInInitializerError` |
| Modern embed module | Pass | LSPatch v1.2 logs `VectorModuleManager: Loaded module dev.fatter.gpp.morphe.payload successfully`; `GppXposedModule.onModuleLoaded` / `onPackageLoaded` / `onPackageReady` |
| Pack + bridge init | Pass | `GrindrPlus.init` `hasPack=true`, `bridge_connect connected=true`, `init_ok initTimeMs=297` |
| Settings toggle matrix | Blocked | Clone reaches login, but the supplied lab account is **Account Restricted / Additional Review Required**; login is rejected by Grindr before a session exists |
| Morphe B timing | Pass (optimized) | After dirty-DEX-only rewrite + stored-entry preservation, Create/Update reached the installer in under 60s and the full install completed in ~80s on the emulator |

## 2026-11-09 retry — 26.23.0 / 194910 and account blocker

| Check | Result | Evidence |
| --- | --- | --- |
| Stock 194910 launch (x86_64 emulator) | Crash | `SIGSEGV` in `split_config.arm64_v8a.apk!libpairipcore.so` (ARM translation); no login screen |
| 194910 Morphe clone | Crash (inherited) | Same PairIP native crash; Morphe B reports `pairip-static-decrypt` deferred (no 194910 pack) |
| 26.16.1 Morphe clone | Pass to login | `com.grindrapp.android.plus` installs and reaches the login screen |
| Login with supplied lab account | Blocked | Stock Grindr also shows **Account Restricted / Additional Review Required**; not a clone/patch regression |
| Settings toggle matrix on Morphe clone | Blocked | No authenticated session; matrix evidence remains the rooted run above |

No credentials are stored in the repo or docs. PairIP 194910 needs a real ARM64 stock dump before a decrypt pack can be produced.



- Behavioral ON/OFF asserts for every mapped Manage Hooks row’s *user-visible* effect (screenshots, albums, explorer, etc.).
- Partial-hook remaining sites (ban UI details, boost upsells, profile detail fields).
- Settings UI badge walkthrough in Manager (config already has `runtimeStatus`/`runtimeReason`).
- Remap soft-skipped hooks when DEX allows; Command Prefix blocked while Chat terminal skipped.

## Sign-off

| Role | Name | Date | Notes |
| --- | --- | --- | --- |
| Code-verified gate | (agent / static review) | 2026-10-04 | Device not run |
| Device E2E | autonomous agent + rooted lab device | 2026-10-05 | Matrix + toggle + login session; see `debug-5dd9df.log` |

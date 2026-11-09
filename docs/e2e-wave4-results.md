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
| Patcher | JingMatrix LSPatch **v1.2** (`-l 1` + slim embed; `-l 2` rewrites DEX and corrupts Compose interface static methods) |
| Pack | `mapping-packs/179451.json` |
| Device | `Medium_Phone_API_36.1` emulator (x86_64, Play image, ARM translation) |

| Check | Result | Evidence |
| --- | --- | --- |
| Create/Update Grindr++ pipeline | Pass | Export installed splits → clone `com.grindrapp.android.plus` → icon → Morphe B → sign → LSPatch → PackageInstaller |
| `resources.arsc` install rule | Pass | Patched base + `config.xxhdpi` have `resources.arsc` **STORED** and data offset `% 4 == 0`; `adb install-multiple` succeeds |
| Clone launch | Pass | `com.grindrapp.android.plus` reaches the Grindr login screen; no `ExceptionInInitializerError` |
| Modern embed module | Pass | LSPatch v1.2 logs `VectorModuleManager: Loaded module dev.fatter.gpp.morphe.payload successfully`; `GppXposedModule.onModuleLoaded` / `onPackageLoaded` / `onPackageReady` |
| Pack + bridge init | Pass | `GrindrPlus.init` `hasPack=true`, `bridge_connect connected=true`, `init_ok initTimeMs=297` |
| Settings toggle matrix | Pass (2026-11-09 retry) | 26.16.1 clone on the emulator with LSPatch `-l 1`; login reached Browse, toggle OFF/ON verified (see the clone E2E section below) |
| Morphe B timing | Pass (optimized) | After dirty-DEX-only rewrite + stored-entry preservation, Create/Update reached the installer in under 60s and the full install completed in ~80s on the emulator |

## 2026-11-09 retry — 26.23.0 / 194910 and account blocker

| Check | Result | Evidence |
| --- | --- | --- |
| Stock 194910 launch (x86_64 emulator) | Crash | `SIGSEGV` in `split_config.arm64_v8a.apk!libpairipcore.so` (ARM translation); no login screen |
| 194910 Morphe clone | Crash (inherited) | Same PairIP native crash; Morphe B reports `pairip-static-decrypt` deferred (no 194910 pack) |
| 26.16.1 Morphe clone | Pass to login | `com.grindrapp.android.plus` installs and reaches the login screen |
| Morphe B disable-updates recipe (26.16.1) | Pass | Manager log: `Morphe B bytecode: disable-updates-dialog located Lrc9;, Lxa0;`; re-patched clone still launches to login |
| Login with supplied lab account | Pass (2026-11-09 retry) | The supplied lab account logged in on the 26.16.1 Morphe clone; the earlier “Account Restricted” state was not reproduced |
| Settings toggle matrix on Morphe clone | Pass (2026-11-09 retry) | Toggle OFF/ON verified on the clone; see the clone E2E section below |

No credentials are stored in the repo or docs. PairIP 194910 needs a real ARM64 stock dump before a decrypt pack can be produced.

## 2026-11-09 ARM64 lab — 194910 PairIP pack + Morphe B clone

Device: **Mi A2 lite** (`daisy`, Evolution X 10.14 / Android 15, arm64, rooted Magisk + Zygisk Vector). The user reinstalled the lab apps after a clean wipe; stock Grindr 194910 was installed **from Play Store** (`installerPackageName=com.android.vending`, signer SHA-1 `823f5a17c33b16b4775480b31607e7df35d67af8`).

| Check | Result | Evidence |
| --- | --- | --- |
| Stock 194910 launch + login | Pass | Login with the supplied lab account reached `HomeActivityOriginal`; no PairIP crash |
| `libsqliteJni` load | Pass (lab trigger) | The Play build does not expose the obfuscated `ti4` loader class to Frida; `Runtime.loadLibrary0(appClassLoader, "sqliteJni")` loaded the AndroidX bundled driver |
| PairIP RX dump | Pass | Two contiguous r-x maps of `split_config.arm64_v8a.apk` at file offset `0x1578000`, total `0xb5000`; script spliced `libsqliteJni.so` at `0x48778` (`RX@JNI-ish ff0301d1fd7b03a9…`, valid AArch64) |
| `pairip-decrypt/194910` pack | Pass | `libsqliteJni.so` + `pack.json` committed; Morphe B log: `Morphe B PairIP: detected`, `splice lib/arm64-v8a/libsqliteJni.so (1345696 bytes)`, `DEX integrity stubs=2` |
| Morphe B disable-updates (194910) | Pass | Manager log: `disable-updates-dialog located Lsu1;`, `Lk96;`, `Lbh0;`, `Ld2o;`; marker + recipe applied |
| Clone package install | Blocked by ROM UI | The LineageOS package installer dialog is `NOT_TOUCHABLE`; `InputDispatcher` drops taps as “untrusted touch due to occlusion”, so the manager's `SessionInstaller` gets `INSTALL_FAILED_ABORTED: User rejected permissions`. Manual `adb install-multiple` of the patched clone APKs is used for the lab instead of the UI path |

The package-installer failure is a **device/ROM input issue**, not an APK validation failure: the dialog is visible and the patched APKs are produced, but its window is non-touchable while the manager task is on top. The manual install bypasses that UI. This must be documented as a lab workaround; the rootless product path still requires a touchable system installer on the user's device.

## 2026-11-09 UI defects + LSPatch level 1 (ARM64 lab)

Device: **Mi A2 lite** (`daisy`, Evolution X 10.14, Android 15 per `ro.build.version.release`, arm64, Magisk root). Manager `5.0.0-dev-c4c0594` (debug), embed `5c84c0e`, host 26.23.0 / 194910 from Play.

| Check | Result | Evidence |
| --- | --- | --- |
| Clone icon injection | Pass | Patched APK has **0 duplicate ZIP entries**; `res/mipmap-anydpi-v21/ic_launcher.xml` / `ic_launcher_round.xml` refs remapped `0x7f100037/38/39` (brand p35 background/foreground/monochrome); legacy + p35 assets byte-identical to `app/src/main/assets/brand/`; launcher shows the cream/phoenix icon, padded (no zoom/crop) |
| Icon root cause | Fixed | `ZipWriter.writeEntry` appends; the previous step created duplicate names so Android kept the stock entry. Replacement now uses `deleteEntry(fillVoid=true)` + `writeEntry` |
| News HTML | Pass | `ReleaseNotes` sanitizes release bodies (HTML → text, entities, Markdown preview) and `news.json`; unit tests `ReleaseNotesTest`; News tab shows no raw tags |
| Product naming | Pass | User-facing “GrindrPlus/GrindPlus” removed from Install labels, Settings, dialogs, status dialog, bridge notification channel, Location/import messages; upstream `R0rt1z2/GrindrPlus` attribution intentionally kept. Android never renames an existing channel, so the bridge uses `bridge_service_channel_v2` (“Grindr++ Background Service”) and deletes the legacy channel |
| LSPatch `-l 1` Compose | Pass | `LSPatch: Signature bypass level: 1`; no `IncompatibleClassChangeError` / `DrawScope` crash |
| 194910 clone launch | Blocked | Native `SIGSEGV` in `split_config.arm64_v8a.apk!libpairipcore.so` (`tombstone_23`, offset `0x470f4`, fault addr `0x005f5e3333841dda`) right after `libpairipcore.so` loads; no login screen. The decrypted `libsqliteJni.so` splice alone does not bypass the native anti-tamper |
| Package installer | Blocked (ROM) | Same `NOT_TOUCHABLE` dialog; `adb install-multiple` of the 4 patched APKs succeeds |

## 2026-11-09 Morphe clone 26.16.1 — login + Settings matrix

Device: **Android 16 x86_64 emulator** (`sdk_gphone64_x86_64`), host **26.16.1 / 179451** from Play, manager debug `5.0.0-dev-90dc400`, LSPatch **`-l 1`**, pack 179451.

| Check | Result | Evidence |
| --- | --- | --- |
| Repatch + install | Pass | Export base + `arm64_v8a` + `xxhdpi`; LSPatch `-l 1`; PackageInstaller update succeeds |
| Clone launch | Pass | `LSPatch: Signature bypass level: 1`; no `IncompatibleClassChangeError`; reaches `LoginActivity` |
| Clone login compatibility | Pass | `CloneLoginCompat: reCAPTCHA assignment flags forced off for clone`; `Initialized hook: Clone login compatibility` |
| Login | Pass | Supplied lab account reaches Browse/Home (location + notification permissions accepted, AI consent declined) |
| Pack + hook summary | Pass | `hasPack=true`, 83 symbols, 7 fingerprint soft-warnings; `enabled=19, partial=3, skipped=6, disabled=1, failed=0, total=29` |
| Toggle OFF → runtime | Pass | Manager app selector **Clone: Grindr++** → “Allow screenshots” OFF; clone log `Hook Allow screenshots is disabled.`; summary `enabled=18, disabled=2` |
| Toggle restore | Pass | “Allow screenshots” ON; summary `enabled=19, disabled=1` |

The manager config is **per selected app**: toggling “Main Grindr App” does not change the clone. The app selector must show **Clone: Grindr++** for clone runtime changes; this is correct behavior but worth surfacing in the UI.



- Behavioral ON/OFF asserts for every mapped Manage Hooks row’s *user-visible* effect (screenshots, albums, explorer, etc.).
- Partial-hook remaining sites (ban UI details, boost upsells, profile detail fields).
- Settings UI badge walkthrough in Manager (config already has `runtimeStatus`/`runtimeReason`).
- Remap soft-skipped hooks when DEX allows; Command Prefix blocked while Chat terminal skipped.

## Sign-off

| Role | Name | Date | Notes |
| --- | --- | --- | --- |
| Code-verified gate | (agent / static review) | 2026-10-04 | Device not run |
| Device E2E | autonomous agent + rooted lab device | 2026-10-05 | Matrix + toggle + login session; see `debug-5dd9df.log` |

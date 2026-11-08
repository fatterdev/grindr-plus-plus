# ADR 0003: Morphe A — patch backend (orchestrator around LSPatch)

- **Status:** Accepted (design for Ver.5)
- **Date:** 2026-10-04
- **Deciders:** GrindrPlus Ver.5 maintainers
- **Related:** [vision.md](../vision.md), [manager-ui-lspatch.md](../manager-ui-lspatch.md), [0001-mapping-pack-schema-v2.md](0001-mapping-pack-schema-v2.md)

## Context

Rootless install today patches Grindr by embedding the module via integrated LSPatch inside:

`app/src/main/java/com/grindrplus/manager/installation/steps/PatchApkStep.kt`

That step already invokes `org.lsposed.patch.LSPatch` with `-l 2` (integrated), `-m` mod APK, and the manager keystore. Pipeline context: extract → patch → install (see [manager-ui-lspatch.md](../manager-ui-lspatch.md), `PlayGrindrDownloadStep`, `Installation` steps).

**Morphe A** is the Ver.5 delivery pillar: orchestrator-rootless (patch + embed Vector/LSPatch). It must **not** rewrite product hooks/features — that is **Morphe B**, after Ver.5 green ([vision.md](../vision.md) sequence).

## Decision

### 1. Morphe A = orchestrator, not feature rewrite

- Wrap and stabilize the existing LSPatch-integrated path (`-l 2`).
- Do not relocate or redesign hooks under `dev.fatter.gpp.hooks` as part of Morphe A.
- Vector / LSPatch embedding stays the rootless mechanism; GrindrPlus remains the module payload.

### 2. Extract `PatchBackend` from `PatchApkStep`

Introduce a `PatchBackend` (name may vary) interface extracted from the patching body of `PatchApkStep`:

- Inputs: APK set, mod file, keystore material, output dir, flags (e.g. embed on/off).
- Output: patched artifacts + structured logs (compatible with existing `Print`).
- Default implementation: current integrated LSPatch CLI invocation (`-l 2`, `-f`, `-v`, `-m`, `-k`, …).

`PatchApkStep` becomes a thin step that delegates to `PatchBackend`.

### 3. Default mode and ownership boundaries

| Concern | Owner |
| --- | --- |
| Default patch mode | **Integrated** (`-l 2`) |
| Keystore create/use | GrindrPlus **manager** (unchanged ownership) |
| Session install of splits | GrindrPlus **manager** (`SessionInstaller`, install steps) |
| Hook / mapping behavior | Module runtime (out of Morphe A scope) |

### 4. Pin LSPatch / Vector artifacts

- Ship or resolve **pinned** LSPatch/Vector artifact versions (checksum / explicit revision).
- **No blind nightly** pulls in CI or on-device patch flows.
- Document the pin location beside Gradle / manager dependencies when implemented.

**Current pin status:** `scripts/setup_lspatch.gradle.kts` downloads **JingMatrix/LSPatch v1.2** from GitHub Releases and verifies SHA-256 before installing into `app/libs/lspatch.jar` + `app/src/main/assets/lspatch/`.

**API 102 note (ADR 0008):** Embed uses modern `META-INF/xposed/*` registration. LSPatch **v0.8** predates libxposed API 102 and loads the embed through its legacy path, which crashes with a null `PreLoadedApk`. **v1.2** (August 2026) is the first stable release on API 102 and loads modern modules; it is the pin required by the Morphe embed path.

**resources.arsc note:** LSPatch v1.2 still aligns only `.so` and the nested original APK. `LSPatchIntegratedBackend` extends `ApkPatcher.Z_FILE_OPTIONS` at runtime with a 4-byte `resources.arsc` rule, because Android 11+ rejects a patched APK whose `resources.arsc` is compressed or unaligned.

| Artifact | Location | Pin |
| --- | --- | --- |
| Integrated LSPatch jar / `so*` / dex | `app/libs/lspatch.jar` + `app/src/main/assets/lspatch/` | **v1.2** — URL `…/releases/download/v1.2/lspatch-v1.2-487-release.jar`, upstream SHA-256 `D238FDC414D121B7FA454D8B4CCF420DF3A8C97D563761861FF92BD9C5DA2165` (see `app/libs/lspatch-v1.2.sha256.txt`) |
| Runtime patch CLI | `LSPatchIntegratedBackend` (`-l 2`) | Same jar pin; no on-device nightly fetch |

See also [manager-ui-lspatch.md](../manager-ui-lspatch.md) and [morphe-a.md](../morphe-a.md).

### 5. Pack-aware Grindr version selection

Install / download selection must prefer a Grindr `versionCode` for which a mapping pack exists (catalog `index.json` from [ADR 0001](0001-mapping-pack-schema-v2.md), or bundled `assets/mappings/`), rather than always trusting Play tip alone.

Today `PlayGrindrDownloadStep` can download Play’s tip even when it differs from the fork target — that mismatch is frozen as P0 debt in [wave0-p0-debt.md](../wave0-p0-debt.md). Morphe A closes the orchestration side by selecting pack-known versions when possible.

## Consequences

- **Positive:** Clear seam for testing patch backends; Ver.5 can green rootless delivery without waiting on Morphe B feature parity; artifact pins reduce supply-chain / breakage risk.
- **Negative:** Extra abstraction over a currently concrete step; catalog/pack awareness couples Manager install to mapping publishing.
- **Out of scope:** Static feature parity, new hooks, Settings truthfulness UI (separate ADRs / Wave 0).

## References

- `app/src/main/java/com/grindrplus/manager/installation/PatchBackend.kt`
- `app/src/main/java/com/grindrplus/manager/installation/LSPatchIntegratedBackend.kt` (`LSPatch(…, "-l", "2", …)`)
- `app/src/main/java/com/grindrplus/manager/installation/MorpheOrchestrator.kt`
- `app/src/main/java/com/grindrplus/manager/installation/steps/PatchApkStep.kt`
- `app/src/main/java/com/grindrplus/manager/installation/steps/PlayGrindrDownloadStep.kt`
- `scripts/setup_lspatch.gradle.kts`
- [morphe-a.md](../morphe-a.md)
- [manager-ui-lspatch.md](../manager-ui-lspatch.md)

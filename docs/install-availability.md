# Install availability matrix

The Manager decides which Grindr host versions it can install from a
server-side matrix instead of assuming the Play tip works.

- Source of truth: `install-availability.json` at the repository root,
  served from
  `https://raw.githubusercontent.com/fatterdev/grindr-plus-plus/refs/heads/master/install-availability.json`.
- The Manager fetches it with retries, falls back to the bundled
  `app/src/main/assets/install-availability.json`, and allows an override
  through the `custom_availability` setting.
- `scripts/validate_install_availability.py` checks the matrix against
  `mapping-packs/index.json` and the PairIP state.

## Schema (version 1)

| Field | Meaning |
| --- | --- |
| `schemaVersion` | Matrix schema version (currently `1`) |
| `moduleBuild` | Module build that produced the matrix |
| `updatedAt` | Last update date (`YYYY-MM-DD`) |
| `versions[]` | Host versions, newest first |
| `versionCode` / `versionName` | Grindr host version |
| `download` | `aurora` (Play/Aurora delivery), `url` (bundle URL), `unavailable` |
| `patch` | `mapping`, `mapping+pairip` |
| `pairip` | Host carries native PairIP anti-tamper |
| `morphe` / `alloy` | `working`, `not_working`, `untested` |
| `verifiedAt` | Last E2E date for this row |
| `notes` | Human-readable status |

## UI contract

- The Install screen lists the installed host first, then only versions
  with a download source.
- Statuses are shown verbatim; `not_working` rows require an explicit
  confirmation before patching.
- The selected host is displayed above the action button; the button reads
  "Create/Update Grindr++" for the installed host and
  "Download & patch <version>" for downloadable rows.
- Selecting another version clears the post-success "Open Grindr++" state
  so a new flow can start without restarting the Manager.

## Current state (2026-11-12)

- 26.16.1 / 179451: working rootless clone target (no PairIP).
- 26.23.0 / 194910: download + patch + install complete, launch blocked by
  native PairIP (`libpairipcore.so` SIGSEGV) — matches the matrix.
- Older packs: mapping present, download/patch untested.

See [e2e-wave5-install-availability.md](e2e-wave5-install-availability.md)
for the verification run.

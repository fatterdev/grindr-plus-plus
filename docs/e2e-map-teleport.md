# E2E — map teleport

Status: **implementation verified, functional E2E blocked by lab login**.

## Build under test

| Item | Value |
| --- | --- |
| Branch | `feat/map-teleport` |
| Commit | `c8ac0c5` |
| Module payload | `gpp_v5.0.0-dev-a1c2f93-embed-debug.apk` |
| Manager | `gpp_v5.0.0-dev-a1c2f93-morphe-debug.apk` (versionCode 17) |
| Host | stock Grindr 26.16.1 / 179451 |
| Clone | `com.grindrapp.android.plus`, LSPatch `-l 1` |
| Device | `emulator-5554` (Android 16 x86_64) |

## Verified

- `:app:compileEmbedDebugKotlin` and `:app:compileAlloyDebugKotlin` pass.
- `:app:testMorpheDebugUnitTest` passes.
- Mapping packs validate (`scripts/validate_mapping_packs.py`).
- Manager updated; clone re-patched with the new embed payload and installed.
- Clone logcat at startup:
  - `MapTeleport: installed on pj4#l (field=p)`
  - `GPP-Debug` `MAP_TP / installed` with `class=pj4`, `method=l`,
    `currentLocationField=p`.
- Hook summary unchanged (`enabled=20, partial=3, skipped=5, disabled=1,
  failed=0, total=29`); MapTeleport is intentionally not a `Hook` and has no
  Settings toggle.

## Blocked

The clone reaches `LoginActivity`; the lab session is not present. Opening the
Explore map and tapping **Explore Here** requires a logged-in account.

No credentials are stored in the repo or docs. The functional E2E must be
resumed after one of these:

1. a manual login on `emulator-5554` (or another 179451 clone);
2. the lab account supplied out-of-band;
3. a logged-in device with a 179451 clone.

## Functional checks to run once logged in

1. Open Explore, drag the map to a known point, tap **Explore Here**.
2. Expect: no cascade/exploration screen; toast `Teleported to Map selection`.
3. `adb logcat` shows `GPP-Debug` `MAP_TP / map_selection` and
   `TeleportController / teleport_applied`.
4. Manager config: `current_location` and `forced_coordinates` match the point;
   `current_location_name = "Map selection"`.
5. Manager external file `com.grindrapp.android.plus.location` contains the
   coordinates.
6. Teleport Locations dialog shows the saved `Map selection` entry.
7. `/tp` still overwrites the target (no “Teleportation disabled” dialog);
   `/tp off` clears both keys and deletes the Manager file.
8. App reads the spoofed latitude/longitude (LocationSpoofer hook).

## Release note

The mapping pack loader prefers the remote pack on `master`. Until
`mapping-packs/179451.json` (and the 185656/194910 packs) is published remotely,
a device with network will fetch the old pack and MapTeleport will soft-skip.
The local E2E uses the bundled assets (cache cleared / remote fetch failed).
No remote push has been performed.

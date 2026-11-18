# E2E — map teleport

Status: **functional E2E verified on the visible map button (179451)**.

## Build under test

| Item | Value |
| --- | --- |
| Branch | `feat/map-teleport` |
| Module payload | `gpp_v5.0.0-dev-ebd6918-embed-debug.apk` |
| Manager | `gpp_v5.0.0-dev-f5c9702-morphe-debug.apk` (versionCode 17) |
| Host | stock Grindr 26.16.1 / 179451 |
| Clone | `com.grindrapp.android.plus`, LSPatch `-l 1` |
| Device | lab emulator (Android 16 x86_64) |

## Verified

- `:app:compileEmbedDebugKotlin` and `:app:assembleEmbedDebug` pass.
- Manager updated; clone re-patched with the new embed payload and installed.
- Clone logcat at startup:
  - `MapTeleport: primary installed on pj4#l`
  - `MapTeleport: roam candidates b(WeakReference,Roam,...), c(WeakReference), e(WeakReference,Roam), f(WeakReference,RoamOfferUiData,...)`
  - `MapTeleport: roam fallback installed on coa (4 methods)`
- Functional flow (Profile → Roam → map → **Show My Profile Here**):
  - no "Wait for Boost to End" dialog;
  - the map closes and the grid is shown again;
  - toast `Teleported to Map selection`;
  - `GPP-Debug` `MAP_TP / map_state` → `mapView=pj4`, `pin=true`, `coords=true`;
  - `GPP-Debug` `MAP_TP / roam_filter` → `method=c`,
    `activity=...BrowseExploreMapActivity`, `coords=true`;
  - `GPP-Debug` `MAP_TP / map_selection` and `TeleportController /
    teleport_applied` with the selected latitude/longitude, source `map-roam`,
    `displayName`/`saveAs` = `Map selection`.
- Manager config for the clone:
  - `current_location = <lat>,<lon>`
  - `current_location_name = Map selection`
  - `forced_coordinates = <lat>,<lon>`
- Manager external file `com.grindrapp.android.plus.location` contains the same
  coordinates.
- The saved `Map selection` DB upsert completes without an error log.

## Notes

- `TeleportController` logs `httpClient not initialized; geohash skipped` on this
  lab build: the `InstanceManager` never captured the session instances, so the
  explicit `PUT /v4/location` call is skipped. The in-process
  `forced_coordinates` override still applies to every `Location` read, which is
  the same behavior as `/tp` on this build.
- The premium **Explore Here** entry is not reachable on the lab account (no
  premium map entry). The `pj4#l` hook is verified installed; its functional
  path is unchanged and shares the same `TeleportController`.

## Manual checks still worth repeating

1. Drag the map to a different known point, tap **Show My Profile Here**;
   confirm the config/Manager file coordinates change.
2. Disable **Location spoofer** and confirm the stock Roam/Boost flow is shown
   again (no teleport, no suppression).
3. `/tp` still overwrites the target (no “Teleportation disabled” dialog);
   `/tp off` clears both keys and deletes the Manager file.
4. Teleport Locations dialog shows the saved `Map selection` entry.

## Release note

The mapping pack loader prefers the remote pack on `master`. Until
`mapping-packs/179451.json` (and the 185656/194910 packs) is published remotely,
a device with network will fetch the old pack and MapTeleport will soft-skip.
The local E2E uses the bundled assets (cache cleared / remote fetch failed).
No remote push has been performed.

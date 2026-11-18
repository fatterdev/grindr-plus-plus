# Map teleport

Grindr's map screens have a center-pin selection flow: drag the map, then confirm
with the map action button. By default that confirmation opens the cascade /
exploration view (premium **Explore Here**) or the Roam purchase flow (visible
**Show My Profile Here**); it does **not** change the account location.

This patch intercepts the confirmation and runs the same real teleport path as
the `/tp` command. It is always active (no Settings toggle) and soft-skips when
the active mapping pack does not provide the hook keys.

## Hook points

| Grindr | Map view class | Explore Here | Selected-location field |
| --- | --- | --- | --- |
| 26.16.1 / 179451 | `pj4` | `l()` | `p` (`ExploreMapLocation`) |
| 26.19.0 / 185656 | `ffe` | `g0()` | `currentLocation` |
| 26.23.0 / 194910 | `ffe` | `g0()` | `currentLocation` |

`ExploreMapLocation` coordinates:

| Grindr | Latitude | Longitude |
| --- | --- | --- |
| 179451 | field `f7730a` | field `b` |
| 185656 | `getLatitude()` | `getLongitude()` |
| 194910 | `d()` | `e()` |

The hook is `BEFORE` and calls `setResult(null)` only when the selected
location was read successfully. If the coordinates are unavailable, the stock
behaviour is left untouched.

### Visible map button (Roam path)

On 179451 the map button rendered by the current build is a Compose button
whose handler lives in `BrowseExploreMapFragment`. It routes through the Roam
dialogs helper (`coa`):

- `coa.c(WeakReference)` — "Wait for Boost to End" gate;
- `coa.b/e/f` — Roam offer / session dialogs.

The fallback hooks those methods and, only while the current activity is
`BrowseExploreMapActivity`, reads the selected location from the map fragment
and runs the teleport. The stock dialog is suppressed and the map activity is
finished so the grid reloads on the new location.

The fragment is found by walking `getSupportFragmentManager().getFragments()`
via reflection. Do **not** cast the activity to `androidx.fragment.app.FragmentActivity`:
the module's androidx classes can be loaded by a different class loader than the
host APK, and the cast silently fails (this was the first E2E failure).

When the selected-location field is still `null` (pin not updated yet), the
camera target of the map's `GoogleMap` is used instead — the pin is centered on
it, so the coordinates match.

## Mapping pack keys

```json
"MapTeleport.exploreMapView": {
  "kind": "class",
  "name": "pj4",
  "method": "l"
},
"MapTeleport.exploreMapView.currentLocationField": {
  "kind": "field",
  "name": "p"
},
"MapTeleport.exploreMapView.googleMapField": {
  "kind": "field",
  "name": "o"
},
"MapTeleport.exploreMapLocation.latitudeField": {
  "kind": "field",
  "name": "f7730a"
},
"MapTeleport.exploreMapLocation.longitudeField": {
  "kind": "field",
  "name": "b"
},
"MapTeleport.exploreMapFragment": {
  "kind": "class",
  "name": "com.grindrapp.android.ui.browse.presentation.ui.BrowseExploreMapFragment"
},
"MapTeleport.exploreMapFragment.mapViewField": {
  "kind": "field",
  "name": "r"
},
"MapTeleport.roamDialog": {
  "kind": "class",
  "name": "coa"
},
"MapTeleport.roamDialog.c": { "kind": "method", "name": "c" },
"MapTeleport.roamDialog.e": { "kind": "method", "name": "e" },
"MapTeleport.roamDialog.b": { "kind": "method", "name": "b" },
"MapTeleport.roamDialog.f": { "kind": "method", "name": "f" }
```

Method-based coordinates use
`MapTeleport.exploreMapLocation.latitudeMethod` /
`...longitudeMethod` instead of the field keys.

185656/194910 carry the fragment keys (`mapViewField = exploreMapView`) but not
the `roamDialog` keys; the Roam fallback soft-skips there and only the
**Explore Here** hook is active.

Older packs without resolved names keep the keys with an empty `name` and a
note; `MappingDictionary` treats them as a soft-skip.

## Shared teleport path

`TeleportController` is the single write path for:

- `/tp` and `/tp off`
- the map selection (Explore Here and the visible map button)
- the in-app **Teleport Locations** dialog

`apply(lat, lon, ...)` writes:

1. `current_location` (session/legacy key)
2. `current_location_name`
3. `forced_coordinates` (persistent key)
4. the Manager bridge file `$packageName.location` (`setForcedLocation`)
5. an optional saved location in the module Room DB
6. the Grindr API geohash (`PUT /v4/location`)

`clear()` reverses all of it: both config keys are emptied and the Manager
forced-location file is deleted.

`forced_coordinates` is therefore the persistent teleport target, not a lock.
`/tp` overwrites it instead of refusing to run.

## Truthfulness

- The hooks are installed unconditionally from `GrindrPlus.initializeCore()`.
- There is no new Settings entry/toggle.
- If **Location spoofer** is disabled, the map selection is **not** hijacked:
  the stock Explore/Roam flow runs and the module only logs the skip.
- If the mapping pack lacks the hook keys, the button keeps the stock behaviour
  and the module logs a soft-skip.
- A successful selection shows `Teleported to Map selection` and leaves the map.

## E2E notes

The mapping pack loader prefers the remote pack, then the on-device cache, then
the bundled assets. Until the remote pack is published, testing a new pack
requires forcing the bundled copy:

1. clear `files/mapping-packs-cache/` in the clone;
2. write `files/mapping_pack_base_url.txt` with an unreachable base URL (or
   disable network for the clone);
3. restart Grindr.

Verification: no cascade/Roam dialog opens, `GPP-Debug` logs `map_state`,
`roam_filter` and `map_selection`, the `current_location` /
`forced_coordinates` values match the selected point, the Manager
`$packageName.location` file is written, and the saved `Map selection` entry
appears in the teleport dialog.

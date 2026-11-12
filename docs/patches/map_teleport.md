# Map teleport

Grindr's Explore map has a center-pin selection flow: drag the map, then confirm
with **Explore Here** (`R.id.view_nearby_profiles`). By default that confirmation
opens the cascade/exploration view for the selected area; it does **not** change
the account location.

This patch intercepts the confirmation and runs the same real teleport path as
the `/tp` command. It is always active (no Settings toggle) and soft-skips when
the active mapping pack does not provide the hook keys.

## Hook points

| Grindr | Map view class | Selection method | Selected-location field |
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
Explore behaviour is left untouched.

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
"MapTeleport.exploreMapLocation.latitudeField": {
  "kind": "field",
  "name": "f7730a"
},
"MapTeleport.exploreMapLocation.longitudeField": {
  "kind": "field",
  "name": "b"
}
```

Method-based coordinates use
`MapTeleport.exploreMapLocation.latitudeMethod` /
`...longitudeMethod` instead of the field keys.

Older packs without resolved names keep the keys with an empty `name` and a
note; `MappingDictionary` treats them as a soft-skip.

## Shared teleport path

`TeleportController` is the single write path for:

- `/tp` and `/tp off`
- the map **Explore Here** selection
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

- The hook is installed unconditionally from `GrindrPlus.initializeCore()`.
- There is no new Settings entry/toggle.
- If **Location spoofer** is disabled, the map confirmation is suppressed and a
  toast asks the user to enable it; the module does not pretend the teleport
  succeeded.
- If the mapping pack lacks the hook keys, the button keeps the stock Explore
  behaviour and the module logs a soft-skip.

## E2E notes

The mapping pack loader prefers the remote pack, then the on-device cache, then
the bundled assets. Until the remote pack is published, testing a new pack
requires forcing the bundled copy:

1. clear `files/mapping-packs-cache/` in the clone;
2. write `files/mapping_pack_base_url.txt` with an unreachable base URL (or
   disable network for the clone);
3. restart Grindr.

Verification: no cascade opens, `GPP-Debug` logs `map_selection`, the
`current_location` / `forced_coordinates` values match the selected point, the
Manager `$packageName.location` file is written, and the saved `Map selection`
entry appears in the teleport dialog.

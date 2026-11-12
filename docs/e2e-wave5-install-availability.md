# E2E wave 5 — install pipeline + availability matrix

Date: 2026-11-12  
Device: Android 16 x86_64 emulator (`sdk_gphone64_x86_64`, 6 GB data
partition), host Grindr 26.16.1 / 179451 (Play-signed origin), Manager
debug `5.0.0-dev-42612c3` from branch `release/v5.0.0-dev-187cccf`.

## Results

| Check | Result | Evidence |
| --- | --- | --- |
| Export installed host | Pass | Bundle 79 MB; the installed host was already LSPatch-patched, so the export pulled the embedded `origin.apk` (118 MB, 11 dex) |
| Download mod from manifest | Pass | 9.3 MB embed APK |
| Clone + icon + Morphe B | Pass | base + `arm64_v8a` + `xxhdpi`; brand icon injected |
| LSPatch embed (`-l 1`) | Pass | `Signature bypass level: 1` |
| Session install | Pass | `APK installation completed successfully`, `Installed!` |
| Clone launch + module init | Pass | `com.grindrapp.android.plus` 26.16.1 runs; `module: Initialization completed in 824 ms` |
| Download another host from Play | Pass | 194910: 3 splits from Play CDN (sequential Fetch), patch, install |
| Matrix honesty for 194910 | Pass | Clone crashes in `libpairipcore.so` as declared (`morphe: not_working`) |
| Update path on the 6 GB emulator | Blocked by storage | `INSTALL_FAILED_INSUFFICIENT_STORAGE` / `Failed to free … on /data`; the cleanup step reduces but does not remove the constraint |

## Fixes found by this run

1. `resources.arsc` must stay STORED and 4-byte aligned (Android 11+):
   archive rewrites now preserve the entry compression method.
2. Play APKs keep compressed native libraries after a rewrite; the clone
   manifest is patched with `extractNativeLibs=true`.
3. Re-patching an already LSPatch-patched host nests LSPatch and crashes
   ART class verification at startup; the export extracts the embedded
   original APK instead.
4. Low-storage devices: free the extracted splits and the host bundle
   before the install step so the system installer can stage the patched
   APKs.
5. Play CDN splits: parallel Fetch range requests died at ~97 % of the
   arm64 split (`UNKNOWN_IO_ERROR`); a sequential download plus one clean
   retry completes.

## Residual debt

- Update path E2E on a device or AVD with more free space (the emulator has
  6 GB; peak staging for a ~250 MB patched set does not fit).
- Aurora/Play availability beyond 179451/194910 is untested; the matrix
  marks those rows `unavailable` / `untested`.

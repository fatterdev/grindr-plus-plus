# PairIP lab wiki — Grindr 26.19 + Alloy/Vector

Canonical write-up of the Alloy PairIP campaign (successes, failures, confounds) so the work is reusable.

## Read order

1. **[TOMBSTONE.md](TOMBSTONE.md)** — Alloy runtime path closed  
2. **[MORPHE_B_DECRYPT.md](MORPHE_B_DECRYPT.md)** — phase-1 static decrypt (natives + integrity stubs)  
3. **[EXPERIMENT_LOG.md](EXPERIMENT_LOG.md)** — full matrix v1–v11, lab hygiene, repro  
4. **[tombstone_20_sigill_summary.md](tombstone_20_sigill_summary.md)** — condensed crash  
5. **[ADR 0009](../../adr/0009-pairip-static-decrypt.md)** — decision + pack layout  
6. **[ADR 0007](../../adr/0007-morphe-b-bytecodepatch.md)** — bytecode rewrite scaffold  

## One-paragraph verdict

On Play **26.19.0 / 185656**, Vector injection breaks real PairIP; stubbing PairIP reaches `init_ok` then **SIGILL inside `libsqliteJni!JNI_OnLoad`** (encrypted native). Alloy runtime bypass is **tombstoned**. Use non-PairIP hosts for Alloy green; use **Morphe B static decrypt** for PairIP Play.

## 26.23.0 / 194910 update (2026-11-09)

Stock 194910 crashes on the x86_64 API 36 emulator with **SIGSEGV in `split_config.arm64_v8a.apk!libpairipcore.so`** before the login screen; the clone inherits the same crash. This is a host/PairIP native issue, not a Grindr++ hook issue.

On the real ARM64 lab device (rooted, Android 15), the **Play Store install** of 194910 (original signature `823f5a17c33b16b4775480b31607e7df35d67af8`, installer `com.android.vending`) launches and logs in. `libsqliteJni.so` is not loaded by the login/home flow; it is loaded by the AndroidX bundled SQLite driver. Triggering `Runtime.loadLibrary0(appClassLoader, "sqliteJni")` through Frida (lab only) mapped the decrypted RX from `split_config.arm64_v8a.apk` at file offset `0x1578000` (two contiguous r-x maps, `0xb5000` total). `scripts/pairip_dump_natives.py` spliced it into `pairip-decrypt/194910/libsqliteJni.so` at `0x48778` (`RX@JNI-ish ff0301d1fd7b03a9…`, valid AArch64). The pack is committed; Morphe B now applies it instead of reporting `pairip-static-decrypt` deferred.

The emulator remains unsuitable for PairIP native work; use the ARM64 device for dump and clone validation.

## Quick links

| Item | Path |
|------|------|
| Bypass code (diagnostic) | `app/src/module/java/com/gpp/hooks/PairIpEarlyBypass.kt` |
| Stub SO | `native/pairip-stub/` |
| Inventory script | `scripts/pairip_inventory.py` |
| Control success (no PairIP) | Experiment log § B2 — Grindr 26.16.1 / 179451 |

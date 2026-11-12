# PairIP lab wiki — Grindr 26.19 + Alloy/Vector

Canonical write-up of the Alloy PairIP campaign (successes, failures, confounds) so the work is reusable.

## Read order

1. **[TOMBSTONE.md](TOMBSTONE.md)** — Alloy runtime path closed  
2. **[MORPHE_B_DECRYPT.md](MORPHE_B_DECRYPT.md)** — phase-1 static decrypt (natives + integrity stubs)  
3. **[EXPERIMENT_LOG.md](EXPERIMENT_LOG.md)** — full matrix v1–v11, lab hygiene, repro  
4. **[PHASE3_195444.md](PHASE3_195444.md)** — 26.24.0 / 195444 native pack, stock dump, framework confounds  
5. **[tombstone_20_sigill_summary.md](tombstone_20_sigill_summary.md)** — condensed crash  
6. **[ADR 0009](../../adr/0009-pairip-static-decrypt.md)** — decision + pack layout  
7. **[ADR 0007](../../adr/0007-morphe-b-bytecodepatch.md)** — bytecode rewrite scaffold  

## One-paragraph verdict

On Play **26.19.0 / 185656**, Vector injection breaks real PairIP; stubbing PairIP reaches `init_ok` then **SIGILL inside `libsqliteJni!JNI_OnLoad`** (encrypted native). Alloy runtime bypass is **tombstoned**. Use non-PairIP hosts for Alloy green; use **Morphe B static decrypt** for PairIP Play.

## 26.23.0 / 194910 update (2026-11-09)

Stock 194910 crashes on the x86_64 API 36 emulator with **SIGSEGV in `split_config.arm64_v8a.apk!libpairipcore.so`** before the login screen; the clone inherits the same crash. This is a host/PairIP native issue, not a Grindr++ hook issue.

On the real ARM64 lab device (rooted, Android 15), the **Play Store install** of 194910 (original signature `823f5a17c33b16b4775480b31607e7df35d67af8`, installer `com.android.vending`) launches and logs in. `libsqliteJni.so` is not loaded by the login/home flow; it is loaded by the AndroidX bundled SQLite driver. Triggering `Runtime.loadLibrary0(appClassLoader, "sqliteJni")` through Frida (lab only) mapped the decrypted RX from `split_config.arm64_v8a.apk` at file offset `0x1578000` (two contiguous r-x maps, `0xb5000` total). `scripts/pairip_dump_natives.py` spliced it into `pairip-decrypt/194910/libsqliteJni.so` at `0x48778` (`RX@JNI-ish ff0301d1fd7b03a9…`, valid AArch64). The pack is committed; Morphe B now applies it instead of reporting `pairip-static-decrypt` deferred.

The emulator remains unsuitable for PairIP native work; use the ARM64 device for dump and clone validation.

## 26.24.0 / 195444 update (2026-11-12)

Play 195444 is a different PairIP build: `libpairipcore` 79.8 % different, `executeVM` at
`0x69db8`, crash analogue `0x40338`, FNV `0x4c950` / `0x37574`. The 194910 native pack is
not reusable. A Play-signed stock RX dump on the A2 Lite produced
`pairip-decrypt/195444/libsqliteJni.so` with **50 933 B / 188 ranges** (`0x48778..0x54f27`);
`libpairipcore` and the controls diff 0. Stock 195444 on the rooted A2 Lite only runs with
`com.grindrapp.android` in the Magisk denylist: Vector/LSPosed injection trips
`libpairipcore+0x6ef60`, and Frida trips `+0x2a34c`. The mapping pack is refined to
45/85 named symbols. Clone E2E is pending. Full report: [PHASE3_195444.md](PHASE3_195444.md).

## Quick links

| Item | Path |
|------|------|
| Bypass code (diagnostic) | `app/src/module/java/com/gpp/hooks/PairIpEarlyBypass.kt` |
| Stub SO | `native/pairip-stub/` |
| Inventory script | `scripts/pairip_inventory.py` |
| Control success (no PairIP) | Experiment log § B2 — Grindr 26.16.1 / 179451 |

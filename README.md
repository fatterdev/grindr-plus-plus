<p align="center">
  <img src="docs/brand/grindr-plus-plus/hero-master.jpg" alt="Grindr++ — take them all" width="100%">
</p>

<h1 align="center">Grindr++</h1>

<p align="center"><em>take them all</em></p>

<p align="center">
  Maintained by <a href="https://github.com/fatterdev">@fatterdev</a> — Xposed / Vector module for Grindr.
</p>

<p align="center">
  <a href="https://github.com/fatterdev/grindr-plus-plus/actions/workflows/verify.yml"><img src="https://img.shields.io/github/actions/workflow/status/fatterdev/grindr-plus-plus/verify.yml?branch=master&logo=github&label=Verify" alt="Verify"></a>
  <a href="https://github.com/fatterdev/grindr-plus-plus/actions/workflows/build_apk.yml"><img src="https://img.shields.io/github/actions/workflow/status/fatterdev/grindr-plus-plus/build_apk.yml?branch=master&logo=github&label=Build" alt="Build"></a>
  <a href="https://github.com/fatterdev/grindr-plus-plus/releases/latest"><img src="https://img.shields.io/github/v/release/fatterdev/grindr-plus-plus?label=Release" alt="Release"></a>
</p>

## What this is

**Grindr++** is the product. **GrindMod** is the Manager app (`dev.fatter.gpp.morphe` / `dev.fatter.gpp.alloy`). It rose from the archived GrindrPlus line (ElJaviLuki → R0rt1z2, then this tree) after that project was frozen and after the PairIP / VM phase. Not affiliated with legacy XDA **GrindrMod**.

The phoenix/mask is the product mark: a masked head, ready to take them all. GrindMod’s launcher adds a small gear badge so it is not confused with Grindr++.

**Ver.5 direction:** version-agnostic module driven by **mapping packs** (schema v2), soft-fail hooks, Settings↔runtime truthfulness, and **Morphe A** rootless install (export Play-installed Grindr → Grindr++ clone → LSPatch). Pack catalog: `mapping-packs/index.json`. Vision: [docs/vision.md](docs/vision.md).

**Mapping packs:** JSON per `versionCode` under `app/src/main/assets/mappings/` (bundled offline) **and** remotely from GitHub `mapping-packs/` so a new Grindr build can get a remapped pack **without** a full module APK rebuild. Loader: `MappingDictionary` — remote → device cache → assets → literals (soft-fail; active pack does not fall back to wrong-version literals). Details: [docs/remote-mapping-packs.md](docs/remote-mapping-packs.md).

This module is **not** affiliated with Grindr LLC. Use at your own risk.

## State now

- Dual APK: **Morphe** (GrindMod Manager + Install → Grindr++ clone — not a Vector module) and **Alloy** (GrindMod + Vector; Settings modding toggle).
- Remote mapping packs so version jumps do not always need a new APK.
- In-app **News** = wiki CTA + GitHub Releases (no Telegram feed).
- Soft-fail hooks: missing DEX fingerprints skip instead of crashing the host.

## Disclaimer

Free mod, no warranty. We are not responsible for lost chats, bans, or other issues. This project does not collect personal data and does not serve ads — the code is open source (GPL-3.0).

## Download & news

- Releases / APKs: [Releases](https://github.com/fatterdev/grindr-plus-plus/releases)
- In-app **News** tab: wiki CTA + GitHub Releases list — [docs/news.md](docs/news.md)
- Wiki: [fatterdev/grindr-plus-plus/wiki](https://github.com/fatterdev/grindr-plus-plus/wiki)
- CI: [Verify](https://github.com/fatterdev/grindr-plus-plus/actions/workflows/verify.yml) · [Build & Release](https://github.com/fatterdev/grindr-plus-plus/actions/workflows/build_apk.yml)

Compatibility is **pack-driven** per installed Grindr `versionCode` (catalog: `mapping-packs/index.json`).

## Installation

> **Migration note (Ver.5 namespace change):** GrindMod APKs now use the `dev.fatter.gpp.*` applicationIds (`dev.fatter.gpp.morphe`, `dev.fatter.gpp.alloy`). The new app id means the system treats it as a different app: **uninstall the old `com.gpp.*` build before installing the new one** — this also deletes the old app's local data (settings, block log, saved phrases, and the cache-held clone-signing key), so back up anything you need first. Existing Morphe clones stay bound to the old Manager (bridge + signing key) and cannot be updated by the new one: after installing the new Manager, **recreate the Grindr++ clone** via GrindMod → **Install**. For Alloy (Vector/LSPosed), re-open the module manager afterwards and re-confirm Grindr is in scope for `dev.fatter.gpp.alloy`.

### Rootless (Ver.5 / Morphe A — recommended product path)

1. Install **stock Grindr** from the Play Store.
2. Install **`gpp-morphe-*.apk`** (GrindMod) from [Releases](https://github.com/fatterdev/grindr-plus-plus/releases).
3. Open GrindMod → **Install** → **Create Grindr++** (copies the installed Play APK, clones as `com.grindrapp.android.plus`, LSPatch embed).
4. Open **Grindr++** for the modded app; stock **Grindr** stays for Play updates. Re-run Create/Update after Store updates.

Details: [docs/morphe-a.md](docs/morphe-a.md), [docs/manager-ui-lspatch.md](docs/manager-ui-lspatch.md).

### Root (Vector — Alloy APK)

**Requirements:** Magisk / KernelSU + [JingMatrix/Vector](https://github.com/JingMatrix/Vector) (API 102 / Vector ≥ 2.2).

1. Install **`gpp-alloy-*.apk`** (GrindMod Alloy) from [Releases](https://github.com/fatterdev/grindr-plus-plus/releases).
2. Install stock Grindr (mapping pack required for your `versionCode`).
3. Enable modding in GrindMod **Settings → Modding active** (root + Vector CLI) *or* enable the module in Vector with Grindr in scope.
4. Open Grindr and verify.

**Quick check:** long-press the **Browse** tab → Grindr++ status popup; unlimited cascade profiles and no third-party ads. In Settings → Manage Hooks, skipped/partial hooks show a status note (not silent no-ops).

## Features (maintained)

<details>
  <summary>Chat</summary>

  - Command console (`/help`)
  - Video calls on new chats
  - Hide chat indicators
  - Delete messages regardless of age
</details>

<details>
  <summary>Media</summary>

  - Unlimited expiring photos
  - View all received albums
  - Screenshots allowed
</details>

<details>
  <summary>Global</summary>

  - Ban details
  - Spoof Android ID
  - Reduced analytics
  - Developer features
  - Mod settings / hook management
  - Disable forced updates
</details>

<details>
  <summary>Profiles / Location / Premium</summary>

  - BMI, boost indicator, copy profile ID, distance, hidden fields, online status, favorites layout
  - Teleport / spoof location / saved locations
  - Unlimited cascade, Explore, filters, no third-party ads, saved phrases, no boost upsell, hide views, incognito
</details>

Some hooks are **skipped** or **partial** when the DEX fingerprint is gone — see soft-fail in `HookManager`.

## Known issues (notable)

- **Incognito:** unstable / turns itself off.
- **“Viewed Me”:** server-side, not modifiable.
- **Boost / Roaming:** disabled by default; re-enable by turning off the “Disable Boosting” hook.
- **Crash / albums:** try disabling “Unlimited Albums”.
- **Ad blocker:** empty profiles → whitelist `cdn.cookielaw.org` or disable AdAway.

## Development

See [docs/README.md](docs/README.md).

- Mapping packs (bundled): `app/src/main/assets/mappings/<versionCode>.json`
- Mapping packs (remote publish path): `mapping-packs/<versionCode>.json` — [docs/remote-mapping-packs.md](docs/remote-mapping-packs.md)
- Loader: `dev.fatter.gpp.core.mapping.MappingDictionary`
- Brand assets: [docs/brand/grindr-plus-plus](docs/brand/grindr-plus-plus)

## Lineage

Grindr++ is not a continuation-in-name of GrindrPlus. The useful parts of that archive were carried forward; the product is this tree.

- Original idea and mod: [ElJaviLuki/GrindrPlus](https://github.com/ElJaviLuki/GrindrPlus)
- Rewrite through archive: [R0rt1z2/GrindrPlus](https://github.com/R0rt1z2/GrindrPlus) and contributors
- This product: [fatterdev/grindr-plus-plus](https://github.com/fatterdev/grindr-plus-plus)
- Vector / LSPatch: [JingMatrix](https://github.com/JingMatrix) ([Vector](https://github.com/JingMatrix/Vector), [LSPatch](https://github.com/JingMatrix/LSPatch))

## License

GPL-3.0 — see [LICENSE](LICENSE). Credit lineage if you ship a derivative; do not present this tree as the archived GrindrPlus project.

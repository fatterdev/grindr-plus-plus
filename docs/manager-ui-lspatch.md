# Manager UI — Install tab (Morphe)

Bottom nav (Morphe): **Install · Block Log · Home · News · Settings**. Alloy omits Install.

## Primary path (rootless)

1. User installs **stock Grindr** from Play (`com.grindrapp.android`).
2. GrindMod Install → **Create / Update Grindr++**.
3. Pipeline: export installed APK/splits → clone package `com.grindrapp.android.plus` (label **Grindr++**, brand icon) → LSPatch `-l 1` embed → session install. If the installed host is already LSPatch-patched, the export pulls its embedded original APK instead of re-patching the wrapper.
4. Stock Play Grindr remains; Grindr++ is the modded clone. After a Play update, tap Update again (mapping packs apply).

Module DEX comes from GitHub Releases (`manifest.json` mod URL). The host APK comes from the installed Play app by default, or from Play/Aurora when a version is picked in the availability list ([install-availability.md](install-availability.md)).

## Emergency fallback

Custom Files (local Grindr bundle zip + mod) remains available under Install advanced section for lab/offline use — not the documented primary UX.

## News tab

Wiki CTA + GitHub Releases — see [news.md](news.md).

# Dual APK migration (Morphe / Alloy)

GrindMod ships two packages (Manager label **GrindMod**):

| Audience | Package | Release asset |
| --- | --- | --- |
| Rootless | `dev.fatter.gpp.morphe` | `gpp_v5.0.0-dev-<sha>-morphe-release.apk` |
| Rooted | `dev.fatter.gpp.alloy` | `gpp_v5.0.0-dev-<sha>-alloy-release.apk` |

The `embed` flavor (`dev.fatter.gpp.morphe.payload`, `...-embed-release.apk`) is the slim `-m` payload the Manager feeds to LSPatch — not a user app. Debug APKs are CI artifacts only and must not be used to update an installed app.

Product / Morphe clone label: **Grindr++** (`com.grindrapp.android.plus`).

## Morphe

1. Install stock Grindr from Play.
2. Install the Morphe release APK.
3. GrindMod → Install → Create / Update Grindr++. The Install screen lists the installed host first; downloadable versions are the fallback when the installed host has no working patch ([install-availability.md](install-availability.md)).
4. If the installed host is already LSPatch-patched (same-package patch), the Manager exports its embedded original APK instead of re-patching the wrapper.

## Alloy

1. Install the Alloy release APK.
2. Enable the module in **Vector** and scope `com.grindrapp.android`, **or** use GrindMod Settings → **Modding active**.
3. Do not expect a Grindr++ clone on Alloy — stock Grindr + hooks.

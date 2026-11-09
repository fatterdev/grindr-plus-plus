# Morphe B (post Ver.5 green)

Static + hybrid feature-parity path. Starts after Ver.5 green + device E2E.

See [adr/0004-morphe-b.md](adr/0004-morphe-b.md).

## Pipeline

```text
select APKs
    → MorpheBPatchEngine
         · DexlibBytecodeBackend (dexlib2 rewrite + scan fallback)
              → mutated classes*.dex + assets/grindrplus/bytecode_scan.json
         · marker → assets/grindrplus/morphe_b.json
    → Morphe A LSPatchIntegratedBackend (-l 2 + slim embed)
    → SessionInstaller
```

`ApplyMorpheBStep` runs B before `SignClonedGrindrApk`; `PatchApkStep` then runs A. B failures are non-fatal (A continues). Running B after signing would invalidate the input signature LSPatch reads.

Bytecode today is a **dexlib2 rewriter** inside the orchestrator (ADR 0007): MorpheApp-shaped `bytecodePatch` execute, marker class inject, fingerprint locate. A real feature recipe now stubs the forced-update deprecation dialog (`disable-updates-dialog`: locate the class that builds `market://details?id=com.grindrapp.android`, replace its `showDeprecatedDialog(AppCompatActivity)` with `return-void`). Chat / Favorites instruction recipes remain deferred because their tip-DEX surfaces (`chatMessageMetaData`, `FavoritesFragment`) are absent; Cascade data-class getters are not rewrite targets.

## Delivery modes

| Mode | Meaning |
| --- | --- |
| `STATIC_RESOURCE` | Mutate APK before LSPatch (DEX marker class + JSON report; feature recipes later) |
| `RUNTIME_REMAP` | Remapped Xposed hooks for sites found on tip DEX |
| `DEFERRED` | No safe target — Settings stay skipped/honest |

## Candidates

| Feature | Mode | Notes |
| --- | --- | --- |
| Chat terminal | RUNTIME_REMAP | Needs `ChatTerminal.HANDLER` in pack |
| Video calls | RUNTIME_REMAP | `VideoCallHasNotChattedException` / fragment gates |
| Disable shuffle | DEFERRED | ShuffleUiState gone (Cascade V2) |
| Notification Alerts | DEFERRED | reminder fingerprint absent |
| Favorites | RUNTIME_REMAP | Soft-skip until `Favorites.FRAGMENT` remapped |
| Keep Alive WebSocket | RUNTIME_REMAP | `websocket.a` + `jcd` |
| Status Dialog | RUNTIME_REMAP | Material `TabLayout$TabView` |

## Package

`dev.fatter.gpp.morphe.b` — `MorpheBCatalog`, `MorpheBPatchEngine`.

package dev.fatter.gpp.core

import dev.fatter.gpp.BuildConfig

/**
 * Product delivery channel for the dual-APK split (ADR 0005).
 * Manager UI label is GrindMod for both; Grindr++ is the product / Morphe clone.
 * Package IDs differ (dev.fatter.gpp.morphe / dev.fatter.gpp.alloy).
 */
enum class DeliveryChannel {
    /** Rootless Manager / LSPatch installer only — not a Vector module. Package: dev.fatter.gpp.morphe */
    MORPHE,

    /** Sole rooted Vector module + Manager without LSPatch. Package: dev.fatter.gpp.alloy */
    ALLOY,

    /** Internal slim embed payload for LSPatch -m (not a user-facing product). */
    EMBED,
    ;

    val isRootlessManager: Boolean get() = this == MORPHE
    val isRootedModule: Boolean get() = this == ALLOY
    val showsInstallTab: Boolean get() = this == MORPHE

    /**
     * Package that hosts [dev.fatter.gpp.bridge.BridgeService] for IPC.
     * Slim embed payload is not an installed app — bind to Morphe Manager.
     */
    val bridgeHostPackage: String
        get() = when (this) {
            EMBED -> "dev.fatter.gpp.morphe"
            else -> BuildConfig.APPLICATION_ID
        }

    companion object {
        val current: DeliveryChannel =
            when (BuildConfig.DELIVERY_CHANNEL) {
                "alloy" -> ALLOY
                "embed" -> EMBED
                else -> MORPHE
            }
    }
}

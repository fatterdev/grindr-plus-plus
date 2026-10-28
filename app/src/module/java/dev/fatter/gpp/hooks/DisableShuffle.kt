package dev.fatter.gpp.hooks

import dev.fatter.gpp.core.mapping.MappingDictionary
import dev.fatter.gpp.core.logi
import dev.fatter.gpp.utils.Hook
import dev.fatter.gpp.utils.HookStage
import dev.fatter.gpp.utils.SoftSkipException
import dev.fatter.gpp.utils.hookConstructor
import dev.fatter.gpp.utils.compat.XposedHelpers.setObjectField

/**
 * Force-disable shuffle. Cascade V2 (26.16.1) removed ShuffleUiState — soft-skip unless pack remaps.
 */
class DisableShuffle : Hook(
    "Disable shuffle",
    "Forcefully disable the shuffle feature"
) {
    private val viewState = MappingDictionary.resolve("DisableShuffle.VIEW_STATE", "")
    private val shuffleUiState = MappingDictionary.resolve("DisableShuffle.SHUFFLE_UI_STATE", "")

    override fun init() {
        if (shuffleUiState.isBlank() || viewState.isBlank()) {
            throw SoftSkipException("fingerprint absent on this DEX (ShuffleUiState / Cascade V2)")
        }

        findClass(shuffleUiState).hookConstructor(HookStage.AFTER) { param ->
            listOf("a", "b", "c", "d", "f", "g", "j").forEach {
                runCatching { setObjectField(param.thisObject(), it, false) }
            }
            listOf("h", "i").forEach {
                runCatching { setObjectField(param.thisObject(), it, true) }
            }
        }

        findClass(viewState).hookConstructor(HookStage.AFTER) { param ->
            runCatching { setObjectField(param.thisObject(), "b", false) }
            runCatching { setObjectField(param.thisObject(), "d", false) }
        }
        logi("Disable shuffle: hooked $shuffleUiState / $viewState")
    }
}

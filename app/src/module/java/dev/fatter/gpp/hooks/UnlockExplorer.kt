package dev.fatter.gpp.hooks

import dev.fatter.gpp.utils.Hook
import dev.fatter.gpp.utils.HookStage
import dev.fatter.gpp.utils.hook

class UnlockExplorer : Hook(
    "Unlock Explorer",
    "Unlock all profiles in Explorer"
) {
    override fun init() {
        findClass("com.grindrapp.android.ui.profileV2.model.ProfileViewState")
            .hook("getShouldLockQuickbar", HookStage.BEFORE) { param ->
                param.setResult(false)
            }
    }
    // Alternative methods
    // getNumOfFreeExploreChatsRemaining
    // numOfFreeExploreChatsRemaining
}
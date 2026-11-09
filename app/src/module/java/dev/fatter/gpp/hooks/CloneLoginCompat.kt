package dev.fatter.gpp.hooks

import dev.fatter.gpp.GrindrPlus
import dev.fatter.gpp.core.Constants.GRINDR_PACKAGE_NAME
import dev.fatter.gpp.core.logi
import dev.fatter.gpp.core.mapping.MappingDictionary
import dev.fatter.gpp.utils.Hook
import dev.fatter.gpp.utils.HookStage
import dev.fatter.gpp.utils.hook
import dev.fatter.gpp.utils.compat.XposedHelpers.callMethod

/**
 * Rootless LSPatch clones cannot obtain a Play Integrity-backed reCAPTCHA
 * token: Play Integrity rejects the clone package/signature. Grindr's login
 * flow explicitly supports skipping reCAPTCHA when the assignment flag is
 * off, so force the two reCAPTCHA assignment flags off on clones only.
 *
 * Stock (Alloy) keeps the real reCAPTCHA path.
 */
class CloneLoginCompat : Hook(
    "Clone login compatibility",
    "Skip Play Integrity-backed reCAPTCHA on LSPatch clones"
) {
    override fun init() {
        if (GrindrPlus.context.packageName == GRINDR_PACKAGE_NAME) {
            logi("CloneLoginCompat: stock package, no-op")
            return
        }

        // MappingDictionary.resolve returns "" for an absent key even when a
        // fallback is supplied (active pack path), so fall back explicitly.
        val impl = MappingDictionary.resolve("CloneLoginCompat.isFeatureFlagEnabled", "")
            .ifEmpty { MappingDictionary.resolve("FeatureGranting.isFeatureFlagEnabled", "iv6") }
        val method = MappingDictionary.resolve("CloneLoginCompat.isFeatureFlagEnabled.invokeMethod", "")
            .ifEmpty {
                MappingDictionary.resolve("FeatureGranting.isFeatureFlagEnabled.invokeMethod", "a")
            }
        if (impl.isEmpty() || method.isEmpty()) {
            logi("CloneLoginCompat: soft-skip (empty remap)")
            return
        }

        findClass(impl).hook(method, HookStage.BEFORE) { param ->
            val flag = param.args().firstOrNull() ?: return@hook
            val key = runCatching { callMethod(flag, "toString") as? String }.getOrNull()
            if (key == "RecaptchaFirstPartyFeatureFlag" ||
                key == "RecaptchaThirdPartyFeatureFlag"
            ) {
                param.setResult(false)
            }
        }
        logi("CloneLoginCompat: reCAPTCHA assignment flags forced off for clone")
    }
}

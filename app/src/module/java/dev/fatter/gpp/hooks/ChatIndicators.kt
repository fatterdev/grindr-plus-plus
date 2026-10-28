package dev.fatter.gpp.hooks

import dev.fatter.gpp.core.Obfuscation
import dev.fatter.gpp.utils.Hook
import dev.fatter.gpp.utils.HookStage
import dev.fatter.gpp.utils.RetrofitUtils
import dev.fatter.gpp.utils.RetrofitUtils.RETROFIT_NAME
import dev.fatter.gpp.utils.RetrofitUtils.createServiceProxy
import dev.fatter.gpp.utils.hook

class ChatIndicators : Hook(
    "Chat indicators",
    "Don't show chat markers / indicators to others"
) {
    private val blacklistedPaths = setOf(
        "v4/chatstatus/typing"
    )

    override fun init() {
        val chatRestServiceClass = findClass(Obfuscation.G.ChatIndicators.CHAT_REST_SERVICE)

        val methodBlacklist = blacklistedPaths.mapNotNull {
            RetrofitUtils.findPOSTMethod(chatRestServiceClass, it)?.name
        }

        findClass(RETROFIT_NAME)
            .hook("create", HookStage.AFTER) { param ->
                val service = param.getResult()
                if (service != null && chatRestServiceClass.isAssignableFrom(service.javaClass)) {
                    param.setResult(createServiceProxy(
                        service,
                        chatRestServiceClass,
                        methodBlacklist.toTypedArray()
                    ))
                }
            }
    }
}

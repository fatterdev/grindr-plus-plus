package dev.fatter.gpp.hooks

import dev.fatter.gpp.core.mapping.MappingDictionary
import dev.fatter.gpp.core.logi
import dev.fatter.gpp.utils.Hook
import dev.fatter.gpp.utils.HookStage
import dev.fatter.gpp.utils.SoftSkipException
import dev.fatter.gpp.utils.hook

/**
 * Disable Grindr notification-reminder warnings.
 * 26.16.1: `notification_reminder_time` / historic `ue.e` absent.
 */
class NotificationAlerts : Hook(
    "Notification Alerts",
    "Disable all Grindr warnings related to notifications"
) {
    private val notificationManager = MappingDictionary.resolve(
        "NotificationAlerts.MANAGER",
        ""
    )
    private val managerMethod = MappingDictionary.resolve(
        "NotificationAlerts.MANAGER_METHOD",
        "a"
    )

    override fun init() {
        if (notificationManager.isBlank()) {
            throw SoftSkipException("notification_reminder_time absent")
        }
        findClass(notificationManager).hook(managerMethod, HookStage.BEFORE) { param ->
            param.setResult(null)
        }
        logi("Notification Alerts: hooked $notificationManager.$managerMethod")
    }
}

package dev.fatter.gpp.manager

import dev.fatter.gpp.manager.ui.installNavItem

/** Morphe contributes the rootless Install tab. */
object FlavorNav {
    fun installTab(): MainNavItem? = installNavItem()
}

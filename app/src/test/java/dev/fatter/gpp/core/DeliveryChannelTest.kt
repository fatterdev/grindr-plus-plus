package dev.fatter.gpp.core

import dev.fatter.gpp.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryChannelTest {
    @Test
    fun buildConfig_matchesMorpheFlavorDefaults() {
        // This class runs under testMorpheDebugUnitTest / testAlloyDebugUnitTest.
        when (BuildConfig.DELIVERY_CHANNEL) {
            "morphe" -> {
                assertEquals(DeliveryChannel.MORPHE, DeliveryChannel.current)
                assertTrue(DeliveryChannel.current.showsInstallTab)
                assertEquals("dev.fatter.gpp.morphe", BuildConfig.APPLICATION_ID)
                assertEquals("dev.fatter.gpp.morphe", DeliveryChannel.current.bridgeHostPackage)
            }
            "alloy" -> {
                assertEquals(DeliveryChannel.ALLOY, DeliveryChannel.current)
                assertEquals(false, DeliveryChannel.current.showsInstallTab)
                assertEquals("dev.fatter.gpp.alloy", BuildConfig.APPLICATION_ID)
                assertEquals("dev.fatter.gpp.alloy", DeliveryChannel.current.bridgeHostPackage)
            }
            "embed" -> {
                assertEquals(DeliveryChannel.EMBED, DeliveryChannel.current)
                assertEquals("dev.fatter.gpp.morphe.payload", BuildConfig.APPLICATION_ID)
                // Slim embed is not installed — bridge IPC targets Morphe Manager.
                assertEquals("dev.fatter.gpp.morphe", DeliveryChannel.current.bridgeHostPackage)
            }
            else -> error("Unexpected DELIVERY_CHANNEL=${BuildConfig.DELIVERY_CHANNEL}")
        }
    }
}

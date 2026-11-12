package dev.fatter.gpp.core

import android.widget.Toast
import dev.fatter.gpp.GrindrPlus
import dev.fatter.gpp.core.Utils.coordsToGeoHash
import dev.fatter.gpp.debug.AgentDebugLog
import dev.fatter.gpp.persistence.model.TeleportLocationEntity

/**
 * Single teleport path shared by the `/tp` command, the teleport dialog and the
 * map "Explore Here" selection.
 *
 * Writes both the legacy session key (`current_location`) and the persistent
 * key (`forced_coordinates`), mirrors the persistent key into the Manager
 * bridge file (`$packageName.location`) and updates the Grindr API geohash.
 *
 * Soft-fail: every persistence step is independent so a missing/old Manager
 * bridge never blocks the in-process spoof.
 */
object TeleportController {
    const val MAP_SAVED_NAME = "Map selection"

    private const val TAG = "TeleportController"

    suspend fun apply(
        latitude: Double,
        longitude: Double,
        displayName: String = "",
        saveAs: String? = null,
        source: String,
        notify: Boolean = true,
    ) {
        val coordinates = "$latitude,$longitude"

        runCatching { Config.put("current_location", coordinates) }
            .onFailure { Logger.e("$TAG: current_location write failed: ${it.message}", LogSource.MODULE, TAG) }
        runCatching { Config.put("current_location_name", displayName) }
            .onFailure { Logger.e("$TAG: current_location_name write failed: ${it.message}", LogSource.MODULE, TAG) }
        runCatching { Config.put("forced_coordinates", coordinates) }
            .onFailure { Logger.e("$TAG: forced_coordinates write failed: ${it.message}", LogSource.MODULE, TAG) }

        runCatching {
            GrindrPlus.bridgeClient.setForcedLocation(GrindrPlus.packageName, coordinates)
        }.onFailure {
            Logger.w(
                "$TAG: Manager forced-location file not updated (old Manager?): ${it.message}",
                LogSource.MODULE,
                TAG,
            )
        }

        if (saveAs != null) {
            runCatching {
                GrindrPlus.database.teleportLocationDao().upsertLocation(
                    TeleportLocationEntity(saveAs, latitude, longitude)
                )
            }.onFailure {
                Logger.e("$TAG: saved location '$saveAs' failed: ${it.message}", LogSource.MODULE, TAG)
            }
        }

        AgentDebugLog.log(
            hypothesisId = "MAP_TP",
            location = "TeleportController.apply",
            message = "teleport_applied",
            data = mapOf(
                "source" to source,
                "lat" to latitude,
                "lon" to longitude,
                "displayName" to displayName,
                "saveAs" to saveAs,
            ),
        )

        val httpClient = runCatching { GrindrPlus.httpClient }.getOrNull()
        if (httpClient != null) {
            runCatching {
                val geohash = coordsToGeoHash(latitude, longitude)
                httpClient.updateLocation(geohash, silent = true)
            }.onFailure {
                Logger.e("$TAG: geohash update failed: ${it.message}", LogSource.MODULE, TAG)
            }
        } else {
            Logger.w("$TAG: httpClient not initialized; geohash skipped", LogSource.MODULE, TAG)
        }

        if (notify) {
            val label = displayName.ifBlank { coordinates }
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Teleported to $label")
        }
    }

    suspend fun clear(
        source: String,
        notify: Boolean = true,
    ) {
        runCatching { Config.put("current_location", "") }
            .onFailure { Logger.e("$TAG: current_location clear failed: ${it.message}", LogSource.MODULE, TAG) }
        runCatching { Config.put("current_location_name", "") }
            .onFailure { Logger.e("$TAG: current_location_name clear failed: ${it.message}", LogSource.MODULE, TAG) }
        runCatching { Config.put("forced_coordinates", "") }
            .onFailure { Logger.e("$TAG: forced_coordinates clear failed: ${it.message}", LogSource.MODULE, TAG) }

        runCatching {
            GrindrPlus.bridgeClient.deleteForcedLocation(GrindrPlus.packageName)
        }.onFailure {
            Logger.w(
                "$TAG: Manager forced-location file not deleted: ${it.message}",
                LogSource.MODULE,
                TAG,
            )
        }

        AgentDebugLog.log(
            hypothesisId = "MAP_TP",
            location = "TeleportController.clear",
            message = "teleport_cleared",
            data = mapOf("source" to source),
        )

        if (notify) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Teleportation disabled")
        }
    }
}

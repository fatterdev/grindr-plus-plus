package dev.fatter.gpp.hooks

import android.widget.Toast
import dev.fatter.gpp.GrindrPlus
import dev.fatter.gpp.core.Config
import dev.fatter.gpp.core.LogSource
import dev.fatter.gpp.core.Logger
import dev.fatter.gpp.core.TeleportController
import dev.fatter.gpp.core.mapping.MappingDictionary
import dev.fatter.gpp.debug.AgentDebugLog
import dev.fatter.gpp.utils.HookStage
import dev.fatter.gpp.utils.Unhook
import dev.fatter.gpp.utils.compat.XposedHelpers
import dev.fatter.gpp.utils.hook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Always-on map teleport.
 *
 * Grindr's Explore map confirms a location with the "Explore Here" button
 * (`R.id.view_nearby_profiles`). On 26.16.1 that button calls `pj4.l()`, which
 * emits an ExploreCascadeArgs navigation event. On 26.19/26.23 it calls
 * `ffe.g0()`.
 *
 * This object is intentionally NOT a [dev.fatter.gpp.utils.Hook]: it has no
 * Settings toggle and is installed unconditionally from
 * [GrindrPlus.initializeCore]. Mapping-pack keys decide whether the hook can be
 * installed; missing keys are a soft-skip, never a crash.
 */
object MapTeleport {
    private const val TAG = "MapTeleport"
    private const val KEY_MAP_VIEW = "MapTeleport.exploreMapView"
    private const val KEY_CURRENT_LOCATION_FIELD = "MapTeleport.exploreMapView.currentLocationField"
    private const val KEY_LAT_FIELD = "MapTeleport.exploreMapLocation.latitudeField"
    private const val KEY_LON_FIELD = "MapTeleport.exploreMapLocation.longitudeField"
    private const val KEY_LAT_METHOD = "MapTeleport.exploreMapLocation.latitudeMethod"
    private const val KEY_LON_METHOD = "MapTeleport.exploreMapLocation.longitudeMethod"

    private val scope = CoroutineScope(Dispatchers.IO)
    private val unhooks = mutableListOf<Unhook>()

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return

        val className = MappingDictionary.className(KEY_MAP_VIEW)
        val methodName = MappingDictionary.methodName(KEY_MAP_VIEW)
        val currentLocationField = MappingDictionary.resolve(KEY_CURRENT_LOCATION_FIELD, "")

        if (className.isEmpty() || methodName.isNullOrEmpty() || currentLocationField.isEmpty()) {
            Logger.w(
                "$TAG: mapping keys missing " +
                    "(class='$className', method='$methodName', field='$currentLocationField') — soft-skip",
                LogSource.MODULE,
                TAG,
            )
            AgentDebugLog.log(
                hypothesisId = "MAP_TP",
                location = "MapTeleport.install",
                message = "soft_skip_missing_keys",
                data = mapOf(
                    "class" to className,
                    "method" to methodName,
                    "currentLocationField" to currentLocationField,
                ),
            )
            return
        }

        val mapViewClass = runCatching { GrindrPlus.loadClass(className) }.getOrElse {
            Logger.w("$TAG: class $className not found — soft-skip: ${it.message}", LogSource.MODULE, TAG)
            return
        }

        val selectMethod = mapViewClass.declaredMethods.firstOrNull {
            it.name == methodName && it.parameterCount == 0
        }
        if (selectMethod == null) {
            Logger.w(
                "$TAG: $className#$methodName() not found — soft-skip",
                LogSource.MODULE,
                TAG,
            )
            return
        }

        runCatching {
            selectMethod.hook(HookStage.BEFORE) { param ->
                // Suppress the original ExploreCascadeArgs navigation only when we
                // actually have a selected location. Otherwise leave stock Explore.
                if (handleSelection(param.thisObject(), currentLocationField)) {
                    param.setResult(null)
                }
            }
        }.onSuccess { unhook ->
            unhooks += unhook
            installed = true
            Logger.i(
                "$TAG: installed on $className#$methodName (field=$currentLocationField)",
                LogSource.MODULE,
                TAG,
            )
            AgentDebugLog.log(
                hypothesisId = "MAP_TP",
                location = "MapTeleport.install",
                message = "installed",
                data = mapOf(
                    "class" to className,
                    "method" to methodName,
                    "currentLocationField" to currentLocationField,
                ),
            )
        }.onFailure {
            Logger.e("$TAG: hook failed: ${it.message}", LogSource.MODULE, TAG)
            Logger.writeThrowable(it)
        }
    }

    fun cleanup() {
        unhooks.forEach { runCatching { it.unhook() } }
        unhooks.clear()
        installed = false
    }

    /** @return true when the stock Explore navigation must be suppressed. */
    private fun handleSelection(mapView: Any, currentLocationField: String): Boolean {
        val location = runCatching {
            XposedHelpers.getObjectField(mapView, currentLocationField)
        }.getOrNull()

        val coordinates = readCoordinates(location)
        if (coordinates == null) {
            Logger.w(
                "$TAG: selected location unreadable; leaving stock Explore behaviour",
                LogSource.MODULE,
                TAG,
            )
            return false
        }

        if (!Config.isHookEnabled("Location spoofer")) {
            Logger.w("$TAG: Location spoofer disabled; map teleport ignored", LogSource.MODULE, TAG)
            GrindrPlus.showToast(
                Toast.LENGTH_LONG,
                "Enable 'Location spoofer' to use map teleport",
            )
            return true
        }

        val (latitude, longitude) = coordinates
        AgentDebugLog.log(
            hypothesisId = "MAP_TP",
            location = "MapTeleport.handleSelection",
            message = "map_selection",
            data = mapOf("lat" to latitude, "lon" to longitude),
        )

        scope.launch {
            TeleportController.apply(
                latitude = latitude,
                longitude = longitude,
                displayName = TeleportController.MAP_SAVED_NAME,
                saveAs = TeleportController.MAP_SAVED_NAME,
                source = "map",
            )
        }
        return true
    }

    private fun readCoordinates(location: Any?): Pair<Double, Double>? {
        if (location == null) return null

        val latitude = readDouble(
            location,
            fieldKey = KEY_LAT_FIELD,
            methodKey = KEY_LAT_METHOD,
            fallbackGetter = "getLatitude",
        )
        val longitude = readDouble(
            location,
            fieldKey = KEY_LON_FIELD,
            methodKey = KEY_LON_METHOD,
            fallbackGetter = "getLongitude",
        )

        if (latitude != null && longitude != null) {
            return validate(latitude, longitude)
        }

        // Last resort for an unknown pack: the data class exposes exactly two doubles.
        val doubles = runCatching {
            location.javaClass.declaredFields
                .filter { it.type == Double::class.javaPrimitiveType }
                .onEach { it.isAccessible = true }
                .map { it.getDouble(location) }
        }.getOrDefault(emptyList())

        return if (doubles.size == 2) validate(doubles[0], doubles[1]) else null
    }

    private fun readDouble(
        target: Any,
        fieldKey: String,
        methodKey: String,
        fallbackGetter: String,
    ): Double? {
        val fieldName = MappingDictionary.resolve(fieldKey, "")
        if (fieldName.isNotEmpty()) {
            val value = runCatching { XposedHelpers.getObjectField(target, fieldName) }.getOrNull()
            (value as? Number)?.toDouble()?.let { return it }
        }

        val methodName = MappingDictionary.resolve(methodKey, "")
        if (methodName.isNotEmpty()) {
            val value = runCatching { XposedHelpers.callMethod(target, methodName) }.getOrNull()
            (value as? Number)?.toDouble()?.let { return it }
        }

        val value = runCatching { XposedHelpers.callMethod(target, fallbackGetter) }.getOrNull()
        return (value as? Number)?.toDouble()
    }

    private fun validate(latitude: Double, longitude: Double): Pair<Double, Double>? {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            Logger.w("$TAG: invalid coordinates $latitude,$longitude", LogSource.MODULE, TAG)
            return null
        }
        return latitude to longitude
    }
}

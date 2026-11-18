package dev.fatter.gpp.hooks

import android.app.Activity
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
 * Two entry points:
 * 1. "Explore Here" (`pj4.l()` on 179451, `ffe.g0()` on 185656/194910) — the
 *    premium/explore map confirmation.
 * 2. The visible "Show My Profile Here" button on the map currently exposed by
 *    the lab account. That button is a Compose button and routes through the
 *    Roam dialogs helper (`coa` on 179451); we intercept those methods when the
 *    current activity is the map and use the map's selected location.
 *
 * This object is intentionally NOT a [dev.fatter.gpp.utils.Hook]: it has no
 * Settings toggle and is installed unconditionally from
 * [GrindrPlus.initializeCore]. Missing mapping keys are a soft-skip.
 */
object MapTeleport {
    private const val TAG = "MapTeleport"
    private const val KEY_MAP_VIEW = "MapTeleport.exploreMapView"
    private const val KEY_CURRENT_LOCATION_FIELD = "MapTeleport.exploreMapView.currentLocationField"
    private const val KEY_GOOGLE_MAP_FIELD = "MapTeleport.exploreMapView.googleMapField"
    private const val KEY_LAT_FIELD = "MapTeleport.exploreMapLocation.latitudeField"
    private const val KEY_LON_FIELD = "MapTeleport.exploreMapLocation.longitudeField"
    private const val KEY_LAT_METHOD = "MapTeleport.exploreMapLocation.latitudeMethod"
    private const val KEY_LON_METHOD = "MapTeleport.exploreMapLocation.longitudeMethod"

    private const val KEY_MAP_ACTIVITY = "core.browseExploreActivity"
    private const val KEY_MAP_FRAGMENT = "MapTeleport.exploreMapFragment"
    private const val KEY_MAP_FRAGMENT_VIEW_FIELD = "MapTeleport.exploreMapFragment.mapViewField"
    private const val KEY_ROAM_DIALOG = "MapTeleport.roamDialog"
    private const val KEY_ROAM_METHOD_C = "MapTeleport.roamDialog.c"
    private const val KEY_ROAM_METHOD_E = "MapTeleport.roamDialog.e"
    private const val KEY_ROAM_METHOD_B = "MapTeleport.roamDialog.b"
    private const val KEY_ROAM_METHOD_F = "MapTeleport.roamDialog.f"

    private val scope = CoroutineScope(Dispatchers.IO)
    private val unhooks = mutableListOf<Unhook>()

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        val primary = installPrimary()
        val roam = installRoamFallback()
        installed = primary || roam
        if (!installed) {
            Logger.w("$TAG: no usable map hook keys — soft-skip", LogSource.MODULE, TAG)
        }
    }

    private fun installPrimary(): Boolean {
        val className = MappingDictionary.className(KEY_MAP_VIEW)
        val methodName = MappingDictionary.methodName(KEY_MAP_VIEW)
        val currentLocationField = MappingDictionary.resolve(KEY_CURRENT_LOCATION_FIELD, "")

        if (className.isEmpty() || methodName.isNullOrEmpty() || currentLocationField.isEmpty()) {
            Logger.w(
                "$TAG: primary map keys missing " +
                    "(class='$className', method='$methodName', field='$currentLocationField') — soft-skip",
                LogSource.MODULE,
                TAG,
            )
            return false
        }

        val mapViewClass = runCatching { GrindrPlus.loadClass(className) }.getOrElse {
            Logger.w("$TAG: class $className not found — soft-skip: ${it.message}", LogSource.MODULE, TAG)
            return false
        }

        val selectMethod = mapViewClass.declaredMethods.firstOrNull {
            it.name == methodName && it.parameterCount == 0
        }
        if (selectMethod == null) {
            Logger.w("$TAG: $className#$methodName() not found — soft-skip", LogSource.MODULE, TAG)
            return false
        }

        runCatching {
            selectMethod.hook(HookStage.BEFORE) { param ->
                if (handleSelection(param.thisObject(), currentLocationField)) {
                    param.setResult(null)
                }
            }
        }.onSuccess { unhook ->
            unhooks += unhook
            Logger.i("$TAG: primary installed on $className#$methodName", LogSource.MODULE, TAG)
            return true
        }.onFailure {
            Logger.e("$TAG: primary hook failed: ${it.message}", LogSource.MODULE, TAG)
            Logger.writeThrowable(it)
        }
        return false
    }

    private fun installRoamFallback(): Boolean {
        val mapActivityClass = MappingDictionary.resolve(KEY_MAP_ACTIVITY, "")
        val fragmentClass = MappingDictionary.className(KEY_MAP_FRAGMENT)
        val mapViewField = MappingDictionary.resolve(KEY_MAP_FRAGMENT_VIEW_FIELD, "")
        val dialogClass = MappingDictionary.className(KEY_ROAM_DIALOG)
        val methodNames = listOf(KEY_ROAM_METHOD_C, KEY_ROAM_METHOD_E, KEY_ROAM_METHOD_B, KEY_ROAM_METHOD_F)
            .mapNotNull { MappingDictionary.resolve(it, "") }
            .filter { it.isNotEmpty() }

        if (mapActivityClass.isEmpty() || fragmentClass.isEmpty() || mapViewField.isEmpty() ||
            dialogClass.isEmpty() || methodNames.isEmpty()
        ) {
            Logger.w(
                "$TAG: roam fallback keys missing — soft-skip " +
                    "(activity='$mapActivityClass', fragment='$fragmentClass', field='$mapViewField', " +
                    "dialog='$dialogClass', methods=$methodNames)",
                LogSource.MODULE,
                TAG,
            )
            return false
        }

        val dialog = runCatching { GrindrPlus.loadClass(dialogClass) }.getOrElse {
            Logger.w("$TAG: roam dialog class $dialogClass not found — soft-skip: ${it.message}", LogSource.MODULE, TAG)
            return false
        }

        val candidates = dialog.declaredMethods
            .filter { it.name in methodNames }
            .joinToString { m -> m.name + m.parameterTypes.joinToString(",", "(", ")") { it.simpleName } }
        Logger.i("$TAG: roam candidates $candidates", LogSource.MODULE, TAG)

        var hooked = 0
        methodNames.forEach { methodName ->
            runCatching {
                dialog.hook(
                    methodName,
                    HookStage.BEFORE,
                    filter = {
                        val activity = GrindrPlus.currentActivity
                        val coords = runCatching {
                            if (activity != null && activity.javaClass.name == mapActivityClass) {
                                teleportFromMap(activity, fragmentClass, mapViewField)
                            } else null
                        }.getOrElse { err ->
                            AgentDebugLog.log(
                                hypothesisId = "MAP_TP",
                                location = "MapTeleport.roamFilter",
                                message = "filter_error",
                                data = mapOf("method" to methodName, "error" to err.toString()),
                            )
                            null
                        }
                        AgentDebugLog.log(
                            hypothesisId = "MAP_TP",
                            location = "MapTeleport.roamFilter",
                            message = "roam_filter",
                            data = mapOf(
                                "method" to methodName,
                                "activity" to activity?.javaClass?.name,
                                "coords" to (coords != null),
                            ),
                        )
                        if (coords != null) {
                            if (applyTeleport(coords.first, coords.second, "map-roam")) {
                                // Leave the map so the grid reloads on the new
                                // location; the stock Roam/Boost dialog stays
                                // suppressed either way.
                                runCatching { activity?.finish() }
                                true
                            } else false
                        } else false
                    },
                    consumer = { it.setResult(null) },
                )
            }.onSuccess { unhooksForMethod ->
                unhooks += unhooksForMethod
                hooked += unhooksForMethod.size
            }.onFailure {
                Logger.w("$TAG: roam hook $dialogClass#$methodName failed: ${it.message}", LogSource.MODULE, TAG)
            }
        }

        if (hooked > 0) {
            Logger.i("$TAG: roam fallback installed on $dialogClass ($hooked methods)", LogSource.MODULE, TAG)
            return true
        }
        return false
    }

    fun cleanup() {
        unhooks.forEach { runCatching { it.unhook() } }
        unhooks.clear()
        installed = false
    }

    /** @return true when the stock map action must be suppressed. */
    private fun handleSelection(mapView: Any, currentLocationField: String): Boolean {
        val location = runCatching {
            XposedHelpers.getObjectField(mapView, currentLocationField)
        }.getOrNull()

        val coords = readCoords(location) ?: return false
        return applyTeleport(coords.first, coords.second, "map-explore")
    }

    private fun teleportFromMap(
        activity: Activity,
        fragmentClass: String,
        mapViewField: String,
    ): Pair<Double, Double>? {
        // Do not cast to host androidx types here: module dependencies can live in
        // a different class loader than the host APK. Reflection works across both.
        val fragment = findFragment(activity, fragmentClass) ?: run {
            AgentDebugLog.log(
                hypothesisId = "MAP_TP",
                location = "MapTeleport.teleportFromMap",
                message = "fragment_missing",
                data = mapOf("fragment" to fragmentClass),
            )
            return null
        }
        val mapView = runCatching {
            XposedHelpers.getObjectField(fragment, mapViewField)
        }.getOrNull() ?: run {
            AgentDebugLog.log(
                hypothesisId = "MAP_TP",
                location = "MapTeleport.teleportFromMap",
                message = "map_view_missing",
                data = mapOf("field" to mapViewField, "fragment" to fragmentClass),
            )
            return null
        }
        val currentLocationField = MappingDictionary.resolve(KEY_CURRENT_LOCATION_FIELD, "")
        val location = if (currentLocationField.isEmpty()) null else runCatching {
            XposedHelpers.getObjectField(mapView, currentLocationField)
        }.getOrNull()
        val fromPin = readCoords(location)
        AgentDebugLog.log(
            hypothesisId = "MAP_TP",
            location = "MapTeleport.teleportFromMap",
            message = "map_state",
            data = mapOf(
                "mapView" to mapView.javaClass.name,
                "pin" to (location != null),
                "coords" to (fromPin != null),
            ),
        )
        if (fromPin != null) return fromPin
        // The pin location is populated on camera idle; when it is still null,
        // read the map camera target (the pin is centered on it).
        return readCameraTarget(mapView)
    }

    private fun readCameraTarget(mapView: Any): Pair<Double, Double>? {
        val googleMapField = MappingDictionary.resolve(KEY_GOOGLE_MAP_FIELD, "")
        if (googleMapField.isEmpty()) return null
        val googleMap = runCatching { XposedHelpers.getObjectField(mapView, googleMapField) }.getOrNull()
            ?: return null
        val camera = runCatching { XposedHelpers.callMethod(googleMap, "getCameraPosition") }.getOrNull()
            ?: return null
        val target = runCatching { XposedHelpers.getObjectField(camera, "target") }.getOrNull()
            ?: return null
        val latitude = readMemberNumber(target, "latitude") ?: return null
        val longitude = readMemberNumber(target, "longitude") ?: return null
        return validate(latitude, longitude)
    }

    private fun readMemberNumber(target: Any, name: String): Double? {
        val fieldValue = runCatching { XposedHelpers.getObjectField(target, name) }.getOrNull()
        (fieldValue as? Number)?.let { return it.toDouble() }
        val methodValue = runCatching { XposedHelpers.callMethod(target, name) }.getOrNull()
        return (methodValue as? Number)?.toDouble()
    }

    private fun findFragment(activity: Activity, fragmentClass: String): Any? {
        val manager = runCatching {
            XposedHelpers.callMethod(activity, "getSupportFragmentManager")
        }.getOrNull() ?: return null
        return findInManager(manager, fragmentClass, 0)
    }

    private fun findInManager(manager: Any, fragmentClass: String, depth: Int): Any? {
        if (depth > 4) return null
        val fragments = runCatching {
            XposedHelpers.callMethod(manager, "getFragments") as? List<*>
        }.getOrNull() ?: return null
        fragments.filterNotNull().forEach { fragment ->
            if (fragment.javaClass.name == fragmentClass) return fragment
            val childManager = runCatching {
                XposedHelpers.callMethod(fragment, "getChildFragmentManager")
            }.getOrNull()
            if (childManager != null) {
                val child = findInManager(childManager, fragmentClass, depth + 1)
                if (child != null) return child
            }
        }
        return null
    }

    private fun applyTeleport(latitude: Double, longitude: Double, source: String): Boolean {
        if (!Config.isHookEnabled("Location spoofer")) {
            // Do not hijack the stock map action when teleporting is off.
            Logger.w("$TAG: Location spoofer disabled; map teleport ignored", LogSource.MODULE, TAG)
            return false
        }

        AgentDebugLog.log(
            hypothesisId = "MAP_TP",
            location = "MapTeleport.applyTeleport",
            message = "map_selection",
            data = mapOf("lat" to latitude, "lon" to longitude, "source" to source),
        )

        scope.launch {
            TeleportController.apply(
                latitude = latitude,
                longitude = longitude,
                displayName = TeleportController.MAP_SAVED_NAME,
                saveAs = TeleportController.MAP_SAVED_NAME,
                source = source,
            )
        }
        return true
    }

    private fun readCoords(location: Any?): Pair<Double, Double>? {
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

        val doubles = runCatching {
            location.javaClass.declaredFields
                .filter { it.type == Double::class.javaPrimitiveType }
                .onEach { it.isAccessible = true }
                .map { it.getDouble(location) }
        }.getOrElse { emptyList<Double>() }

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
            Logger.w("$TAG: invalid coords $latitude,$longitude", LogSource.MODULE, TAG)
            return null
        }
        return latitude to longitude
    }
}

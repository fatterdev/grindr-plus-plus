package dev.fatter.gpp.manager.installation

import org.json.JSONObject

/** Patch status for one delivery channel. */
enum class InstallStatus(val label: String) {
    WORKING("Working"),
    NOT_WORKING("Not working"),
    UNTESTED("Untested"),
    ;

    companion object {
        fun parse(raw: String?): InstallStatus = when (raw?.trim()?.lowercase()) {
            "working" -> WORKING
            "not_working" -> NOT_WORKING
            else -> UNTESTED
        }
    }
}

/** Where the Grindr APK for a version can come from. */
enum class DownloadSource {
    AURORA,
    URL,
    UNAVAILABLE,
    ;

    companion object {
        fun parse(raw: String?): DownloadSource = when (raw?.trim()?.lowercase()) {
            "aurora" -> AURORA
            "url" -> URL
            else -> UNAVAILABLE
        }
    }
}

/**
 * One row of the server-side install availability matrix.
 *
 * The matrix pairs an APK source with the patch status for each delivery
 * channel. It is deliberately separate from the module manifest: the module
 * build can change while the host-version matrix stays the same.
 */
data class GrindrVersion(
    val versionCode: Long,
    val versionName: String,
    val download: DownloadSource,
    val apkUrl: String?,
    val patch: String,
    val pairip: Boolean,
    val morphe: InstallStatus,
    val alloy: InstallStatus,
    val verifiedAt: String?,
    val notes: String?,
    val installed: Boolean = false,
) {
    val hasPatch: Boolean get() = patch != "none"
    val canDownload: Boolean get() = download != DownloadSource.UNAVAILABLE
    val label: String get() = "$versionName ($versionCode)"

    /** Morphe is the Install tab's delivery channel. */
    val status: InstallStatus get() = morphe
}

data class InstallAvailability(
    val schemaVersion: Int,
    val moduleBuild: String,
    val updatedAt: String,
    val versions: List<GrindrVersion>,
) {
    /**
     * Mark the installed host (if present in the matrix) and return the rows
     * the Install screen should show: the local installed APK (when it has a
     * patch) plus every version with a download source.
     */
    fun visibleVersions(installedVersionCode: Long?): List<GrindrVersion> {
        val marked = versions.map { version ->
            version.copy(installed = installedVersionCode != null &&
                version.versionCode == installedVersionCode)
        }
        return marked
            .filter { it.installed || (it.canDownload && it.hasPatch) }
            .sortedWith(
                compareByDescending<GrindrVersion> { it.installed }
                    .thenBy { it.status.ordinal }
                    .thenByDescending { it.versionCode },
            )
    }

    fun installedVersion(installedVersionCode: Long?): GrindrVersion? =
        versions.firstOrNull { it.versionCode == installedVersionCode }

    companion object {
        fun parse(json: String): InstallAvailability {
            val root = JSONObject(json)
            val versionsJson = root.optJSONArray("versions")
                ?: throw IllegalArgumentException("install-availability: missing versions")
            val versions = buildList {
                for (index in 0 until versionsJson.length()) {
                    val item = versionsJson.getJSONObject(index)
                    val code = item.optLong("versionCode", 0L)
                    if (code <= 0L) continue
                    add(
                        GrindrVersion(
                            versionCode = code,
                            versionName = item.optString("versionName").ifBlank { code.toString() },
                            download = DownloadSource.parse(item.optString("download")),
                            apkUrl = item.optString("apkUrl").takeIf { it.isNotBlank() },
                            patch = item.optString("patch", "none").ifBlank { "none" },
                            pairip = item.optBoolean("pairip", false),
                            morphe = InstallStatus.parse(item.optString("morphe")),
                            alloy = InstallStatus.parse(item.optString("alloy")),
                            verifiedAt = item.optString("verifiedAt").takeIf { it.isNotBlank() },
                            notes = item.optString("notes").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
            return InstallAvailability(
                schemaVersion = root.optInt("schemaVersion", 1),
                moduleBuild = root.optString("moduleBuild"),
                updatedAt = root.optString("updatedAt"),
                versions = versions,
            )
        }
    }
}

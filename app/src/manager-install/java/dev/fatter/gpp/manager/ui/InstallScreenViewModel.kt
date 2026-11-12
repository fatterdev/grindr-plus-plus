package dev.fatter.gpp.manager.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fatter.gpp.core.Constants.GRINDR_PACKAGE_NAME
import dev.fatter.gpp.core.Logger
import dev.fatter.gpp.manager.installation.GrindrVersion
import dev.fatter.gpp.manager.installation.InstallAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class InstallScreenViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _moduleBuild = MutableStateFlow<Data?>(null)
    val moduleBuild = _moduleBuild.asStateFlow()

    private val _availability = MutableStateFlow<InstallAvailability?>(null)
    val availability = _availability.asStateFlow()

    private val _versions = MutableStateFlow<List<GrindrVersion>>(emptyList())
    val versions = _versions.asStateFlow()

    private val _installedVersionCode = MutableStateFlow<Long?>(null)
    val installedVersionCode = _installedVersionCode.asStateFlow()

    private val _installedVersionName = MutableStateFlow<String?>(null)
    val installedVersionName = _installedVersionName.asStateFlow()

    /**
     * Loads the module manifest (mod payload URL) and the host-version
     * availability matrix (APK source + patch status per delivery channel).
     */
    fun loadData(manifestUrl: String, availabilityUrl: String, context: Context) {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            val appContext = context.applicationContext
            val installed = readInstalledVersion(appContext)
            _installedVersionCode.value = installed?.first
            _installedVersionName.value = installed?.second

            val manifest = fetchManifest(manifestUrl)
            if (manifest.isFailure) {
                _errorMessage.value =
                    manifest.exceptionOrNull()?.localizedMessage ?: "Module manifest unavailable"
                _isLoading.value = false
                return@launch
            }
            val builds = manifest.getOrThrow()
            _moduleBuild.value = builds.firstOrNull()
            if (_moduleBuild.value == null) {
                _errorMessage.value = "Module manifest is empty"
                _isLoading.value = false
                return@launch
            }

            val availability = fetchAvailability(availabilityUrl, appContext)
            if (availability.isFailure) {
                _errorMessage.value =
                    availability.exceptionOrNull()?.localizedMessage
                        ?: "Install availability unavailable"
                _isLoading.value = false
                return@launch
            }
            val matrix = availability.getOrThrow()
            _availability.value = matrix
            _versions.value = matrix.visibleVersions(installed?.first)
            Logger.d("Install availability: ${_versions.value.size} visible host version(s)")
            _isLoading.value = false
        }
    }

    private suspend fun fetchManifest(manifestUrl: String): Result<List<Data>> {
        return withContext(Dispatchers.IO) {
            val client = HttpClient.instance
            val maxRetries = 3
            var lastException: Exception? = null

            for (attempt in 1..maxRetries) {
                try {
                    Logger.d("Loading module manifest from $manifestUrl (attempt $attempt/$maxRetries)")
                    val request = Request.Builder().url(manifestUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Server error: ${response.code}")
                        val body = response.body?.string() ?: throw IOException("Empty response body")
                        return@withContext Result.success(parseVersionData(body))
                    }
                } catch (e: Exception) {
                    lastException = e
                    Logger.e("Manifest attempt $attempt failed: ${e.message}")
                    if (attempt < maxRetries) delay(2000)
                }
            }
            Result.failure(lastException ?: IOException("Unknown error after $maxRetries retries"))
        }
    }

    private suspend fun fetchAvailability(
        availabilityUrl: String,
        context: Context,
    ): Result<InstallAvailability> {
        return withContext(Dispatchers.IO) {
            val client = HttpClient.instance
            val maxRetries = 3
            var lastException: Exception? = null

            for (attempt in 1..maxRetries) {
                try {
                    Logger.d("Loading install availability from $availabilityUrl (attempt $attempt/$maxRetries)")
                    val request = Request.Builder().url(availabilityUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Server error: ${response.code}")
                        val body = response.body?.string() ?: throw IOException("Empty response body")
                        return@withContext Result.success(InstallAvailability.parse(body))
                    }
                } catch (e: Exception) {
                    lastException = e
                    Logger.e("Availability attempt $attempt failed: ${e.message}")
                    if (attempt < maxRetries) delay(2000)
                }
            }

            // Offline / first-run fallback: the matrix bundled in the APK.
            try {
                val body = context.assets.open("install-availability.json")
                    .bufferedReader()
                    .use { it.readText() }
                Result.success(InstallAvailability.parse(body))
            } catch (e: Exception) {
                Result.failure(
                    IOException(
                        "Install availability unavailable: " +
                            (lastException?.message ?: e.message ?: "unknown error"),
                        lastException ?: e,
                    ),
                )
            }
        }
    }

    private fun parseVersionData(jsonData: String): List<Data> {
        return try {
            val result = mutableListOf<Data>()
            val jsonObject = JSONObject(jsonData)
            jsonObject.keys().forEach { key ->
                val jsonArray = jsonObject.getJSONArray(key)
                if (jsonArray.length() >= 2) {
                    result.add(
                        Data(
                            modVer = key,
                            grindrUrl = jsonArray.getString(0),
                            modUrl = jsonArray.getString(1),
                        ),
                    )
                }
            }
            result
        } catch (e: JSONException) {
            throw IOException("Invalid data format: ${e.localizedMessage}")
        }
    }

    private fun readInstalledVersion(context: Context): Pair<Long, String>? {
        return try {
            val info = context.packageManager.getPackageInfo(GRINDR_PACKAGE_NAME, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            code to (info.versionName ?: "")
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

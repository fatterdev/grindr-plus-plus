package dev.fatter.gpp.manager.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fatter.gpp.core.Config
import dev.fatter.gpp.core.Constants.GRINDR_PACKAGE_NAME
import dev.fatter.gpp.core.DeviceFlags
import dev.fatter.gpp.manager.AVAILABILITY_URL
import dev.fatter.gpp.manager.DATA_URL
import dev.fatter.gpp.manager.ManagerBridge
import dev.fatter.gpp.manager.TAG
import dev.fatter.gpp.manager.activityScope
import dev.fatter.gpp.manager.installation.DownloadSource
import dev.fatter.gpp.manager.installation.GrindrVersion
import dev.fatter.gpp.manager.installation.InstallStatus
import dev.fatter.gpp.manager.installation.Installation
import dev.fatter.gpp.manager.installation.steps.Print
import dev.fatter.gpp.manager.ui.components.BannerType
import dev.fatter.gpp.manager.ui.components.FileDialog
import dev.fatter.gpp.manager.ui.components.GrindrVersionSelector
import dev.fatter.gpp.manager.ui.components.MessageBanner
import dev.fatter.gpp.manager.utils.ErrorHandler
import dev.fatter.gpp.manager.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private val logEntries = mutableStateListOf<LogEntry>()

@Composable
fun InstallPage(
    context: Activity,
    innerPadding: PaddingValues,
    viewModel: InstallScreenViewModel = viewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val moduleBuild by viewModel.moduleBuild.collectAsState()
    val versions by viewModel.versions.collectAsState()
    val availability by viewModel.availability.collectAsState()
    val installedVersionCode by viewModel.installedVersionCode.collectAsState()
    val installedVersionName by viewModel.installedVersionName.collectAsState()

    var selectedVersion by remember { mutableStateOf<GrindrVersion?>(null) }
    var pendingNotWorking by remember { mutableStateOf<GrindrVersion?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var warningBannerVisible by remember { mutableStateOf(true) }
    val vectorFrameworkPresent = DeviceFlags.isLSPosed()
    var rootedBannerVisible by remember { mutableStateOf(vectorFrameworkPresent) }
    var showCustomFileDialog by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var customVersionName by remember { mutableStateOf("custom") }
    var customBundleUri by remember { mutableStateOf<Uri?>(null) }
    var customModUri by remember { mutableStateOf<Uri?>(null) }

    val grindrInstalled = remember { isPackageInstalled(context, GRINDR_PACKAGE_NAME) }
    var cloneInstalled by remember {
        mutableStateOf(isPackageInstalled(context, Installation.PRODUCT_CLONE_PACKAGE))
    }

    val manifestUrl = (Config.get("custom_manifest", DATA_URL) as String).ifBlank { DATA_URL }
    val availabilityUrl = (Config.get("custom_availability", AVAILABILITY_URL) as String)
        .ifBlank { AVAILABILITY_URL }

    LaunchedEffect(Unit) {
        viewModel.loadData(manifestUrl, availabilityUrl, context)
    }

    LaunchedEffect(versions) {
        if (selectedVersion == null && versions.isNotEmpty()) {
            selectedVersion = versions.first()
            addLog("Host: ${selectedVersion?.label}", LogType.INFO)
        }
    }

    val installedEntry = availability?.installedVersion(installedVersionCode)

    val print: Print = { output ->
        val logType = ConsoleLogger.parseLogType(output)
        context.runOnUiThread { addLog(output, logType) }
    }

    if (showCustomFileDialog) {
        FileDialog(
            context = context,
            onDismiss = { showCustomFileDialog = false },
            onSelect = { versionName, bundleUri, modUri ->
                customVersionName = versionName
                customBundleUri = bundleUri
                customModUri = modUri
                showCustomFileDialog = false
                addLog("Emergency custom files selected: $versionName", LogType.INFO)
            },
        )
    }

    Column(
        modifier = Modifier
            .padding(innerPadding)
            .padding(16.dp)
            .fillMaxSize(),
    ) {
        when {
            isLoading -> LoadingScreen()
            errorMessage != null -> ErrorScreen(errorMessage!!) {
                viewModel.loadData(manifestUrl, availabilityUrl, context)
            }
            else -> {
                MessageBanner(
                    text = "• Installed Grindr is the primary path: Grindr++ is a separate clone\n" +
                        "• If the installed host has no working patch, pick a downloadable version below\n" +
                        "• Status comes from the server-side APK + patch matrix\n" +
                        "• Don't close GrindMod mid-patch",
                    isVisible = warningBannerVisible,
                    isPulsating = isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    type = BannerType.WARNING,
                    onDismiss = { warningBannerVisible = false },
                )

                if (vectorFrameworkPresent) {
                    MessageBanner(
                        text = "Vector detected — rooted users should use GrindMod Alloy " +
                            "(dev.fatter.gpp.alloy) and the Modding toggle in Settings. " +
                            "This Install tab is the rootless Morphe path.",
                        isVisible = rootedBannerVisible,
                        isPulsating = true,
                        modifier = Modifier.fillMaxWidth(),
                        type = BannerType.ERROR,
                        onDismiss = { rootedBannerVisible = false },
                    )
                }

                if (!grindrInstalled) {
                    MessageBanner(
                        text = "Grindr is not installed. Choose a downloadable host version below; " +
                            "GrindMod will fetch and patch it without touching stock Grindr.",
                        isVisible = true,
                        isPulsating = false,
                        modifier = Modifier.fillMaxWidth(),
                        type = BannerType.WARNING,
                        onDismiss = {},
                    )
                }

                if (installedVersionCode != null && installedEntry == null) {
                    MessageBanner(
                        text = "No patch entry for installed ${installedVersionName ?: ""} " +
                            "($installedVersionCode). Choose a working version below.",
                        isVisible = true,
                        isPulsating = false,
                        modifier = Modifier.fillMaxWidth(),
                        type = BannerType.ERROR,
                        onDismiss = {},
                    )
                } else if (installedEntry?.status == InstallStatus.NOT_WORKING) {
                    MessageBanner(
                        text = "Installed ${installedEntry.label} is marked " +
                            "${installedEntry.status.label.lowercase()}" +
                            (if (installedEntry.pairip) " (PairIP)" else "") +
                            ". Choose a working version below.",
                        isVisible = true,
                        isPulsating = false,
                        modifier = Modifier.fillMaxWidth(),
                        type = BannerType.ERROR,
                        onDismiss = {},
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Module build: ${moduleBuild?.modVer ?: "unavailable"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                GrindrVersionSelector(
                    versions = versions,
                    selected = selectedVersion,
                    onSelected = { selected ->
                        selectedVersion = selected
                        // A new selection starts a new operation: leave the
                        // post-success "Open Grindr++" state.
                        success = false
                        addLog("Host: ${selected.label} (${selected.status.label})", LogType.INFO)
                    },
                    isEnabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                ConsoleOutput(
                    logEntries = logEntries,
                    modifier = Modifier.weight(0.5f),
                    onClear = {
                        logEntries.clear()
                        addLog("Logs cleared", LogType.SUCCESS)
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = {
                            activityScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        StorageUtils.cleanupOldInstallationFiles(
                                            context,
                                            true,
                                            moduleBuild?.modVer,
                                        )
                                    }
                                    addLog("Cleaned up old files", LogType.SUCCESS)
                                } catch (e: Exception) {
                                    addLog("Cleanup failed: ${e.localizedMessage}", LogType.ERROR)
                                }
                            }
                        },
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clean Up", modifier = Modifier.padding(vertical = 8.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    val host = selectedVersion
                    val build = moduleBuild
                    Button(
                        onClick = {
                            when {
                                success -> launchPackage(
                                    context,
                                    Installation.PRODUCT_CLONE_PACKAGE,
                                    "Grindr++",
                                )
                                host == null -> Unit
                                host.status == InstallStatus.NOT_WORKING ->
                                    pendingNotWorking = host
                                build != null -> startCreateGrindrPlus(
                                    host = host,
                                    moduleBuild = build,
                                    context = context,
                                    print = print,
                                    onStarted = { isWorking = true },
                                    onCompleted = { ok ->
                                        isWorking = false
                                        success = ok
                                        if (ok) {
                                            cloneInstalled = isPackageInstalled(
                                                context,
                                                Installation.PRODUCT_CLONE_PACKAGE,
                                            )
                                        }
                                    },
                                )
                            }
                        },
                        enabled = (
                            success || (
                                host != null && build != null &&
                                    (host.installed || host.canDownload)
                                )
                            ) && !isWorking,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = when {
                                isWorking -> "Patching…"
                                success -> "Open Grindr++"
                                host?.installed == true ->
                                    if (cloneInstalled) "Update Grindr++" else "Create Grindr++"
                                host != null -> "Download & patch ${host.versionName}"
                                else -> "Select a version"
                            },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    enabled = !isWorking,
                ) {
                    Text(if (showAdvanced) "Hide emergency fallback" else "Emergency: custom files")
                }

                if (showAdvanced) {
                    OutlinedButton(
                        onClick = { showCustomFileDialog = true },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Select custom Grindr bundle + mod")
                    }
                    if (customBundleUri != null && customModUri != null) {
                        Button(
                            onClick = {
                                isWorking = true
                                activityScope.launch {
                                    try {
                                        val bundleFile = createTempFileFromUri(
                                            context,
                                            customBundleUri!!,
                                            "grindr-$customVersionName.zip",
                                        )
                                        val modFile = createTempFileFromUri(
                                            context,
                                            customModUri!!,
                                            "mod-$customVersionName.zip",
                                        )
                                        val mapsApiKey =
                                            (Config.get("maps_api_key", "") as String).ifBlank { null }
                                        val installation = Installation(
                                            context,
                                            customVersionName,
                                            modFile.absolutePath,
                                            mapsApiKey,
                                        )
                                        withContext(Dispatchers.IO) {
                                            installation.installCustom(bundleFile, modFile, print)
                                        }
                                        addLog("Custom install completed", LogType.SUCCESS)
                                        success = true
                                    } catch (e: Exception) {
                                        handleInstallationError(e, context)
                                    } finally {
                                        isWorking = false
                                    }
                                }
                            },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Run custom install (same-package)")
                        }
                    }
                }
            }
        }
    }

    pendingNotWorking?.let { host ->
        AlertDialog(
            onDismissRequest = { pendingNotWorking = null },
            title = { Text("Version marked not working") },
            text = {
                Text(
                    buildString {
                        append("${host.label} is marked ${host.status.label.lowercase()}")
                        if (host.pairip) append(" (PairIP)")
                        append(".\n\n")
                        if (!host.notes.isNullOrBlank()) append(host.notes).append("\n\n")
                        append("Continue anyway?")
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingNotWorking = null
                        val build = moduleBuild
                        if (build != null) {
                            startCreateGrindrPlus(
                                host = host,
                                moduleBuild = build,
                                context = context,
                                print = print,
                                onStarted = { isWorking = true },
                                onCompleted = { ok ->
                                    isWorking = false
                                    success = ok
                                    if (ok) {
                                        cloneInstalled = isPackageInstalled(
                                            context,
                                            Installation.PRODUCT_CLONE_PACKAGE,
                                        )
                                    }
                                },
                            )
                        }
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { pendingNotWorking = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Loading module builds…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
fun ErrorScreen(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Error: $errorMessage",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

private fun startCreateGrindrPlus(
    host: GrindrVersion,
    moduleBuild: Data,
    context: Activity,
    print: Print,
    onStarted: () -> Unit,
    onCompleted: (Boolean) -> Unit,
) {
    if (moduleBuild.modUrl.isBlank()) {
        addLog("Manifest entry missing mod URL.", LogType.ERROR)
        showToast(context, "Missing module download URL.")
        onCompleted(false)
        return
    }
    if (host.installed && !isPackageInstalled(context, GRINDR_PACKAGE_NAME)) {
        addLog("Installed host selected, but Grindr is not installed.", LogType.ERROR)
        showToast(context, "Install Grindr from Play Store first.")
        onCompleted(false)
        return
    }
    if (!host.installed && !host.canDownload) {
        addLog("No download source for ${host.label}.", LogType.ERROR)
        showToast(context, "No download source for ${host.label}.")
        onCompleted(false)
        return
    }

    onStarted()
    addLog(
        "Host: ${host.label} " +
            "(${if (host.installed) "installed" else host.download.name.lowercase()})",
        LogType.INFO,
    )
    addLog("Module: ${moduleBuild.modVer}", LogType.INFO)

    activityScope.launch {
        try {
            val mapsApiKey = (Config.get("maps_api_key", "") as String).ifBlank { null }
            val installation = Installation(
                context,
                moduleBuild.modVer,
                moduleBuild.modUrl,
                mapsApiKey,
                grindrVersionCode = if (host.installed) null else host.versionCode,
            )
            withContext(Dispatchers.IO) {
                if (host.installed) {
                    installation.createOrUpdateGrindrPlus(print)
                } else {
                    installation.createFromVersion(
                        versionCode = host.versionCode,
                        source = host.download,
                        apkUrl = host.apkUrl,
                        print = print,
                    )
                }
            }
            addLog("Grindr++ ready!", LogType.SUCCESS)
            showToast(context, "Grindr++ installed")
            onCompleted(true)
        } catch (e: Exception) {
            handleInstallationError(e, context)
            onCompleted(false)
        }
    }
}

private suspend fun createTempFileFromUri(context: Context, uri: Uri, filename: String): File {
    return withContext(Dispatchers.IO) {
        val tempFile = File(context.filesDir, filename)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IOException("Failed to open input stream for URI: $uri")
        tempFile
    }
}

private fun handleInstallationError(e: Exception, context: Context) {
    val errorMessage = "ERROR: ${e.localizedMessage ?: "Unknown error"}"
    addLog(errorMessage, LogType.ERROR)

    if (errorMessage.contains("INCOMPATIBLE") || e.message?.contains("INCOMPATIBLE") == true) {
        if (context is Activity) {
            context.runOnUiThread { ManagerBridge.showUninstallDialog.value = true }
        } else {
            showToast(context, "Signature mismatch. Uninstall the previous Grindr++ clone first.")
        }
    } else {
        showToast(context, "Failed: ${e.localizedMessage}")
    }

    ErrorHandler.logError(context, TAG, "Installation failed", e)
}

private fun addLog(message: String, type: LogType = LogType.INFO) {
    if (message.contains("<>:")) {
        val prefix = message.split("<>:")[0]
        logEntries.find { it.message.startsWith(prefix) }?.let { logEntries.remove(it) }
    }
    logEntries.add(ConsoleLogger.log(message.replace("<>:", ":"), type))
}

private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun launchPackage(context: Context, packageName: String, label: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            showToast(context, "Could not launch $label")
        }
    } catch (e: Exception) {
        showToast(context, "Error launching $label: ${e.localizedMessage}")
    }
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

fun installNavItem(): dev.fatter.gpp.manager.MainNavItem =
    dev.fatter.gpp.manager.MainNavItem(
        Icons.Rounded.Download,
        "Install",
        { InstallPage(it, this) },
    )

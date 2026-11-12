package dev.fatter.gpp.manager.installation.steps

import android.content.Context
import dev.fatter.gpp.manager.utils.download
import dev.fatter.gpp.manager.utils.validateFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Downloads a Grindr APK or split-bundle from a server-provided URL.
 *
 * The server-side availability matrix can point at any source (Aurora/Play
 * delivery failed, a mirror, or a local HTTP server). A single APK is wrapped
 * into a one-entry bundle so [ExtractBundleStep] can consume it unchanged.
 */
class DownloadBundleStep(
    private val bundleFile: File,
    private val url: String,
) : BaseStep() {

    override val name = "Downloading Grindr bundle"

    override suspend fun doExecute(context: Context, print: Print) {
        if (bundleFile.exists() && bundleFile.length() > 0 && validateFile(bundleFile)) {
            print("Existing Grindr bundle found, skipping URL download")
            return
        }

        val tempFile = File(bundleFile.parentFile, "${bundleFile.name}.download")
        try {
            print("Downloading Grindr bundle from server URL...")
            val result = download(context, tempFile, url, print)
            if (!result.success || !tempFile.exists() || tempFile.length() <= 0) {
                throw IOException("Failed to download Grindr bundle: ${result.reason}")
            }
            if (!validateFile(tempFile)) {
                throw IOException("Downloaded Grindr bundle is not a valid ZIP/APK")
            }

            if (isSingleApk(tempFile)) {
                print("Single APK detected; wrapping it as a split bundle")
                bundleFile.parentFile?.mkdirs()
                ZipOutputStream(FileOutputStream(bundleFile)).use { zip ->
                    zip.putNextEntry(ZipEntry("base.apk"))
                    tempFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            } else {
                tempFile.copyTo(bundleFile, overwrite = true)
            }

            if (!bundleFile.exists() || bundleFile.length() <= 0) {
                throw IOException("Failed to prepare Grindr bundle")
            }
            print("Grindr bundle ready (${bundleFile.length() / 1024 / 1024}MB)")
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun isSingleApk(file: File): Boolean {
        return try {
            ZipFile(file).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null &&
                    zip.entries().asSequence().none { it.name.endsWith(".apk", ignoreCase = true) }
            }
        } catch (_: Exception) {
            false
        }
    }
}

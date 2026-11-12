package dev.fatter.gpp.manager.installation.steps

import android.content.Context
import dev.fatter.gpp.manager.utils.ManifestPatcher
import dev.fatter.gpp.manager.utils.copyEntry
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class CloneGrindrStep(
    val folder: File,
    val packageName: String,
    val appName: String,
    val debuggable: Boolean,
) : BaseStep() {
    override suspend fun doExecute(
        context: Context,
        print: Print,
    ) {
        print("Cloning Grindr APK...")

        for (file in folder.listFiles()!!) {
            if (!file.name.endsWith(".apk")) {
                print("Skipping ${file.name} as it is not an APK")
                continue
            }

            val manifest = ZipFile(file)
                .use { zip ->
                    zip.getEntry("AndroidManifest.xml")?.let { entry ->
                        zip.getInputStream(entry).use { it.readBytes() }
                    }
                }
                ?: throw IllegalStateException("No manifest in ${file.name}")

            print("Changing package and app name in ${file.name}")
            val patchedManifestBytes = if (file.name.contains("base")) {
                ManifestPatcher.patchManifest(
                    manifestBytes = manifest,
                    packageName = packageName,
                    appName = appName,
                    debuggable = debuggable,
                )
            } else {
                print("Changing package name in ${file.name}")
                ManifestPatcher.renamePackage(manifest, packageName)
            }

            // Rewrite the archive once instead of leaving voids for zipalign.
            // Play APKs can carry a source stamp in the signing block; a clean
            // rewrite also drops it so apksig can sign the clone.
            val temp = File(file.parentFile, "${file.name}.tmp")
            ZipFile(file).use { zip ->
                ZipOutputStream(temp.outputStream().buffered()).use { out ->
                    for (entry in zip.entries()) {
                        out.copyEntry(
                            zip,
                            entry,
                            if (entry.name == "AndroidManifest.xml") patchedManifestBytes else null,
                        )
                    }
                }
            }
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) {
                    throw IllegalStateException("Could not replace ${file.name} with the cloned APK")
                }
            }
        }

        print("Cloning Grindr APK completed")
    }

    override val name = "Clone grindr apk"
}
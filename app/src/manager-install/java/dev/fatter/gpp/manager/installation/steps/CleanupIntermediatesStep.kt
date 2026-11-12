package dev.fatter.gpp.manager.installation.steps

import android.content.Context
import java.io.File

/**
 * Deletes intermediate artifacts (extracted host splits, exported/downloaded
 * host bundle) right before the install step.
 *
 * The system installer stages a full copy of the patched APKs in /data before
 * replacing the installed clone. On devices with little free space, keeping
 * the extracted splits and the host bundle alive makes that staging fail with
 * `INSTALL_FAILED_INSUFFICIENT_STORAGE` ("Failed to free … on storage device").
 * Both artifacts are regenerable (re-export or re-download), so they can be
 * dropped once patching is complete.
 */
class CleanupIntermediatesStep(
    private val unzipFolder: File,
    private val bundleFile: File,
) : BaseStep() {
    override val name = "Freeing intermediate files"

    override suspend fun doExecute(context: Context, print: Print) {
        var freed = 0L
        unzipFolder.listFiles()?.forEach { file ->
            freed += file.length()
            if (!file.delete()) {
                print("WARN: could not delete ${file.name}")
            }
        }
        if (bundleFile.exists()) {
            freed += bundleFile.length()
            if (!bundleFile.delete()) {
                print("WARN: could not delete ${bundleFile.name}")
            }
        }
        print("Freed ${freed / 1024 / 1024}MB of intermediate files")
    }
}

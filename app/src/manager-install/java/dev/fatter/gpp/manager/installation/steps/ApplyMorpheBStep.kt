package dev.fatter.gpp.manager.installation.steps

import android.content.Context
import dev.fatter.gpp.morphe.b.MorpheBPatchEngine
import java.io.File

/**
 * Applies the Morphe B static DEX marker before the clone is signed.
 *
 * LSPatch reads the input APK signature; mutating a signed APK first makes it fail with
 * `get original signature failed`. Running B here keeps the signed inputs valid for Morphe A.
 */
class ApplyMorpheBStep(
    private val unzipFolder: File,
) : BaseStep() {

    override val name = "Applying Morphe B static patch"

    override suspend fun doExecute(context: Context, print: Print) {
        val apks = unzipFolder.listFiles()
            ?.filter { it.name.endsWith(".apk") && it.exists() && it.length() > 0 }
            .orEmpty()
        if (apks.isEmpty()) {
            print("Morphe B: no APK files, skipping")
            return
        }

        try {
            MorpheBPatchEngine().apply(apks, print)
        } catch (t: Throwable) {
            print("Morphe B failed (continuing with Morphe A only): ${t.message}")
        }
    }
}

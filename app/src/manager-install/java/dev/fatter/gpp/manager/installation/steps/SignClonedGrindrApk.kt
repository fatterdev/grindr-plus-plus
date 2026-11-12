package dev.fatter.gpp.manager.installation.steps

import android.content.Context
import dev.fatter.gpp.manager.utils.KeyStoreUtils
import dev.fatter.gpp.manager.utils.copyEntry
import com.iyxan23.zipalignjava.ZipAlign
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SignClonedGrindrApk(val keyStoreUtils: KeyStoreUtils, val outputDir: File): BaseStep() {
    override suspend fun doExecute(
        context: Context,
        print: Print,
    ) {
        for (file in outputDir.listFiles()!!) {
            if (!file.name.endsWith(".apk")) {
                print("Skipping ${file.name} as it is not an APK")
                continue
            }

            val alignedFile = File(outputDir, "${file.name}-aligned.apk")
            val strippedFile = File(outputDir, "${file.name}-stripped.apk")
            try {
                val inputToSign = try {
                    RandomAccessFile(file, "r").use { zipIn ->
                        alignedFile.outputStream().use { zipOut ->
                            print("Aligning ${file.name}...")
                            ZipAlign.alignZip(zipIn, zipOut)
                        }
                    }
                    alignedFile
                } catch (e: Exception) {
                    // Play APKs may carry a source stamp in the APK signing block;
                    // the old zipalign helper cannot parse every layout, and apksig
                    // rejects the stale source stamp. Rewriting the entries strips
                    // the signing block, so apksig can sign a clean archive.
                    print(
                        "ZipAlign failed for ${file.name} " +
                            "(${e.javaClass.simpleName}); stripping the old signing block",
                    )
                    if (alignedFile.exists()) alignedFile.delete()
                    stripSigningBlock(file, strippedFile, print)
                    strippedFile
                }

                print("Signing ${inputToSign.name}...")
                keyStoreUtils.signApk(
                    inputToSign,
                    File(outputDir, "${file.name}-signed.apk"),
                )
                file.delete()
            } catch (e: Exception) {
                print(
                    "Failed to sign ${file.name}: " +
                        "${e.javaClass.simpleName}: ${e.message ?: "no message"}",
                )
                throw e
            } finally {
                if (alignedFile.exists()) {
                    alignedFile.delete()
                }
                if (strippedFile.exists()) {
                    strippedFile.delete()
                }
            }
        }
    }

    /**
     * Copies every ZIP entry into a fresh archive, dropping the APK signing
     * block (including the Play source stamp) and the old v1 signature files.
     * apksig can then sign the clean archive.
     */
    private fun stripSigningBlock(source: File, target: File, print: Print) {
        if (target.exists()) target.delete()
        ZipFile(source).use { zip ->
            ZipOutputStream(target.outputStream().buffered()).use { out ->
                var copied = 0
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val lower = entry.name.lowercase()
                    if (lower.startsWith("meta-inf/") &&
                        (lower.endsWith(".sf") || lower.endsWith(".rsa") ||
                            lower.endsWith(".dsa") || lower.endsWith(".ec"))
                    ) {
                        continue
                    }
                    out.copyEntry(zip, entry)
                    copied++
                }
                print("Stripped signing block from ${source.name} ($copied entries)")
            }
        }
    }

    override val name: String = "Sign cloned Grindr APK"
}
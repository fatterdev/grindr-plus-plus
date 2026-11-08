import java.net.URI
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.FileSystems
import java.security.MessageDigest
import java.util.Comparator

// Morphe A / ADR 0003: pin LSPatch/Vector artifacts to a release tag + checksum.
// Do not use unpinned nightlies in CI or on-device patch flows.
tasks.register("setupLSPatch") {
    doLast {
        val tempDir = layout.buildDirectory.get().asFile.resolve("lspatch_temp")
        tempDir.mkdirs()

        // Pinned JingMatrix/LSPatch v1.2 (https://github.com/JingMatrix/LSPatch/releases/tag/v1.2)
        // v1.2 is the first stable release on libxposed API 102 and loads modern modules.
        val jarUrl = "https://github.com/JingMatrix/LSPatch/releases/download/v1.2/lspatch-v1.2-487-release.jar"
        val expectedSha256 = "D238FDC414D121B7FA454D8B4CCF420DF3A8C97D563761861FF92BD9C5DA2165"

        val jarDownload = tempDir.resolve("lspatch-v1.2.jar")
        URI(jarUrl).toURL().openStream().use { input ->
            FileOutputStream(jarDownload).use { output ->
                input.copyTo(output)
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        jarDownload.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { b -> "%02x".format(b) }.uppercase()
        if (actualSha256 != expectedSha256) {
            throw GradleException(
                "LSPatch v1.2 SHA-256 mismatch: expected $expectedSha256 got $actualSha256"
            )
        }

        // Extract only native libs under assets/lspatch/so* to the morphe flavor.
        // loader.dex / metaloader.dex remain inside lspatch.jar (packaging both duplicates entries).
        copy {
            from(zipTree(jarDownload)) {
                include("assets/lspatch/so*/**")
            }
            into(project.projectDir.resolve("src/morphe/"))
        }

        // Move/Copy jar to libs/lspatch.jar
        val targetLib = project.projectDir.resolve("libs/lspatch.jar")
        targetLib.parentFile.mkdirs()
        jarDownload.copyTo(targetLib, overwrite = true)

        // Delete conflicting classes/packages from the jar using ZipFileSystem
        val jarUri = URI.create("jar:" + targetLib.toURI())
        val env = mapOf("create" to "false")

        FileSystems.newFileSystem(jarUri, env).use { fs ->
            val p1 = fs.getPath("com/google/common/util/concurrent/ListenableFuture.class")
            if (Files.exists(p1)) {
                Files.delete(p1)
            }

            val p2 = fs.getPath("com/google/errorprone/annotations")
            if (Files.exists(p2)) {
                Files.walk(p2)
                    .sorted(Comparator.reverseOrder())
                    .forEach { p ->
                        Files.delete(p)
                    }
            }
        }

        // Record pin metadata next to the jar for audits
        project.projectDir.resolve("libs/lspatch-v1.2.sha256.txt").writeText(
            """
            # LSPatch pin (Morphe A / ADR 0003)
            # Source: https://github.com/JingMatrix/LSPatch/releases/tag/v1.2
            # Artifact URL: $jarUrl
            # Upstream SHA256: $expectedSha256
            # Installed as: app/libs/lspatch.jar (+ assets/lspatch/*)
            """.trimIndent() + "\n"
        )
    }
}

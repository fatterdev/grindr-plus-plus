package dev.fatter.gpp.morphe.b

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class PairIpStaticDecryptTest {

    @Test
    fun detectPairIp_falseWithoutMarkers() {
        val dir = createTempDirectory(prefix = "pairip-neg-").toFile()
        val apk = File(dir, "base.apk")
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("assets/hello.txt"))
            zos.write("hi".toByteArray())
            zos.closeEntry()
        }
        assertFalse(PairIpStaticDecrypt.detectPairIp(apk))
    }

    @Test
    fun detectPairIp_trueWithPairipcore() {
        val dir = createTempDirectory(prefix = "pairip-pos-").toFile()
        val apk = File(dir, "base.apk")
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libpairipcore.so"))
            zos.write(ByteArray(16) { 0x7f })
            zos.closeEntry()
        }
        assertTrue(PairIpStaticDecrypt.detectPairIp(apk))
    }

    @Test
    fun apply_splicesNativeWhenPackPresent() {
        val dir = createTempDirectory(prefix = "pairip-apply-").toFile()
        val base = File(dir, "base.apk")
        ZipOutputStream(base.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libpairipcore.so"))
            zos.write(byteArrayOf(1, 2, 3, 4))
            zos.closeEntry()
        }
        val arm64 = File(dir, "split_config.arm64_v8a.apk")
        ZipOutputStream(arm64.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libsqliteJni.so"))
            zos.write(ByteArray(32) { 0xAA.toByte() })
            zos.closeEntry()
        }
        val pack = File(dir, "pairip-decrypt/185656").also { it.mkdirs() }
        val decrypted = ByteArray(32) { 0x55 }
        File(pack, "libsqliteJni.so").writeBytes(decrypted)
        File(pack, "pack.json").writeText("""{"versionCode":185656,"libs":["libsqliteJni.so"]}""")

        val logs = mutableListOf<String>()
        val report = PairIpStaticDecrypt.apply(listOf(base, arm64)) { logs += it }
        assertTrue(report.present)
        assertTrue(report.packApplied)
        assertTrue(report.natives.contains("libsqliteJni.so"))

        // Re-read spliced entry
        java.util.zip.ZipFile(arm64).use { zip ->
            val bytes = zip.getInputStream(zip.getEntry("lib/arm64-v8a/libsqliteJni.so")).readBytes()
            assertTrue(bytes.contentEquals(decrypted))
        }
        assertTrue(logs.any { it.contains("splice") })
    }

    @Test
    fun parseManifestVersionCode_readsVersionCode() {
        assertEquals(194910, PairIpStaticDecrypt.parseManifestVersionCode(axmlManifest(194910)))
        assertEquals(179451, PairIpStaticDecrypt.parseManifestVersionCode(axmlManifest(179451)))
        assertNull(PairIpStaticDecrypt.parseManifestVersionCode(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun readVersionCode_fromApkManifest() {
        val dir = createTempDirectory(prefix = "pairip-vc-").toFile()
        val apk = File(dir, "base.apk")
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(axmlManifest(194910))
            zos.closeEntry()
        }
        assertEquals(194910, PairIpStaticDecrypt.readVersionCode(apk))
    }

    @Test
    fun selectPackDir_prefersHostVersion() {
        val dir = createTempDirectory(prefix = "pairip-select-").toFile()
        val old = pack(dir, 185656, 0x55)
        val current = pack(dir, 194910, 0x66)

        assertEquals(current, PairIpStaticDecrypt.selectPackDir(listOf(old, current), 194910))
        assertEquals(old, PairIpStaticDecrypt.selectPackDir(listOf(old, current), 185656))
        assertNull(PairIpStaticDecrypt.selectPackDir(listOf(old, current), 999999))
        assertNull(PairIpStaticDecrypt.selectPackDir(listOf(old, current), null))
        assertEquals(old, PairIpStaticDecrypt.selectPackDir(listOf(old), null))
    }

    @Test
    fun selectPackDir_matchesPackJsonWhenDirNameDiffers() {
        val dir = createTempDirectory(prefix = "pairip-select2-").toFile()
        val legacy = File(dir, "legacy").also { it.mkdirs() }
        File(legacy, "pack.json").writeText("""{"versionCode":194910}""")
        File(legacy, "libsqliteJni.so").writeBytes(ByteArray(8))
        val other = File(dir, "194911").also { it.mkdirs() }
        File(other, "pack.json").writeText("""{"versionCode":194911}""")

        assertEquals(legacy, PairIpStaticDecrypt.selectPackDir(listOf(other, legacy), 194910))
    }

    @Test
    fun apply_selectsPackMatchingHostVersion() {
        val dir = createTempDirectory(prefix = "pairip-apply-vc-").toFile()
        val base = File(dir, "base.apk")
        ZipOutputStream(base.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(axmlManifest(194910))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libpairipcore.so"))
            zos.write(byteArrayOf(1, 2, 3, 4))
            zos.closeEntry()
        }
        val arm64 = File(dir, "split_config.arm64_v8a.apk")
        ZipOutputStream(arm64.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libsqliteJni.so"))
            zos.write(ByteArray(32) { 0xAA.toByte() })
            zos.closeEntry()
        }
        val oldPack = pack(dir, 185656, 0x11)
        val newPack = pack(dir, 194910, 0x22)

        val logs = mutableListOf<String>()
        val report = PairIpStaticDecrypt.apply(listOf(base, arm64)) { logs += it }

        assertTrue(report.present)
        assertEquals(194910, report.hostVersionCode)
        assertTrue(report.packApplied)
        assertEquals(newPack.absolutePath, report.packDir)
        assertTrue(logs.any { it.contains("host versionCode=194910") })
        java.util.zip.ZipFile(arm64).use { zip ->
            val bytes = zip.getInputStream(zip.getEntry("lib/arm64-v8a/libsqliteJni.so")).readBytes()
            assertTrue(bytes.all { it == 0x22.toByte() })
        }
        assertTrue(oldPack.isDirectory)
    }

    @Test
    fun apply_failsSoftWhenVersionAmbiguous() {
        val dir = createTempDirectory(prefix = "pairip-ambiguous-").toFile()
        val base = File(dir, "base.apk")
        ZipOutputStream(base.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libpairipcore.so"))
            zos.write(byteArrayOf(1, 2, 3, 4))
            zos.closeEntry()
        }
        val arm64 = File(dir, "split_config.arm64_v8a.apk")
        ZipOutputStream(arm64.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libsqliteJni.so"))
            zos.write(ByteArray(32) { 0xAA.toByte() })
            zos.closeEntry()
        }
        pack(dir, 185656, 0x11)
        pack(dir, 194910, 0x22)

        val logs = mutableListOf<String>()
        val report = PairIpStaticDecrypt.apply(listOf(base, arm64)) { logs += it }

        assertTrue(report.present)
        assertNull(report.hostVersionCode)
        assertFalse(report.packApplied)
        assertNull(report.packDir)
        assertTrue(logs.any { it.contains("ambiguous") })
    }

    private fun pack(root: File, versionCode: Int, fill: Int): File {
        val dir = File(root, "pairip-decrypt/$versionCode").also { it.mkdirs() }
        File(dir, "libsqliteJni.so").writeBytes(ByteArray(32) { fill.toByte() })
        File(dir, "pack.json").writeText("""{"versionCode":$versionCode,"libs":["libsqliteJni.so"]}""")
        return dir
    }

    // --- minimal binary AndroidManifest (AXML) with android:versionCode ---

    private fun axmlManifest(versionCode: Int): ByteArray {
        val strings = listOf(
            "versionCode",
            "manifest",
            "android",
            "http://schemas.android.com/apk/res/android",
        )
        val body = stringPool(strings) + startElementManifest(
            nameIdx = 1,
            attrNsIdx = 3,
            attrNameIdx = 0,
            versionCode = versionCode,
        )
        val out = ByteArray(8 + body.size)
        putU16(out, 0, 0x0003)
        putU16(out, 2, 8)
        putU32(out, 4, out.size)
        body.copyInto(out, 8)
        return out
    }

    private fun stringPool(strings: List<String>): ByteArray {
        val encoded = strings.map { s ->
            val bytes = s.toByteArray(Charsets.UTF_8)
            byteArrayOf(bytes.size.toByte()) + bytes + byteArrayOf(0)
        }
        val stringsStart = 28 + strings.size * 4
        val size = stringsStart + encoded.sumOf { it.size }
        val out = ByteArray(size)
        putU16(out, 0, 0x0001)
        putU16(out, 2, 28)
        putU32(out, 4, size)
        putU32(out, 8, strings.size)
        putU32(out, 12, 0)
        putU32(out, 16, 0x100) // UTF-8
        putU32(out, 20, stringsStart)
        putU32(out, 24, 0)
        var off = 0
        var pos = stringsStart
        encoded.forEachIndexed { i, bytes ->
            putU32(out, 28 + i * 4, off)
            bytes.copyInto(out, pos)
            off += bytes.size
            pos += bytes.size
        }
        return out
    }

    private fun startElementManifest(
        nameIdx: Int,
        attrNsIdx: Int,
        attrNameIdx: Int,
        versionCode: Int,
    ): ByteArray {
        val out = ByteArray(36 + 20)
        putU16(out, 0, 0x0102)
        putU16(out, 2, 16)
        putU32(out, 4, out.size)
        putU32(out, 8, 1)
        putU32(out, 12, -1)
        putU32(out, 16, -1)
        putU32(out, 20, nameIdx)
        putU16(out, 24, 20)
        putU16(out, 26, 20)
        putU16(out, 28, 1)
        putU16(out, 30, 0)
        putU16(out, 32, 0)
        putU16(out, 34, 0)
        putU32(out, 36, attrNsIdx)
        putU32(out, 40, attrNameIdx)
        putU32(out, 44, -1)
        putU16(out, 48, 8)
        out[50] = 0
        out[51] = 0x10 // TYPE_INT_DEC
        putU32(out, 52, versionCode)
        return out
    }

    private fun putU16(b: ByteArray, off: Int, value: Int) {
        b[off] = (value and 0xff).toByte()
        b[off + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun putU32(b: ByteArray, off: Int, value: Int) {
        b[off] = (value and 0xff).toByte()
        b[off + 1] = ((value ushr 8) and 0xff).toByte()
        b[off + 2] = ((value ushr 16) and 0xff).toByte()
        b[off + 3] = ((value ushr 24) and 0xff).toByte()
    }
}

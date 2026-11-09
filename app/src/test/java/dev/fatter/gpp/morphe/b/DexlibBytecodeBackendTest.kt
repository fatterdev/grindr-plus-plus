package dev.fatter.gpp.morphe.b

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11n
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class DexlibBytecodeBackendTest {
    @Test
    fun prependsReturnTrueOnStringFingerprint() {
        val dir = createTempDirectory("morpheb-dexlib").toFile()
        val apk = File(dir, "base.apk")
        writeApkWithDex(apk, fixtureDexReturningFalse())

        val patch = MorpheBytecodePatch("example-gate") { ctx ->
            ctx.findMethodsUsingString("showBannerAds").forEach { ctx.prependReturnTrue(it) }
        }
        DexlibBytecodeBackend(patches = listOf(patch)).applyBytecodePatches(apk) { }

        val dex = loadFirstDex(apk)
        val method = dex.classes
            .first { it.type == "Lcom/example/AdLoader;" }
            .methods
            .first { it.name == "showAds" }
        val first = method.implementation!!.instructions.first() as Instruction11n
        assertEquals(Opcode.CONST_4, first.opcode)
        assertEquals(1, first.narrowLiteral)
    }

    @Test
    fun injectsMarkerClassAndRewriteReport() {
        val dir = createTempDirectory("morpheb-marker").toFile()
        val apk = File(dir, "base.apk")
        writeApkWithDex(apk, fixtureDexReturningFalse())

        val logs = mutableListOf<String>()
        DexlibBytecodeBackend().applyBytecodePatches(apk) { logs += it }

        val dex = loadFirstDex(apk)
        assertTrue(dex.classes.any { it.type == DexlibBytecodeBackend.MARKER_TYPE })
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry(FingerprintScanBytecodeBackend.REPORT_ENTRY)
            assertNotNull(entry)
            val text = zip.getInputStream(entry).bufferedReader().readText()
            assertTrue(text.contains("DexlibBytecodeBackend"))
            assertTrue(text.contains("\"mode\": \"rewrite\""))
            assertTrue(text.contains("morphe-b-dex-marker"))
        }
        assertTrue(logs.any { it.contains("rewrite") })
    }

    @Test
    fun stubsDisableUpdatesDialog() {
        val dir = createTempDirectory("morpheb-updates").toFile()
        val apk = File(dir, "base.apk")
        writeApkWithDex(apk, fixtureUpdateManagerDex())

        DexlibBytecodeBackend().applyBytecodePatches(apk) { }

        val dex = loadFirstDex(apk)
        val method = dex.classes
            .first { it.type == "Lcom/example/UpdateManager;" }
            .methods
            .first { it.name == "showDeprecatedDialog" }
        val first = method.implementation!!.instructions.first()
        assertEquals(Opcode.RETURN_VOID, first.opcode)
    }

    @Test
    fun rewritePreservesStoredEntries() {
        val dir = createTempDirectory("morpheb-stored").toFile()
        val apk = File(dir, "base.apk")
        val arsc = ByteArray(64) { it.toByte() }
        val dex = ByteArray(128) { (it * 3).toByte() }
        ZipOutputStream(apk.outputStream()).use { zos ->
            writeStored(zos, "resources.arsc", arsc)
            writeStored(zos, "classes.dex", dex)
            zos.putNextEntry(java.util.zip.ZipEntry("assets/readme.txt"))
            zos.write("deflated".toByteArray())
            zos.closeEntry()
        }

        ApkAssetInjector.replaceEntries(
            apk,
            mapOf(
                "classes.dex" to dex,
                "assets/grindrplus/morphe_b.json" to """{"ok":true}""".toByteArray(),
            ),
        )

        ZipFile(apk).use { zip ->
            assertEquals(
                java.util.zip.ZipEntry.STORED,
                zip.getEntry("resources.arsc").method,
            )
            assertEquals(
                java.util.zip.ZipEntry.STORED,
                zip.getEntry("classes.dex").method,
            )
            assertEquals(
                java.util.zip.ZipEntry.DEFLATED,
                zip.getEntry("assets/readme.txt").method,
            )
            assertEquals(
                java.util.zip.ZipEntry.DEFLATED,
                zip.getEntry("assets/grindrplus/morphe_b.json").method,
            )
        }
    }

    private fun writeStored(
        zos: ZipOutputStream,
        name: String,
        data: ByteArray,
    ) {
        val crc = java.util.zip.CRC32()
        crc.update(data)
        val entry = java.util.zip.ZipEntry(name).apply {
            method = java.util.zip.ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            this.crc = crc.value
        }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    @Test
    fun invalidDexFallsBackToByteScan() {        val dir = createTempDirectory("morpheb-fallback").toFile()
        val apk = File(dir, "base.apk")
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("classes.dex"))
            zos.write("xxFavoritesFragmentyyFavoritesHeaderDatazz".toByteArray())
            zos.closeEntry()
        }
        DexlibBytecodeBackend().applyBytecodePatches(apk) { }
        ZipFile(apk).use { zip ->
            val text = zip.getInputStream(zip.getEntry(FingerprintScanBytecodeBackend.REPORT_ENTRY))
                .bufferedReader().readText()
            assertTrue(text.contains("FingerprintScanBytecodeBackend"))
            assertTrue(text.contains("\"status\": \"hit\""))
            assertFalse(text.contains("\"mode\": \"rewrite\""))
        }
    }

    private fun writeApkWithDex(apk: File, dex: ByteArray) {
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("classes.dex"))
            zos.write(dex)
            zos.closeEntry()
        }
    }

    private fun loadFirstDex(apk: File): DexFile {
        ZipFile(apk).use { zip ->
            val bytes = zip.getInputStream(zip.getEntry("classes.dex")).readBytes()
            return DexBackedDexFile.fromInputStream(
                Opcodes.getDefault(),
                ByteArrayInputStream(bytes),
            )
        }
    }

    private fun fixtureUpdateManagerDex(): ByteArray {
        val clinit = ImmutableMethod(
            "Lcom/example/UpdateManager;",
            "<clinit>",
            emptyList(),
            "V",
            AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            ImmutableMethodImplementation(
                1,
                listOf(
                    ImmutableInstruction21c(
                        Opcode.CONST_STRING,
                        0,
                        ImmutableStringReference("market://details?id=com.grindrapp.android"),
                    ),
                    ImmutableInstruction10x(Opcode.RETURN_VOID),
                ),
                emptyList(),
                emptyList(),
            ),
        )
        val show = ImmutableMethod(
            "Lcom/example/UpdateManager;",
            "showDeprecatedDialog",
            listOf(
                ImmutableMethodParameter(
                    "Landroidx/appcompat/app/AppCompatActivity;",
                    emptySet(),
                    "activity",
                ),
            ),
            "V",
            AccessFlags.PUBLIC.value,
            emptySet(),
            emptySet(),
            ImmutableMethodImplementation(
                2,
                listOf(ImmutableInstruction10x(Opcode.RETURN_VOID)),
                emptyList(),
                emptyList(),
            ),
        )
        val classDef = ImmutableClassDef(
            "Lcom/example/UpdateManager;",
            AccessFlags.PUBLIC.value,
            "Ljava/lang/Object;",
            emptyList(),
            null,
            emptySet(),
            emptyList(),
            emptyList(),
            listOf(clinit),
            listOf(show),
        )
        val dexFile = object : DexFile {
            override fun getClasses() = setOf(classDef)
            override fun getOpcodes() = Opcodes.getDefault()
        }
        val store = MemoryDataStore()
        DexPool.writeTo(store, dexFile)
        return store.data
    }

    private fun fixtureDexReturningFalse(): ByteArray {
        val method = ImmutableMethod(
            "Lcom/example/AdLoader;",
            "showAds",
            emptyList(),
            "Z",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            ImmutableMethodImplementation(
                1,
                listOf(
                    ImmutableInstruction21c(
                        Opcode.CONST_STRING,
                        0,
                        ImmutableStringReference("showBannerAds"),
                    ),
                    ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
                    ImmutableInstruction11x(Opcode.RETURN, 0),
                ),
                emptyList(),
                emptyList(),
            ),
        )
        val classDef = ImmutableClassDef(
            "Lcom/example/AdLoader;",
            AccessFlags.PUBLIC.value,
            "Ljava/lang/Object;",
            emptyList(),
            null,
            emptySet(),
            emptyList(),
            emptyList(),
            listOf(method),
            emptyList(),
        )
        val dexFile = object : DexFile {
            override fun getClasses() = setOf(classDef)
            override fun getOpcodes() = Opcodes.getDefault()
        }
        val store = MemoryDataStore()
        DexPool.writeTo(store, dexFile)
        return store.data
    }
}

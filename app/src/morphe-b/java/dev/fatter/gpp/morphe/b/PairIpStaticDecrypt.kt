package dev.fatter.gpp.morphe.b

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * Morphe B PairIP static decrypt (ADR 0009).
 *
 * 1. Detect PairIP markers in base APK.
 * 2. If a versioned decrypt pack is present, splice decrypted native `.so` RX into
 *    the arm64 split (stock in-memory dump — see `scripts/pairip_dump_natives.py`).
 * 3. DEX-neutralize integrity entrypoints so LSPatch re-sign does not trip PairIP;
 *    leave [StartupLauncher] / [VMRunner] so string/VM init can still run with the
 *    real `libpairipcore` on the rootless clone (no Vector).
 *
 * Fail-soft: missing pack → report only, no crash.
 */
object PairIpStaticDecrypt {
    private val integrityTargets = listOf(
        "Lcom/pairip/SignatureCheck;" to "verifyIntegrity",
        "Lcom/pairip/licensecheck/LicenseClient;" to "checkLicense",
    )

    fun apply(inputApks: List<File>, print: Print): PairIpDecryptReport {
        val base = MorpheBPatchEngine.selectBaseApk(inputApks)
        val present = detectPairIp(base)
        if (!present) {
            print("Morphe B PairIP: not present, skip")
            return PairIpDecryptReport(present = false, packApplied = false, natives = emptyList(), dexStubs = 0)
        }
        print("Morphe B PairIP: detected")

        val hostVersionCode = readVersionCode(base)
        if (hostVersionCode != null) {
            print("Morphe B PairIP: host versionCode=$hostVersionCode")
        } else {
            print("Morphe B PairIP: host versionCode unknown (manifest parse failed)")
        }
        val packDir = resolvePackDir(inputApks, base, hostVersionCode, print)
        var natives = emptyList<String>()
        var packApplied = false
        if (packDir != null) {
            val arm64 = inputApks.find {
                it.name.contains("arm64", ignoreCase = true) ||
                    it.name.contains("split_config.arm64", ignoreCase = true)
            }
            if (arm64 == null) {
                print("Morphe B PairIP: pack found but no arm64 split in inputs")
            } else {
                natives = spliceNatives(arm64, packDir, print)
                packApplied = natives.isNotEmpty()
            }
        } else {
            print("Morphe B PairIP: no decrypt pack (looked beside APKs / pairip-decrypt/<vc>)")
        }

        val dexStubs = try {
            neutralizeIntegrity(base, print)
        } catch (t: Throwable) {
            print("Morphe B PairIP: DEX neutralize soft-fail: ${t.message}")
            0
        }

        return PairIpDecryptReport(
            present = true,
            packApplied = packApplied,
            natives = natives,
            dexStubs = dexStubs,
            packDir = packDir?.absolutePath,
            hostVersionCode = hostVersionCode,
        )
    }

    fun detectPairIp(apk: File): Boolean {
        if (!apk.isFile) return false
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.startsWith("lib/") && name.endsWith("libpairipcore.so")) return true
                if (name.startsWith("com/pairip/") || name.contains("/pairip/")) return true
            }
        }
        // DEX string scan (pairip often only in classes*.dex)
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!DexlibBytecodeBackend.isDexEntry(entry.name)) continue
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                if (bytes.toString(Charsets.ISO_8859_1).contains("Lcom/pairip/")) return true
                // cheaper: look for ASCII
                if (indexOf(bytes, "com/pairip/".toByteArray()) >= 0) return true
            }
        }
        return false
    }

    private fun resolvePackDir(
        inputApks: List<File>,
        base: File,
        hostVersionCode: Int?,
        print: Print,
    ): File? {
        val roots = linkedSetOf<File>()
        val parents = (inputApks.mapNotNull { it.parentFile } + base.parentFile).filterNotNull().distinct()
        for (parent in parents) {
            roots += File(parent, "pairip-decrypt")
            parent.parentFile?.let { roots += File(it, "pairip-decrypt") }
        }
        // Walk up a few levels for repo root pairip-decrypt/
        var walk: File? = base.parentFile
        repeat(6) {
            val cand = walk ?: return@repeat
            roots += File(cand, "pairip-decrypt")
            walk = cand.parentFile
        }
        val candidates = roots
            .filter { it.isDirectory }
            .flatMap { root -> packCandidates(root) }
            .distinctBy { it.absolutePath }
        if (candidates.isEmpty()) return null
        val chosen = selectPackDir(candidates, hostVersionCode)
        if (chosen == null) {
            print(
                "Morphe B PairIP: pack selection ambiguous " +
                    "(versionCode=$hostVersionCode, candidates=${candidates.joinToString { it.name }})",
            )
        }
        return chosen
    }

    /** A pack candidate is a dir holding `pack.json` or at least one `.so`. */
    private fun packCandidates(root: File): List<File> {
        val out = mutableListOf<File>()
        if (File(root, "pack.json").isFile || root.listFiles()?.any { it.isFile && it.name.endsWith(".so") } == true) {
            out += root
        }
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.forEach { child ->
                if (File(child, "pack.json").isFile || child.listFiles()?.any { it.isFile && it.name.endsWith(".so") } == true) {
                    out += child
                }
            }
        return out
    }

    /**
     * Deterministic pack choice: exact `versionCode` directory, else a pack whose
     * `pack.json` declares the host version, else the only candidate (back-compat).
     * Ambiguous inputs fail soft (no pack) instead of splicing a wrong-version image.
     */
    internal fun selectPackDir(candidates: List<File>, hostVersionCode: Int?): File? {
        if (candidates.isEmpty()) return null
        if (hostVersionCode != null) {
            candidates.firstOrNull { it.name == hostVersionCode.toString() }?.let { return it }
            candidates.firstOrNull { packVersionCode(it) == hostVersionCode }?.let { return it }
        }
        return candidates.singleOrNull()
    }

    /** Minimal `pack.json` scan (no org.json so JVM unit tests stay dependency-free). */
    internal fun packVersionCode(packDir: File): Int? {
        val meta = File(packDir, "pack.json")
        if (!meta.isFile) return null
        val text = runCatching { meta.readText() }.getOrNull() ?: return null
        return Regex("\"versionCode\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Reads `android:versionCode` from the base APK's binary `AndroidManifest.xml`. */
    internal fun readVersionCode(apk: File): Int? {
        if (!apk.isFile) return null
        return try {
            ZipFile(apk).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return null
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                parseManifestVersionCode(bytes)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Binary XML (AXML) scan for the `<manifest android:versionCode>` attribute. */
    internal fun parseManifestVersionCode(xml: ByteArray): Int? {
        if (xml.size < 8 || u16(xml, 0) != 0x0003) return null
        val end = minOf(xml.size, u32(xml, 4))
        var off = u16(xml, 2)
        var strings = emptyList<String>()
        while (off + 8 <= end) {
            val chunkType = u16(xml, off)
            val headerSize = u16(xml, off + 2)
            val chunkSize = u32(xml, off + 4)
            if (chunkSize < 8 || off + chunkSize > end) break
            when (chunkType) {
                0x0001 -> strings = parseStringPool(xml, off, chunkSize)
                0x0102 -> {
                    if (headerSize < 16) {
                        off += chunkSize
                        continue
                    }
                    val nameIdx = u32(xml, off + 20)
                    if (strings.getOrNull(nameIdx) == "manifest") {
                        val attrStart = u16(xml, off + 24)
                        val attrSize = u16(xml, off + 26)
                        val attrCount = u16(xml, off + 28)
                        var attr = off + 16 + attrStart
                        repeat(attrCount) {
                            if (attr + 20 > end) return null
                            val attrName = strings.getOrNull(u32(xml, attr + 4))
                            if (attrName == "versionCode") {
                                return u32(xml, attr + 16)
                            }
                            attr += if (attrSize >= 20) attrSize else 20
                        }
                    }
                }
            }
            off += chunkSize
        }
        return null
    }

    private fun parseStringPool(xml: ByteArray, chunkStart: Int, chunkSize: Int): List<String> {
        if (chunkSize < 28) return emptyList()
        val count = u32(xml, chunkStart + 8)
        val flags = u32(xml, chunkStart + 16)
        val stringsStart = u32(xml, chunkStart + 20)
        val dataStart = chunkStart + stringsStart
        val utf8 = (flags and 0x100) != 0
        val out = ArrayList<String>(count)
        for (i in 0 until count) {
            val offsetPos = chunkStart + 28 + i * 4
            if (offsetPos + 4 > chunkStart + chunkSize) break
            var p = dataStart + u32(xml, offsetPos)
            if (p >= xml.size) break
            if (utf8) {
                // AXML UTF-8 strings carry TWO length prefixes: the UTF-16 code
                // unit count, then the UTF-8 byte count. Each is 1 or 2 bytes
                // (high bit = continuation). Skipping only the first prefix
                // shifts every string and makes the manifest scan miss names.
                var utf16Len = xml[p].toInt() and 0xff
                p++
                if (utf16Len and 0x80 != 0) {
                    utf16Len = ((utf16Len and 0x7f) shl 8) or (xml[p].toInt() and 0xff)
                    p++
                }
                var len = xml[p].toInt() and 0xff
                p++
                if (len and 0x80 != 0) {
                    len = ((len and 0x7f) shl 8) or (xml[p].toInt() and 0xff)
                    p++
                }
                val bytes = if (p + len <= xml.size) xml.copyOfRange(p, p + len) else ByteArray(0)
                out += String(bytes, Charsets.UTF_8)
            } else {
                var len = u16(xml, p)
                p += 2
                if (len and 0x8000 != 0) {
                    len = ((len and 0x7fff) shl 16) or u16(xml, p)
                    p += 2
                }
                val n = minOf(len * 2, maxOf(0, xml.size - p))
                out += String(xml, p, n, Charsets.UTF_16LE)
            }
        }
        return out
    }

    private fun u16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

    private fun u32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)

    private fun spliceNatives(arm64Apk: File, packDir: File, print: Print): List<String> {
        val updates = linkedMapOf<String, ByteArray>()
        val applied = mutableListOf<String>()
        val libs = packDir.listFiles()?.filter { it.isFile && it.name.endsWith(".so") }.orEmpty()
        for (lib in libs) {
            val entry = "lib/arm64-v8a/${lib.name}"
            updates[entry] = lib.readBytes()
            applied += lib.name
            print("Morphe B PairIP: splice $entry (${lib.length()} bytes)")
        }
        if (updates.isEmpty()) {
            print("Morphe B PairIP: pack has no .so files")
            return emptyList()
        }
        // Confirm zip has the entry (soft-fail if not)
        ZipFile(arm64Apk).use { zip ->
            for (name in updates.keys.toList()) {
                if (zip.getEntry(name) == null) {
                    print("Morphe B PairIP: missing $name in ${arm64Apk.name}, skip lib")
                    updates.remove(name)
                    applied.remove(name.substringAfterLast('/'))
                }
            }
        }
        if (updates.isEmpty()) return emptyList()
        ApkAssetInjector.replaceEntries(arm64Apk, updates)
        // Optional marker asset on pack
        runCatching {
            val meta = File(packDir, "pack.json")
            if (meta.isFile) {
                print("Morphe B PairIP: pack ${meta.readText().trim().take(120)}…")
            }
        }
        return applied
    }

    private fun neutralizeIntegrity(base: File, print: Print): Int {
        val dexBytes = linkedMapOf<String, ByteArray>()
        ZipFile(base).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!DexlibBytecodeBackend.isDexEntry(entry.name)) continue
                dexBytes[entry.name] = zip.getInputStream(entry).use { it.readBytes() }
            }
        }
        if (dexBytes.isEmpty()) return 0

        var stubs = 0
        val updates = linkedMapOf<String, ByteArray>()
        for ((name, bytes) in dexBytes) {
            val dex = try {
                DexBackedDexFile.fromInputStream(Opcodes.getDefault(), ByteArrayInputStream(bytes))
            } catch (_: Throwable) {
                continue
            }
            var dexStubs = 0
            val classes = dex.classes.map { cls ->
                val rewritten = rewriteIntegrityClass(cls) ?: return@map cls
                dexStubs += rewritten.second
                rewritten.first
            }
            if (dexStubs == 0) continue
            stubs += dexStubs
            val file = object : DexFile {
                override fun getClasses() = classes.toSet()
                override fun getOpcodes() = dex.opcodes
            }
            val store = MemoryDataStore()
            DexPool.writeTo(store, file)
            updates[name] = store.data
        }
        if (updates.isNotEmpty()) {
            ApkAssetInjector.replaceEntries(base, updates)
            print("Morphe B PairIP: DEX integrity stubs=$stubs")
        } else {
            print("Morphe B PairIP: no integrity methods found to stub")
        }
        return stubs
    }

    private fun rewriteIntegrityClass(cls: ClassDef): Pair<ClassDef, Int>? {
        val targets = integrityTargets.filter { it.first == cls.type }
        if (targets.isEmpty()) return null
        val names = targets.map { it.second }.toSet()
        var count = 0
        val methods = cls.methods.map { method ->
            if (method.name !in names) return@map method
            count++
            stubMethod(method)
        }
        if (count == 0) return null
        return ImmutableClassDef(
            cls.type,
            cls.accessFlags,
            cls.superclass,
            cls.interfaces,
            cls.sourceFile,
            cls.annotations,
            cls.fields,
            methods,
        ) to count
    }

    private fun stubMethod(method: Method): Method {
        val ret = method.returnType
        val impl = when {
            ret == "V" -> MutableMethodImplementation(1).also {
                it.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            }
            ret == "Z" || ret == "B" || ret == "S" || ret == "C" || ret == "I" ->
                MutableMethodImplementation(2).also {
                    it.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 1))
                    it.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
                }
            else -> MutableMethodImplementation(2).also {
                it.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
                it.addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
            }
        }
        return ImmutableMethod(
            method.definingClass,
            method.name,
            method.parameters,
            method.returnType,
            method.accessFlags or AccessFlags.PUBLIC.value,
            method.annotations,
            method.hiddenApiRestrictions,
            impl,
        )
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}

data class PairIpDecryptReport(
    val present: Boolean,
    val packApplied: Boolean,
    val natives: List<String>,
    val dexStubs: Int,
    val packDir: String? = null,
    val hostVersionCode: Int? = null,
)

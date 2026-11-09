package dev.fatter.gpp.manager.installation.steps

import android.content.Context
import com.github.diamondminer88.zip.ZipWriter
import java.io.File
import java.util.zip.ZipFile

/**
 * Replaces the cloned Grindr launcher icon with the Grindr++ brand.
 *
 * The stock 26.x APK ships:
 *  - legacy `res/mipmap-<density>/ic_launcher.png` / `ic_launcher_round.png`
 *  - adaptive `res/mipmap-anydpi-v21/ic_launcher.xml` whose foreground is the
 *    original Grindr vector and whose background is a black color resource
 *  - themed `ic_p35_launcher_*` webp layers
 *
 * The manifest points at `mipmap/ic_launcher`, and the launcher picks the
 * `anydpi-v21` XML over the legacy PNGs. Deleting that XML would leave the
 * resource table dangling, so instead we remap its three drawable references
 * (background / foreground / monochrome) to the p35 layer ids and overwrite
 * those layers with the brand assets. Legacy PNGs are replaced as well for
 * launchers that do not use adaptive icons.
 */
class InjectCloneIconStep(
    private val folder: File,
) : BaseStep() {
    override val name = "Inject Grindr++ icon"

    override suspend fun doExecute(context: Context, print: Print) {
        val assets = loadAssets(context, print)
        if (assets.isEmpty()) {
            print("WARN: brand icon assets missing — skipping icon inject")
            return
        }

        val apkFiles = folder.listFiles()?.filter { it.name.endsWith(".apk") } ?: emptyList()
        for (apk in apkFiles) {
            injectIntoApk(apk, assets, print)
        }
        print("Grindr++ icon inject completed")
    }

    private fun loadAssets(context: Context, print: Print): Map<String, ByteArray> {
        val names = buildList {
            add("legacy" to "brand/ic_launcher_192.png")
            add("legacy_round" to "brand/ic_launcher_round_192.png")
            add("foreground" to "brand/ic_launcher_foreground.png")
            add("background" to "brand/ic_launcher_background.png")
            add("monochrome" to "brand/ic_launcher_monochrome.png")
            add("p35" to "brand/ic_p35_launcher.webp")
            add("p35_round" to "brand/ic_p35_launcher_round.webp")
            add("p35_foreground" to "brand/ic_p35_launcher_foreground.webp")
            add("p35_background" to "brand/ic_p35_launcher_background.webp")
            add("p35_monochrome" to "brand/ic_p35_launcher_monochrome.webp")
            for ((_, size) in DENSITIES) {
                add("legacy_$size" to "brand/ic_launcher_$size.png")
                add("legacy_round_$size" to "brand/ic_launcher_round_$size.png")
            }
        }
        val out = mutableMapOf<String, ByteArray>()
        for ((key, path) in names) {
            try {
                context.assets.open(path).use { out[key] = it.readBytes() }
            } catch (_: Exception) {
                print("DEBUG: asset $path not found")
            }
        }
        return out
    }

    private fun injectIntoApk(apk: File, assets: Map<String, ByteArray>, print: Print) {
        val replacements = mutableMapOf<String, ByteArray>()

        ZipFile(apk).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            val launcherNames = names.filter { name ->
                val lower = name.lowercase()
                lower.startsWith("res/mipmap") &&
                    (lower.contains("ic_launcher") || lower.contains("ic_p35_launcher")) &&
                    !lower.contains("_alt_")
            }
            if (launcherNames.isEmpty()) {
                print("No launcher mipmaps in ${apk.name} — skip")
                return
            }

            // The p35 adaptive XML carries the ids of the brand layer slots we
            // replace; reuse them for the main adaptive XML.
            val p35Refs = names
                .firstOrNull { it.equals("res/mipmap-anydpi-v21/ic_p35_launcher.xml", true) }
                ?.let { readEntry(zip, it) }
                ?.let(::referenceIds)
                ?.takeIf { it.size == 3 }
                ?: emptyList()
            if (p35Refs.isEmpty()) {
                print("WARN: ${apk.name} has no p35 adaptive layers — legacy icon only")
            }

            for (name in launcherNames) {
                val lower = name.lowercase()
                when {
                    lower.endsWith(".xml") -> {
                        if (!lower.contains("p35_launcher") && p35Refs.size == 3) {
                            readEntry(zip, name)?.let { xml ->
                                replacements[name] = remapReferences(xml, p35Refs)
                            }
                        }
                    }
                    lower.contains("p35_launcher_foreground") ->
                        assets["p35_foreground"]?.let { replacements[name] = it }
                    lower.contains("p35_launcher_background") ->
                        assets["p35_background"]?.let { replacements[name] = it }
                    lower.contains("p35_launcher_monochrome") ->
                        assets["p35_monochrome"]?.let { replacements[name] = it }
                    lower.endsWith("ic_p35_launcher_round.webp") ->
                        assets["p35_round"]?.let { replacements[name] = it }
                    lower.endsWith("ic_p35_launcher.webp") ->
                        assets["p35"]?.let { replacements[name] = it }
                    else -> {
                        val size = DENSITIES.firstOrNull { (density, _) ->
                            lower.contains("mipmap-$density")
                        }?.second
                        val round = lower.contains("ic_launcher_round")
                        val key = when {
                            size == null -> if (round) "legacy_round" else "legacy"
                            round -> "legacy_round_$size"
                            else -> "legacy_$size"
                        }
                        assets[key]?.let { replacements[name] = it }
                    }
                }
            }
        }

        if (replacements.isEmpty()) {
            print("No matching icon entries in ${apk.name} — skip")
            return
        }

        print("Injecting icon into ${apk.name} (${replacements.size} entries)")
        ZipWriter(apk, true).use { zip ->
            replacements.forEach { (name, bytes) ->
                // writeEntry appends; without removing the stock entry first the
                // APK ends up with duplicate names and Android keeps the original.
                zip.deleteEntry(name, /* fillVoid = */ true)
                zip.writeEntry(name, bytes)
            }
        }
    }

    private fun readEntry(zip: ZipFile, name: String): ByteArray? =
        zip.getEntry(name)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }

    /** Little-endian resource references (0x7f......) in a compiled XML file. */
    private fun referenceIds(xml: ByteArray): List<Int> = buildList {
        for (i in 0..xml.size - 4) {
            val value = readInt(xml, i)
            if (value in 0x7f000000..0x7fffffff) add(value)
        }
    }

    /**
     * Replaces the first [targets].size resource references in order. The
     * adaptive-icon attribute order is always background, foreground,
     * monochrome, and the file length does not change.
     */
    private fun remapReferences(xml: ByteArray, targets: List<Int>): ByteArray {
        val positions = mutableListOf<Int>()
        for (i in 0..xml.size - 4) {
            if (readInt(xml, i) in 0x7f000000..0x7fffffff) positions += i
        }
        if (positions.size < targets.size) return xml
        val patched = xml.copyOf()
        positions.take(targets.size).forEachIndexed { index, position ->
            writeInt(patched, position, targets[index])
        }
        return patched
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }

    private companion object {
        /** mipmap bucket -> legacy PNG edge in px. */
        val DENSITIES = listOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192,
        )
    }
}

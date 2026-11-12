package dev.fatter.gpp.manager.utils

import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Copies a ZIP entry into [out] preserving its original compression method.
 *
 * A plain [ZipEntry] defaults to DEFLATED, so rewriting a whole APK archive
 * would compress every entry. Android 11+ rejects installs whose
 * `resources.arsc` is compressed and not 4-byte aligned, and stock Play APKs
 * keep `resources.arsc` and (with `extractNativeLibs=false`) native libraries
 * STORED. Full-archive rewrites must therefore carry the method over.
 *
 * When [replacement] is provided its bytes replace the entry data; the entry
 * keeps the original method (a STORED replacement is written with its own
 * size/CRC).
 */
fun ZipOutputStream.copyEntry(
    zip: ZipFile,
    entry: ZipEntry,
    replacement: ByteArray? = null,
) {
    val newEntry = ZipEntry(entry.name)
    newEntry.time = entry.time

    if (entry.isDirectory) {
        newEntry.method = ZipEntry.STORED
        newEntry.size = 0
        newEntry.compressedSize = 0
        newEntry.crc = 0
        putNextEntry(newEntry)
        closeEntry()
        return
    }

    if (entry.method == ZipEntry.STORED) {
        newEntry.method = ZipEntry.STORED
        if (replacement != null) {
            newEntry.size = replacement.size.toLong()
            newEntry.compressedSize = replacement.size.toLong()
            newEntry.crc = crc32(replacement)
            putNextEntry(newEntry)
            write(replacement)
        } else {
            newEntry.size = entry.size
            newEntry.compressedSize = entry.size
            newEntry.crc = entry.crc
            putNextEntry(newEntry)
            zip.getInputStream(entry).use { it.copyTo(this) }
        }
        closeEntry()
        return
    }

    newEntry.method = ZipEntry.DEFLATED
    putNextEntry(newEntry)
    if (replacement != null) {
        write(replacement)
    } else {
        zip.getInputStream(entry).use { it.copyTo(this) }
    }
    closeEntry()
}

private fun crc32(data: ByteArray): Long {
    val crc = CRC32()
    crc.update(data)
    return crc.value
}

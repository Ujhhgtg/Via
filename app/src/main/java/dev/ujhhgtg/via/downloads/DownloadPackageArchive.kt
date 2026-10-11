package dev.ujhhgtg.via.downloads

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipException

/** APK/APKS/APKM/XAPK are ZIP containers. Recognition uses manifests, including inside unnamed entries. */
internal object DownloadPackageArchive {
    enum class Kind { OTHER, APK, BUNDLE }
    data class Prepared(val directory: File, val parts: List<ApkPart>) {
        fun obbFiles(packageName: String) = File(directory, "obb/$packageName").listFiles()?.filter(File::isFile).orEmpty()
        fun close() { directory.deleteRecursively() }
    }
    private const val MANIFEST = "AndroidManifest.xml"
    private const val MAX_MANIFEST = 4 * 1024 * 1024

    suspend fun inspect(context: Context, record: DownloadRecord): DownloadRecord {
        if (!record.isComplete || record.packageInspected) return record
        val uri = DownloadFiles.uri(context, record) ?: return record
        return classified(record, kind(context, uri))
    }

    internal fun classified(record: DownloadRecord, kind: Kind): DownloadRecord = record.copy(flags =
        (record.flags and DownloadRecord.PACKAGE_MASK.inv()) or when (kind) {
            Kind.OTHER -> DownloadRecord.PACKAGE_OTHER
            Kind.APK -> DownloadRecord.PACKAGE_APK
            Kind.BUNDLE -> DownloadRecord.PACKAGE_BUNDLE
        })

    /** Extract only a base APK for the existing PackageManager icon loader, without preparing every split. */
    suspend fun <T> withBaseApk(context: Context, uri: Uri, read: (File) -> T): T? = withZip(context, uri) { archive ->
        val cache = File.createTempFile("package-icon-", ".apk", context.cacheDir)
        try {
            for (entry in archive.entries()) {
                if (entry.isDirectory) continue
                archive.getInputStream(entry).buffered().use { input ->
                    input.mark(4)
                    val signature = input.readUpTo(4)
                    input.reset()
                    if (!zipSignature(signature)) return@use
                    cache.outputStream().use { copy(input, it) }
                    val base = try { ZipFile(cache).use { apk ->
                        val metadata = apk.getEntry(MANIFEST) ?: return@use false
                        apk.getInputStream(metadata).use(::manifest).splitName == null
                    } } catch (_: IOException) { false }
                    if (base) return@withZip read(cache)
                }
            }
            null
        } finally { cache.delete() }
    }

    suspend fun kind(context: Context, uri: Uri): Kind {
        context.contentResolver.openInputStream(uri)?.use { if (!zipSignature(it.readUpTo(4))) return Kind.OTHER }
            ?: throw IOException("Cannot open download")
        return try { withZip(context, uri) { kind(it) } } catch (_: ZipException) { Kind.OTHER }
    }

    internal suspend fun kind(archive: ZipFile): Kind {
        archive.getEntry(MANIFEST)?.let { entry ->
            archive.getInputStream(entry).use(::manifest)
            return Kind.APK
        }
        for (entry in archive.entries()) {
            currentCoroutineContext().ensureActive()
            if (entry.isDirectory) continue
            archive.getInputStream(entry).buffered().use { input ->
                input.mark(4)
                val signature = input.readUpTo(4)
                input.reset()
                if (zipSignature(signature)) {
                    try { ZipInputStream(input).use { nested ->
                        while (true) {
                            val child = nested.nextEntry ?: break
                            if (child.name == MANIFEST) {
                                try { manifest(nested); return Kind.BUNDLE } catch (_: IOException) { break }
                            }
                        }
                    } } catch (_: IOException) { /* A nested ZIP without a valid APK is not a package. */ }
                }
            }
        }
        return Kind.OTHER
    }

    suspend fun prepare(context: Context, uri: Uri): Prepared {
        val directory = File(context.cacheDir, "package-install-${UUID.randomUUID()}")
        if (!directory.mkdirs()) throw IOException("Cannot create installation cache")
        return try { withZip(context, uri) { prepare(it, directory) } }
            catch (error: Throwable) { directory.deleteRecursively(); throw error }
    }

    internal suspend fun prepare(archive: ZipFile, directory: File): Prepared {
        try {
            val apks = mutableListOf<ApkPart>()
            for (entry in archive.entries()) {
                currentCoroutineContext().ensureActive()
                if (entry.isDirectory) continue
                val obb = obbPath(entry.name)
                if (obb != null) {
                    val target = File(directory, "obb/$obb")
                    if (!target.parentFile!!.isDirectory && !target.parentFile!!.mkdirs()) throw IOException("Cannot create OBB cache")
                    archive.getInputStream(entry).use { input -> target.outputStream().use { copy(input, it) } }
                    continue
                }
                archive.getInputStream(entry).buffered().use { input ->
                    input.mark(4)
                    val signature = input.readUpTo(4)
                    input.reset()
                    if (!zipSignature(signature)) return@use
                    val file = File(directory, "part-${apks.size}.apk")
                    file.outputStream().use { copy(input, it) }
                    val part = try { ZipFile(file).use { apk ->
                        val manifestEntry = apk.getEntry(MANIFEST) ?: return@use null
                        val metadata = apk.getInputStream(manifestEntry).use(::manifest)
                        val abis = apk.entries().asSequence().map { it.name.split('/') }
                            .filter { it.size >= 3 && it[0] == "lib" }.map { it[1] }.toSet()
                        ApkPart(file, entry.name, metadata, abis)
                    } } catch (_: IOException) { null }
                    if (part != null) apks += part else file.delete()
                }
            }
            val parts = apks
            if (parts.none { it.isBase }) throw IOException("No base APK found in archive")
            return Prepared(directory, parts)
        } catch (error: Throwable) { directory.deleteRecursively(); throw error }
    }

    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(minOf(limit, 8192))
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun manifest(input: InputStream): ApkManifest {
        val bytes = input.readUpTo(MAX_MANIFEST + 1)
        if (bytes.size > MAX_MANIFEST) throw IOException("Manifest is too large")
        return ApkManifest.read(bytes)
    }

    internal fun zipSignature(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
        bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /** Cache paths are derived from a package identifier and a basename, never from an archive path. */
    internal fun obbPath(name: String): String? {
        val segments = name.replace('\\', '/').split('/')
        if (segments.any { it == ".." || it == "." || it.isEmpty() }) return null
        val file = segments.last()
        val packageName = Regex("(?:main|patch)\\.[0-9]+\\.([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+)\\.obb", RegexOption.IGNORE_CASE)
            .matchEntire(file)?.groupValues?.get(1) ?: return null
        if (segments.size > 1 && segments.takeLast(2).first() != packageName) return null
        return "$packageName/$file"
    }

    private suspend fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) return
            output.write(buffer, 0, count)
        }
    }

    private suspend fun <T> withZip(context: Context, uri: Uri, block: suspend (ZipFile) -> T): T {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Cannot read archive")
        descriptor.use {
            // The common file/MediaStore/SAF case is seekable and needs no copy of the outer archive.
            val direct = try { ZipFile("/proc/self/fd/${descriptor.fd}") } catch (_: IOException) { null }
            if (direct != null) return direct.use { block(it) }
        }
        // Providers may expose a pipe instead. Materialize that source on disk, never in memory.
        val cache = File.createTempFile("package-source-", ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> cache.outputStream().use { copy(input, it) } }
                ?: throw IOException("Cannot read archive")
            return ZipFile(cache).use { block(it) }
        } finally { cache.delete() }
    }
}

package dev.ujhhgtg.via.downloads

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File
import java.net.URLDecoder

/** Filename policy l5.b and destination creation z8.b1.c/t/u. */
object DownloadFiles {
    data class Destination(val name: String, val path: String?, val uri: Uri?)

    fun name(url: String, disposition: String?, mime: String?): String {
        val fromHeader = disposition?.let(::dispositionName)
        var result = fromHeader ?: url.substringBefore('?').takeUnless { it.endsWith('/') }
            ?.takeIf { it.lastIndexOf('/') >= 0 }?.substringAfterLast('/') ?: "downloadfile"
        if (result.contains('%')) result = decode(result)
        result = result.replace('/', '_')
        val dot = result.lastIndexOf('.')
        val extension = DownloadMimeTypes.extension(mime)
        if (dot < 0) {
            val suffix = extension ?: if (mime?.startsWith("image/") == true) "png" else null
            if (!suffix.isNullOrEmpty() && !suffix.equals("bin", true)) result += ".$suffix"
        } else if (fromHeader == null && !DownloadMimeTypes.known(result.substring(dot + 1)) &&
            !extension.isNullOrEmpty() && !extension.equals("bin", true)) result = result.substring(0, dot) + ".$extension"
        return result
    }

    fun dispositionName(value: String): String? {
        var start = value.indexOf("filename*=")
        if (start < 0) start = value.indexOf("filename")
        if (start < 0) return null
        var result = value.substring(start).substringBefore(';').trim()
        val equals = result.indexOf('=')
        if (equals > 0) {
            result = result.substring(equals + 1).trim()
            val charset = result.indexOf("''")
            if (charset > 0) result = result.substring(charset + 2).trim()
            val spaced = result.indexOf("' '")
            if (spaced > 0) result = result.substring(spaced + 3).trim()
        }
        result = result.trim('"', '\'')
        return result.takeIf { it.isNotEmpty() }?.let { if (it.indexOf('%') > 0) decode(it) else it }
    }

    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    fun create(context: Context, directory: String, filename: String, mime: String?): Destination {
        val name = filename.replace('/', '_').ifBlank { "downloadfile" }
        val type = mime?.takeIf { it.isNotEmpty() } ?: "application/octet-stream"
        if (directory.startsWith("content://")) {
            val tree = directory.toUri()
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val names = mutableSetOf<String>()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) names += c.getString(0)
            }
            val unique = uniqueName(name, names)
            val uri = DocumentsContract.createDocument(context.contentResolver, parent, type, unique)
                ?: throw java.io.IOException("Cannot create download in $directory")
            return Destination(unique, directory, uri)
        }
        if (!directory.startsWith('/')) {
            val location = directory.trim('/').ifBlank { Environment.DIRECTORY_DOWNLOADS } + "/"
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val names = mutableSetOf<String>()
            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(location), null)?.use { c ->
                while (c.moveToNext()) names += c.getString(0)
            }
            val unique = uniqueName(name, names)
            val uri = context.contentResolver.insert(collection, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, unique)
                put(MediaStore.MediaColumns.MIME_TYPE, type)
                put(MediaStore.MediaColumns.RELATIVE_PATH, location)
            }) ?: throw java.io.IOException("Cannot create download in $directory")
            return Destination(unique, directory, uri)
        }
        @Suppress("DEPRECATION")
        val folder = if (directory.startsWith('/')) File(directory) else File(Environment.getExternalStorageDirectory(), directory)
        if (!folder.isDirectory && !folder.mkdirs()) throw java.io.IOException("Cannot create $directory")
        val unique = uniqueName(name, folder.list()?.toSet().orEmpty())
        return Destination(unique, File(folder, unique).path, null)
    }

    fun uri(context: Context, record: DownloadRecord): Uri? {
        val stored = record.fileUri
        if (stored != null && stored.scheme != "file") return stored
        val path = stored?.path ?: record.path?.takeIf { !it.startsWith("content://") } ?: return null
        return dev.ujhhgtg.via.BrowserFileProvider.uri(context, File(path))
    }

    /** z8.f1.b derives the type from the final filename and uses INSTALL_PACKAGE for APKs. */
    fun resolvedMime(record: DownloadRecord): String {
        val extension = record.name.substringAfterLast('.', "").lowercase()
        return DownloadMimeTypes.mime(extension, record.mimeType ?: "application/octet-stream") ?: "application/octet-stream"
    }

    fun openIntent(context: Context, record: DownloadRecord): Intent? {
        if (!record.isComplete) return null
        val uri = uri(context, record) ?: return null
        if (record.isPackageBundle) return SplitPackageInstallerActivity.intent(context, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val mime = if (record.isAndroidPackage) "application/vnd.android.package-archive" else resolvedMime(record)
        return Intent(if (mime == "application/vnd.android.package-archive") Intent.ACTION_INSTALL_PACKAGE else Intent.ACTION_VIEW)
            .setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun remove(context: Context, record: DownloadRecord): Boolean = runCatching {
        val uri = record.fileUri
        when {
            uri == null || uri.scheme == "file" -> File(uri?.path ?: record.path ?: return false).delete()
            DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(context.contentResolver, uri)
            else -> context.contentResolver.delete(uri, null, null) > 0
        }
    }.getOrDefault(false)

    data class Prepared(val record: DownloadRecord, val status: Int)

    /** mark.via.download.i1.h: existing output, old folder, timestamp fallback, then current folder. */
    fun prepare(context: Context, record: DownloadRecord, currentDirectory: String): Prepared {
        val directory = record.path
        if (directory.isNullOrEmpty() || record.name.isEmpty()) return Prepared(record, 2)
        if (exists(context, record)) return Prepared(record, 0)
        val dot = record.name.indexOf('.')
        val suffix = "_" + java.lang.Long.toHexString(System.currentTimeMillis())
        val fallback = if (dot > 0) record.name.substring(0, dot) + suffix + record.name.substring(dot) else record.name + suffix
        val candidates = buildList {
            add(directory to record.name); add(directory to fallback)
            if (currentDirectory != directory) { add(currentDirectory to record.name); add(currentDirectory to fallback) }
        }
        for ((folder, name) in candidates) {
            val destination = runCatching { create(context, folder, name, record.mimeType) }.getOrNull() ?: continue
            val uri = destination.uri ?: destination.path?.let { Uri.fromFile(File(it)) } ?: continue
            runCatching { context.contentResolver.openOutputStream(uri, "w")?.close() }
            return Prepared(record.copy(name = destination.name, path = folder, fileUri = uri), 1)
        }
        return Prepared(record, 3)
    }

    fun exists(context: Context, record: DownloadRecord): Boolean = runCatching {
        if (record.fileUri == null) record.path?.let { File(it).isFile } == true
        else context.contentResolver.openFileDescriptor(record.fileUri, "r")?.use { true } == true
    }.getOrDefault(false)

    /** z8.b1.s and b1.b3: rename the existing document/media entry/file, then persist its new URI and MIME. */
    fun rename(context: Context, record: DownloadRecord, name: String): Destination {
        require(name.isNotBlank() && '/' !in name && name != "." && name != "..")
        val uri = record.fileUri
        if (uri != null && DocumentsContract.isDocumentUri(context, uri)) {
            val renamed = DocumentsContract.renameDocument(context.contentResolver, uri, name)
                ?: throw java.io.IOException("Cannot rename download")
            return Destination(name, record.path, renamed)
        }
        if (uri != null && uri.scheme == "content") {
            val mime = DownloadMimeTypes.mime(name.substringAfterLast('.', ""), "application/octet-stream")
            val updated = context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
            }, null, null)
            if (updated <= 0) throw java.io.IOException("Cannot rename download")
            return Destination(name, record.path, uri)
        }
        val old = File(uri?.path ?: record.path ?: throw java.io.IOException("Missing download path"))
        val renamed = File(old.parentFile, name)
        if (renamed.exists() || !old.renameTo(renamed)) throw java.io.IOException("Cannot rename download")
        return Destination(name, record.path, Uri.fromFile(renamed))
    }

    fun uniqueName(name: String, existing: Set<String>): String {
        if (name !in existing) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val suffix = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while ("$base ($index)$suffix" in existing) index++
        return "$base ($index)$suffix"
    }
}

package dev.ujhhgtg.via.downloads

import dev.ujhhgtg.via.R
import android.content.Context
import java.util.Locale

object DownloadPresentation {

    fun category(record: DownloadRecord): Int = if (record.isAndroidPackage) 3 else category(record.name).let {
        if (record.packageInspected && it == 3) 8 else it
    }

    /** z8.b0.w uses a 0.8 boundary for the next unit and one decimal place. */
    fun size(bytes: Long): String {
        val value: Double
        val unit: String
        when {
            bytes >= 858993459.2 -> { value = bytes / 1073741824.0; unit = "GB" }
            bytes >= 838860.8 -> { value = bytes / 1048576.0; unit = "MB" }
            bytes >= 819.2 -> { value = bytes / 1024.0; unit = "KB" }
            else -> { value = bytes.toDouble(); unit = "B" }
        }
        return String.format(Locale.ROOT, "%.1f %s", value, unit)
    }

    /** b1.e4/f4 filters by the filename extension, including explicit document/archive lists. */
    fun category(name: String): Int {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = DownloadMimeTypes.mime(extension, "").orEmpty()
        return when {
            mime.startsWith("audio/") -> 1
            mime.startsWith("video/") -> 2
            mime.startsWith("image/") -> 7
            extension == "apk" || extension == "xapk" -> 3
            mime.startsWith("text/") || extension in setOf("mht", "docx", "doc", "xml", "xul", "xls", "xlsx", "json", "jsonld", "epub", "js", "pdf", "ppt", "pptx", "ts", "azw", "rtf", "odp", "ods", "odt", "sh", "php", "py") -> 6
            extension in setOf("zip", "rar", "gz", "7z", "tar", "gtar", "bz", "bz2", "xz", "lzma", "z", "arj", "cab", "lzh", "iso", "jar", "ace", "tgz") -> 5
            else -> 8
        }
    }

    /** DownloadTaskViewDelegate.r: seconds through 60, minutes through 120, hours through 12. */
    private fun eta(context: Context, record: DownloadRecord, speed: Long): String {
        if (record.totalSize <= 0 || speed <= 0) return context.getString(R.string.download_eta_unknown)
        val seconds = ((record.totalSize - record.downloadedSize) / speed).coerceAtLeast(0)
        val (quantity, resource) = when {
            seconds <= 60 -> seconds.toInt() to R.plurals.secs_left
            seconds / 60 <= 120 -> (seconds / 60).toInt() to R.plurals.mins_left
            seconds / 3600 <= 12 -> (seconds / 3600).toInt() to R.plurals.hours_left
            else -> return context.getString(R.string.download_eta_unknown)
        }
        return context.resources.getQuantityString(resource, quantity, quantity)
    }

    fun detail(context: Context, record: DownloadRecord, speed: Long = -1): String {
        if (record.isComplete) return size(maxOf(record.downloadedSize, record.totalSize))
        if (record.isFailed) return record.errorMessage?.takeIf { it.isNotEmpty() }
            ?.let { context.getString(R.string.download_failed_with_message, it) }
            ?: context.getString(R.string.download_failed)
        val progress = if (record.totalSize > 0) context.getString(R.string.download_progress, size(record.downloadedSize), size(record.totalSize)) else context.getString(R.string.download_progress_indeterminate, size(record.downloadedSize))
        if (record.state == DownloadState.PAUSED) return context.getString(R.string.download_progress_status, progress, context.getString(R.string.download_status_paused))
        if (record.state == DownloadState.WAITING_NETWORK) return context.getString(R.string.download_progress_status, progress, context.getString(R.string.download_status_waiting_for_network))
        val rate = if (speed < 0) context.getString(R.string.download_speed_unknown) else "${size(speed)}/s"
        return if (record.totalSize > 0) context.getString(R.string.download_progress_speed_eta, progress, rate, eta(context, record, speed))
        else context.getString(R.string.download_progress_speed, progress, rate)
    }
}

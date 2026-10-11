package dev.ujhhgtg.via.downloads

import android.net.Uri

data class DownloadRecord(
    val id: Long = 0,
    val name: String,
    val url: String?,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val path: String? = null,
    val fileUri: Uri? = null,
    val downloadedSize: Long = 0,
    val totalSize: Long = 0,
    val chunks: Int = 1,
    val priority: Int = 0,
    val state: Int = DownloadState.WAITING,
    val errorMessage: String? = null,
    val flags: Int = 0,
    val updatedAt: Long = 0,
    val createdAt: Long = 0
) {
    val progress: Int get() = if (totalSize > 0) ((downloadedSize * 100) / totalSize).toInt().coerceIn(0, 100) else 0
    val isActive: Boolean get() = state in 1..99 && state != DownloadState.PAUSED
    val isComplete: Boolean get() = state in 100..199
    val isFailed: Boolean get() = state in 200..299
    /** Via-owned content classification; the original transfer flags occupy bits 0 through 2. */
    val packageInspected: Boolean get() = flags and PACKAGE_MASK != 0
    val isPackageBundle: Boolean get() = flags and PACKAGE_MASK == PACKAGE_BUNDLE
    val isAndroidPackage: Boolean get() = if (packageInspected) flags and PACKAGE_MASK >= PACKAGE_APK
        else mimeType == "application/vnd.android.package-archive"

    companion object {
        internal const val PACKAGE_MASK = 24
        internal const val PACKAGE_OTHER = 8
        internal const val PACKAGE_APK = 16
        internal const val PACKAGE_BUNDLE = 24
    }
}

/** g5.b and m5.e: terminal 100 is success, terminal 200 is failure. */
object DownloadState {
    const val WAITING = 90
    const val CONNECTING = 91
    const val DOWNLOADING = 92
    const val PAUSED = 80
    const val WAITING_NETWORK = 95
    const val FAILED = 200
    const val CANCELED = PAUSED
    const val COMPLETE = 100
}

/** g5.a, with start/length/downloaded mapped to the original downloader v3 chunks table. */
internal data class DownloadChunk(var id: Long = 0, val taskId: Long, val start: Long, var length: Long,
    @Volatile var downloaded: Long = 0) {
    val begin: Long get() = start + downloaded
    val end: Long get() = start + length - 1
}

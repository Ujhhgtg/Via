package dev.ujhhgtg.via.downloads

import android.app.Activity
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.provider.MediaStore
import androidx.lifecycle.LifecycleOwner
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.applicationIoScope
import dev.ujhhgtg.via.ui.ViaToast
import kotlinx.coroutines.launch

/** c8.s6.B4/p1.J and g6.n: feedback is owned by the visible browser, not the background service. */
class DownloadFeedback(
    private val activity: Activity,
    private val owner: LifecycleOwner,
    private val visible: () -> Boolean,
    private val showDownloads: () -> Unit,
) {
    private val coordinator = DownloadCoordinator.get(activity)
    private val listener: (DownloadRecord, Int, Int) -> Unit = { record, _, state ->
        if (visible() && (state in 100..199 || state in 200..299)) finished(record)
    }

    /** Original s6.R1/M1 registers this state observer only while the browser is resumed. */
    fun start() = coordinator.addStateListener(listener)
    fun stop() = coordinator.removeStateListener(listener)

    fun started() = ViaToast.show(activity, R.string.download_pending, actionText = R.string.view_downloads, action = showDownloads)

    private fun finished(record: DownloadRecord) {
        val name = abbreviate(record.name)
        val message = activity.getString(if (record.isComplete) R.string.file_download_completed else R.string.file_download_failed, name)
        if (record.isComplete && record.isAndroidPackage) {
            ViaToast.show(activity, message, actionText = activity.getString(R.string.install)) { install(record) }
            return
        }
        if (record.isComplete && record.mimeType?.startsWith("image/") == true) updateImageMetadata(record)
        ViaToast.show(activity, message, actionText = activity.getString(R.string.view_downloads), action = showDownloads)
    }

    private fun install(record: DownloadRecord) {
        DownloadFiles.openIntent(activity, record)?.let { intent ->
            try { activity.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { }
        }
    }

    /** i1.f/g updates MediaStore dimensions/size, and scans legacy file destinations. */
    // Fire-and-forget: failures are logged, and there is nothing to cancel with the dialog.
    private fun updateImageMetadata(record: DownloadRecord) {
        val uri = record.fileUri ?: return
        val context = activity.applicationContext
        if (uri.scheme == "file" && uri.path != null) MediaScannerConnection.scanFile(context, arrayOf(uri.path!!), null, null)
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        if (uri.scheme != collection.scheme || uri.authority != collection.authority || uri.path?.startsWith(collection.path + "/") != true) return
        applicationIoScope.launch {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, dimensions)
                    val values = ContentValues().apply {
                        if (dimensions.outWidth > 0 && dimensions.outHeight > 0) {
                            put("width", dimensions.outWidth); put("height", dimensions.outHeight)
                        }
                        if (descriptor.statSize >= 0) put("_size", descriptor.statSize)
                    }
                    if (values.size() > 0) context.contentResolver.update(uri, values, null, null)
                }
            } catch (error: Exception) { android.util.Log.w("ViaDownloads", "Image metadata update failed", error) }
        }
    }

    companion object {
        /** g6.p.q(name,24) retains both ends of a long filename. */
        private fun abbreviate(name: String) = if (name.length > 24) name.take(11) + "..." + name.takeLast(10) else name
    }
}

package dev.ujhhgtg.via.downloads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Size
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.Executors

/** DownloadTaskViewDelegate.G/y and z8.t3: two workers and a 64-entry access-ordered preview cache. */
internal class DownloadPreviews(context: Context, private val changed: (Long) -> Unit) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newFixedThreadPool(2)
    private data class Key(val id: Long, val uri: String?, val updatedAt: Long)
    private data class Preview(val drawable: Drawable?)
    private data class Loaded(val record: DownloadRecord, val drawable: Drawable?)
    private val cache = object : LinkedHashMap<Key, Preview>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Preview>?) = size > 64
    }
    private val pending = HashSet<Key>()
    private var closed = false
    var enabled = false

    fun icon(record: DownloadRecord): Drawable? {
        if (!record.isComplete) return null
        val key = Key(record.id, record.fileUri?.toString(), record.updatedAt)
        cache[key]?.let { return it.drawable }
        if (!enabled || closed || !pending.add(key)) return null
        workers.execute {
            val result = runCatching {
                runBlocking {
                    val classified = DownloadPackageArchive.inspect(context, record)
                    val uri = record.fileUri ?: DownloadFiles.uri(context, record)
                    val drawable = if (uri == null) null else when {
                        classified.isPackageBundle -> DownloadPackageArchive.withBaseApk(context, uri) { file ->
                            iconFromApk(file.path)?.let { icon -> BitmapDrawable(context.resources, icon.toBitmap(dp(24), dp(24))) }
                        }
                        classified.isAndroidPackage -> apkIcon(uri)
                        supported(classified.mimeType) -> thumbnail(uri)?.let { RoundedPreview(it, dp(2)) }
                        else -> null
                    }
                    Loaded(classified, drawable)
                }
            }
            main.post {
                pending.remove(key)
                if (!closed) {
                    val loaded = result.getOrNull()
                    cache[key] = Preview(loaded?.drawable)
                    if (loaded != null && loaded.record.flags != record.flags)
                        DownloadCoordinator.get(context).savePackageClassification(loaded.record)
                    // DownloadTaskViewDelegate$c.a publishes icon payload 2 even
                    // for a normal null result; onError caches the fallback only.
                    if (result.isSuccess) changed(record.id)
                }
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun iconFromApk(path: String?): Drawable? {
        if (path == null) return null
        val manager = context.packageManager
        val info = manager.getPackageArchiveInfo(path, 0)?.applicationInfo ?: return null
        info.sourceDir = path; info.publicSourceDir = path
        return info.loadIcon(manager).apply { setBounds(0, 0, dp(24), dp(24)) }
    }

    private fun apkIcon(uri: Uri): Drawable? {
        var descriptor: ParcelFileDescriptor? = null
        return try {
            if (uri.scheme == "file") iconFromApk(uri.path)
            else {
                descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                descriptor?.let { iconFromApk("/proc/self/fd/${it.fd}") }
            }
        } catch (_: Exception) {
            // z8.t3.b treats APK inspection failures as ordinary null previews.
            null
        } finally {
            try { descriptor?.close() } catch (_: Exception) { }
        }
    }

    /** z8.t3.d catches IO failures; unexpected loader errors use the delegate's onError path. */
    private fun thumbnail(uri: Uri): Bitmap? = try {
        context.contentResolver.loadThumbnail(uri, Size(dp(24), dp(24)), null)
    } catch (_: IOException) { null }

    fun close() { closed = true; enabled = false; pending.clear(); cache.clear(); workers.shutdownNow() }
    private fun supported(mime: String?) = mime == "application/vnd.android.package-archive" || mime?.startsWith("image/") == true || mime?.startsWith("video/") == true
    private fun dp(value: Int) = (context.resources.displayMetrics.density * value + .5f).toInt()

    /** k8.l draws the bitmap at its own dimensions; ImageView handles the final fit. */
    private class RoundedPreview(bitmap: Bitmap, private val radius: Int) : Drawable() {
        private val width = bitmap.width
        private val height = bitmap.height
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        override fun draw(canvas: Canvas) = canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius.toFloat(), radius.toFloat(), paint)
        override fun getIntrinsicWidth() = width
        override fun getIntrinsicHeight() = height
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

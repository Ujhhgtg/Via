package dev.ujhhgtg.via.browser

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import java.io.File
import java.util.UUID

/**
 * The full tab state cannot travel inside the activity's saved instance state: Android delivers
 * that Bundle in a one-way Binder transaction whose buffer is roughly half a megabyte, while a
 * single engine session state can already reach hundreds of kilobytes (Gecko serializes the whole
 * session, including its back-forward history). The browser therefore writes the full state to a
 * cache file and the Bundle only carries [VERSION], the metadata-only fallback and the file name.
 *
 * The file is deleted as soon as it is restored, when the browser's activity finishes, or by
 * [prune] when a previous save was never restored.
 */
internal object BrowserInstanceState {
    /** Bump when the saved keys or the engine state encoding change; older files are discarded. */
    const val VERSION = 1

    /** Above this size the metadata fallback would risk the same crash; only the file is used. */
    private const val MAX_INLINE_BYTES = 128 * 1024
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private const val DIRECTORY = "browser-state"

    fun newName(): String = "tabs-" + UUID.randomUUID()

    fun write(context: Context, name: String, state: Bundle): Boolean {
        if (!SessionState.write(state, file(context, name))) return false
        prune(context, keep = name)
        return true
    }

    /** Reads the file once and deletes it: a state restored from disk is never needed again. */
    fun read(context: Context, name: String): Bundle? =
        SessionState.readState(file(context, name).path)?.also { delete(context, name) }

    fun delete(context: Context, name: String) { runCatching { file(context, name).delete() } }

    /** Exact Binder cost of the metadata fallback, measured on a scratch Parcel. */
    fun fitsInline(state: Bundle): Boolean {
        val parcel = Parcel.obtain()
        return try { parcel.writeBundle(state); parcel.dataSize() <= MAX_INLINE_BYTES } finally { parcel.recycle() }
    }

    private fun file(context: Context, name: String): File = File(directory(context), name)

    private fun directory(context: Context): File = File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    private fun prune(context: Context, keep: String) {
        val now = System.currentTimeMillis()
        directory(context).listFiles()?.forEach { file ->
            if (file.name != keep && now - file.lastModified() > MAX_AGE_MS) runCatching { file.delete() }
        }
    }
}

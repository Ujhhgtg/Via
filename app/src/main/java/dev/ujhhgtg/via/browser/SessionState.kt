package dev.ujhhgtg.via.browser

import android.content.Context
import android.os.BadParcelableException
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import dev.ujhhgtg.via.data.SessionTab
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.search.UrlInputText
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** na.j.c/d and r4.d.b: the original Parcel-of-Bundle session file, with no alternate encoding. */
internal object SessionState {
    /** c8.ua.f1 / i6.i0.i: exclude built-in documents and about: pages, not other URL schemes. */
    fun acceptsUrl(context: Context, url: String?): Boolean = !url.isNullOrEmpty() &&
        !UrlInputText.isInternalDocument(url, context.filesDir.path) &&
        !(url.length > 6 && url.startsWith("about:", true))

    fun directory(context: Context): File = (context.getExternalFilesDir("tabs") ?: File(context.filesDir, "tabs")).apply { mkdirs() }

    /** Key of the backend that wrote a session Bundle. Files without it predate backends and came from WebView. */
    const val KEY_ENGINE = "ENGINE"
    private const val LEGACY_ENGINE = "webview"

    /**
     * Reads a saved tab, or null when it is missing, unreadable, or written by another backend.
     * Callers then restore the tab from its URL alone, since engine history states aren't portable.
     */
    fun read(path: String?): Bundle? = readFile(path)?.takeIf {
        writtenBy(it.getString(KEY_ENGINE), dev.ujhhgtg.via.engine.Engines.backend.id)
    }

    /** Whether a state tagged [engine] (null for files from before backends existed) belongs to [backend]. */
    internal fun writtenBy(engine: String?, backend: String): Boolean = (engine ?: LEGACY_ENGINE) == backend

    /** Reads a Parcel-of-Bundle written by [write] without applying the session engine tag. */
    fun readState(path: String?): Bundle? = readFile(path)

    private fun readFile(path: String?): Bundle? {
        if (path.isNullOrEmpty()) return null
        return try {
            FileInputStream(path).use { stream ->
                val data = ByteArray(stream.channel.size().toInt())
                stream.read(data)
                val parcel = Parcel.obtain()
                try {
                    parcel.unmarshall(data, 0, data.size)
                    parcel.setDataPosition(0)
                    parcel.readBundle(ClassLoader.getSystemClassLoader())?.also { it.putAll(it) }
                } finally { parcel.recycle() }
            }
        } catch (error: IOException) { failed(error) }
          catch (error: NullPointerException) { failed(error) }
          catch (error: IllegalStateException) { failed(error) }
          catch (error: BadParcelableException) { failed(error) }
    }

    fun write(bundle: Bundle, path: File): Boolean = try {
        FileOutputStream(path).use { stream ->
            val parcel = Parcel.obtain()
            try { parcel.writeBundle(bundle); stream.write(parcel.marshall()); stream.flush() }
            finally { parcel.recycle() }
        }
        true
    } catch (error: IOException) { failed(error); false }

    private fun failed(error: Exception): Nothing? {
        Log.w("ViaSessions", "Cannot read or write a tab state", error)
        return null
    }

    /** t4.c.l/t4.b.saveState: COLOR accompanies a successful engine state, url and scroll are fallbacks. */
    fun capture(page: EnginePage): Bundle = Bundle().apply {
        if (page.saveState(this)) putInt("COLOR", PageColorSampler.colorOf(page))
        page.url?.takeIf(String::isNotEmpty)?.let { putString("url", it) }
        val scroll = page.scrollX.toLong() or (page.scrollY.toLong() shl 32)
        if (scroll != 0L) putLong("scroll", scroll)
    }

    /** t4.c.k: use the restored current history item, then the stored URL, with the original scroll delays. */
    fun restore(page: EnginePage, state: Bundle, loadFallback: (String) -> Unit): String? {
        if (state.isEmpty) return null
        PageColorSampler.restoreColor(page, state.getInt("COLOR", PageColorSampler.colorOf(page)))
        val restored = page.restoreState(state)
        val fallback = if (restored.isNullOrEmpty()) state.getString("url")?.takeIf(String::isNotEmpty) else null
        if (fallback != null) loadFallback(fallback)
        val scroll = state.getLong("scroll", 0L)
        if (scroll != 0L) page.view.postDelayed({
            if (page.scrollY <= 1000) page.scrollTo(scroll.toInt(), (scroll ushr 32).toInt())
        }, if (fallback == null) 100L else 500L)
        return restored
    }
}

/** Page capture is performed on the UI thread; writeFiles may then run on the browser's IO worker. */
class PendingSessionSnapshot internal constructor(
    private val directory: File,
    private val entries: List<Entry>,
) {
    internal class SavedRow(var value: SessionTab?)
    internal data class Entry(val row: SessionTab, val state: Bundle, val saved: SavedRow)

    fun writeFiles(): List<SessionTab> = entries.map { (row, state, saved) ->
        val target = File(directory, row.id)
        val result = if (!state.isEmpty && SessionState.write(state, target)) row.copy(filePath = target.path)
            else row.copy(filePath = saved.value?.filePath, lastVisitedAt = saved.value?.lastVisitedAt ?: 0L)
        // ua.C/c updates the file reference and timestamp only when the Parcel write succeeds.
        saved.value = result
        result
    }
}

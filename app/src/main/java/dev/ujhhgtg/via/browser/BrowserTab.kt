package dev.ujhhgtg.via.browser

import android.os.Bundle
import dev.ujhhgtg.via.engine.EnginePage
import java.util.UUID

/** A tab's current page plus the small amount of shell metadata that survives process recreation. */
class BrowserTab(
    val id: Long,
    /** The current QuickBack segment; replaced when the tab moves between segments. */
    var page: EnginePage,
    var requestedUrl: String = UrlResolver.HOME,
    var title: String = "",
    var sessionId: String = UUID.randomUUID().toString(),
) {
    /** r4.d.u reads the current history segment's page, without a second favicon snapshot. */
    val favicon: android.graphics.Bitmap? get() = page.favicon
    internal var internalDocumentUrl: String? = null

    val url: String
        get() = page.url?.takeIf { it.isNotBlank() }?.let(::displayUrl) ?: requestedUrl

    fun displayUrl(pageUrl: String): String = if (pageUrl == internalDocumentUrl) requestedUrl else pageUrl

    fun update(url: String? = null, title: String? = null) {
        if (!url.isNullOrBlank() && url != internalDocumentUrl) {
            internalDocumentUrl = null
            requestedUrl = url
        }
        if (title != null) this.title = title
    }

    /**
     * Saves the shell metadata, plus the engine's back-forward state when [includeEngineState].
     * Without the engine state [restoreState] reports false and the tab reloads [requestedUrl];
     * that metadata-only copy is what fits into the activity's size-limited instance state.
     */
    fun saveState(includeEngineState: Boolean = true): Bundle = Bundle().apply {
        putLong(KEY_ID, id)
        putString(KEY_SESSION_ID, sessionId)
        putString(KEY_REQUESTED_URL, requestedUrl)
        putString(KEY_TITLE, title)
        putString(KEY_INTERNAL_DOCUMENT, internalDocumentUrl)
        putInt(KEY_SCROLL_X, page.scrollX)
        putInt(KEY_SCROLL_Y, page.scrollY)
        if (includeEngineState) putBundle(KEY_WEBVIEW, Bundle().also { page.saveState(it) })
    }

    fun restoreState(state: Bundle): Boolean {
        requestedUrl = state.getString(KEY_REQUESTED_URL, requestedUrl)
        sessionId = state.getString(KEY_SESSION_ID, sessionId)
        title = state.getString(KEY_TITLE, title)
        internalDocumentUrl = state.getString(KEY_INTERNAL_DOCUMENT)
        val webState = state.getBundle(KEY_WEBVIEW) ?: return false
        return page.restoreState(webState) != null
    }

    /** t4.c restores scroll separately because Chromium's state does not always retain it. */
    fun restoreScroll(state: Bundle, afterReload: Boolean) {
        val x = state.getInt(KEY_SCROLL_X)
        val y = state.getInt(KEY_SCROLL_Y)
        val target = page
        if (x != 0 || y != 0) target.view.postDelayed({
            if (target.scrollY <= 1000) target.scrollTo(x, y)
        }, if (afterReload) 500L else 100L)
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_REQUESTED_URL = "requested_url"
        private const val KEY_TITLE = "title"
        private const val KEY_WEBVIEW = "webview"
        private const val KEY_INTERNAL_DOCUMENT = "internal_document"
        private const val KEY_SCROLL_X = "scroll_x"
        private const val KEY_SCROLL_Y = "scroll_y"

        // Tab creation/restoration runs on the UI thread, including popup callbacks.
        private var nextId = 1L
        fun nextId(): Long = nextId++
        fun observeId(id: Long) { nextId = maxOf(nextId, id + 1) }
        fun stateId(state: Bundle): Long = if (state.containsKey(KEY_ID)) state.getLong(KEY_ID) else nextId()
    }
}

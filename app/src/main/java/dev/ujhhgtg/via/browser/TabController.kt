package dev.ujhhgtg.via.browser

import android.content.Context
import android.os.Bundle
import dev.ujhhgtg.via.browser.script.ScriptManager
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SessionTab
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.engine.Engines
import dev.ujhhgtg.via.engine.FileChooserRequest
import dev.ujhhgtg.via.engine.FormResubmissionRequest
import dev.ujhhgtg.via.engine.FullscreenRequest
import dev.ujhhgtg.via.engine.HttpAuthRequest
import dev.ujhhgtg.via.engine.LoadError
import dev.ujhhgtg.via.engine.LocationRequest
import dev.ujhhgtg.via.engine.MediaPermissionRequest
import dev.ujhhgtg.via.engine.PopupRequest
import dev.ujhhgtg.via.engine.SslErrorRequest
import java.io.InputStream

/** Owns engine pages and their saved state while leaving activity UI decisions to [Host]. */
class TabController(
    context: Context,
    private val preferences: BrowserPreferences,
    private val host: Host = Host.NONE,
    private val siteConfiguration: (String) -> SiteConfiguration? = { null },
    private val userAgentForId: (Int) -> String? = { null },
) {
    interface Host {
        fun onReaderCheckRequested() = Unit

        fun onPageCreated(tab: BrowserTab) = Unit
        fun onCurrentPageChanged(tab: BrowserTab) = Unit
        fun onResourceAvailabilityChanged(tab: BrowserTab, hasMedia: Boolean) = Unit
        fun onPageStarted(tab: BrowserTab, url: String) = Unit
        fun onVisited(tab: BrowserTab, url: String): Boolean = false
        fun getVisited(tab: BrowserTab, urls: Array<String>): BooleanArray = BooleanArray(urls.size)
        fun onPageFinished(tab: BrowserTab, url: String, title: String?) = Unit
        fun onUserScript(tab: BrowserTab, url: String) = Unit
        fun onTitleChanged(tab: BrowserTab, title: String) = Unit
        fun onIconChanged(tab: BrowserTab, icon: android.graphics.Bitmap?) = Unit
        fun onTouchIconChanged(tab: BrowserTab, iconUrl: String) = Unit
        fun onProgressChanged(tab: BrowserTab, progress: Int) = Unit
        fun onDownload(tab: BrowserTab, url: String, userAgent: String?, disposition: String?, mimeType: String?, size: Long, body: InputStream? = null) = Unit
        fun onBridgeCommand(tab: BrowserTab, command: Int): Int = 0
        fun onBridgeDownload(tab: BrowserTab, url: String, name: String?, mime: String?) = Unit
        fun onBridgeMessage(tab: BrowserTab, token: String, json: String) = Unit
        fun onBridgeRecord(tab: BrowserTab, url: String, mime: String?) = Unit
        fun onBridgeToast(tab: BrowserTab, text: String) = Unit
        fun onBridgeAddon(tab: BrowserTab, id: String) = Unit
        fun installedAddonIds(tab: BrowserTab): String = "[]"
        fun onBeforeNavigate(tab: BrowserTab, url: String, proceed: () -> Unit): Boolean = false
        /** Gate a restored page history before it is allowed to load a network page. */
        fun onBeforeRestore(tab: BrowserTab, url: String, proceed: () -> Unit): Boolean = false
        fun onNavigationRequest(tab: BrowserTab, url: String): Boolean = false
        fun onExternalUrl(tab: BrowserTab, url: String) = Unit
        fun onInternalUrl(tab: BrowserTab, url: String) = onExternalUrl(tab, url)
        fun onError(tab: BrowserTab, error: LoadError) = Unit
        fun onHttpAuth(tab: BrowserTab, request: HttpAuthRequest) = request.cancel()
        fun onSslError(tab: BrowserTab, request: SslErrorRequest) = request.cancel()
        fun onCreateWindow(tab: BrowserTab, request: PopupRequest) = request.deny()
        fun onPopupCreated(opener: BrowserTab, popup: BrowserTab) = Unit
        fun onWindowClosed(tab: BrowserTab) = Unit
        fun onGeolocationPrompt(tab: BrowserTab, request: LocationRequest) = request.respond(allow = false, retain = false)
        fun onAndroidPermissions(permissions: Array<String>, complete: (Boolean) -> Unit) = complete(false)
        fun onGeolocationHidePrompt(tab: BrowserTab) = Unit
        fun onPermissionRequest(tab: BrowserTab, request: MediaPermissionRequest) = request.deny()
        fun onPermissionRequestCanceled(tab: BrowserTab, request: MediaPermissionRequest) = Unit
        fun onShowFullscreen(tab: BrowserTab, request: FullscreenRequest) = request.exited()
        fun onHideFullscreen(tab: BrowserTab) = Unit
        fun onFormResubmission(tab: BrowserTab, request: FormResubmissionRequest) = request.cancel()
        fun onFileChooser(tab: BrowserTab, request: FileChooserRequest): Boolean = false

        companion object {
            val NONE = object : Host {}
        }
    }

    /**
     * r4.f: model change events consumed by live surfaces such as the tab
     * sheet (f8.l0) while they are open.  Fired on the main thread only.
     */
    interface Listener {
        fun onTabInserted(index: Int, tab: BrowserTab) = Unit // Z
        fun onTabRemoved(index: Int) = Unit // H
        fun onTabSelected(from: Int, to: Int) = Unit // Y
        fun onTabChanged(index: Int) = Unit // f
        fun onTabsMoved(from: Int, to: Int, selected: Int) = Unit // G
    }

    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        if (listener !in listeners) listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /** r4.f.f is raised by the model whenever a row's content changed. */
    fun notifyTabChanged(tab: BrowserTab) {
        val index = all.indexOfFirst { it.id == tab.id }
        if (index >= 0) listeners.toList().forEach { it.onTabChanged(index) }
    }

    fun indexOf(tab: BrowserTab?): Int = if (tab == null) -1 else all.indexOfFirst { it.id == tab.id }


    // Pages need the host's themed context for popup/window and file chooser behavior.
    private val appContext = context
    private val filterEngine = dev.ujhhgtg.via.browser.filter.FilterRuntime.get(appContext, preferences.appFlags and 64 != 0)
    private val filterStatistics = FilterStatistics(preferences)
    @Volatile private var allowedBlockedPageHosts: Set<String> = emptySet()
    private val scripts = ScriptManager(ScriptStore(appContext))
    private val privacyPolicy = BrowserPrivacyPolicy(preferences, siteConfiguration)
    private val quickBackPolicy = QuickBackPolicy(appContext.filesDir.path, preferences, siteConfiguration)
    val bridgeSecret: String = java.util.UUID.randomUUID().toString()

    private val tabs = LinkedHashMap<Long, BrowserTab>()
    private val controllers = LinkedHashMap<Long, PageController>()
    /** One QuickBack segment. [controller] is null while the segment is released to [state]. */
    private class PageState(var state: Bundle = Bundle(), var controller: PageController? = null,
        var lastActiveAt: Long = 0L,
        // t4.b.j: freeze the old page's forward boundary when another segment is retained.
        var nativeForwardAllowance: Int = Int.MAX_VALUE)
    private class TabHistory(val pages: MutableList<PageState>, var current: Int, original: SessionTab? = null) {
        val saved = PendingSessionSnapshot.SavedRow(original)
    }
    private val histories = LinkedHashMap<Long, TabHistory>()
    private var selectedId: Long? = null
    // c8.ua.u/v: only the most recent eligible single close can be recovered
    // by Forward, and only until a later explicit creation/title/forward event.
    private var closedTabGeneration = 0
    private var recoverableClosedTabId: String? = null
    val canRecoverClosedTab: Boolean get() = !recoverableClosedTabId.isNullOrEmpty()

    fun beginClosedTabWrite(): Int = ++closedTabGeneration
    fun completeClosedTabWrite(generation: Int, sessionId: String) {
        if (generation == closedTabGeneration) recoverableClosedTabId = sessionId
    }
    fun clearClosedTabRecovery() { recoverableClosedTabId = null; closedTabGeneration++ }
    fun consumeClosedTabRecovery(): String? {
        val sessionId = recoverableClosedTabId?.takeIf(String::isNotEmpty) ?: return null
        clearClosedTabRecovery()
        return sessionId
    }

    val selected: BrowserTab?
        get() = selectedId?.let(tabs::get)
    val size: Int
        get() = tabs.size
    val all: List<BrowserTab>
        get() = tabs.values.toList()

    private fun newController(tab: () -> BrowserTab, url: String?) = PageController(appContext, preferences, callbacksFor(tab),
        siteConfiguration, filterEngine, scripts, userAgentForId, bridgeSecret,
        allowBlockedPage = { DocumentPolicy.host(it) in allowedBlockedPageHosts }, initialUrl = url)

    /** Creates the first tab after the activity has installed its host callbacks. */
    fun ensureInitialTab(): BrowserTab = selected ?: createTab(preferences.home, select = true, clearClosedTabRecovery = false)

    fun createTab(
        url: String = preferences.home,
        select: Boolean = true,
        savedState: Bundle? = null,
        loadInitialUrl: Boolean = true,
        insertIndex: Int = tabs.size,
        clearClosedTabRecovery: Boolean = true,
    ): BrowserTab {
        if (clearClosedTabRecovery) this.clearClosedTabRecovery()
        val id = savedState?.let(BrowserTab::stateId) ?: BrowserTab.nextId()
        BrowserTab.observeId(id)
        // Via's privacy is global plus SiteConf, not a permanent property of a tab.
        lateinit var tab: BrowserTab
        val controller = newController({ tab }, url)
        tab = BrowserTab(id, controller.page, url)
        val index = insertIndex.coerceIn(0, tabs.size)
        if (index == tabs.size) tabs[id] = tab else {
            val ordered = tabs.values.toMutableList().apply { add(index, tab) }
            tabs.clear(); ordered.forEach { tabs[it.id] = it }
        }
        controllers[id] = controller
        histories[id] = TabHistory(mutableListOf(PageState(controller = controller)), 0)
        host.onPageCreated(tab)
        if (select || selectedId == null) {
            val previous = selectedId?.let(tabs::get)
            if (previous?.id != id) previous?.let { controllers[it.id]?.deactivate() }
            selectedId = id
            controller.activate()
        }
        if (savedState != null) {
            fun restoreSavedTab() {
                val restored = tab.restoreState(savedState)
                if (restored) controller.reloadPreferences()
                if (!restored && loadInitialUrl) navigate(tab, tab.requestedUrl)
                tab.restoreScroll(savedState, afterReload = !restored)
                savedState.getBundle(KEY_ORIGINAL_SESSION)?.let { source ->
                    val history = historyFrom(source)
                    if (history.pages.isNotEmpty()) {
                        history.pages[history.current].controller = controller
                        PageColorSampler.restoreColor(controller.page, history.pages[history.current].state.getInt("COLOR", 0))
                        histories[id] = history
                    }
                }
            }
            val restoreUrl = savedState.getString("requested_url", url)
            if (!host.onBeforeRestore(tab, restoreUrl) { if (tab.id in tabs) restoreSavedTab() }) restoreSavedTab()
        } else if (loadInitialUrl) {
            navigate(tab, url)
        }
        listeners.toList().forEach { it.onTabInserted(all.indexOf(tab), tab) }
        return tab
    }

    /** c8.s6.ea/f5 + ua.i1: only an accepted request creates a selected blank tab. */
    fun createPopupWindow(request: PopupRequest) {
        val opener = selected
        if (!request.canAttach || opener == null) { request.deny(); return }
        val referer = opener.url
        val popup = createTab("", select = true,
            loadInitialUrl = false, insertIndex = all.indexOf(opener) + 1)
        host.onPopupCreated(opener, popup)
        popup.page.view.post {
            controllers.getValue(popup.id).preparePopupWindow(referer)
            request.attach(popup.page)
        }
    }

    fun select(id: Long): BrowserTab? {
        if (!tabs.containsKey(id)) return null
        val previous = selectedId?.let(tabs::get)
        if (previous?.id != id) previous?.let { controllers[it.id]?.deactivate() }
        selectedId = id
        val tab = tabs[id]
        tab?.let { controllers[id]?.activate() }
        if (tab != null && previous?.id != id) {
            val to = all.indexOfFirst { it.id == id }
            val from = all.indexOfFirst { it.id == previous?.id }
            listeners.toList().forEach { it.onTabSelected(from, to) }
        }
        return tab
    }

    fun selectAt(index: Int): BrowserTab? = all.getOrNull(index)?.let { select(it.id) }

    /** c8.s6's L0.p: move a tab in the ordered tab list, retaining its id and
     * selected state while notifying the host through the next chrome refresh. */
    fun move(from: Int, to: Int): Boolean {
        if (from !in 0 until tabs.size || to !in 0 until tabs.size || from == to) return false
        val ordered = tabs.values.toMutableList()
        val moved = ordered.removeAt(from)
        ordered.add(to, moved)
        tabs.clear()
        ordered.forEach { tabs[it.id] = it }
        listeners.toList().forEach { it.onTabsMoved(from, to, all.indexOfFirst { tab -> tab.id == selectedId }) }
        return true
    }

    /** Newest resource first; the original resource/log page displays at most 64 requests. */
    fun resources(tab: BrowserTab): List<BrowserResource> = controllers[tab.id]?.resources().orEmpty()
    fun hasMediaResources(tab: BrowserTab?): Boolean = tab != null && controllers[tab.id]?.hasMediaResources() == true
    fun clearResources(tab: BrowserTab) { controllers[tab.id]?.clearResources() }
    fun isResourceBlocked(url: String): Boolean = filterEngine.shouldBlock(ResourceDocumentActions.filterRequest(url))
    /** c8.ua.n0/q: bypass only the main-page blocking response, for the lifetime of this browser presenter. */
    fun allowBlockedPage(url: String) { DocumentPolicy.host(url).takeIf(String::isNotEmpty)?.let { allowedBlockedPageHosts = allowedBlockedPageHosts + it } }

    /** c8.ua.p1/c2: called when the browser is hidden or paused, before pausing its pages. */
    fun flushFilterStatistics() = filterStatistics.flush()

    fun injectDocumentPhase(pageId: Int, runAt: Int): Boolean {
        val tab = tabs.values.firstOrNull { it.page.id == pageId } ?: return false
        return controllers[tab.id]?.injectDocumentPhase(runAt) == true
    }

    fun scriptMenuState(tab: BrowserTab, callback: (String) -> Unit) {
        tab.page.evaluate(scripts.menuStateSource(), callback)
    }

    fun executeScriptMenu(tab: BrowserTab, scriptId: String, name: String) {
        tab.page.evaluate(scripts.executeMenuSource(scriptId, name))
    }

    fun navigate(tab: BrowserTab, input: String, referer: String? = null, localNetworkChecked: Boolean = false): String? {
        if (tab.id !in tabs) return null
        val target = UrlResolver.resolveInput(input, preferences.effectiveSearchUrl()) ?: return null
        if (!localNetworkChecked && host.onBeforeNavigate(tab, target) { if (tab.id in tabs) navigateResolved(tab, target, referer) }) return target
        return navigateResolved(tab, target, referer)
    }

    private fun navigateResolved(tab: BrowserTab, target: String, referer: String?): String {
        if (UrlResolver.isInternal(target) || target.equals(UrlResolver.HOME, ignoreCase = true)) {
            tab.update(target)
            host.onInternalUrl(tab, target)
            return target
        }
        val page = tab.page
        val source = page.url
        if (target == source) {
            // r4.d.v reloads an identical explicit URL without creating/discarding segments.
            controllers[tab.id]?.reloadPreferences(target)
            page.reload()
            return target
        }
        if (target.startsWith("javascript:", true)) {
            page.load(target)
            return target
        }
        if (!source.isNullOrEmpty() && quickBackPolicy.shouldRetain(source, target, automatic = false)) {
            // r4.d.v passes e.a, so an explicit URL does not inherit the old Referer.
            retainPage(tab, target, referer = null)
        } else {
            tab.update(target)
            controllers[tab.id]?.load(target, referer)
            histories.getValue(tab.id).pages[histories.getValue(tab.id).current].nativeForwardAllowance = 0
            discardForwardPages(tab)
        }
        return target
    }

    /** Generated file loads take the same N decision after the logical about:/v: action resolves. */
    fun loadInternalPage(tab: BrowserTab, documentUrl: String, logicalUrl: String = tab.requestedUrl) {
        if (tab.id !in tabs) return
        val source = tab.page.url
        if (source == documentUrl) {
            tab.requestedUrl = logicalUrl
            tab.internalDocumentUrl = documentUrl
            controllers[tab.id]?.reloadPreferences(documentUrl)
            tab.page.reload()
            return
        }
        if (!source.isNullOrEmpty() && quickBackPolicy.shouldRetain(source, documentUrl, automatic = false)) {
            retainPage(tab, documentUrl, referer = null, logicalUrl = logicalUrl)
        } else {
            tab.requestedUrl = logicalUrl
            tab.internalDocumentUrl = documentUrl
            controllers[tab.id]?.load(documentUrl)
            histories.getValue(tab.id).pages[histories.getValue(tab.id).current].nativeForwardAllowance = 0
            discardForwardPages(tab)
        }
    }

    /** r4.d.H/S: retain the old page, discard its obsolete future, then select a fresh page. */
    private fun retainPage(tab: BrowserTab, target: String, referer: String?, logicalUrl: String? = null,
        freezePreviousForward: Boolean = false) {
        val history = histories.getValue(tab.id)
        val oldPage = history.pages[history.current]
        if (freezePreviousForward) oldPage.nativeForwardAllowance = 0 // H(..., 0) calls the old view's o().
        discardForwardPages(tab)
        val controller = newController({ tab }, target)
        tab.page.stopLoading()
        controllers[tab.id]?.deactivate()
        oldPage.lastActiveAt = android.os.SystemClock.elapsedRealtime()
        history.pages += PageState(controller = controller)
        history.current = history.pages.lastIndex
        tab.page = controller.page
        tab.title = controller.page.title.orEmpty()
        controllers[tab.id] = controller
        host.onPageCreated(tab)
        if (logicalUrl != null) {
            tab.requestedUrl = logicalUrl
            tab.internalDocumentUrl = target
        } else tab.update(target)
        controller.load(target, referer)
        controller.activate()
        releaseDistantPages(history)
        host.onCurrentPageChanged(tab)
        notifyTabChanged(tab)
    }

    fun close(id: Long, allowLast: Boolean = false): Boolean {
        val tab = tabs[id] ?: return false
        if (tabs.size == 1 && !allowLast) return false
        val index = all.indexOfFirst { it.id == id }
        val wasSelected = selectedId == id
        tabs.remove(id)
        destroyTab(tab)
        if (selectedId == id) selectedId = tabs.keys.lastOrNull()
        selectedId?.let { controllers[it] }?.activate()
        if (wasSelected) {
            // o4.c.b re-selects through j(), which fires the r4.f Y event
            // before the H removal event.
            val to = all.indexOfFirst { it.id == selectedId }
            listeners.toList().forEach { it.onTabSelected(index, to) }
        }
        if (index >= 0) listeners.toList().forEach { it.onTabRemoved(index) }
        host.onWindowClosed(tab)
        return true
    }

    fun pause() = tabs.values.forEach { controllers[it.id]?.pause() }

    fun resume() { selected?.let { controllers[it.id]?.resume() } }

    fun reloadPreferences() {
        dev.ujhhgtg.via.browser.filter.FilterRuntime.get(appContext, preferences.appFlags and 64 != 0)
        tabs.values.forEach { controllers[it.id]?.reloadPreferences(it.internalDocumentUrl ?: it.url) }
    }

    /** ua.v1 updates only the page edited through the browser's site-settings entry. */
    fun reloadSitePreferences(tab: BrowserTab) {
        controllers[tab.id]?.reloadPreferences(tab.page.url ?: tab.url)
        tab.page.reload()
    }

    /** c8.ua.n1(false,true): rebind the active page of each resident tab. */
    fun applyNightTheme(night: Boolean) {
        tabs.keys.forEach { id -> controllers[id]?.applyNightTheme(night) }
    }

    /**
     * Writes the tab state into [out]. [includeEngineState] = false keeps shell metadata only:
     * the activity's instance state travels through a size-limited Binder transaction, so the
     * browser writes this bundle to a file and puts the metadata-only copy into that Bundle
     * (see BrowserInstanceState).
     */
    fun saveState(out: Bundle = Bundle(), includeEngineState: Boolean = true): Bundle = out.apply {
        val ids = tabs.keys.toLongArray()
        putLongArray(KEY_IDS, ids)
        putLong(KEY_SELECTED, selectedId ?: -1L)
        ids.forEach { id ->
            tabs[id]?.let { tab ->
                putBundle(KEY_TAB_PREFIX + id, tab.saveState(includeEngineState).apply {
                    if (includeEngineState) putBundle(KEY_ORIGINAL_SESSION, captureOriginalState(tab))
                })
            }
        }
    }

    /** Capture on the UI thread, then call writeFiles on the persistence worker (c8.ua.N1). */
    fun captureSessionSnapshot(now: Long = System.currentTimeMillis()): PendingSessionSnapshot = PendingSessionSnapshot(
        SessionState.directory(appContext), all.filter { SessionState.acceptsUrl(appContext, it.url) }.map { tab ->
            val saved = histories.getValue(tab.id).saved
            PendingSessionSnapshot.Entry(SessionTab(tab.sessionId, tab.url, tab.title, saved.value?.filePath,
                privacyPolicy.openSessionFlags(tab.url, tab.id == selectedId), now), captureOriginalState(tab), saved)
        })

    /** c8.ua.K1: a closed row is retained only for a recordable, non-generated URL. */
    fun captureClosedSession(tab: BrowserTab, now: Long = System.currentTimeMillis()): PendingSessionSnapshot? {
        if (!SessionState.acceptsUrl(appContext, tab.url) || !privacyPolicy.mayRecord(tab.url)) return null
        val saved = histories.getValue(tab.id).saved
        val row = SessionTab(tab.sessionId, tab.url, tab.title, saved.value?.filePath,
            (saved.value?.flags ?: 0) and 7.inv(), now)
        return PendingSessionSnapshot(SessionState.directory(appContext),
            listOf(PendingSessionSnapshot.Entry(row, captureOriginalState(tab), saved)))
    }

    /** c8.ua.N1/G keeps a UUID per persisted tab and flag 4 identifies the selected tab. */
    fun restoreSession(rows: List<SessionTab>) {
        tabs.values.forEach(::destroyTab)
        tabs.clear()
        selectedId = null
        val restored = rows.map { restoreSessionTab(it, select = false) }
        if (restored.isEmpty()) ensureInitialTab()
        else select(restored[rows.indexOfFirst { it.flags and 4 != 0 }.coerceAtLeast(0)].id)
    }

    /** ua.F1/na.g: append a selected closed session without replacing the remaining tabs. */
    fun restoreSessionTab(row: SessionTab, select: Boolean = true, original: Bundle? = SessionState.read(row.filePath)): BrowserTab {
        // Row bit 1 records the policy at save time; ua.o0 recomputes it on restore.
        val tab = createTab(row.url ?: preferences.home, select = select, loadInitialUrl = false,
            clearClosedTabRecovery = false)
        tab.sessionId = row.id
        tab.title = row.title.orEmpty()
        val history = original?.let { historyFrom(it, row) }
        if (original == null || history == null || history.pages.isEmpty()) {
            histories[tab.id]?.let { existing -> histories[tab.id] = TabHistory(existing.pages, existing.current, row) }
            navigate(tab, row.url ?: preferences.home)
        } else {
            histories[tab.id] = history
            val page = history.pages[history.current]
            page.controller = controllers[tab.id]
            tab.requestedUrl = original.getString("URL", row.url ?: preferences.home)
            tab.title = original.getString("TITLE", row.title.orEmpty())
            val restoreUrl = page.state.getString("url") ?: tab.requestedUrl
            if (!host.onBeforeRestore(tab, restoreUrl) { if (tab.id in tabs) restorePage(tab, page) }) restorePage(tab, page)
        }
        return tab
    }

    fun restoreState(state: Bundle?) {
        if (state == null) return
        tabs.values.forEach(::destroyTab)
        tabs.clear()
        selectedId = null
        val ids = state.getLongArray(KEY_IDS) ?: LongArray(0)
        ids.forEach { id -> state.getBundle(KEY_TAB_PREFIX + id)?.let { createTab(it.getString("requested_url", preferences.home), select = false, savedState = it) } }
        if (tabs.isEmpty()) createTab(preferences.home, select = true)
        else select(state.getLong(KEY_SELECTED, ids.firstOrNull() ?: -1L))
    }

    fun destroy() {
        tabs.values.forEach(::destroyTab)
        tabs.clear()
        selectedId = null
        scripts.close()
    }

    /** r4.d.j/n/M: engine history takes priority, then move between the saved page segments. */
    fun canGoBack(tab: BrowserTab? = selected): Boolean = tab != null &&
        (tab.page.canGoBack || (histories[tab.id]?.current ?: 0) > 0)

    fun canGoForward(tab: BrowserTab? = selected): Boolean = tab != null && histories[tab.id]?.let { history ->
        history.pages[history.current].nativeForwardAllowance > 0 && tab.page.canGoForward || history.current + 1 < history.pages.size
    } == true

    fun goBack(tab: BrowserTab? = selected): Boolean = moveInHistory(tab, -1)
    fun goForward(tab: BrowserTab? = selected): Boolean = moveInHistory(tab, 1).also { moved ->
        if (moved) clearClosedTabRecovery()
    }

    private fun moveInHistory(tab: BrowserTab?, direction: Int): Boolean {
        if (tab == null) return false
        val current = tab.page
        val history = histories[tab.id] ?: return false
        val currentPage = history.pages[history.current]
        if (direction < 0 && current.canGoBack) {
            if (currentPage.nativeForwardAllowance < Int.MAX_VALUE) currentPage.nativeForwardAllowance++
            current.goBack()
            return true
        }
        if (direction > 0 && currentPage.nativeForwardAllowance > 0 && current.canGoForward) {
            currentPage.nativeForwardAllowance--
            current.goForward()
            return true
        }
        val next = history.current + direction
        if (next !in history.pages.indices) return false
        controllers[tab.id]?.deactivate()
        history.pages[history.current].lastActiveAt = android.os.SystemClock.elapsedRealtime()
        history.current = next
        val page = history.pages[next]
        val cached = page.controller
        if (cached == null) {
            val controller = newController({ tab }, page.state.getString("url"))
            page.controller = controller
            page.nativeForwardAllowance = Int.MAX_VALUE
            tab.page = controller.page
            controllers[tab.id] = controller
            host.onPageCreated(tab)
            val restoreUrl = page.state.getString("url") ?: tab.requestedUrl
            if (!host.onBeforeRestore(tab, restoreUrl) { if (tab.id in tabs) restorePage(tab, page) }) restorePage(tab, page)
        } else {
            tab.page = cached.page
            controllers[tab.id] = cached
            tab.update(cached.page.url ?: page.state.getString("url"), cached.page.title.orEmpty())
        }
        controllers[tab.id]?.activate()
        releaseDistantPages(history)
        host.onCurrentPageChanged(tab)
        notifyTabChanged(tab)
        return true
    }

    private fun restorePage(tab: BrowserTab, page: PageState) {
        val controller = page.controller ?: return
        val sourceUrl = page.state.getString("url") ?: tab.requestedUrl
        tab.update(url = sourceUrl)
        val restored = SessionState.restore(controller.page, page.state) { controller.load(it) }
        // t4.c.k -> r4.d.U -> e8.i.B uses the actual restored history URL.
        if (!restored.isNullOrEmpty()) controller.reloadPreferences(restored)
        tab.update(controller.page.url, controller.page.title.orEmpty())
    }

    @Suppress("DEPRECATION")
    private fun historyFrom(source: Bundle, original: SessionTab? = null): TabHistory {
        val entries = if (android.os.Build.VERSION.SDK_INT >= 33) source.getParcelableArray("LIST", Bundle::class.java)
            else source.getParcelableArray("LIST")?.map { it as? Bundle }?.toTypedArray()
        val selected = source.getInt("CUR", -1)
        var current = selected
        val pages = mutableListOf<PageState>()
        entries?.forEachIndexed { index, state ->
            if (index == selected) current = pages.size
            if (state != null) pages += PageState(Bundle(state))
        }
        return TabHistory(pages, current.coerceIn(0, (pages.size - 1).coerceAtLeast(0)), original)
    }

    /** r4.d.b keeps six segments before CUR and three after it in the persisted LIST. */
    private fun captureOriginalState(tab: BrowserTab): Bundle {
        val history = histories.getValue(tab.id)
        val start = maxOf(0, history.current - 6)
        val end = minOf(history.current + 4, history.pages.size)
        val pages = history.pages.subList(start, end).map { page ->
            page.controller?.let { page.state = SessionState.capture(it.page) }
            Bundle(page.state)
        }
        return Bundle().apply {
            putString(SessionState.KEY_ENGINE, Engines.backend.id)
            putString("TITLE", tab.title)
            putString("URL", tab.url)
            putParcelableArray("LIST", pages.toTypedArray())
            putInt("CUR", history.current - start)
        }
    }

    private fun discardForwardPages(tab: BrowserTab) {
        val history = histories[tab.id] ?: return
        while (history.pages.size > history.current + 1) {
            val page = history.pages.removeAt(history.pages.lastIndex)
            page.controller?.destroy()
        }
    }

    /** r4.d.O/Q keeps the nearby three-back/two-forward pages live for at most five minutes. */
    private fun releaseDistantPages(history: TabHistory) {
        val now = android.os.SystemClock.elapsedRealtime()
        history.pages.forEachIndexed { index, page ->
            if (index == history.current) return@forEachIndexed
            val controller = page.controller ?: return@forEachIndexed
            if (index in history.current - 3..history.current + 2 && now - page.lastActiveAt <= 300_000L) return@forEachIndexed
            page.state = SessionState.capture(controller.page)
            controller.destroy()
            page.controller = null
        }
    }

    private fun destroyTab(tab: BrowserTab) {
        val history = histories.remove(tab.id)
        if (history != null) history.pages.forEach { page -> page.controller?.destroy() }
        else controllers[tab.id]?.destroy() ?: tab.page.destroy()
        controllers.remove(tab.id)
    }

    private fun callbacksFor(tabProvider: () -> BrowserTab): PageController.Callbacks = object : PageController.Callbacks {
        override fun onRequestBlocked(url: String) = filterStatistics.record(url)
        override fun onReaderCheckRequested() = host.onReaderCheckRequested()
        private fun tab() = tabProvider()
        override fun onResourceAvailabilityChanged(page: EnginePage, hasMedia: Boolean) {
            if (tab().page === page) host.onResourceAvailabilityChanged(tab(), hasMedia)
        }
        override fun onPageStarted(page: EnginePage, url: String) {
            if (tab().page !== page) return
            tab().update(url = url)
            if ((url.startsWith("http://") || url.startsWith("https://")) &&
                url.substringBefore('?').lowercase().endsWith(".user.js")) {
                host.onUserScript(tab(), url)
            }
            host.onPageStarted(tab(), tab().displayUrl(url))
        }
        override fun onVisited(page: EnginePage, url: String): Boolean = host.onVisited(tab(), url)
        override fun getVisited(page: EnginePage, urls: Array<String>): BooleanArray =
            host.getVisited(tab(), urls)
        override fun onPageFinished(page: EnginePage, url: String, title: String?) {
            if (tab().page !== page) return
            tab().update(url = url, title = title)
            host.onPageFinished(tab(), tab().displayUrl(url), title)
        }
        override fun onReceivedTitle(page: EnginePage, title: String) {
            if (tab().page !== page) return
            tab().update(title = title)
            // e8.n0.w -> s6.g0 clears ua.v on titles from any non-home document,
            // including a background tab; the generated homepage is exempt.
            val document = page.url.orEmpty().substringBefore('?').substringBefore('#')
            val files = "file://${appContext.filesDir.path}/"
            if (document != "${files}homepage.html" && document != "${files}homepage2.html") clearClosedTabRecovery()
            host.onTitleChanged(tab(), title)
        }
        override fun onReceivedIcon(page: EnginePage, icon: android.graphics.Bitmap?) {
            if (tab().page !== page) return
            host.onIconChanged(tab(), icon)
        }
        override fun onReceivedTouchIconUrl(page: EnginePage, url: String) {
            if (tab().page === page) host.onTouchIconChanged(tab(), url)
        }
        override fun onProgressChanged(page: EnginePage, progress: Int) {
            if (tab().page === page) host.onProgressChanged(tab(), progress)
        }
        override fun onDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, size: Long, body: InputStream?) =
            host.onDownload(tab(), url, userAgent, contentDisposition, mimeType, size, body)
        override fun onBridgeCommand(page: EnginePage, command: Int): Int = host.onBridgeCommand(tab(), command)
        override fun onBridgeDownload(page: EnginePage, url: String, name: String?, mime: String?) = host.onBridgeDownload(tab(), url, name, mime)
        override fun onBridgeMessage(page: EnginePage, token: String, json: String) = host.onBridgeMessage(tab(), token, json)
        override fun onBridgeRecord(page: EnginePage, url: String, mime: String?) = host.onBridgeRecord(tab(), url, mime)
        override fun onBridgeToast(page: EnginePage, text: String) = host.onBridgeToast(tab(), text)
        override fun onBridgeAddon(page: EnginePage, id: String) = host.onBridgeAddon(tab(), id)
        override fun onInstalledAddonIds(page: EnginePage): String = host.installedAddonIds(tab())
        override fun onNavigationRequest(page: EnginePage, url: String, isRedirect: Boolean, isPopup: Boolean): Boolean {
            if (host.onNavigationRequest(tab(), url)) return true
            // e8.i consumes a first-popup marker before N; handled schemes also precede N.
            if (isPopup || UrlResolver.isInternal(url) || UrlResolver.isExternalScheme(url) || url.startsWith("javascript:", true)) return false
            if (quickBackPolicy.shouldRetain(page.url, url, isRedirect)) {
                retainPage(tab(), url, referer = page.url, freezePreviousForward = true)
                return true
            }
            // r4.d.e(2): allow the native navigation, reset its forward limit and drop future segments.
            val history = histories.getValue(tab().id)
            history.pages[history.current].nativeForwardAllowance = Int.MAX_VALUE
            discardForwardPages(tab())
            return false
        }
        override fun onExternalUrl(page: EnginePage, url: String) = host.onExternalUrl(tab(), url)
        override fun onInternalUrl(page: EnginePage, url: String) = host.onInternalUrl(tab(), url)
        override fun onError(page: EnginePage, error: LoadError) = host.onError(tab(), error)
        override fun onHttpAuth(page: EnginePage, request: HttpAuthRequest) = host.onHttpAuth(tab(), request)
        override fun onSslError(page: EnginePage, request: SslErrorRequest) = host.onSslError(tab(), request)
        override fun onCreateWindow(source: EnginePage, request: PopupRequest) = host.onCreateWindow(tab(), request)
        override fun onCloseWindow(page: EnginePage) {
            tabs.values.firstOrNull { it.page === page }?.let { close(it.id) }
        }
        override fun onOpenScriptTab(page: EnginePage, url: String, active: Boolean, insert: Int) {
            // c8.s6.q.d uses an absolute insertion index, defaulting to after the selected tab.
            val index = if (insert in 0..tabs.size) insert else all.indexOfFirst { it.id == selectedId }.coerceAtLeast(0) + 1
            val created = createTab(url, select = active, loadInitialUrl = false)
            val ordered = all.filter { it.id != created.id }.toMutableList().apply { add(index.coerceAtMost(size), created) }
            tabs.clear()
            ordered.forEach { tabs[it.id] = it }
            navigate(created, url)
            listeners.toList().forEach { it.onTabsMoved(all.size - 1, ordered.indexOfFirst { tab -> tab.id == created.id }, ordered.indexOfFirst { tab -> tab.id == selectedId }) }
            host.onPopupCreated(tab(), created)
        }
        override fun onGeolocationPrompt(request: LocationRequest) = host.onGeolocationPrompt(tab(), request)
        override fun onAndroidPermissions(permissions: Array<String>, complete: (Boolean) -> Unit) = host.onAndroidPermissions(permissions, complete)
        override fun onGeolocationHidePrompt() = host.onGeolocationHidePrompt(tab())
        override fun onPermissionRequest(request: MediaPermissionRequest) = host.onPermissionRequest(tab(), request)
        override fun onPermissionRequestCanceled(request: MediaPermissionRequest) = host.onPermissionRequestCanceled(tab(), request)
        override fun onShowFullscreen(request: FullscreenRequest) = host.onShowFullscreen(tab(), request)
        override fun onHideFullscreen() = host.onHideFullscreen(tab())
        override fun onFormResubmission(page: EnginePage, request: FormResubmissionRequest) = host.onFormResubmission(tab(), request)
        override fun onFileChooser(page: EnginePage, request: FileChooserRequest): Boolean = host.onFileChooser(tab(), request)
    }

    companion object {
        private const val KEY_IDS = "tab_ids"
        private const val KEY_SELECTED = "selected_tab"
        private const val KEY_TAB_PREFIX = "tab_"
        private const val KEY_ORIGINAL_SESSION = "original_session"
    }
}

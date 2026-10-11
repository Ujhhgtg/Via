package dev.ujhhgtg.via

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.browser.AddressTitleFormatter
import dev.ujhhgtg.via.browser.BrowserIntentRouter
import dev.ujhhgtg.via.browser.BrowserTab
import dev.ujhhgtg.via.browser.InternalDocuments
import dev.ujhhgtg.via.browser.PageColorCache
import dev.ujhhgtg.via.browser.PageColorSampler
import dev.ujhhgtg.via.browser.PageScriptsDialogFragment
import dev.ujhhgtg.via.browser.TabController
import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.browser.script.ScriptInstaller
import dev.ujhhgtg.via.browser.script.ScriptManager
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.common.LocalNetworkAccess
import dev.ujhhgtg.via.common.ViaIntents
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.common.applicationIoScope
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.data.BookmarkItem
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.Favorite
import dev.ujhhgtg.via.data.FavoritesRepository
import dev.ujhhgtg.via.data.HistoryRepository
import dev.ujhhgtg.via.data.SessionRepository
import dev.ujhhgtg.via.data.SessionTab
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.data.SiteConfigurationRepository
import dev.ujhhgtg.via.downloads.DownloadCoordinator
import dev.ujhhgtg.via.downloads.DownloadRequest
import dev.ujhhgtg.via.downloads.DownloadsFragment
import dev.ujhhgtg.via.passwords.PasswordFormController
import dev.ujhhgtg.via.platform.CustomTabToolbar
import dev.ujhhgtg.via.reader.ReadAloudController
import dev.ujhhgtg.via.reader.ReadAloudDialog
import dev.ujhhgtg.via.reader.ReaderMode
import dev.ujhhgtg.via.records.RecordsBrowserView
import dev.ujhhgtg.via.search.SearchProvider
import dev.ujhhgtg.via.search.SearchProviders
import dev.ujhhgtg.via.search.UrlInputText
import dev.ujhhgtg.via.settings.SettingsController
import dev.ujhhgtg.via.sites.SiteSettingsFragment
import dev.ujhhgtg.via.skins.setSkinImageResource
import dev.ujhhgtg.via.sync.WebDavSyncRuntime
import dev.ujhhgtg.via.ui.AnchorTextMenu
import dev.ujhhgtg.via.ui.BrowserBackgrounds
import dev.ujhhgtg.via.ui.BrowserLayout
import dev.ujhhgtg.via.ui.BrowserMenu
import dev.ujhhgtg.via.ui.BrowserMenuChoices
import dev.ujhhgtg.via.ui.BrowserMenuDialog
import dev.ujhhgtg.via.ui.CompactBookmarksFragment
import dev.ujhhgtg.via.ui.HomeDocument
import dev.ujhhgtg.via.ui.NavigationBar
import dev.ujhhgtg.via.ui.SearchStrip
import dev.ujhhgtg.via.ui.SettingsBackgroundView
import dev.ujhhgtg.via.ui.TabSheetFragment
import dev.ujhhgtg.via.ui.ToolbarColorController
import dev.ujhhgtg.via.ui.ViaTabBar
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.WebsitePermissions
import dev.ujhhgtg.via.ui.WindowBackgroundDrawable
import dev.ujhhgtg.via.ui.behavior.BehaviorPreferences
import dev.ujhhgtg.via.ui.behavior.BrowserActions
import dev.ujhhgtg.via.ui.behavior.PageGestureController
import dev.ujhhgtg.via.ui.behavior.ToolbarSwipeLayout
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import dev.ujhhgtg.via.video.FullscreenVideoController
import dev.ujhhgtg.via.video.FullscreenVideoControls
import dev.ujhhgtg.via.video.VideoPictureInPicture
import dev.ujhhgtg.via.video.VideoScripts
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** e8.i.f / e8.i.g: generated ids of the selection toolbar's Search and Find entries. */
private const val SELECTION_SEARCH = 1
private const val SELECTION_FIND = 2

/** Browser fragment hosted by Shell, corresponding to original c8.s6. */
class BrowserFragment : Fragment(), BrowserMenuDialog.Host, dev.ujhhgtg.via.ui.FindInPageFragment.Host, dev.ujhhgtg.via.tools.AdMarkerFragment.Host {
    private val host: Shell get() = requireActivity() as Shell
    private lateinit var preferences: BrowserPreferences
    private lateinit var database: BrowserDatabase
    private lateinit var privacyPolicy: dev.ujhhgtg.via.browser.BrowserPrivacyPolicy
    private lateinit var bookmarks: BookmarkRepository
    private lateinit var history: HistoryRepository
    private lateinit var favorites: FavoritesRepository
    private lateinit var sessions: SessionRepository
    private lateinit var siteConfigurations: SiteConfigurationRepository
    private lateinit var passwordForms: PasswordFormController
    private lateinit var adMarker: dev.ujhhgtg.via.tools.AdMarker
    private lateinit var gameMode: dev.ujhhgtg.via.tools.GameModeController
    private var passwordAssist: View? = null
    private var customChrome: CustomTabToolbar? = null
    private val customMode: Boolean get() = host.intent.getBooleanExtra("CUSTOM_TAB", false)
    private lateinit var downloader: DownloadCoordinator
    private var networkMonitor: ConnectivityManager? = null
    private var networkAllowsImages = false // w9.p.b starts false until the first network callback.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onBrowserNetworkChanged()
        override fun onLost(network: Network) = onBrowserNetworkChanged()
    }
    private var downloadFeedback: dev.ujhhgtg.via.downloads.DownloadFeedback? = null
    private val downloadDestinations = dev.ujhhgtg.via.downloads.DownloadDestinationController(this) { downloadFeedback?.started() }
    private var readAloudController: ReadAloudController? = null
    private var readAloudTaskId: String? = null
    private var readAloudControls: dev.ujhhgtg.via.reader.ReadAloudFloatingControls? = null
    private val readAloudListener: (dev.ujhhgtg.via.reader.ReadAloudTask?) -> Unit = { task ->
        readAloudControls?.update(task, readAloudTaskId)
    }
    private lateinit var tabs: TabController
    /** Prompts, popups and tabs for WebExtensions; null on engines without them. */
    private var extensionHost: dev.ujhhgtg.via.extensions.ExtensionHost? = null

    /** Child overlays (f8.l0) register their r4.f listeners through this. */
    internal val tabController: TabController?
        get() = if (::tabs.isInitialized) tabs else null
    /** c8.f8: the fragment view; holds the blur image layer, unpadded so it reaches under the system bars. */
    private lateinit var frame: FrameLayout
    private lateinit var root: FrameLayout
    private lateinit var shell: LinearLayout
    private lateinit var browserHost: FrameLayout
    private lateinit var browserLayout: BrowserLayout
    private lateinit var tabStrip: ViaTabBar
    private lateinit var nativeBackground: WindowBackgroundDrawable
    private var browserBackgroundImage: SettingsBackgroundView? = null
    private lateinit var toolbarColors: ToolbarColorController
    private lateinit var pageColors: PageColorSampler
    private var toolbarControls: ToolbarColorController.Controls? = null
    private var currentBackgroundColor = 0
    /** Homepage background inputs that change how ToolbarColorController paints the cover; dimming toggle included. */
    private data class BackgroundDesign(val image: String?, val info: Int, val color: Int, val dimmingDisabled: Boolean)
    private var backgroundDesign: BackgroundDesign? = null
    private lateinit var gesturePreview: ImageView
    private var appFullscreen = false
    private lateinit var address: TextView
    private lateinit var addressRow: View
    private lateinit var navigationBar: NavigationBar
    private lateinit var progress: dev.ujhhgtg.via.ui.BrowserProgressBar
    private lateinit var reloadButton: ImageView
    private lateinit var snifferButton: dev.ujhhgtg.via.ui.ResourceSnifferButton
    private lateinit var siteInfoButton: ImageView
    private var resourceSourceTabId: Long? = null
    private val resourceDocumentActions by lazy { dev.ujhhgtg.via.browser.ResourceDocumentActions(host, preferences) }
    private val resourceImageActions by lazy {
        dev.ujhhgtg.via.browser.ResourceImageActions(host, worker) { uri -> decodeImageQrCode(uri.toString()) }
    }
    private var searchStrip: SearchStrip? = null
    private var customView: View? = null
    private var customCallback: dev.ujhhgtg.via.engine.FullscreenRequest? = null
    private var videoContainer: FrameLayout? = null
    private var videoControls: FullscreenVideoControls? = null
    /** c8.s6 H0/I0: rapid custom-view transitions reset the stored orientation. */
    private var videoShownAt = 0L
    private var rapidVideoHideCount = 0
    private val videoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val web = currentWebView() ?: return
            when (intent.action) {
                ViaIntents.ACTION_MEDIA_PLAY -> web.evaluate(VideoScripts.togglePlayback()) { raw ->
                    // c8.s6$n/e7 -> g2.f: rebuild PiP actions from the
                    // paused/playing state returned by the page.
                    VideoPictureInPicture.updateActions(host, raw.trim('"').toIntOrNull() ?: 0, true)
                }
                ViaIntents.ACTION_MEDIA_REWIND -> web.evaluate(VideoScripts.seekBy(-15), null)
                ViaIntents.ACTION_MEDIA_FASTFORWARD -> web.evaluate(VideoScripts.seekBy(15), null)
            }
        }
    }
    private var originalOrientation = 0
    private var fileCallback: dev.ujhhgtg.via.engine.FileChooserRequest? = null
    private var pendingPermission: dev.ujhhgtg.via.engine.MediaPermissionRequest? = null
    private var permissionResult: (() -> Unit)? = null
    private var nightApplied: Boolean? = null
    private var homeDocumentDirty = false
    private val webContentFilter = Color.TRANSPARENT.toDrawable()
    private var menuSettings: SettingsController? = null
    private var menuDialog: BrowserMenuDialog? = null
    private var appliedLanguage: String? = null
    private lateinit var behavior: BehaviorPreferences
    private val worker = Executors.newSingleThreadExecutor()
    private var pendingRequest = 0
    private val activityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (passwordForms.onActivityResult(pendingRequest, result.resultCode)) return@registerForActivityResult
        menuSettings?.onActivityResult(pendingRequest, result.resultCode, result.data)
    }
    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.complete(result.resultCode, result.data); fileCallback = null
    }
    private val runtimePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val result = permissionResult; permissionResult = null; result?.invoke()
    }
    private val httpAuthorization = mutableMapOf<String, String>()
    private var isPrivate = false
    private var restoring = false
    private var homepageSuggestions: kotlinx.coroutines.Job? = null
    private val remoteHomepageSuggestions = dev.ujhhgtg.via.search.RemoteSuggestions()
    private var toolbarSwipePreview = 0
    private var pageGesture: PageGestureController? = null
    private var pageGestureOrientation = android.content.res.Configuration.ORIENTATION_UNDEFINED

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun text(key: Int) = getString(key)
    private fun night(): Boolean {
        val scheme = host.intent.getIntExtra("CUSTOM_TAB_COLOR_SCHEME", 0)
        return if (customMode && scheme != 0) scheme == 2
        else preferences.isNightMode
    }
    private fun themedColor(attribute: Int): Int = host.obtainStyledAttributes(intArrayOf(attribute)).let {
        try { it.getColor(0, 0) } finally { it.recycle() }
    }
    private fun ink() = themedColor(R.attr.viaPrimaryTextColor)
    private fun background() = themedColor(R.attr.viaBackgroundColor)
    private fun label(value: String, size: Float = 16f) = TextView(host).apply { text = value; textSize = size; gravity = Gravity.CENTER_VERTICAL; setTextColor(ink()); typeface = preferences.selectedTypeface() }
    private fun toast(value: String) = ViaToast.makeText(host, value, ViaToast.LENGTH_SHORT).show()
    private fun current() = if (::tabs.isInitialized) tabs.selected else null
    private fun currentWebView() = current()?.page
    private fun isLocal(url: String) = url.startsWith("file://${host.filesDir.path}/") || url.startsWith("about:") ||
        url.startsWith("moz-extension:") || UrlResolver.isInternal(url)
    private fun visibleUrl(tab: BrowserTab?) = tab?.url?.takeUnless { isLocal(it) }.orEmpty()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        pendingRequest = state?.getInt("pending_request") ?: 0
        preferences = BrowserPreferences(host)
        GeneratedDocumentState.initialize(preferences)
        dev.ujhhgtg.via.skins.SkinResources.load(host, preferences.skin)
        behavior = BehaviorPreferences(preferences)
        appliedLanguage = preferences.language
        database = BrowserDatabase(host)
        bookmarks = BookmarkRepository(database); history = HistoryRepository(database)
        favorites = FavoritesRepository(database); sessions = SessionRepository(database)
        siteConfigurations = SiteConfigurationRepository(database)
        privacyPolicy = dev.ujhhgtg.via.browser.BrowserPrivacyPolicy(preferences, siteConfigurations::get)
        downloader = DownloadCoordinator.get(host)
        downloadFeedback = dev.ujhhgtg.via.downloads.DownloadFeedback(host, this, { isVisible }, ::showDownloads)
        passwordForms = PasswordFormController(host, ::launchForResult, ::showPasswordAssist)
        adMarker = dev.ujhhgtg.via.tools.AdMarker(host) { if (::tabs.isInitialized) reloadTabPreferences() }
        adMarker.presentPanel = ::showAdMarkerPanel
        adMarker.dismissPanel = {
            if (childFragmentManager.findFragmentByTag(dev.ujhhgtg.via.tools.AdMarkerFragment.TAG) != null) childFragmentManager.popBackStack()
        }
        gameMode = dev.ujhhgtg.via.tools.GameModeController(host) { enabled ->
            appFullscreen = enabled
            if (::browserLayout.isInitialized) {
                updateSearchStrip()
                if (enabled) {
                    WindowInsetsHelper.setFullscreen(host.window, true)
                    configureBrowserLayout(); browserLayout.hideToolbars()
                } else {
                    browserLayout.showToolbars()
                    WindowInsetsHelper.setFullscreen(host.window, preferences.appFlags and 1 != 0)
                    browserHost.postDelayed({ if (view != null) configureBrowserLayout() }, 200L)
                }
                updateBrowserBackCallback()
            }
        }
        host.requestedOrientation = preferences.resolvedScreenOrientation()
        parentFragmentManager.setFragmentResultListener("qr_scan", host) { _, result ->
            val value = result.getString("value").orEmpty()
            if (value.isNotEmpty()) {
                navigate(dev.ujhhgtg.via.tools.QrResults.scanTarget(value, preferences.effectiveSearchUrl(), getString(R.string.qr_code_scan_result)))
            }
        }
    }

    private var externalIntentReturnToCaller = false
    private val pendingIncomingRoutes = ArrayList<BrowserIntentRouter.Route>()

    /** s6.a0/ua.q1: pop the Shell stack, then select or insert the incoming target. */
    fun onNewIntent(intent: Intent) {
        menuDialog?.dismiss()
        val router = BrowserIntentRouter { preferences.effectiveSearchUrl() }
        if (router.belongsToBrowser(intent)) parentFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        router.resolve(intent, host.referrer, host.packageName)?.let { route ->
            if (::tabs.isInitialized) routeIncoming(route, coldStart = false) else pendingIncomingRoutes += route
        }
    }

    /** ua.C0 consumes its flag on every close, returning to the caller only for the last current non-internal page. */
    fun consumeExternalIntentClose(tab: BrowserTab): Boolean {
        val selected = current()
        val shouldReturn = externalIntentReturnToCaller && selected != null && selected.id == tab.id && tabs.size <= 1 &&
            !dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(host, selected.page.url ?: selected.url)
        externalIntentReturnToCaller = false
        return shouldReturn
    }

    /** ua.p1 clears the intent request after tab persistence / pause. */
    fun clearExternalIntentOrigin() { externalIntentReturnToCaller = false }

    private fun routeIncoming(route: BrowserIntentRouter.Route, coldStart: Boolean) {
        when (route.kind) {
            BrowserIntentRouter.Kind.COMMAND -> root.post { if (isAdded) dispatchBrowserCommand(route.target) }
            BrowserIntentRouter.Kind.EXTERNAL -> root.post { if (isAdded) dispatchBrowserCommand(route.target) }
            BrowserIntentRouter.Kind.MULTIPLE_URLS -> root.post { if (isAdded) showIncomingCandidates(route) }
            BrowserIntentRouter.Kind.PAGE -> {
                externalIntentReturnToCaller = route.returnToCaller
                val needsFile = route.target.startsWith("content:", true) || route.target.startsWith("folder://", true) || route.target.startsWith("history://", true)
                fun open(target: String) {
                    if (!isAdded || !::tabs.isInitialized) return
                    // ua.y only deduplicates HTTP(S), against each tab's current URL.
                    val duplicate = if (!coldStart && UrlResolver.isHttpUrl(target)) tabs.all.firstOrNull { it.url == target } else null
                    if (duplicate != null) tabs.select(duplicate.id)
                    else tabs.createTab(target, select = true, insertIndex = if (coldStart) tabs.size else tabs.indexOf(current()) + 1)
                    attachSelected(); updateChrome()
                }
                if (needsFile) worker.execute {
                    val target = incomingDocument(route.target)
                    host.runOnUiThread { open(target) }
                } else open(route.target)
            }
        }
    }

    /** ua.f / c1.y: content grants are copied into the original opened directory before WebView navigation. */
    private fun incomingDocument(target: String): String = when {
        target.startsWith("content:", true) -> runCatching {
            val uri = target.toUri()
            val displayName = host.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.takeIf(String::isNotEmpty) ?: (java.util.UUID.randomUUID().toString() + ".html")
            val directory = (host.getExternalFilesDir("opened") ?: File(host.filesDir, "opened")).apply { mkdirs() }
            val file = File(directory, displayName)
            requireNotNull(host.contentResolver.openInputStream(uri)).use { input -> file.outputStream().use { input.copyTo(it) } }
            Uri.fromFile(file).toString()
        }.getOrDefault(target)
        target.startsWith("folder://", true) -> {
            val folder = target.substring(9)
            InternalDocuments.write(host, preferences, database,
                if (folder.isEmpty()) InternalDocuments.Kind.BOOKMARKS else InternalDocuments.Kind.FOLDER, folder)
        }
        target.startsWith("history://", true) -> InternalDocuments.write(host, preferences, database, InternalDocuments.Kind.HISTORY)
        else -> target
    }

    /** s6.p/Y4: choices always insert a new tab; Open all preserves extracted order after the current tab. */
    private fun showIncomingCandidates(route: BrowserIntentRouter.Route) {
        val candidates = route.candidates
        if (candidates.isEmpty()) return
        fun openOne(url: String) { tabs.createTab(url, select = true, insertIndex = tabs.indexOf(current()) + 1); attachSelected() }
        ViaDialog(host).title(R.string.dialog_message).message(route.originalText)
            .items(candidates.toTypedArray(), onClick = { openOne(candidates[it]) })
            .positive(R.string.open_all) { _, _ ->
                val base = tabs.indexOf(current()) + 1
                candidates.forEachIndexed { index, target -> tabs.createTab(target, select = index == candidates.lastIndex, insertIndex = base + index) }
                attachSelected()
            }.negative(android.R.string.cancel).show()
    }

    /** ua.F1 restores requested session IDs by appending; it does not replace current tabs. */
    private fun restoreIncomingSessions(ids: List<String>, selected: String?) {
        worker.execute {
            val entries = ids.mapNotNull { id -> sessions.find(id)?.let { it to dev.ujhhgtg.via.browser.SessionState.read(it.filePath) } }
            host.runOnUiThread {
                if (!isAdded || entries.isEmpty()) return@runOnUiThread
                val shouldSelect = tabs.size == 0 || current() == null || entries.any { it.first.id == selected }
                val selectedIndex = entries.indexOfFirst { it.first.id == selected }.takeIf { it >= 0 } ?: entries.lastIndex
                val restored = entries.map { (row, state) -> tabs.restoreSessionTab(row, select = false, original = state) }
                if (shouldSelect) tabs.select(restored[selectedIndex].id)
                // pc.B1 -> ua.F1 clears the closed-session marker as each row is
                // reopened. Keeping it closed leaves the row in Records and
                // makes a second restore appear to do nothing.
                val selectedId = if (shouldSelect) entries.getOrNull(selectedIndex)?.first?.id else null
                entries.forEach { (row, _) ->
                    val flags = privacyPolicy.openSessionFlags(row.url, row.id == selectedId)
                    worker.execute { sessions.save(row.copy(flags = flags, lastVisitedAt = System.currentTimeMillis())) }
                }
                attachSelected()
            }
        }
    }

    /** ua.s1/h: native-generated documents replace an internal page, otherwise append a new tab. */
    private fun openInternalDocument(kind: InternalDocuments.Kind, folderId: String? = null) {
        worker.execute {
            val file = InternalDocuments.write(host, preferences, database, kind, folderId)
            host.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                val selected = current()
                if (selected != null && dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(host, selected.page.url ?: selected.url)) tabs.navigate(selected, file)
                else tabs.createTab(file, select = true)
                attachSelected()
            }
        }
    }

    /** s6.u, including command boundaries and parameters from z8.w2. */
    private fun dispatchBrowserCommand(raw: String): Boolean {
        val url = if (raw.startsWith("via://", true)) "v://" + raw.substring(6) else raw
        fun matches(prefix: String): Boolean = url.startsWith(prefix, true) &&
            (url.length == prefix.length || url[prefix.length] == '/' || url[prefix.length] == '?')
        fun query(marker: String): String? = url.indexOf(marker).takeIf { it >= 0 }?.let { start ->
            url.substring(start + marker.length).substringBefore('&')
        }
        fun decoded(value: String?) = value?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        val page = pageTypeOf(currentWebView()?.url)
        when {
            url.startsWith("folder://", true) -> {
                val folder = url.substring(9)
                if (page == 2 || page == 11) openInternalDocument(if (folder.isEmpty()) InternalDocuments.Kind.BOOKMARKS else InternalDocuments.Kind.FOLDER, folder)
                else showBookmarks(decoded(folder).orEmpty())
            }
            url.startsWith("history://", true) -> openInternalDocument(InternalDocuments.Kind.HISTORY)
            url.startsWith("v://", true) -> when {
                url.startsWith("v://error/jump?url=", true) -> decoded(query("url="))?.takeIf { it.isNotEmpty() && !it.startsWith("javascript:", true) }?.let(::navigate)
                url.startsWith("v://blocker/jump?url=", true) -> decoded(query("url="))?.takeIf(UrlResolver::isHttpUrl)?.let { target -> tabs.allowBlockedPage(target); navigate(target) }
                matches(UrlResolver.SCANNER) -> performMenuAction(25)
                matches("v://skins") -> host.navigate(dev.ujhhgtg.via.settings.SkinSettingsFragment())
                matches(UrlResolver.SEARCH) -> decoded(query("q="))?.takeIf(String::isNotEmpty)?.let {
                    recordSearchQuery(it)
                    navigate(UrlResolver.search(preferences.effectiveSearchUrl(), it))
                } ?: showAddressInput()
                matches(UrlResolver.HISTORY) -> if (page == 5) openInternalDocument(
                    InternalDocuments.Kind.HISTORY) else showHistory()
                matches(UrlResolver.DOWNLOADER) -> showDownloads()
                matches(UrlResolver.READ_ALOUD) -> ReadAloudDialog.show(childFragmentManager)
                matches(UrlResolver.BOOKMARKS) -> {
                    val folder = query("folder=")
                    if (page in setOf(2, 11, 5)) openInternalDocument(if (folder.isNullOrEmpty()) InternalDocuments.Kind.BOOKMARKS else InternalDocuments.Kind.FOLDER, folder)
                    else showBookmarks(folder.orEmpty())
                }
                url.startsWith("v://translator/translate?text=", true) -> showTextTranslation(decoded(query("text=")))
                url.startsWith("v://tabs", true) -> query("/restore?ids=")?.split(',')?.takeIf { it.isNotEmpty() }?.let { restoreIncomingSessions(it, query("selected=")) }
                matches("v://home") -> current()?.let { tab -> if (!isHome(tab.page.url ?: tab.url)) tabs.navigate(tab, preferences.home) }
                matches("v://about") -> openInternalDocument(InternalDocuments.Kind.ABOUT)
                matches("v://offline") -> openInternalDocument(InternalDocuments.Kind.SAVED_PAGES)
                matches("v://log") -> {
                    val source = tabs.all.firstOrNull { it.id == resourceSourceTabId }
                    val file = dev.ujhhgtg.via.browser.ResourceDocument(host, preferences).write(source?.let(tabs::resources).orEmpty(), false, night())
                    val selected = current()
                    if (selected != null && page > 0) tabs.navigate(selected, file)
                    else tabs.createTab(file, select = true, insertIndex = tabs.indexOf(selected) + 1)
                    attachSelected()
                }
                else -> openInternalDocument(InternalDocuments.Kind.CATALOG)
            }
            url.startsWith("thunder://") || url.startsWith("qqdl://") || url.startsWith("flashget://") -> {
                UrlResolver.unwrapDownloadScheme(url)?.let { target -> current()?.let { requestDownload(it, target, it.page.userAgent, "attachment", null, -1) } }
            }
            url.startsWith("baidubox://") || url.startsWith("baiduboxapp://") || url.startsWith("baiduboxlite://") -> Unit
            url.startsWith("file://", true) && url.substringBefore('?').endsWith(".pdf", true) -> host.navigate(dev.ujhhgtg.via.tools.PdfViewerFragment.newInstance(url.toUri()))
            UrlResolver.isExternalScheme(url) -> requestExternalApp(url)
            else -> return false
        }
        return true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        showBrowser(state)
        return frame
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        downloadDestinations.interceptPageDownload = ::interceptPageDownload
        val videoActions = IntentFilter().apply {
            addAction(ViaIntents.ACTION_MEDIA_PLAY)
            addAction(ViaIntents.ACTION_MEDIA_REWIND)
            addAction(ViaIntents.ACTION_MEDIA_FASTFORWARD)
        }
        if (Build.VERSION.SDK_INT >= 33) host.registerReceiver(videoReceiver, videoActions, Context.RECEIVER_NOT_EXPORTED)
        // c8.s6 passes RECEIVER_NOT_EXPORTED on API 33+ and has no flag to pass before it.
        else @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag") host.registerReceiver(videoReceiver, videoActions)
        // s6.V1 -> X8 / ab.b$c: the listener survives hidden settings pages and
        // is released with the browser Fragment, rather than on each pause.
        if (networkMonitor == null) {
            networkMonitor = host.applicationContext.getSystemService(ConnectivityManager::class.java)?.also {
                it.registerDefaultNetworkCallback(networkCallback)
            }
        }
        val popupBackStackListener = androidx.fragment.app.FragmentManager.OnBackStackChangedListener {
            if (childFragmentManager.backStackEntryCount == 0) onBrowserPopupClosed()
            updateBrowserBackCallback()
        }
        childFragmentManager.addOnBackStackChangedListener(popupBackStackListener)
        viewLifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                childFragmentManager.removeOnBackStackChangedListener(popupBackStackListener)
            }
        })
        parentFragmentManager.setFragmentResultListener("home_customization_changed", viewLifecycleOwner) { _, _ ->
            // d9.t only dirties the browser's document while its independent preview is visible.
            homeDocumentDirty = true
        }
        childFragmentManager.setFragmentResultListener(BrowserMenuDialog.RESULT, viewLifecycleOwner) { key, result ->
            val mark = result.getInt("mark")
            val flags = result.getInt("flags")
            if (mark != 0) {
                if (flags and 2 != 0) performMenuLongPress(mark, flags)
                else if (flags and 1 != 0) performMenuAction(mark, flags)
            }
            childFragmentManager.clearFragmentResult(key)
        }
        parentFragmentManager.setFragmentResultListener("input", viewLifecycleOwner) { key, result ->
            val input = result.getString("input").orEmpty()
            if (input.isNotEmpty()) {
                when {
                    result.getInt("input_action") == 2 -> {
                        val providers = SearchProviders(host, preferences, database)
                        val query = result.getString("input_query") ?: input
                        recordSearchQuery(query)
                        navigate(UrlResolver.search(providers.template(result.getInt("input_engine")), query))
                    }
                    input.startsWith("VIA-SWITCH-TAB:") -> input.substring(15).toLongOrNull()?.let { tabs.select(it); attachSelected() }
                    else -> navigate(input)
                }
            }
            parentFragmentManager.clearFragmentResult(key)
        }
        val back = object : OnBackPressedCallback(!isHidden) {
            override fun handleOnBackPressed() = goBack()
        }
        browserBackCallback = back
        host.onBackPressedDispatcher.addCallback(viewLifecycleOwner, back)
        updateBrowserBackCallback()
        // W8 -> ua.r0: the APK applies cleardataonexit2 on browser creation, before updating.
        dev.ujhhgtg.via.browser.BrowserDataCleaner(host, database).clearOnBrowserCreated()
        // c8.s6.V1 -> W8 -> ua.q0: subscriptions and userscripts share the startup update pass.
        val appContext = host.applicationContext
        worker.execute {
            dev.ujhhgtg.via.data.LegacyBookmarkMigration.migrate(database, preferences)
            // Network updates outlive the browser view, as the former worker's queued task did.
            applicationIoScope.launch {
                val filterStore = dev.ujhhgtg.via.browser.filter.FilterStore(appContext)
                val filtersUpdated = dev.ujhhgtg.via.browser.filter.FilterSubscriptionUpdater.updateDue(filterStore, preferences)
                val manager = ScriptManager(ScriptStore(appContext))
                try { manager.updateDue(preferences) } finally { manager.close() }
                if (filtersUpdated > 0) host.runOnUiThread { if (this@BrowserFragment.view != null) reloadTabPreferences() }
            }
            dev.ujhhgtg.via.extensions.ExtensionFiles.cleanup(appContext)
        }
        dev.ujhhgtg.via.engine.Engines.backend.extensions?.let { manager ->
            // Gecko's add-on manager runs on the main thread; updates that need new permissions prompt there.
            applicationIoScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                runCatching { dev.ujhhgtg.via.extensions.ExtensionUpdater.updateDue(manager, preferences) }
            }
        }
    }

    private var browserBackCallback: OnBackPressedCallback? = null

    /** s6.xb/Ta and n3.g: a single configured home yields Back to the system's exit animation. */
    private fun updateBrowserBackCallback() {
        if (!::tabs.isInitialized) return
        val predictive = resources.getBoolean(R.bool.enable_predictive_back) && !preferences.disablePredictiveBack &&
            listOf(Build.BRAND, Build.MANUFACTURER).none { it.equals("HONOR", true) || it.equals("HUAWEI", true) }
        val home = current()?.let { isHome(it.page.url ?: it.url) } == true
        val systemExit = predictive && isAdded && parentFragmentManager.backStackEntryCount == 0 &&
            childFragmentManager.backStackEntryCount == 0 && customView == null && !appFullscreen &&
            !customMode && tabs.size <= 1 && home
        browserBackCallback?.isEnabled = !isHidden && !systemExit
    }

    fun onKeyboardHiddenChanged(hidden: Boolean) {
        if (::browserLayout.isInitialized) browserLayout.setKeyboardVisible(!hidden)
    }

    /** CustomTab.I9: focus changes can reassert the stored fullscreen preference. */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (customMode && preferences.appFlags and 1 != 0 && hasFocus) WindowInsetsHelper.setFullscreen(host.window, true)
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean) {
        super.onPictureInPictureModeChanged(inPip)
        videoControls?.setInPipMode(inPip)
        if (inPip) {
            // c8.s6.N1 hides the browser chrome containers while the Activity
            // owns the PiP surface.
            if (::browserLayout.isInitialized) {
                browserLayout.top.visibility = View.GONE
                browserLayout.bottom.visibility = View.GONE
            }
        } else {
            if (::browserLayout.isInitialized) {
                browserLayout.top.visibility = if (browserLayout.toolbarsShown) View.VISIBLE else View.GONE
                browserLayout.bottom.visibility = if (browserLayout.toolbarsShown) View.VISIBLE else View.GONE
            }
            // c8.s6.N1 calls the selected WebView's onPause on PiP exit so
            // Chromium rebinds its media surface on the next frame.
            currentWebView()?.pause()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        updateBrowserBackCallback()
        if (hidden) {
            // s6.G1(true) calls M1; hiding a Fragment does not itself call onPause.
            GeneratedDocumentState.flush(preferences)
            if (::tabs.isInitialized) { tabs.flushFilterStatistics(); tabs.pause() }
            menuDialog?.dismiss()
        }
        else if (::tabs.isInitialized) {
            // s6.G1(false) calls R1 -> o4.c.a -> r4.d.a -> WebView.onResume().
            // an Activity resumed while settings was visible left this WebView paused.
            tabs.resume()
            refreshCustomizedHome()
            refreshGeneratedDocuments()
            reloadTabPreferences()
            host.requestedOrientation = preferences.resolvedScreenOrientation()
            configureBrowserLayout()
            applyAppearance()
            updateReadAloudControls()
        }
    }

    private fun showBrowser(state: Bundle?) {
        browserBackgroundImage = null
        nativeBackground = BrowserBackgrounds.update(host, night())
        root = FrameLayout(host).apply {
            // c8.s6.w8/W5 on the f8 canvas: the canvas pads with status/nav
            // insets while the keyboard is closed; when it opens the canvas
            // keeps only the status-bar inset, and status/nav/ime are all
            // consumed so no child ever sees the ime inset (the content
            // height itself is driven by SoftInputAssistObserver).
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                if (!imeVisible) {
                    val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                    if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                        view.setPadding(status.left, 0, status.right, 0)
                    } else {
                        view.setPadding(0, status.top, 0, 0)
                    }
                }
                WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime(),
                        androidx.core.graphics.Insets.NONE)
                    .setVisible(WindowInsetsCompat.Type.ime(), false)
                    .build()
            }
        }
        frame = FrameLayout(host)
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        root.requestApplyInsets()
        shell = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        browserLayout = BrowserLayout(host)
        shell.addView(browserLayout, LinearLayout.LayoutParams(-1, 0, 1f))
        browserHost = browserLayout.content
        readAloudControls = dev.ujhhgtg.via.reader.ReadAloudFloatingControls(browserLayout.getChildAt(0) as ViewGroup) { action ->
            when (action) {
                1 -> readAloudController?.play()
                2 -> readAloudController?.pause()
                3 -> { readAloudController?.stop(); readAloudTaskId = null; readAloudControls?.update(null, null) }
                4 -> ReadAloudDialog.show(childFragmentManager)
            }
        }
        readAloudController?.addListener(readAloudListener)
        progress = browserLayout.progress
        val top = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        addressRow = top
        // The leading address-bar control has two distinct gestures in the
        // original n0 toolbar: a tap opens the inline website-details sheet,
        // while a long press opens the persisted search-engine picker.  Keep
        // those handlers on the control itself so the behavior is identical
        // when the address row is embedded in the bottom toolbar.
        siteInfoButton = icon(R.drawable.search, text(R.string.site_info)) { siteInfo() }
        siteInfoButton.setOnLongClickListener { showSearchEngineSelector(); true }
        top.addView(siteInfoButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        address = label(text(R.string.home)).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END; setOnClickListener { showAddressInput() }; setOnLongClickListener { showAddressMenu(it); true } }
        top.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        snifferButton = dev.ujhhgtg.via.ui.ResourceSnifferButton(host).apply {
            setOnClickListener {
                current()?.let { tab -> dev.ujhhgtg.via.browser.ResourceDocumentActions(host, preferences)
                    .playSelection(tabs.resources(tab), tab.title) { update(preferences.showSnifferButton,
                        hasMedia = false,
                        animated = true
                    ) } }
            }
            setOnLongClickListener { dismissBrowserOverlay(); showNetworkLog(true); true }
        }
        top.addView(snifferButton)
        reloadButton = icon(R.drawable.reload, text(R.string.operation_reload)) { currentWebView()?.let { if (it.progress < 100) it.stopLoading() else reloadPage(it) } }
        top.addView(reloadButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        if (customMode) {
            customChrome = CustomTabToolbar(host, host.intent, object : CustomTabToolbar.Host {
                override fun currentUrl() = current()?.url
                // s6.Va: i0.s, r9.k.e and p5.a.C (patterns of every non-negative script).
                override fun hasScripts(): Boolean {
                    val url = current()?.url
                    return preferences.scriptsEnabled && url != null && url.length > 6 && UrlResolver.isHttpUrl(url) &&
                        !CustomTabToolbar.scriptsBlocked(url) &&
                        ScriptStore(host).use { store -> store.list().any { it.id >= 0 && it.appliesTo(url) } }
                }
                // s6.T4 / s6.l5
                override fun onAction(action: String) {
                    val url = current()?.url
                    when (action) {
                        CustomTabToolbar.SHARE -> if (!url.isNullOrEmpty()) shareUrl(url)
                        CustomTabToolbar.BOOKMARK -> editBookmark()
                        CustomTabToolbar.FIND -> showFind()
                        CustomTabToolbar.TRANSLATE -> showTranslation()
                        CustomTabToolbar.SCRIPTS -> if (!url.isNullOrEmpty()) showPageScripts()
                        CustomTabToolbar.COPY -> if (!url.isNullOrEmpty()) {
                            copy(url)
                            ViaToast.makeText(host, text(R.string.toast_copy_url_successful), ViaToast.LENGTH_SHORT).show()
                        }
                        // z8.b0.P
                        CustomTabToolbar.OPEN_IN_BROWSER -> if (!url.isNullOrEmpty()) {
                            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).setClass(host, Shell::class.java)
                                .putExtra(ViaIntents.EXTRA_INTENT, ViaIntents.MARKER_BROWSER))
                            host.finish()
                        }
                    }
                }
            })
        }
        navigationBar = NavigationBar(host)
        val bottom = navigationBar
        bottom.onItemClick = { index ->
            when (index) {
                0 -> browserBack()
                1 -> browserForward()
                2 -> navigate(preferences.home)
                3 -> showTabs()
                4 -> showMenu()
            }
        }
        bottom.onItemLongClick = { index -> performBrowserAction(behavior.longPressAction(index)); true }
        val swipe = ToolbarSwipeLayout(host, ::onToolbarSwipe)
        swipe.addView(bottom, FrameLayout.LayoutParams(-1, dp(48)))
        tabStrip = ViaTabBar(host).apply {
            // c8.s6 Q3: t.y(iB2, iB) with iB2 = m.f13636d/f13637e (subtle).
            setColors(themedColor(R.attr.viaSubtleColor))
            setCallbacks(object : ViaTabBar.Callbacks {
                override fun onTabSelected(index: Int) { tabs.selectAt(index); attachSelected() }
                override fun onTabClosed(index: Int) { tabs.all.getOrNull(index)?.let(::closeTab) }
                override fun onNewTab(view: View) { newTab(); navigationBar.bounceTabCount() }
                override fun onTabsMoved(from: Int, to: Int): Boolean = tabs.move(from, to)
                override fun onTabLongPress(view: View, index: Int): Boolean {
                    val tab = tabs.all.getOrNull(index) ?: return false
                    // The source tab strip only exposes destructive/duplicate
                    // actions for network pages.  Internal pages (home,
                    // bookmarks, history, etc.) retain the two global close
                    // actions and do not offer a meaningless copy/duplicate.
                    val networkPage = tab.url.startsWith("http://", true) || tab.url.startsWith("https://", true)
                    val actions = mutableListOf<Pair<Int, String>>()
                    if (tabs.size > 1) {
                        // IDs follow common/widget/t.z(): 4 = all, 1 = other.
                        actions += 4 to text(R.string.action_close_all_tabs)
                        actions += 1 to text(R.string.action_close_other_tabs)
                    }
                    if (networkPage) {
                        // IDs 3/2 close the logical left/right ranges. The
                        // source swaps their labels in RTL so the chooser
                        // still describes the side under the user's finger.
                        val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
                        if (index > 0) actions += (if (rtl) 2 else 3) to text(R.string.action_close_tabs_to_the_left)
                        if (index < tabs.size - 1) actions += (if (rtl) 3 else 2) to text(R.string.action_close_tabs_to_the_right)
                        actions += 6 to text(R.string.duplicate_tab)
                        actions += 5 to text(R.string.action_copy)
                    }
                    // common/widget/t.z() returns without opening a chooser
                    // when a single internal page has no applicable action.
                    if (actions.isEmpty()) return true
                    ViaDialog(host).items(actions.map { it.second }.toTypedArray(), onClick = { action ->
                        when (actions[action].first) {
                            4 -> closeAllTabs()
                            1 -> tabs.all.filter { it.id != tab.id }.toList().forEach(::closeTab)
                            2 -> closeTabsToRight(index)
                            3 -> closeTabsToLeft(index)
                            5 -> copy(tab.url)
                            6 -> newTab(tab.url)
                        }
                        attachSelected()
                    }).showAnchored(view)
                    return true
                }
            })
        }
        val strip: View = tabStrip
        browserLayout.setChrome(top, swipe, strip, customChrome) { view ->
            if (view == null) bottom.removeAddress()
            else {
                (view.parent as? ViewGroup)?.removeView(view)
                bottom.embedAddress(view)
            }
        }
        configureBrowserLayout()
        // f8.n/c8.b: the native image/filter/cover spans the browser canvas;
        // f8.n (top) stays transparent and Q7 colors f8.o (bottom) separately.
        browserLayout.top.setBackgroundColor(Color.TRANSPARENT)
        updateNativeBrowserBackground(night())
        toolbarColors = ToolbarColorController(host, preferences, ::night,
            hasBackgroundImage = { nativeBackground.hasImage }, isAttached = { isAdded },
            onControlsChanged = ::applyToolbarControls, onBackgroundFrame = ::applyToolbarBackground)
        pageColors = PageColorSampler(::night) { color -> toolbarColors.setPageColor(color, true) }
        toolbarColors.setPageColor(0, false)
        gesturePreview = ImageView(host).apply {
            setPadding(dp(20), dp(20), dp(20), dp(20)); setColorFilter(Color.WHITE)
            setBackgroundResource(R.drawable.dark_backdrop); elevation = dp(12).toFloat()
            visibility = View.INVISIBLE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(gesturePreview, FrameLayout.LayoutParams(dp(72), dp(60), Gravity.CENTER_VERTICAL).apply { marginStart = -dp(73) })
        browserLayout.setToolbarVisibilityListener { visible ->
            currentWebView()?.let { HomeDocument.updateGestureAvailability(it, visible) }
            // s6.U8 -> ab shows the floating button; s6.fb -> S8 hides it.
            if (visible) floatingToolbarButton?.hide(true) else showFloatingToolbarButton()
        }
        val settingsData = SettingsDataRepository(database)
        dev.ujhhgtg.via.engine.Engines.backend.extensions?.configure(preferences.extensionsEnabled, preferences.allowUnsignedExtensions)
        tabs = TabController(host, preferences, BrowserHost(), siteConfigurations::get) { id ->
            settingsData.find(id)?.takeIf { it.type == SettingsData.USER_AGENT }?.content
        }
        extensionHost = dev.ujhhgtg.via.engine.Engines.backend.extensions?.let { manager ->
            dev.ujhhgtg.via.extensions.ExtensionHost(this, manager, ExtensionTabs()).also { it.attach() }
        }
        // r4.f chrome-side flow (c8.s6): a tab insert closes any open browser
        // overlay (Z's R8) and bounces the tab counter once more than one tab
        // is open; a selection change closes the overlay as well.
        tabs.addListener(object : TabController.Listener {
            override fun onTabInserted(index: Int, tab: BrowserTab) {
                dismissBrowserOverlay()
                if (tabs.size > 1) navigationBar.bounceTabCount()
                if (::tabStrip.isInitialized && browserLayout.tabBarEnabled) tabStrip.onTabInserted(index, tab)
            }
            override fun onTabSelected(from: Int, to: Int) {
                dismissBrowserOverlay()
            }
            override fun onTabChanged(index: Int) {
                // r4.f.f: the strip row rebinds the moment the model reports
                // a favicon/title change.
                if (::tabStrip.isInitialized && browserLayout.tabBarEnabled) tabStrip.onTabChanged(index)
            }
            override fun onTabRemoved(index: Int) {
                if (::tabStrip.isInitialized && browserLayout.tabBarEnabled) tabStrip.onTabRemoved(index)
            }
            override fun onTabsMoved(from: Int, to: Int, selected: Int) {
                if (::tabStrip.isInitialized && browserLayout.tabBarEnabled) tabStrip.onTabsMoved(from, to)
            }
        })
        restoring = true
        val savedTabs = state?.getBundle("browser_tabs")
        if (savedTabs != null) tabs.restoreState(savedTabs)
        val incoming = BrowserIntentRouter { preferences.effectiveSearchUrl() }.resolve(host.intent, host.referrer, host.packageName)
        var askToRestore = emptyList<SessionTab>()
        if (tabs.size == 0 && !customMode) {
            val stored = sessions.listOpen()
            val hasRestorablePage = stored.any { isRestorableSessionUrl(it.url) }
            if (preferences.restoreClosedTabs == 1 && hasRestorablePage) restoreSession(stored)
            else if (preferences.restoreClosedTabs == 2 && hasRestorablePage) askToRestore = stored
        }
        // ua.Z0 concatenates session and launch pages; a default home exists only if neither emitted a page.
        if (tabs.size == 0 && incoming?.kind != BrowserIntentRouter.Kind.PAGE) tabs.ensureInitialTab()
        restoring = false
        attachSelected()
        incoming?.let { routeIncoming(it, coldStart = true) }
        pendingIncomingRoutes.toList().also { pendingIncomingRoutes.clear() }.forEach { routeIncoming(it, coldStart = false) }
        if (askToRestore.isNotEmpty()) {
            val saved = askToRestore
            // ViaToast drops messages while the window isn't shown, which is still the case during view creation.
            root.post {
                if (!isAdded) return@post
                ViaToast.show(host, text(R.string.restore_tabs_hint), ViaToast.LENGTH_LONG, text(android.R.string.ok)) {
                    restoreIncomingSessions(saved.map { it.id }, saved.firstOrNull { it.flags and 4 != 0 }?.id)
                }
            }
        }
        applyAppearance()
    }

    private fun restoreSession(rows: List<SessionTab>) {
        tabs.restoreSession(rows)
    }

    private fun persistRestorableSessions() {
        val snapshot = tabs.captureSessionSnapshot()
        worker.execute { sessions.replaceOpen(snapshot.writeFiles()) }
    }

    private fun isRestorableSessionUrl(url: String?): Boolean = dev.ujhhgtg.via.browser.SessionState.acceptsUrl(host, url)

    private fun configureBrowserLayout() {
        if (!::browserLayout.isInitialized) return
        val metrics = resources.displayMetrics
        val large = resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
        val diagonal = kotlin.math.hypot(metrics.widthPixels / metrics.xdpi.toDouble(), metrics.heightPixels / metrics.ydpi.toDouble())
        // c8.s6.Ka resolves and persists the automatic layout before settings reads appui2.
        if (!customMode && preferences.appUi !in 0..4) preferences.appUi = if (large && diagonal >= 7) 1 else 0
        browserLayout.configure(preferences.appUi, behavior.tabBarEnabled,
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE,
            preferTop = large && diagonal >= 7, customTab = customMode, appFullscreen = appFullscreen,
            fullscreenPreference = preferences.appFlags and 1 != 0, hideMode = preferences.fullScreenMode)
        updateBrowserBackCallback()
    }

    private fun updateTabStrip() {
        if (!::tabStrip.isInitialized || !browserLayout.tabBarEnabled) return
        tabStrip.submitTabs(tabs.all, current()?.id)
        tabStrip.scrollToSelected()
    }

    /** ua.Y1 consumes the page-settings notification after applying settings to resident tabs. */
    private fun reloadTabPreferences() {
        tabs.reloadPreferences()
        GeneratedDocumentState.clear(GeneratedDocumentState.PAGE_SETTINGS)
    }

    /** ab.b$c -> s6.D3/O5: resume waiting tasks and rebind network-dependent WebSettings. */
    private fun onBrowserNetworkChanged() {
        val activity = activity ?: return
        activity.runOnUiThread {
            if (!isAdded) return@runOnUiThread
            val manager = networkMonitor ?: return@runOnUiThread
            val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
            if (capabilities != null && downloader.hasPending()) {
                try {
                    // Original download.a.d uses PAUSE_ALL to start the pending queue.
                    dev.ujhhgtg.via.downloads.DownloadService.start(activity,
                        dev.ujhhgtg.via.downloads.DownloadService.ACTION_PAUSE_ALL)
                } catch (error: IllegalStateException) {
                    Log.w("ViaDownloads", "Cannot start pending downloads", error)
                }
            }
            // k5.t.a / b0.B: 0 = unavailable, 1 = unmetered, 2 = metered.
            // O5 allows the automatic-image policy for both 0 and 1.
            val allowImages = capabilities == null || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            if (networkAllowsImages != allowImages) {
                networkAllowsImages = allowImages
                if (::tabs.isInitialized) reloadTabPreferences() // ua.l1 -> n1(false,false), no page reload.
            }
        }
    }

    /** c8.s6.tb/sb: switch native chrome and loaded WebViews in place. */
    internal fun onNightThemeChanged(night: Boolean) {
        if (!::tabs.isInitialized) return
        val canvas = browserLayout.getChildAt(0)
        val animation = if (isVisible) android.animation.ObjectAnimator.ofFloat(canvas, View.ALPHA, .2f, 1f).apply {
            duration = 300L
            interpolator = android.view.animation.AccelerateInterpolator()
        } else null
        GeneratedDocumentState.mark(GeneratedDocumentState.ALL_DOCUMENTS)
        host.applyNightTheme(night)
        updateReadAloudControls(recreate = true)
        if (childFragmentManager.backStackEntryCount > 0) {
            onBrowserPopupClosed()
            childFragmentManager.popBackStack()
        }
        nightApplied = night
        toolbarColors.onThemeChanged(currentWebView()?.url?.let { pageTypeOf(it) > 0 } == true)
        updateNativeBrowserBackground(night)
        refreshCustomizedHome()
        refreshGeneratedDocuments()
        tabs.applyNightTheme(night)
        GeneratedDocumentState.clear(GeneratedDocumentState.PAGE_SETTINGS)
        if (passwordAssist != null) currentWebView()?.let { showPasswordAssist(it, true) }
        // c8.f8.setNightModeEnabled recreates the fixed two-colour progress drawable.
        progress.progressDrawable.constantState?.newDrawable(resources)?.let { progress.progressDrawable = it }
        if (!preferences.forceDarkPages) {
            browserHost.dispatchConfigurationChanged(browserHost.resources.configuration)
        }
        applyAppearance()
        animation?.start()
    }

    /** ua.w1/x1: regenerate only the internal page kinds that have a source dirty bit. */
    private fun refreshGeneratedDocuments() {
        val pending = tabs.all.mapNotNull { tab ->
            val type = pageTypeOf(tab.page.url)
            val bit = when (type) {
                2, 11 -> GeneratedDocumentState.BOOKMARKS
                3 -> GeneratedDocumentState.HISTORY
                4 -> GeneratedDocumentState.ABOUT
                5 -> GeneratedDocumentState.CATALOG
                else -> return@mapNotNull null
            }
            if (GeneratedDocumentState.has(bit)) tab to type else null
        }
        pending.forEach { (tab, type) ->
            val kind = when (type) {
                2 -> InternalDocuments.Kind.BOOKMARKS
                11 -> InternalDocuments.Kind.FOLDER
                3 -> InternalDocuments.Kind.HISTORY
                4 -> InternalDocuments.Kind.ABOUT
                else -> InternalDocuments.Kind.CATALOG
            }
            val folder = if (type == 11) tab.page.url!!.toUri().getQueryParameter("folder")
                ?: tab.requestedUrl.takeIf { it.startsWith("folder://") }?.toUri()?.host else null
            InternalDocuments.write(host, preferences, database, kind, folder)
            tab.page.reload()
        }
    }

    /** Settings pages use this source-faithful hook after changing filter flags/files. */
    fun reloadBrowserPreferences() {
        if (!::tabs.isInitialized) return
        reloadTabPreferences()
        snifferButton.update(preferences.showSnifferButton, tabs.hasMediaResources(current()), false)
        currentWebView()?.reload()
    }

    private fun installPageGestures(tab: BrowserTab) {
        val page = tab.page
        val view = page.view
        val gesture = PageGestureController(gesturePreview,
            canStart = { !gameMode.enabled && behavior.backForwardGesture && page.edgeGestureReady && !(customMode && appFullscreen && !browserLayout.toolbarsShown) },
            signal = {
                val edge = !page.canScrollHorizontally(1) && !page.canScrollHorizontally(-1)
                12 or (if (edge && (tabs.canGoBack(tab) || !isHome(tab.url))) 1 else 0) or (if (edge && (tabs.canGoForward(tab) || tabs.canRecoverClosedTab)) 2 else 0)
            },
            onGesture = { direction -> when (direction) {
                1 -> if (customMode) goBack() else browserBack()
                2 -> browserForward(tab)
                4 -> browserLayout.hideToolbars()
                8 -> if (!appFullscreen && preferences.fullScreenMode != 2) browserLayout.showToolbars()
            } },
            onPreview = { arrow, direction -> (arrow as ImageView).setImageResource(if (direction == 1) R.drawable.chevron_left else R.drawable.chevron_right) })
        pageGesture = gesture
        pageGestureOrientation = resources.configuration.orientation
        view.setOnTouchListener { target, event -> gesture.onTouch(target, event) }
    }

    /**
     * n0.s driven by c8.s6.A: loading shows the close glyph (the source keeps
     * the Reload label), the home page the QR scanner, anything else refresh.
     * The source adds +20 to the raw progress before testing completion.
     */
    private fun updateReloadButton(forceLoading: Boolean? = null) {
        val tab = current() ?: return
        val loading = forceLoading ?: (tab.page.progress + 20 < 100)
        when {
            loading -> reloadButton.setSkinImageResource(R.drawable.close, "ic_close")
            isHome(tab.url) -> reloadButton.setSkinImageResource(R.drawable.shortcut_scan_icon, "ic_scan")
            else -> reloadButton.setSkinImageResource(R.drawable.reload, "ic_reload")
        }
        reloadButton.contentDescription = text(if (!loading && isHome(tab.url)) R.string.scan_qr_code else R.string.operation_reload)
    }

    /** hb.v4/c8.s6.fa operation IDs are distinct from main-menu IDs. */
    private fun performBrowserAction(action: Int) {
        val view = currentWebView()
        when (action) {
            1 -> if ((view?.progress ?: 100) < 100) view?.stopLoading() else view?.let(::reloadPage)
            2 -> view?.pageUp(true)
            3 -> view?.pageDown(true)
            4 -> { browserLayout.showToolbars(); showAddressInput() }
            5 -> newTab()
            6 -> editBookmark()
            7, 30 -> showBookmarks()
            8 -> showHistory()
            9 -> current()?.let(::closeTab)
            10, 11 -> if (tabs.size > 0) {
                val index = tabs.all.indexOf(current())
                tabs.selectAt((index + if (action == 10) tabs.size - 1 else 1) % tabs.size); attachSelected()
            }
            12 -> browserBack()
            13 -> browserForward()
            14 -> showFind()
            15 -> showTranslation()
            16 -> confirmCloseAllTabs()
            17 -> view?.pageUp(false)
            18 -> view?.pageDown(false)
            19 -> savePage()
            20 -> performMenuAction(15)
            21, 22 -> view?.findNext(action == 21)
            23 -> showTabs()
            24 -> readAloud()
            25 -> currentWebView()?.let { web -> ReaderMode(host).detectPrepared(web) { state -> changeReaderMode(state != 3) } }
            26 -> openSettings()
            27 -> current()?.let { newTab(it.url) }
            28 -> showDownloads()
        }
    }

    private var floatingToolbarButton: dev.ujhhgtg.via.ui.FloatingToolbarButton? = null

    /** s6.ab: only "press to hide/show" (fullscreenmode 2) outside game mode and video. */
    private fun showFloatingToolbarButton() {
        if (!::browserLayout.isInitialized || appFullscreen || customView != null || browserLayout.toolbarsShown) return
        if (preferences.fullScreenMode != 2) return
        val button = floatingToolbarButton ?: dev.ujhhgtg.via.ui.FloatingToolbarButton(host, preferences,
            onTap = { browserLayout.showToolbars() },
            onSwipe = { direction ->
                when (direction) {
                    1 -> browserBack()
                    2 -> browserForward()
                    4 -> showTabs()
                    8 -> performBrowserAction(1)
                }
            }).also { floatingToolbarButton = it }
        dev.ujhhgtg.via.ui.FloatingToolbarButton.attach(browserLayout, button)
        button.show()
    }

    /** c8.s6.ra: the close-all-tabs action confirms first; "Keep" just dismisses. */
    private fun confirmCloseAllTabs() {
        ViaDialog(host).title(R.string.title_close_all_tabs).message(R.string.message_close_all_tabs)
            .positive(R.string.action_close) { _, _ -> closeAllTabs() }
            .negative(R.string.action_keep)
            .show()
    }

    private fun closeAllTabs() {
        val previous = tabs.all
        tabs.createTab(preferences.home, select = true, clearClosedTabRecovery = false)
        val snapshot = tabs.captureSessionSnapshot()
        val closedIds = previous.map { it.sessionId }
        worker.execute {
            sessions.replaceOpen(snapshot.writeFiles())
            closedIds.forEach(sessions::markClosed)
        }
        restoring = true
        try { previous.forEach { tabs.close(it.id) } } finally { restoring = false }
        attachSelected()
    }

    /** Close every tab on the selected tab's requested side (source Z7). */
    private fun closeTabsToLeft(index: Int) {
        tabs.all.take(index).toList().forEach(::closeTab)
        attachSelected()
    }

    private fun closeTabsToRight(index: Int) {
        tabs.all.drop(index + 1).toList().forEach(::closeTab)
        attachSelected()
    }

    /** u9.d.o/n also recognizes a retained generated home when its logical alias is no longer current. */
    private fun isHome(url: String): Boolean = when (pageTypeOf(url)) {
        1 -> preferences.home == "about:home" || preferences.home == "about:links"
        2 -> preferences.home == "about:bookmarks"
        12 -> preferences.home == "about:blank"
        else -> url.equals(preferences.home, true) || url.equals(preferences.home + "/", true) ||
            url == "about:home" && preferences.home == "about:links"
    }

    private fun browserBack() {
        val tab = current() ?: return
        if (!tabs.goBack(tab) && !isHome(tab.url)) closeTab(tab)
    }

    private fun icon(drawable: Int, description: String, click: () -> Unit) = ImageView(host).apply {
        setSkinImageResource(drawable); setColorFilter(ink()); scaleType = ImageView.ScaleType.FIT_CENTER
        setPaddingRelative(dp(13), 0, dp(13), 0); setBackgroundResource(R.drawable.circle_ripple); contentDescription = description; isFocusable = true; setOnClickListener { click() }
    }

    private fun attachSelected() {
        if (!::browserHost.isInitialized) return
        val tab = current()
        val web = tab?.page?.view
        // Detaching a WebView releases its compositor surface, so re-adding the
        // same view paints an empty frame. Keep it attached when the selection is
        // unchanged and add a new page before dropping the old one.
        if (web != null && web.parent !== browserHost) {
            (web.parent as? ViewGroup)?.removeView(web)
            browserHost.addView(web, 0, FrameLayout.LayoutParams(-1, -1))
        }
        for (i in browserHost.childCount - 1 downTo 0) if (browserHost.getChildAt(i) !== web) browserHost.removeViewAt(i)
        tab?.let { installContextMenu(it); installPageGestures(it) }
        updateChrome()
        currentWebView()?.let { web ->
            progress.setPageProgress((if (pageTypeOf(web.url) > 0) 100 else web.progress) + 20)
        }
        currentWebView()?.let(pageColors::onCurrentPageChanged)
        snifferButton.update(preferences.showSnifferButton, tabs.hasMediaResources(current()), true)
    }

    private fun updateChrome() {
        if (!::address.isInitialized) return
        val tab = current() ?: return
        address.text = AddressTitleFormatter.format(host, tab.title, tab.url,
            preferences.urlBoxMode, browserLayout.tabBarEnabled, tab.page.url)
        navigationBar.setTabCount(tabs.size)
        reloadButton.setOnClickListener {
            when {
                isHome(tab.url) -> performMenuAction(25)
                tab.page.progress < 100 -> tab.page.stopLoading()
                else -> reloadPage(tab.page)
            }
        }
        updateReloadButton()
        isPrivate = privacyPolicy.isIncognito(tab.url)
        val secureIcon = when {
            URLUtil.isHttpsUrl(tab.url) && isPrivate -> R.drawable.address_incognito
            URLUtil.isHttpsUrl(tab.url) -> R.drawable.address_lock
            else -> R.drawable.search
        }
        val secureKey = when (secureIcon) {
            R.drawable.address_incognito -> "ic_incognito_mode"
            R.drawable.address_lock -> "ic_lock"
            else -> "ic_search"
        }
        siteInfoButton.setSkinImageResource(secureIcon, secureKey)
        customChrome?.update(tab.title, tab.url)
        updateTabStrip()
        updateAddressSurface()
        updateSearchStrip()
        updateBrowserBackCallback()
    }

    /**
     * c8.s6.y5/Ra/Cb: with the search toolbar enabled (appflag bit 32768
     * clear) and more than one provider configured, the engine strip rides
     * above the bottom toolbar whenever the current page is one of the
     * engines' result pages; otherwise it is removed again.
     */
    private fun updateSearchStrip() {
        if (!::browserLayout.isInitialized) return
        val url = visibleUrl(current())
        val enabled = !appFullscreen && preferences.appFlags and 32768 == 0
        val providers = if (enabled && url.isNotEmpty()) {
            val disabled = preferences.searchToolBarDisabled?.split(',').orEmpty().toSet()
            val order = preferences.searchToolBarOrder.split(',').mapNotNull(String::toIntOrNull)
            SearchProviders(host, preferences, database).list()
                .filter { it.id.toString() !in disabled }
                // c8.s6.y5 sorts the provider rows with ib.n(n0.E1()); the
                // persisted searchtoolbarorder list is the comparator input.
                .sortedWith(compareBy { order.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE })
        } else emptyList()
        if (providers.size <= 1 || url.isEmpty()) {
            if (searchStrip != null) { browserLayout.detachSearchStrip(); searchStrip = null }
            return
        }
        val strip = searchStrip ?: SearchStrip(host).also { created ->
            created.setCallback(object : SearchStrip.Callback {
                override fun onEngineSelected(provider: SearchProvider) {
                    // c8.s6$a.a: re-run the page's query on the chosen engine.
                    val query = created.matchedQuery(visibleUrl(current())) ?: return
                    val target = SearchStrip.buildSearchUrl(provider, query)
                    if (target.isEmpty()) return
                    // s6$a.a: a template that does not yield a URL is shown, with Copy, instead of loaded.
                    if (!dev.ujhhgtg.via.browser.UrlParser(target).isValid) {
                        ViaDialog(host).title(R.string.invalid_url).message(target)
                            .positive(android.R.string.copy) { _, _ -> copy(target); toast(text(R.string.toast_copy_text_successful)) }
                            .negative(android.R.string.cancel).show()
                        return
                    }
                    recordSearchQuery(query)
                    navigate(target)
                }
                override fun onEditSelected() = host.openPage("search_settings")
            })
            searchStrip = created
        }
        // c8.s6: control tint = m.f13636d/f13637e (subtle), label ink,
        // selected label accent (x8.h.g selected branch).
        val colors = toolbarControls
        strip.setColors(colors?.iconColor ?: themedColor(R.attr.viaSubtleColor), colors?.textColor ?: ink())
        strip.setBackgroundColor(if (::toolbarColors.isInitialized) toolbarColors.currentColor else background())
        strip.submit(providers)
        // Cb: attach only while the page matches an engine; detach otherwise.
        if (strip.rematch(url)) browserLayout.attachSearchStrip(strip)
        else browserLayout.detachSearchStrip()
    }

    /** c8.s6.Eb / ib.i0.x: apply the background to n0 itself.  The original
     * does not inset the navigation container or paint the whole bottom
     * chrome; it gives the address view a four dp layer inset and, only in
     * mode 3, adds sixteen dp horizontal content padding. */
    private fun updateAddressSurface() {
        if (!::address.isInitialized || !::browserLayout.isInitialized) return
        address.background = null
        val rowParams = addressRow.layoutParams as? LinearLayout.LayoutParams
        if (rowParams != null) {
            rowParams.setMargins(0, 0, 0, 0)
            addressRow.layoutParams = rowParams
        }
        val mode = browserLayout.mode
        if (mode == 0) {
            addressRow.setBackgroundColor(Color.TRANSPARENT)
            addressRow.setPadding(0, 0, 0, 0)
            return
        }
        val inset = dp(4)
        val fill = if (night()) 0x22ffffff else 0x10000000
        val surface = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(18).toFloat()
        }
        addressRow.background = LayerDrawable(arrayOf(surface)).apply {
            if (mode == 3) {
                val horizontal = inset * 4
                setLayerInset(0, horizontal, inset, horizontal, inset)
                addressRow.setPadding(horizontal, 0, horizontal, 0)
            } else {
                setLayerInset(0, inset, inset, inset, inset)
                addressRow.setPadding(0, 0, 0, 0)
            }
        }
        address.setPadding(0, 0, 0, 0)
    }

    /** c8.s6.ka: an explicit refresh invalidates the host color before the next page samples it. */
    private fun reloadPage(web: dev.ujhhgtg.via.engine.EnginePage) {
        PageColorCache.remove(web.url)
        web.reload()
    }

    /** c8.s6.M7/T2: reader style and exit share the page-color cache. */
    private fun updateReaderColor(web: dev.ujhhgtg.via.engine.EnginePage, active: Boolean) {
        if (active) {
            pageColors.setPageAccentColor(web, preferences.readerThemeColor.takeUnless { it == 0 } ?: -1)
        } else {
            PageColorCache.remove(web.url)
            pageColors.setAccentColor(web, -1)
            toolbarColors.setPageColor(-1, false)
        }
    }

    /** C9(36), F9 and i6.c0: show/hide have distinct callbacks and source toasts. */
    private fun changeReaderMode(show: Boolean) {
        val web = currentWebView() ?: return
        val reader = ReaderMode(host)
        if (!show) {
            if (!web.javaScriptEnabled) { toast(getString(R.string.cannot_work_javascript_is_blocked)); return }
            reader.exit(web) { updateReaderColor(web, false); toast(getString(R.string.reader_is_hidden)) }
        } else reader.showFromMenu(web) { result ->
            when (result) {
                0 -> {
                    reader.applyStyle(web, preferences.readerThemeColor, preferences.readerTextSize, preferences.readerCustomCss.orEmpty())
                    updateReaderColor(web, true)
                    toast(getString(R.string.reader_is_shown))
                }
                // s6.G4 also maps 3; i6.c0.j itself only reports 0, 1, 2 (script error, silent) and 4.
                3 -> toast(getString(R.string.the_page_has_not_fully_loaded))
                1 -> toast(getString(R.string.cannot_work_javascript_is_blocked))
                4 -> toast(getString(R.string.cannot_work))
            }
        }
    }

    private fun navigate(value: String) {
        if (!::tabs.isInitialized) return
        if (!dispatchBrowserCommand(value)) {
            val input = value.trim()
            if (input.isNotEmpty() && !dev.ujhhgtg.via.browser.UrlParser(input).isValid) recordSearchQuery(input)
            tabs.navigate(current() ?: tabs.ensureInitialTab(), value)
        }
        updateChrome()
    }

    /** c8.ua.m0 -> ka.g.e -> la.b.b: only opted-in searches outside global private mode. */
    private fun recordSearchQuery(query: String) {
        if (!privacyPolicy.enabled && preferences.searchSuggestion and 32 != 0) {
            dev.ujhhgtg.via.search.SuggestionRepository(database).recordQuery(query)
        }
    }

    /** c8.s6.J5 previews the destination tab once a configured tab swipe crosses 80%. */
    private fun onToolbarSwipe(fraction: Float, released: Boolean) {
        behavior.toolbarSwipeAction(fraction, released)?.let(::performBrowserAction)
        val action = if (kotlin.math.abs(fraction).toDouble() > .8) behavior.longPressAction(if (fraction > 0f) 5 else 6) else 0
        val direction = when (action) { 10 -> -1; 11 -> 1; else -> 0 }
        if (toolbarSwipePreview == direction) return
        toolbarSwipePreview = direction
        val count = tabs.size
        if (direction == 0) navigationBar.setTabPosition(-1, count)
        else {
            val position = tabs.indexOf(current()) + 1 + direction
            navigationBar.setTabPosition(if (position <= 0) count else if (position > count) 1 else position, count)
        }
    }

    private fun newTab(url: String? = null) {
        // ua.g1(null) creates the configured home without clearing ua.v.
        tabs.createTab(url ?: preferences.home, select = true, clearClosedTabRecovery = url != null,
            insertIndex = if (url == null) tabs.size else tabs.indexOf(current()) + 1)
        attachSelected()
    }

    /** c8.s6.ca: the original editor is an overlay fragment in Shell's container. */
    private fun showAddressInput(prefill: String? = null) {
        if (!isVisible) return
        val top = browserLayout.mode != 2 && browserLayout.mode != 3
        val toolbarColor = toolbarColors.currentColor
        val colored = toolbarColor != 0 && !night()
        // c8.s6.ca asks the engine strip (common.widget.m) to identify the
        // current result URL before opening tb.k0.  The extracted keyword is
        // passed separately from the URL, so the editor shows the query while
        // retaining the selected engine badge.  Do this even when the strip
        // is currently detached (for example when the search toolbar is
        // disabled): the original widget keeps its provider list detached in
        // that state and still uses it for this exact lookup.
        val searchMatch = if (prefill.isNullOrEmpty()) {
            val currentUrl = currentWebView()?.url.orEmpty()
            val order = preferences.searchToolBarOrder.split(',').mapNotNull(String::toIntOrNull)
            val providers = SearchProviders(host, preferences, database).list()
                .sortedWith(compareBy { order.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE })
            if (providers.size > 1 && UrlResolver.isHttpUrl(currentUrl)) {
                providers.firstNotNullOfOrNull { provider ->
                    UrlInputText.extractSearch(currentUrl, provider.template)
                        ?.takeIf(String::isNotEmpty)?.let { provider to it }
                }
            } else null
        } else null
        val editor = dev.ujhhgtg.via.search.UrlInputFragment.newInstance(
            title = prefill?.takeIf(String::isNotEmpty),
            url = if (prefill.isNullOrEmpty()) currentWebView()?.url else null,
            keyword = searchMatch?.second,
            searchEngine = searchMatch?.first?.id ?: 0,
            top = top,
            colored = colored,
            background = toolbarColor,
            rounded = top && !colored && nativeBackground.hasImage,
            incognito = isPrivate,
            tabs = tabs.all.map { dev.ujhhgtg.via.search.SearchTab(it.id, it.url, it.title) },
        )
        parentFragmentManager.beginTransaction().setReorderingAllowed(true)
            .setCustomAnimations(R.anim.search_enter, R.anim.search_hold, R.anim.search_hold, R.anim.search_exit)
            .add(R.id.fragment_container, editor, "UrlInputFragment")
            .addToBackStack(null).commit()
    }

    private fun showMenu() {
        if (dismissBrowserOverlay()) return
        val gravity = Gravity.END or if (browserLayout.mode == 1) Gravity.TOP else Gravity.BOTTOM
        var width = minOf(root.width, root.height).takeIf { it > 0 }
            ?: minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val metrics = resources.displayMetrics
        val screen = resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
        if (screen in 3..4 && browserLayout.mode != 0 && kotlin.math.hypot(metrics.widthPixels / metrics.xdpi, metrics.heightPixels / metrics.ydpi) >= 7f)
            width = minOf(width - dp(96), dp(384))
        val menu = BrowserMenuDialog.newInstance(currentWebView()?.url, currentWebView()?.title, width, gravity)
        menuDialog = menu
        menu.show(childFragmentManager, "browser_menu")
        currentWebView()?.let { web -> ReaderMode(host).detectPrepared(web) { menu.updateReaderState(it) } }
    }

    override fun menuRuntimeState() = BrowserMenuDialog.RuntimeState(privacyPolicy.enabled, preferences.appFlags and 1 != 0, gameMode.menuEnabled)

    /** Original c8.s6.D9 has separate handlers for menu long presses. */
    private fun performMenuLongPress(id: Int, flags: Int) {
        when (id) {
            BrowserMenu.EXTENSIONS -> if (extensionHost != null) openSettings("settings_extensions")
                else ViaToast.show(host, R.string.extensions_unavailable)
            1 -> host.navigate(dev.ujhhgtg.via.settings.NightModeSettingsFragment())
            2 -> {
                parentFragmentManager.setFragmentResultListener(CompactBookmarksFragment.RESULT, viewLifecycleOwner) { key, result ->
                    result.getStringArray("urls")?.forEach(::navigate)
                    parentFragmentManager.clearFragmentResult(key)
                }
                parentFragmentManager.setFragmentResultListener(CompactBookmarksFragment.MANAGE_RESULT, viewLifecycleOwner) { key, _ ->
                    showRecordsPage(RecordsBrowserView.Page.BOOKMARKS)
                    parentFragmentManager.clearFragmentResult(key)
                }
                CompactBookmarksFragment.newInstance().show(childFragmentManager, "bookmark_dialog")
            }
            3 -> worker.execute {
                val target = dev.ujhhgtg.via.browser.HistoryDocument.write(host, preferences, history)
                host.runOnUiThread {
                    if (!isAdded || view == null) return@runOnUiThread
                    if (UrlInputText.isInternalDocument(currentWebView()?.url.orEmpty(), host.filesDir.path)) navigate(target) else newTab(target)
                }
            }
            4 -> BrowserMenuChoices.downloadManager(host)
            6 -> BrowserMenuChoices.external(host, currentWebView()?.url)
            7 -> current()?.let { dev.ujhhgtg.via.tools.AddToHomeScreen.show(host, it.page.url, it.title) }
            8 -> BrowserMenuChoices.userAgent(host, currentWebView()?.url) { reload ->
                reloadTabPreferences()
                if (reload) currentWebView()?.let { if (it.progress < 100) it.stopLoading() else reloadPage(it) }
            }
            9 -> showNetworkLog(true)
            10 -> openSettings("customize_menu")
            14 -> showTranslation(true)
            16 -> openSettings("toolbars_settings")
            19 -> host.navigate(dev.ujhhgtg.via.settings.UserAgentSettingsFragment())
            20, 23 -> host.navigate(dev.ujhhgtg.via.browser.filter.CustomFiltersFragment())
            26 -> host.openPage("site_conf")
            27 -> openSettings("settings_script")
            28 -> openSettings("block_ads")
            29 -> host.navigate(dev.ujhhgtg.via.settings.WebTextSizeSettingsFragment())
            33 -> ReadAloudDialog.show(childFragmentManager)
            36 -> if (flags and 8 == 0) host.navigate(dev.ujhhgtg.via.settings.ReaderSettingsFragment()) else siteInfo()
        }
    }

    private fun performMenuAction(id: Int, flags: Int = 0) {
        when (id) {
            BrowserMenu.EXTENSIONS -> extensionHost?.showActions(current()?.page)
                ?: ViaToast.show(host, R.string.extensions_unavailable)
            -1, 21 -> exitBrowser()
            1 -> {
                val systemNight = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                preferences.setNightMode(!night(), systemNight)
                GeneratedDocumentState.initialize(preferences)
                GeneratedDocumentState.mark(GeneratedDocumentState.ALL_DOCUMENTS)
                if (preferences.isNightMode == systemNight) toast(text(R.string.follow_system_dark_mode_on))
                onNightThemeChanged(preferences.isNightMode)
            }
            2 -> showRecordsPage(RecordsBrowserView.Page.BOOKMARKS)
            3 -> showRecordsPage(RecordsBrowserView.Page.HISTORY)
            4 -> showDownloads()
            5 -> {
                toggleIncognitoMode()
            }
            6 -> sharePage()
            7 -> editBookmark()
            8 -> {
                preferences.webFlags = preferences.webFlags xor 2048
                val desktop = preferences.webFlags and 2048 != 0
                toast(getString(if (desktop) R.string.is_on else R.string.is_off, getString(R.string.action_pcview)))
                reloadTabPreferences()
                currentWebView()?.let { web ->
                    val url = web.url
                    if (!dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(host, url.orEmpty())) {
                        val metrics = resources.displayMetrics
                        val screen = resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
                        val tablet = screen in 3..4 && kotlin.math.hypot(metrics.widthPixels / metrics.xdpi, metrics.heightPixels / metrics.ydpi) >= 7f
                        val target = dev.ujhhgtg.via.browser.DesktopUrlAdapter.adapt(url, desktop || tablet)
                        if (!target.isNullOrEmpty()) { PageColorCache.remove(target); navigate(target) }
                        else reloadPage(web)
                    }
                }
            }
            10 -> openSettings()
            11 -> showFind()
            12 -> savePage()
            13 -> showSavedPages()
            14 -> showTranslation()
            // ua.i2 reads the document URL, including Via's generated home file.
            15 -> currentWebView()?.url?.takeIf { it.isNotEmpty() }?.let { newTab("view-source:$it") }
            16 -> root.postDelayed({
                val enabled = preferences.appFlags and 1 == 0
                preferences.appFlags = preferences.appFlags xor 1
                WindowInsetsHelper.setFullscreen(host.window, appFullscreen || enabled)
                configureBrowserLayout()
                toast(getString(if (enabled) R.string.is_on else R.string.is_off, getString(R.string.action_fullscreen)))
            }, 100L)
            17 -> BrowserMenuChoices.images(host) { reloadTabPreferences() }
            18 -> showNetworkLog(true)
            19 -> BrowserMenuChoices.userAgent(host, currentWebView()?.url) { reload ->
                reloadTabPreferences()
                if (reload) currentWebView()?.let { if (it.progress < 100) it.stopLoading() else reloadPage(it) }
            }
            20 -> showNetworkLog(false)
            23 -> currentWebView()?.let { adMarker.start(it) }
            34 -> current()?.let { dev.ujhhgtg.via.tools.AddToHomeScreen.show(host, it.page.url, it.title) }
            24 -> currentWebView()?.let { if (it.progress < 100) it.stopLoading() else reloadPage(it) }
            25 -> showScanner()
            26 -> openCurrentSiteSettings()
            27 -> {
                val url = currentWebView()?.url.orEmpty()
                val matched = URLUtil.isNetworkUrl(url) &&
                    ScriptStore(host).use { store -> store.list().any { it.id >= 0 && it.appliesTo(url) } }
                if (!preferences.scriptsEnabled) {
                    preferences.scriptsEnabled = true
                    if (matched) currentWebView()?.let { if (it.progress < 100) it.stopLoading() else reloadPage(it) }
                    toast(getString(R.string.scripts_enabled))
                } else if (matched) showPageScripts()
                else openSettings("settings_script")
            }
            28 -> {
                preferences.adBlocking = !preferences.adBlocking
                reloadTabPreferences()
                toast(getString(if (preferences.adBlocking) R.string.is_on else R.string.is_off, getString(R.string.block_ads)))
            }
            29 -> showTextZoom()
            30 -> dev.ujhhgtg.via.tools.ScreenOrientationMenu.show(host)
            31 -> showBrowserSettingAction("clear_data")
            32 -> dev.ujhhgtg.via.tools.BrowserPrinting.print(host, currentWebView())
            33 -> readAloud()
            35 -> openSettings("customize_menu")
            36 -> changeReaderMode(flags and 8 == 0)
            37 -> BrowserMenuChoices.external(host, currentWebView()?.url)
            38 -> gameMode.toggle()
            40 -> current()?.let {
                val url = it.page.url ?: it.url
                val internal = dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(host, url)
                openFavoriteEditor(if (internal) "https://" else url, if (internal) null else it.page.title)
            }
            else -> toast(text(R.string.toast_operation_failed))
        }
    }

    /** c8.s6.q8/Oa/b8: global incognito is a policy switch, not a new-tab shortcut. */
    private fun toggleIncognitoMode() {
        if (!privacyPolicy.enabled) {
            privacyPolicy.enabled = true
            GeneratedDocumentState.mark(GeneratedDocumentState.HISTORY)
            navigationBar.bounceTabCount()
            ViaToast.makeText(host, R.string.incognito_on, ViaToast.LENGTH_SHORT).show()
            reloadTabPreferences()
            updateChrome()
            return
        }
        val selected = current()
        val outsideHome = selected != null && !isConfiguredHome(selected)
        val complex = tabs.size > 1 || tabs.canGoBack() || outsideHome || tabs.canGoForward() || tabs.canRecoverClosedTab
        fun disable(keepTabs: Boolean) {
            if (!keepTabs) {
                // b8(false) starts/records the closes while global privacy is
                // still enabled. Site overrides continue to apply to each URL.
                val previous = tabs.all
                tabs.createTab(preferences.home, select = true, clearClosedTabRecovery = false)
                restoring = true
                try { previous.asReversed().forEach { closeTab(it, attach = false) } }
                finally { restoring = false }
                worker.execute { clearOpenSessionMarkers() }
            }
            privacyPolicy.enabled = false
            GeneratedDocumentState.mark(GeneratedDocumentState.HISTORY)
            ViaToast.makeText(host, R.string.incognito_off, ViaToast.LENGTH_SHORT).show()
            reloadTabPreferences()
            // q8 animates the tab count when enabling; b8 has no second bounce.
            if (keepTabs) updateChrome() else attachSelected()
        }
        if (complex) {
            ViaDialog(host).title(R.string.title_close_all_tabs)
                .message(R.string.message_close_all_tabs_before_disabling_incognito_mode)
                .positive(R.string.action_close) { _, _ -> disable(false) }
                .negative(R.string.action_keep) { disable(true) }
                .show()
        } else disable(true)
    }

    /** u9.d.o/n: compare the selected document with the configured home, including built-in homes. */
    private fun isConfiguredHome(tab: BrowserTab): Boolean = isHome(tab.page.url ?: tab.url)

    /** s6.s8/P3: persist the session, and confirm losing private tabs unless the warning was disabled. */
    private fun exitBrowser() {
        persistRestorableSessions()
        if (preferences.appFlags and 2048 == 0 && privacyPolicy.enabled &&
            (tabs.size > 1 || current()?.let { !isConfiguredHome(it) } == true)) {
            ViaDialog(host).title(R.string.exit).message(R.string.exit_message_if_incognito_mode_is_on)
                .check(R.string.dont_remind_me_again, false)
                .positive(R.string.exit) { _, result ->
                    if (result.checked) preferences.appFlags = preferences.appFlags or 2048
                    host.finish()
                }.negative(android.R.string.cancel).show()
        } else host.finish()
    }

    /** ua.h2: retain ordinary closed rows, delete private open rows and clear only open/selected bits. */
    private fun clearOpenSessionMarkers() {
        sessions.listOpen(true).forEach { row ->
            if (row.isIncognito) deleteSession(row.id)
            else sessions.save(row.copy(flags = row.flags and 6.inv()))
        }
    }

    /** c8.ua.S1 and z8.f1.k/l, including sharing Via itself from local pages. */
    private fun sharePage() {
        val web = currentWebView()
        val url = web?.url
        val content = if (url.isNullOrEmpty() || url.startsWith("file://", true)) "http://viayoo.com/"
        else web.title?.takeIf { it.isNotEmpty() }?.let { "$it\n$url" } ?: url
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, content), getString(R.string.action_share)))
    }

    private fun readAloud() {
        val tab = current() ?: return
        val web = tab.page
        if (!web.view.isShown) return
        if (!web.javaScriptEnabled) { toast(getString(R.string.cannot_work_javascript_is_blocked)); return }
        val controller = readAloudController ?: ReadAloudController.get(host).also {
            readAloudController = it; it.addListener(readAloudListener)
        }
        if (controller.initializationFailed) { toast(getString(R.string.tts_failed_to_initialize)); return }
        toast(getString(R.string.wait_a_moment))
        ReaderMode(host).extractSentences(tab.page) { sentences ->
            if (sentences.isEmpty()) toast(text(R.string.cannot_read_this_page_aloud))
            else controller.start(web.url.orEmpty(), web.title.orEmpty(), sentences)?.let { task ->
                readAloudTaskId = task.id
                readAloudControls?.update(task, readAloudTaskId)
                ViaToast.show(host, R.string.start_reading_aloud, actionText = R.string.view_downloads) { ReadAloudDialog.show(childFragmentManager) }
            }
        }
    }

    private fun updateReadAloudControls(recreate: Boolean = false) {
        readAloudControls?.update(readAloudController?.task?.takeIf { it.id == readAloudTaskId }, readAloudTaskId, recreate)
    }

    private fun showPageScripts() {
        val tab = current() ?: return
        tabs.scriptMenuState(tab) { result ->
            val decoded = runCatching { org.json.JSONTokener(result).nextValue() }.getOrNull()
            val menus = runCatching { JSONObject(decoded as? String ?: "{}") }.getOrDefault(
                JSONObject()
            )
            childFragmentManager.setFragmentResultListener(PageScriptsDialogFragment.RESULT, viewLifecycleOwner) { key, action ->
                val scriptId = action.getString("script_id")
                val name = action.getString("name")
                if (scriptId != null && name != null) {
                    current()?.let { tabs.executeScriptMenu(it, scriptId, name) }
                }
                childFragmentManager.clearFragmentResult(key)
            }
            val url = (tab.page.url ?: tab.url).takeUnless {
                dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(host, it)
            }
            PageScriptsDialogFragment.newInstance(url, menus.toString()).show(childFragmentManager, "page_scripts")
        }
    }

    private fun showPasswordAssist(webView: dev.ujhhgtg.via.engine.EnginePage, visible: Boolean) {
        if (!::browserHost.isInitialized || currentWebView() !== webView) return
        passwordAssist?.let { browserHost.removeView(it) }; passwordAssist = null
        if (visible) {
            val button = Button(host).apply { text = text(R.string.fill_password); isAllCaps = false; setOnClickListener { passwordForms.choose(webView) } }
            passwordAssist = button
            browserHost.addView(button, FrameLayout.LayoutParams(-2, dp(48), Gravity.END or Gravity.BOTTOM).apply { rightMargin = dp(12); bottomMargin = dp(8) })
        }
    }

    private fun openSettings(action: String? = null) = host.openSettings(action)

    /** c8.s6.C9: rotation and data clearing are browser dialogs, without a settings page underneath. */
    private fun showBrowserSettingAction(action: String) {
        val controller = menuSettings ?: SettingsController(
            host,
            openPage = host::openPage,
            launchForResult = ::launchForResult,
            scopeOwner = this,
        ).also { menuSettings = it }
        controller.route(action)
    }

    /** c8.s6.Z9/aa(2): current network site's editor, or the saved-site list on an internal page. */
    private fun openCurrentSiteSettings(flags: Int = 2) {
        val url = currentWebView()?.url.orEmpty()
        val domain = dev.ujhhgtg.via.browser.DocumentPolicy.authority(url)
        if (url.isEmpty() || url.startsWith("file://")) host.openPage("site_conf")
        else if (domain.isEmpty()) return
        else {
            parentFragmentManager.setFragmentResultListener("site_settings_changed", viewLifecycleOwner) { _, result ->
                val changed = result.getInt("changed", 0)
                parentFragmentManager.clearFragmentResultListener("site_settings_changed")
                if (changed and 2 != 0) {
                    dev.ujhhgtg.via.settings.TextZoomDialogs.showBrowser(host, { currentWebView() }, siteOnly = true,
                        onChanged = { reloadTabPreferences() },
                        onCancelled = { openCurrentSiteSettings(changed or 4) })
                } else if (changed and 1 != 0) current()?.let(tabs::reloadSitePreferences)
            }
            host.navigate(SiteSettingsFragment.newInstance(domain, flags))
        }
    }

    /**
     * c8.s6.fa(23) -> R8()/nb(): the tab counter opens f8.l0 in the browser
     * root's popup container (mark.via:id/bg) with the fn scrim; a second tap
     * closes it through the same backstack check.
     */
    private fun showTabs() {
        if (!::tabs.isInitialized || !isVisible) return
        if (dismissBrowserOverlay()) return
        // nb(): the sheet hangs below the top toolbar (W0 == 1) or above the
        // bottom toolbar, filling horizontally.
        val gravity = Gravity.END or if (browserLayout.mode == 1) Gravity.TOP else Gravity.BOTTOM
        // B8(): min canvas dimension, narrowed on tablets in single-toolbar modes.
        var width = minOf(root.width, root.height).takeIf { it > 0 }
            ?: minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val metrics = resources.displayMetrics
        val screen = resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
        val tablet = screen in 3..4 && kotlin.math.hypot(metrics.widthPixels / metrics.xdpi.toDouble(), metrics.heightPixels / metrics.ydpi.toDouble()) >= 7
        if (tablet && browserLayout.mode != 0) width = minOf(width - dp(96), dp(384))
        // max_height = popup container height minus 16dp (c8.s6.nb()).
        val containerHeight = browserLayout.chromeOverlay.height.takeIf { it > 0 } ?: browserLayout.content.height
        val sheet = TabSheetFragment.newInstance(gravity, width, containerHeight - dp(16))
        sheet.callback = TabSheetCallbacks()
        sheet.submitTabs(tabs.all, tabs.indexOf(current()))
        childFragmentManager.beginTransaction().add(R.id.browser_overlay_container, sheet, "tab_sheet")
            .addToBackStack(null).commit()
        onBrowserPopupOpened()
    }

    /** f8.l0.b implemented by c8.s6$l. */
    private inner class TabSheetCallbacks : TabSheetFragment.Callback {
        override fun onRowSelected(position: Int) {
            if (position < 0 || position >= tabs.size) return
            tabs.selectAt(position)
            attachSelected()
            dismissBrowserOverlay()
        }

        override fun onRowDismiss(position: Int) {
            // ua.C0: removing the last row opens a fresh home tab.
            tabs.all.getOrNull(position)?.let(::closeTab)
        }

        override fun onNewTabClicked() {
            // ua.j1(null, true): the r4.f.Z listener closes the sheet; the
            // g0 counter bounces once more than one tab is open (s6.Z).
            newTab()
            if (tabs.size > 1) navigationBar.bounceTabCount()
        }

        override fun onNewTabLongClicked() {
            // s6.l.f: R8() then W9(), the restore-tabs records page.
            dismissBrowserOverlay()
            openClosedTabsFromRecords()
        }

        override fun onRowDuplicate(position: Int) {
            // s6.i8: duplicate the row's page after it and select the copy.
            dismissBrowserOverlay()
            val source = tabs.all.getOrNull(position) ?: return
            newTab(source.url)
            tabs.move(tabs.size - 1, (position + 1).coerceAtMost(tabs.size - 1))
        }

        override fun onRowMenuAction(position: Int, action: Int) {
            // s6.l.b: the sheet closes for the destructive actions that touch
            // the selected tab, then Z7 closes the requested ranges.
            val selectedIndex = tabs.indexOf(current())
            if (action == 4 || (action == 1 && position != selectedIndex) ||
                (action == 2 && selectedIndex > position) || (action == 3 && selectedIndex < position)
            ) {
                dismissBrowserOverlay()
            }
            when (action) {
                4 -> closeAllTabs()
                1 -> tabs.all.getOrNull(position)?.let { source ->
                    tabs.all.filter { it.id != source.id }.toList().forEach(::closeTab)
                }
                2 -> closeTabsToRight(position)
                3 -> closeTabsToLeft(position)
            }
            attachSelected()
        }

        override fun onRowMove(from: Int, to: Int): Boolean = tabs.move(from, to)
    }

    private fun closeTab(tab: BrowserTab) = closeTab(tab, attach = true)

    /** ua.K1/M/f0: persist one eligible closed tab before publishing its temporary Forward target. */
    private fun closeTab(tab: BrowserTab, attach: Boolean) {
        val returnToCaller = consumeExternalIntentClose(tab)
        val wasSelected = current()?.id == tab.id
        val snapshot = tabs.captureClosedSession(tab)
        val generation = if (snapshot != null) tabs.beginClosedTabWrite() else null
        val closedId = tab.sessionId
        val label = tab.title.takeIf(String::isNotEmpty) ?: run {
            val domain = AddressTitleFormatter.domain(tab.url)
            val short = when {
                domain.startsWith("m.") && domain.indexOf('.', 2) >= 0 -> domain.substring(2)
                domain.startsWith("www.") && domain.indexOf('.', 4) >= 0 -> domain.substring(4)
                else -> domain
            }
            if (tab.url.isNotEmpty()) short else text(R.string.untitled)
        }
        worker.execute {
            if (snapshot == null) deleteSession(closedId)
            else {
                val row = snapshot.writeFiles().single()
                val duplicates = sessions.listClosed().filter { it.url == row.url && it.id != row.id }
                if (sessions.save(row) && sessions.markClosed(closedId)) {
                    duplicates.forEach { it.filePath?.let { path -> File(path).delete() } }
                    host.runOnUiThread {
                        if (!isAdded || view == null) return@runOnUiThread
                        tabs.completeClosedTabWrite(generation!!, closedId)
                        if (preferences.showUndoCloseTab) {
                            ViaToast.show(host, getString(R.string.closed_tab, label.take(128)), ViaToast.LENGTH_LONG,
                                getString(R.string.undo), singleLine = true) { restoreClosedTab(closedId, wasSelected) }
                        }
                    }
                }
            }
        }
        val replacingLastTab = tabs.size == 1 && !returnToCaller
        tabs.close(tab.id, allowLast = returnToCaller || replacingLastTab)
        if (returnToCaller) {
            if (tabs.size > 0) host.moveTaskToBack(true) else host.finish()
            return
        }
        if (replacingLastTab) {
            // ua.C0 removes the selected tab before creating the replacement
            // home tab.  The ordering preserves the original tab-strip event
            // sequence, including the brief replacement WebView transition.
            tabs.createTab(preferences.home, select = true, clearClosedTabRecovery = false)
        }
        if (attach) attachSelected()
    }

    /** na.a.a deletes the saved Parcel together with its row. Call on the session worker. */
    private fun deleteSession(id: String) {
        val row = sessions.find(id)
        if (sessions.delete(id)) row?.filePath?.let { File(it).delete() }
    }

    /** c8.s6.J8: normal forward wins; only ua.v may supply the fallback closed tab. */
    private fun browserForward(tab: BrowserTab? = current()) {
        if (tabs.goForward(tab)) return
        tabs.consumeClosedTabRecovery()?.let { restoreClosedTab(it, true) }
    }

    /** ua.F1: append the retained tab, then recompute its current effective session policy. */
    private fun restoreClosedTab(id: String, select: Boolean) {
        worker.execute {
            val row = sessions.find(id) ?: return@execute
            val state = dev.ujhhgtg.via.browser.SessionState.read(row.filePath)
            host.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                tabs.restoreSessionTab(row, select, state)
                val reopened = row.copy(flags = (row.flags and 7.inv()) or privacyPolicy.openSessionFlags(row.url, false),
                    lastVisitedAt = System.currentTimeMillis())
                worker.execute { sessions.save(reopened) }
                attachSelected()
            }
        }
    }

    private fun showBookmarks(folder: String = "", query: String = "") =
        host.navigate(dev.ujhhgtg.via.records.RecordsContainerFragment.newInstance(0, folder, query))

    private fun showRecordsPage(page: RecordsBrowserView.Page) {
        val initial = when (page) {
            RecordsBrowserView.Page.BOOKMARKS, RecordsBrowserView.Page.FAVORITES -> 0
            RecordsBrowserView.Page.HISTORY, RecordsBrowserView.Page.CLOSED_TABS -> 1
            RecordsBrowserView.Page.SAVED_PAGES -> 2
        }
        host.navigate(dev.ujhhgtg.via.records.RecordsContainerFragment.newInstance(initial))
    }

    private fun editBookmark(existing: BookmarkItem? = current()?.let { bookmarks.findByUrl(it.url) }) {
        val tab = current() ?: return
        val url = tab.page.url
        val internal = url == null || dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(host, url)
        parentFragmentManager.setFragmentResultListener("bookmarkDialogResult2", viewLifecycleOwner) { key, result ->
            if (result.getString("id") != null) {
                GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
                refreshCustomizedHome(); refreshGeneratedDocuments()
            }
            parentFragmentManager.clearFragmentResult(key)
            parentFragmentManager.clearFragmentResultListener(key)
        }
        dev.ujhhgtg.via.records.BookmarkEditDialogFragment.newInstance(
            id = existing?.id?.takeUnless { internal }, url = if (internal) "https://" else url,
            title = if (internal) "" else tab.title).show(childFragmentManager, "bookmark_edit")
    }

    private fun showHistory(query: String = "") =
        host.navigate(dev.ujhhgtg.via.records.RecordsContainerFragment.newInstance(1, query = query))

    private fun showDownloads() {
        if (dev.ujhhgtg.via.settings.ExternalDownloadManagers.openDownloads(host, preferences.downloadManager)) return
        host.navigate(DownloadsFragment())
    }

    private fun showScanner() = host.navigate(dev.ujhhgtg.via.tools.QrScannerFragment())

    /** c8.s6.Za: an open pane takes the new text; otherwise it opens with TEXT prefilled. */
    private fun showFind(text: String? = null) {
        val tag = dev.ujhhgtg.via.ui.FindInPageFragment.TAG
        (childFragmentManager.findFragmentByTag(tag) as? dev.ujhhgtg.via.ui.FindInPageFragment)?.let { it.setQuery(text); return }
        // c8.s6.Za/B8: the same content overlay as translation, without dim or blur.
        val shortSide = minOf(root.width, root.height).takeIf { it > 0 }
            ?: minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val screen = resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
        val metrics = resources.displayMetrics
        val large = screen in 3..4 && kotlin.math.hypot(metrics.widthPixels / metrics.xdpi, metrics.heightPixels / metrics.ydpi) >= 7f
        val width = if (large && browserLayout.mode != 0) minOf(shortSide - dp(96), dp(384)) else shortSide
        val gravity = Gravity.END or if (browserLayout.mode == 1 || browserLayout.mode == 4) Gravity.TOP else Gravity.BOTTOM
        val pane = dev.ujhhgtg.via.ui.FindInPageFragment.newInstance(width, gravity, dp(48), isHome(currentWebView()?.url.orEmpty()), text)
        childFragmentManager.beginTransaction().setReorderingAllowed(true)
            .add(R.id.browser_overlay_container, pane, tag).addToBackStack(null).commit()
    }

    /**
     * e8.i.C: the selection toolbar hides the engine's own web search (see EnginePage.setSelectionActions)
     * and "Search in Via" entries, and adds Search and Find, which c8.s6.C routes with the selected text.
     */
    private fun installSelectionActions(web: dev.ujhhgtg.via.engine.EnginePage) {
        web.setSelectionActions(listOf(
            dev.ujhhgtg.via.engine.SelectionAction(0, getString(R.string.search_in_via), hide = true),
            dev.ujhhgtg.via.engine.SelectionAction(SELECTION_SEARCH, getString(R.string.search_hint)),
            dev.ujhhgtg.via.engine.SelectionAction(SELECTION_FIND, getString(R.string.find)),
        )) { id, text ->
            if (text.isNotEmpty() && isAdded) when (id) {
                SELECTION_SEARCH -> if (!dispatchBrowserCommand(text)) searchSelectionInNewTab(text)
                SELECTION_FIND -> { web.finishSelection(); showFind(text) }
            }
        }
    }

    /** c8.s6.Ga(text, false, true): a URL opens directly, anything else is searched and recorded. */
    private fun searchSelectionInNewTab(text: String) {
        val query = text.trim().takeIf(String::isNotEmpty) ?: return
        val target = if (dev.ujhhgtg.via.browser.UrlParser(query).isValid) {
            val url = UrlResolver.normalizeInput(query, preferences.effectiveSearchUrl()) ?: return
            if (dispatchBrowserCommand(url)) return
            url
        } else {
            recordSearchQuery(query)
            UrlResolver.search(preferences.effectiveSearchUrl(), query)
        }
        newTab(target)
    }

    override fun adMarkerController() = adMarker
    private fun showAdMarkerPanel() {
        val tag = dev.ujhhgtg.via.tools.AdMarkerFragment.TAG
        if (childFragmentManager.findFragmentByTag(tag) != null) return
        var width = minOf(root.width, root.height).takeIf { it > 0 }
            ?: minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val metrics = resources.displayMetrics
        val screen = resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
        val tablet = screen in 3..4 && kotlin.math.hypot(metrics.widthPixels / metrics.xdpi, metrics.heightPixels / metrics.ydpi) >= 7f
        if (tablet && browserLayout.mode != 0) width = minOf(width - dp(96), dp(384))
        val gravity = Gravity.END or if (browserLayout.mode == 1 || browserLayout.mode == 4) Gravity.TOP else Gravity.BOTTOM
        childFragmentManager.beginTransaction().setReorderingAllowed(true)
            .add(R.id.browser_overlay_container, dev.ujhhgtg.via.tools.AdMarkerFragment.newInstance(width, gravity), tag)
            .addToBackStack(null).commit()
    }

    override fun findPageText(query: String) {
        val web = currentWebView() ?: return
        val pane = childFragmentManager.findFragmentByTag(dev.ujhhgtg.via.ui.FindInPageFragment.TAG) as? dev.ujhhgtg.via.ui.FindInPageFragment
        if (query.isEmpty()) { web.find(query, null); pane?.updateMatches(-1, 0, true) }
        else web.find(query) { active, total, done -> pane?.updateMatches(active, total, done) }
    }
    override fun findPageNext(forward: Boolean) { currentWebView()?.findNext(forward) }
    override fun closePageFind() {
        findPageText(""); dismissBrowserOverlay()
        (host.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun showTextZoom() = dev.ujhhgtg.via.settings.TextZoomDialogs.showBrowser(
        host, { currentWebView() }, onChanged = { reloadTabPreferences() },
    )

    private fun savePage() {
        val tab = current() ?: return
        dev.ujhhgtg.via.tools.SavedPageTools.show(host, tab.page, tab.page.title.orEmpty(), onViewSavedPages = ::showSavedPages)
    }

    private fun showSavedPages() = showRecordsPage(RecordsBrowserView.Page.SAVED_PAGES)

    /** bb.v: current/new foreground opens leave records; background opens keep it visible. */
    fun openRecordFromRecords(url: String, background: Boolean) = openRecordFromRecords(url, if (background) 2 else 0)

    fun openRecordFromRecords(url: String, mode: Int) {
        if (url.isBlank()) return
        if (mode == 2) {
            tabs.createTab(url, select = false)
            updateChrome()
            toast(text(R.string.opened_in_background_message))
            return
        }
        parentFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        if (mode == 1) newTab(url) else navigate(url)
    }

    /** pc row activation restores the saved WebView state, rather than opening
     * only its URL. The closed-tabs page uses mode 2 for background restore. */
    fun openClosedTabFromRecords(id: String, mode: Int) {
        worker.execute {
            val row = sessions.find(id) ?: return@execute
            val state = dev.ujhhgtg.via.browser.SessionState.read(row.filePath)
            host.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                val restored = tabs.restoreSessionTab(row, select = mode != 2, original = state)
                worker.execute {
                    sessions.save(row.copy(
                        flags = privacyPolicy.openSessionFlags(row.url, mode != 2),
                        lastVisitedAt = System.currentTimeMillis(),
                    ))
                }
                if (mode == 2) {
                    updateChrome()
                    toast(text(R.string.opened_in_background_message))
                } else {
                    parentFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    tabs.select(restored.id)
                    attachSelected()
                }
            }
        }
    }

    fun copyRecordFromRecords(url: String) {
        if (url.isNotBlank()) copy(url)
    }

    fun openClosedTabsFromRecords() {
        host.navigate(dev.ujhhgtg.via.records.ClosedTabsFragment())
    }

    private fun siteInfo() {
        if (childFragmentManager.findFragmentByTag("site_info") != null) {
            childFragmentManager.popBackStack()
            return
        }
        val tab = current() ?: return
        val web = tab.page
        // c8.s6.mb takes G8()/F8 from the actual document. Navigation aliases such
        // as about:home cannot be used by g8.u's u9.d.m internal-page classifier.
        val sourceUrl = web.url.orEmpty()
        val network = dev.ujhhgtg.via.reader.ReaderPageToolsFragment.isNetworkPage(sourceUrl)
        val url = if (network) sourceUrl else AddressTitleFormatter.unwrapErrorUrl(sourceUrl).orEmpty()
        val title = AddressTitleFormatter.formatPageTitle(web.title.orEmpty(), url)
        val pageFlags = (if (network && web.certificate != null) 1 else 0) or
                (if (network && UrlResolver.siteKey(url)?.let { siteConfigurations.get(it)?.isEnabled } == true) 2 else 0)
        var width = minOf(root.width, root.height).takeIf { it > 0 }
            ?: minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val metrics = resources.displayMetrics
        val screen = resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
        val tablet = screen in 3..4 && kotlin.math.hypot(metrics.widthPixels / metrics.xdpi.toDouble(), metrics.heightPixels / metrics.ydpi.toDouble()) >= 7
        if (tablet && browserLayout.mode != 0) width = minOf(width - dp(96), dp(384))
        val gravity = Gravity.START or if (browserLayout.mode == 2 || browserLayout.mode == 3) Gravity.BOTTOM else Gravity.TOP
        childFragmentManager.setFragmentResultListener(dev.ujhhgtg.via.reader.ReaderPageToolsFragment.RESULT, viewLifecycleOwner) { key, result ->
            val action = result.getInt("action")
            if (action != dev.ujhhgtg.via.reader.ReaderPageToolsFragment.STYLE_CHANGED) childFragmentManager.popBackStack()
            // F9 callbacks read G8/C8 again; the header's unwrapped error URL is
            // display data, not a replacement target for cookies/history/QR.
            val currentWeb = currentWebView()
            val currentUrl = currentWeb?.url.orEmpty()
            when (action) {
                1 -> currentWeb?.let { dev.ujhhgtg.via.reader.PageSecurityDialogs.certificate(host, it) }
                2 -> dev.ujhhgtg.via.reader.PageSecurityDialogs.cookies(host, currentUrl)
                3 -> openCurrentSiteSettings(2)
                4 -> showPageScripts()
                5 -> showHistory(dev.ujhhgtg.via.reader.ReaderPageToolsFragment.historyQuery(currentUrl))
                6 -> dev.ujhhgtg.via.tools.QrCodeDialog.show(host, currentUrl)
                7 -> changeReaderMode(result.getBoolean("show_reader"))
                dev.ujhhgtg.via.reader.ReaderPageToolsFragment.STYLE_CHANGED -> currentWeb?.let { page ->
                    ReaderMode(host).applyStyle(page, preferences.readerThemeColor, preferences.readerTextSize, preferences.readerCustomCss.orEmpty())
                    updateReaderColor(page, true)
                }
                dev.ujhhgtg.via.reader.ReaderPageToolsFragment.EDIT_CSS -> {
                    parentFragmentManager.setFragmentResultListener("edit_text_result", viewLifecycleOwner) { name, value ->
                        value.getString("text")?.let { css ->
                            preferences.readerCustomCss = css
                            currentWebView()?.let { page ->
                                ReaderMode(host).applyStyle(page, preferences.readerThemeColor, preferences.readerTextSize, css)
                                updateReaderColor(page, true)
                            }
                        }
                        parentFragmentManager.clearFragmentResultListener(name)
                    }
                    host.navigate(dev.ujhhgtg.via.settings.TextEditorFragment.newInstance(getString(R.string.custom_reader_css), preferences.readerCustomCss, getString(R.string.custom_reader_css), true))
                }
            }
            childFragmentManager.clearFragmentResult(key)
        }
        val overlay = dev.ujhhgtg.via.reader.ReaderPageToolsFragment.newInstance(url, title, pageFlags, width, gravity,
            homepage = toolbarColors.usesDefaultBackground && nativeBackground.hasImage)
        childFragmentManager.beginTransaction().setReorderingAllowed(true)
            .add(R.id.browser_overlay_container, overlay, "site_info")
            .addToBackStack(null).commit()
        // F9 -> U7 -> i6.c0.g starts at state 0, then binds the actual reader state.
        if (web.view.isShown) ReaderMode(host).detectPrepared(web, overlay::updateReaderState)
        onBrowserPopupOpened()
    }

    /**
     * c8.s6$g onLongClick for the n0 address label (f9688y): copy the page
     * url when one is loaded, paste into the address editor, and paste-and-go
     * through the clipboard URL candidates, anchored above the toolbar.
     */
    private fun showAddressMenu(anchor: View) {
        val url = visibleUrl(current())
        val menu = AnchorTextMenu(host)
        if (url.isNotEmpty() && !isLocal(url)) {
            // c8.s6$g uses android.R.string.copy (0x1040001) here.  The
            // app-local action_copy resource is specifically “Copy link” and
            // belongs to page/link context menus, not the address field.
            menu.add(text(android.R.string.copy)) {
                copy(url)
                ViaToast.makeText(host, text(R.string.toast_copy_url_successful), ViaToast.LENGTH_SHORT).show()
            }
        }
        menu.add(getString(android.R.string.paste)) {
            dismissBrowserOverlay()
            showAddressInput(prefill = clipboardText())
        }
        menu.add(text(R.string.paste_and_go)) { pasteAndGo() }
        menu.show(anchor)
    }

    /** g6.n.d: the current clipboard text, empty when unavailable. */
    private fun clipboardText(): String = try {
        val manager = host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    } catch (_: Exception) {
        ""
    }

    /**
     * c8.s6.ga: paste-and-go. With several URLs in the clipboard the source
     * asks which one to open (Open all inserts them all after the current
     * tab, selecting the last); a single URL navigates directly, and plain
     * text goes through the search template.
     */
    private fun pasteAndGo() {
        val text = clipboardText()
        if (text.isEmpty()) return
        val candidates = UrlResolver.extractUrls(text)
        when {
            candidates.size > 1 -> ViaDialog(host)
                .title(R.string.message)
                .message(text)
                .items(candidates.toTypedArray(), onClick = { which -> navigate(candidates[which]) })
                .positive(R.string.open_all) { _, _ ->
                    candidates.forEachIndexed { index, candidate ->
                        tabs.createTab(candidate, select = index == candidates.lastIndex)
                    }
                    attachSelected()
                }
                .negative(android.R.string.cancel)
                .show()
            candidates.isNotEmpty() -> navigate(candidates[0])
            else -> navigate(text)
        }
    }

    /** Search-engine selector used by the leading address-bar long press. */
    private fun showSearchEngineSelector() {
        val providers = SearchProviders(host, preferences, database)
        val options = providers.list()
        if (options.isEmpty()) return
        val currentId = preferences.searchMode
        ViaDialog(host).title(R.string.search)
            .singleChoice(options.map { it.name }.toTypedArray(), options.indexOfFirst { it.id == currentId }) { index ->
                val selected = options.getOrNull(index) ?: return@singleChoice
                if (selected.id != currentId) {
                    providers.selectDefault(selected.id)
                    remoteHomepageSuggestions.clearCache()
                }
            }.show()
    }

    /**
     * e8.i/t4.b/c8.s6$g: the engine resolves the long-pressed element (link url, image src, title)
     * and the o9 element menu opens anchored to the press point. The probe only runs outside game mode (s6.E).
     */
    private fun installContextMenu(tab: BrowserTab) {
        tab.page.setContextMenuHandler(object : dev.ujhhgtg.via.engine.ContextMenuHandler {
            override val enabled get() = !gameMode.enabled
            override fun onContextMenu(target: dev.ujhhgtg.via.engine.ContextTarget) {
                // n9 unwraps the favorite jump proxy (z8.w2.f) before building
                // the menu, so the rows operate on the real url.
                val link = UrlResolver.unwrapErrorJump(target.linkUrl) ?: target.linkUrl ?: ""
                showElementMenu(target.rawX, target.rawY, link, target.srcUrl, target.title, secondPass = false)
            }
        })
    }

    /**
     * c8.s6.o9/Z5: the element long-press menu, mirroring the original's
     * page-type switch (u9.d.d): the generated homepage suppresses presses on
     * its own document (the logo anchor resolves to the page url) and offers
     * Sort/Edit/Delete for a favorite link without an image; the history
     * document offers Delete/Delete all; log/res pages offer resource actions;
     * normal pages get the image/page-info set. Copy/share hide for folder://, v:// and file://
     * links, and the hiddenctxmenus customization moves hidden ids behind a
     * More options entry whose second pass shows them plus Customize menu.
     */
    private fun showElementMenu(rawX: Int, rawY: Int, link: String, src: String?, title: String?, secondPass: Boolean) {
        val tab = current() ?: return
        // G8() is the WebView url, so the generated homepage (file://…/homepage2.html)
        // resolves to u9.d.d's type 1 even though the tab records about:home.
        val pageUrl = tab.page.url ?: tab.url
        val pageType = pageTypeOf(pageUrl)
        // No page loaded and an image pressed: the original opens the image
        // download directly (c8.s6.g8) instead of showing the menu.
        if ((pageType == 0 || pageType == 13) && !src.isNullOrEmpty()) {
            requestDownload(tab, src, tab.page.userAgent, "attachment", if (URLUtil.isNetworkUrl(src)) "image/*" else null, -1)
            return
        }
        if (link.isEmpty() && src.isNullOrEmpty() || pageType == 1 && link == pageUrl) return
        if (pageType == -1 && customView == null) dispatchElementContextMenu(tab, rawX, rawY)
        val items = mutableListOf<Pair<Int, String>>()
        if (!customMode && URLUtil.isNetworkUrl(link)) {
            items += 0 to text(R.string.action_open_in_background)
            items += 1 to text(R.string.action_open_in_new)
        }
        var base = 2
        when (pageType) {
            1 -> if (src == null) {
                // o9's homepage branch: Sort/Edit/Delete, no isNetworkUrl gate,
                // and no image/page-info rows unless an image was pressed.
                items += 32 to text(R.string.sort)
                items += 14 to text(R.string.action_edit)
                items += 15 to text(R.string.action_delete)
                base = 3
            } else base = elementImageRows(items, src, title, pageUrl)
            3 -> { items += 16 to text(R.string.action_delete); items += 17 to text(R.string.action_delete_all) }
            // o9 types 2/11: bookmark rows get Edit/Add to homepage/Delete, folder rows (v://bookmarks) Edit/Delete.
            2, 11 -> if (link.isNotEmpty() && !link.endsWith('=')) {
                val folderRow = link.startsWith("v://bookmarks", true)
                items += (if (folderRow) 12 else 9) to text(R.string.action_edit)
                if (!folderRow) items += 10 to text(R.string.action_add_to_homepage)
                items += (if (folderRow) 13 else 11) to text(R.string.action_delete)
                base = items.size - (if (!customMode && URLUtil.isNetworkUrl(link)) 2 else 0)
            } else base = 0
            // o9 type 7: saved pages only offer Delete.
            7 -> { items += 18 to text(R.string.action_delete); base = 1 }
            6, 10 -> {
                val resourceRows = resourceDocumentActions.rows(link, pageType == 10, adBlockingFor(link), tabs.isResourceBlocked(link))
                items += resourceRows
                base = resourceRows.size
            }
            else -> base = elementImageRows(items, src, title, pageUrl)
        }
        // The copy/share pair appends on every branch that did not return early.
        if (link.isNotEmpty() && !link.startsWith("folder://", true) && !link.startsWith("v://", true) && !link.startsWith("file://", true)) {
            items += 3 to text(R.string.action_copy)
            items += 31 to text(R.string.action_share)
        }
        val shown = filterHiddenElementRows(items, base, secondPass)
        if (shown.isEmpty()) return
        showElementMenuList(rawX, rawY, link, src, title, pageUrl, shown)
    }

    /** o9's generic branch: image rows, page info, copy link text, scan QR code; base 0. */
    private fun elementImageRows(items: MutableList<Pair<Int, String>>, src: String?, title: String?, pageUrl: String): Int {
        val srcUsable = !src.isNullOrEmpty() && (URLUtil.isNetworkUrl(src) || src.startsWith("data:", true) || src.startsWith("file://", true))
        if (!src.isNullOrEmpty()) {
            items += 2 to text(R.string.view_image)
            items += 38 to text(R.string.download_image)
            items += 6 to text(R.string.save_image)
            if (srcUsable) items += 33 to text(R.string.share_image)
            if (URLUtil.isNetworkUrl(src)) items += 7 to text(R.string.search_by_image)
            if (URLUtil.isNetworkUrl(pageUrl) && adBlockingFor(pageUrl)) items += 8 to text(R.string.picture_mode)
        }
        items += 22 to text(R.string.page_info)
        if (URLUtil.isNetworkUrl(pageUrl) && adBlockingFor(pageUrl) && !adMarkedFor(pageUrl)) items += 23 to text(R.string.action_mark)
        if (!title.isNullOrEmpty()) items += 29 to text(R.string.copy_link_text)
        if (srcUsable) items += 34 to text(R.string.scan_qr_code)
        return 0
    }

    /**
     * o9's hiddenctxmenus tail: hidden ids move (from the end, keeping order)
     * into a second list. First pass shows the remainder plus More options,
     * or Customize menu when the list outgrew the page's base rows; the second
     * pass shows exactly the hidden rows plus Customize menu.
     */
    private fun filterHiddenElementRows(items: MutableList<Pair<Int, String>>, base: Int, secondPass: Boolean): List<Pair<Int, String>> {
        val hiddenIds = behavior.hiddenContextMenus()
        if (hiddenIds.isEmpty()) {
            if (items.size > base) items += 37 to text(R.string.customize_menu)
            return items
        }
        val hidden = mutableListOf<Pair<Int, String>>()
        for (index in items.indices.reversed()) {
            if (items[index].first in hiddenIds) hidden.add(0, items.removeAt(index))
            if (hidden.size == hiddenIds.size) break
        }
        if (secondPass) {
            if (hidden.isNotEmpty()) hidden += 37 to text(R.string.customize_menu)
            return hidden.toList()
        }
        if (hidden.isNotEmpty()) items += 36 to text(R.string.more_options)
        else if (items.size > base) items += 37 to text(R.string.customize_menu)
        return items
    }

    /** ua.e1: the global ad-block flag with the site configuration override. */
    private fun adBlockingFor(url: String): Boolean =
        siteConfigurations.get(UrlResolver.host(url) ?: url)?.adBlocking(preferences.webFlags) ?: (preferences.webFlags and 1 != 0)

    /** ua.b1: the original's r9.e.n already-marked check; this app marks pages by appending a custom filter rule. */
    private fun adMarkedFor(url: String): Boolean {
        val siteHost = UrlResolver.host(url).orEmpty()
        return siteHost.isNotEmpty() && dev.ujhhgtg.via.browser.filter.FilterStore(host).readCustom().contains(siteHost)
    }

    /** c8.s6.qb: pages with their own context menus get a synthetic event at the press point. */
    private fun dispatchElementContextMenu(tab: BrowserTab, rawX: Int, rawY: Int) {
        val webView = tab.page
        val surface = webView.view
        if (surface.width <= 0 || surface.height <= 0) return
        val position = IntArray(2); surface.getLocationOnScreen(position)
        val rx = (rawX - position[0]).toFloat() / surface.width
        val ry = (rawY - position[1]).toFloat() / surface.height
        webView.evaluate(
            "(function(){var a=__RX__,c=__RY__;a=Math.max(0,Math.min(1,a));c=Math.max(0,Math.min(1,c));if(window.visualViewport){var b=window.visualViewport;a=b.offsetLeft+a*b.width;b=b.offsetTop+c*b.height}else a*=window.innerWidth,b=c*window.innerHeight;c=document.elementFromPoint(a,b)||document;a=new MouseEvent(\"contextmenu\",{bubbles:!0,cancelable:!1,view:window,button:2,buttons:0,clientX:a,clientY:b});c.dispatchEvent(a)})();"
                .replace("__RX__", String.format(java.util.Locale.ROOT, "%.2f", rx))
                .replace("__RY__", String.format(java.util.Locale.ROOT, "%.2f", ry)),
            null,
        )
    }

    private fun showElementMenuList(rawX: Int, rawY: Int, link: String, src: String?, title: String?, pageUrl: String, items: List<Pair<Int, String>>) {
        val rootPosition = IntArray(2); root.getLocationOnScreen(rootPosition)
        ViaDialog(host).items(items.map { it.second }.toTypedArray(), onClick = { which ->
            when (items[which].first) {
                0 -> {
                    tabs.createTab(link, select = false, insertIndex = tabs.indexOf(current()) + 1)
                    // Background creation leaves the selected WebView mounted.
                    // Only refresh the chrome count; reattaching here causes a
                    // visible flash, and the original link menu has no toast.
                    updateChrome()
                }
                1 -> { tabs.createTab(link, select = true, insertIndex = tabs.indexOf(current()) + 1); attachSelected(); updateChrome() }
                2 -> {
                    val address = src ?: return@items
                    val domain = UrlResolver.host(address).orEmpty()
                    val configuration = (siteConfigurations.get(domain) ?: dev.ujhhgtg.via.data.SiteConfiguration(domain, null))
                        .withEnabled(true).withBoolean(4, true)
                    siteConfigurations.setTemporary(configuration)
                    tabs.createTab(address, select = true, insertIndex = tabs.indexOf(current()) + 1)
                    siteConfigurations.clearTemporary(domain)
                    attachSelected(); updateChrome()
                }
                3 -> { copy(if (pageTypeOf(pageUrl) == 6) link.substring(link.indexOf("://") + 3) else link); ViaToast.makeText(host, text(R.string.toast_copy_url_successful), ViaToast.LENGTH_SHORT).show() }
                31 -> shareUrl(link)
                38 -> current()?.let { tab -> requestDownload(tab, src!!, tab.page.userAgent, "attachment", if (URLUtil.isNetworkUrl(src)) "image/*" else null, -1) }
                6 -> currentWebView()?.let { resourceImageActions.perform(it, src!!, dev.ujhhgtg.via.browser.ResourceImageActions.SAVE, tabs.bridgeSecret) }
                33 -> currentWebView()?.let { resourceImageActions.perform(it, src!!, dev.ujhhgtg.via.browser.ResourceImageActions.SHARE, tabs.bridgeSecret) }
                19, 20, 21, 24, 25, 26, 27 -> resourceDocumentActions.perform(items[which].first, link, current()?.title,
                    download = { url ->
                        val userAgent = currentWebView()?.userAgent
                        lifecycleScope.launch {
                            val request = DownloadRequest(url, userAgent = userAgent,
                                contentDisposition = "attachment", cookies = dev.ujhhgtg.via.engine.Engines.backend.cookies.get(url))
                            downloadDestinations.show(request, -1)
                        }
                    },
                    clearLog = {
                        tabs.all.firstOrNull { it.id == resourceSourceTabId }?.let { tabs.clearResources(it) }
                        // ua.t0 calls x1; x1 does not regenerate page types 6/10, so the current snapshot stays visible.
                    },
                    reloadFilters = { reloadTabPreferences() })
                7 -> showImageSearchEngines(src!!, rawX, rawY)
                8 -> currentWebView()?.let { dev.ujhhgtg.via.tools.PictureMode.open(it, host) }
                9 -> editBookmarkRow(link)
                10 -> bookmarks.findByUrl(link)?.let { item ->
                    // ua.l0: the bookmark becomes a homepage favorite.
                    if (favorites.save(Favorite(url = item.url, title = item.title)) > 0) {
                        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
                        toast(text(R.string.added_favorite_hint))
                    }
                }
                11 -> if (bookmarks.deleteByUrl(link)) { GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS); reloadInternalDocument() }
                12, 13 -> bookmarkFolderId(link)?.let { folderId ->
                    if (items[which].first == 12) {
                        // s6.p5 only consumes the editor's result.
                        parentFragmentManager.setFragmentResultListener(dev.ujhhgtg.via.records.BookmarkFolderEditorFragment.RESULT, viewLifecycleOwner) { key, _ ->
                            parentFragmentManager.clearFragmentResultListener(key)
                        }
                        host.navigate(dev.ujhhgtg.via.records.BookmarkFolderEditorFragment.newInstance(folderId, null, true))
                    } else {
                        // s6 case 13 formats delete_item_message with the folder id, not its title.
                        ViaDialog(host).title(R.string.action_delete).message(getString(R.string.delete_item_message, folderId))
                            .positive(R.string.action_delete) { _, _ ->
                                if (bookmarks.deleteFolders(folderId) > 0) { GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS); reloadInternalDocument() }
                            }.negative(android.R.string.cancel).show()
                    }
                }
                18 -> {
                    // s6 case 18: the row links file://<dir>/<encoded name>; a failure names the path.
                    val path = link.substring(7, link.lastIndexOf('/') + 1) + Uri.decode(link.substring(link.lastIndexOf('/') + 1))
                    if (File(path).delete()) reloadInternalDocument() else toast(getString(R.string.deleted_failed) + path)
                }
                14 -> openFavoriteEditor(link)
                15 -> confirmDeleteHomeFavorite(link)
                16 -> confirmDeleteHistoryEntry(link)
                17 -> confirmClearHistory()
                22 -> {
                    val info = buildString {
                        if (!title.isNullOrEmpty()) { append(text(R.string.page_info_title)); append("\n"); append(title) }
                        if (pageUrl.isNotEmpty()) { if (isNotEmpty()) append("\n\n"); append(text(R.string.page_info_url)); append("\n"); append(pageUrl) }
                        if (link.isNotEmpty()) { if (isNotEmpty()) append("\n\n"); append(text(R.string.page_info_link)); append("\n"); append(link) }
                    }
                    ViaDialog(host).title(R.string.page_info).message(info).positive(android.R.string.ok).show()
                }
                23 -> currentWebView()?.let { adMarker.start(it) }
                29 -> { copy(title!!.trim()); ViaToast.makeText(host, text(R.string.toast_copy_text_successful), ViaToast.LENGTH_SHORT).show() }
                32 -> openFavoriteSortSheet(link)
                34 -> currentWebView()?.let { resourceImageActions.perform(it, src!!, dev.ujhhgtg.via.browser.ResourceImageActions.SCAN, tabs.bridgeSecret) }
                36 -> showElementMenu(rawX, rawY, link, src, title, secondPass = true)
                37 -> openSettings("customize_menu")
            }
        }).onDismiss { currentWebView()?.view?.requestFocus() }
            .showAnchored(root, rawX - rootPosition[0], rawY - rootPosition[1])
    }

    /** z8.w2.i: the folder id of a v://bookmarks/?folder= link. */
    private fun bookmarkFolderId(link: String): String? {
        if (!link.startsWith("v://bookmarks", true)) return null
        val start = link.indexOf("?folder=").takeIf { it >= 0 }?.plus(8) ?: return null
        val end = link.indexOf('&', start).takeIf { it >= 0 } ?: link.length
        return link.substring(start, end).takeIf(String::isNotEmpty)
    }

    /** c8.s6.j8/k8/H5: edit the bookmark behind a row, or add it when none exists. */
    private fun editBookmarkRow(link: String) {
        val internal = dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(host, link)
        val existing = if (internal) null else bookmarks.findByUrl(link)
        parentFragmentManager.setFragmentResultListener("bookmarkDialogResult2", viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResultListener(key)
            if (result.getString("id") != null) { GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS); reloadInternalDocument() }
        }
        dev.ujhhgtg.via.records.BookmarkEditDialogFragment.newInstance(id = existing?.id,
            url = if (internal) "https://" else link, title = existing?.title ?: "").show(childFragmentManager, "bookmark_edit")
    }

    /** c8.s6.Ca: the reverse image search engines, opened in a new tab. */
    private fun showImageSearchEngines(src: String, rawX: Int, rawY: Int) {
        val engines = listOf(
            1 to getString(R.string.google_url) to "https://www.google.com/searchbyimage?safe=off&sbisrc=tg&image_url=",
            2 to getString(R.string.google_lens_url) to "https://lens.google.com/uploadbyurl?url=",
            3 to getString(R.string.bing_url) to "https://www.bing.com/images/search?view=detailv2&iss=sbi&form=SBIVSP&sbisrc=UrlPaste&q=imgurl:",
            4 to getString(R.string.tineye_url) to "https://tineye.com/search/?url=",
            5 to getString(R.string.yandex_url) to "https://yandex.com/images/touch/search?family=yes&rpt=imageview&url=",
            6 to getString(R.string.baidu_url) to "https://graph.baidu.com/details?isfromtusoupc=1&tn=pc&carousel=0&promotion_name=pc_image_shituindex&extUiData%5bisLogoShow%5d=1&image=",
            7 to getString(R.string.haosou_url) to "https://st.so.com/r?img_url=",
            8 to getString(R.string.saucenao_url) to "https://saucenao.com/search.php?db=999&url=",
            9 to getString(R.string.iqdb_url) to "https://iqdb.org/?url=",
            10 to getString(R.string.iqdb_3d_url) to "https://3d.iqdb.org/?url=",
            11 to getString(R.string.whatanime_url) to "https://trace.moe/?url=",
            12 to getString(R.string.ascii2d_url) to "https://ascii2d.net/search/url/",
        )
        val rootPosition = IntArray(2); root.getLocationOnScreen(rootPosition)
        ViaDialog(host).title(R.string.search_by_image).items(engines.map { it.first.second }.toTypedArray(), onClick = { which ->
            val target = engines[which].second + Uri.encode(src)
            tabs.createTab(target, select = true); attachSelected(); updateChrome()
        }).showAnchored(root, rawX - rootPosition[0], rawY - rootPosition[1])
    }

    /** z8.f1.k: the source share entry point. */
    private fun shareUrl(url: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), getString(R.string.action_share)))
    }

    /** u9.d.d: the internal document type from the WebView file url; 0 = no page, -1 = web page. */
    private fun pageTypeOf(url: String?): Int {
        if (url.isNullOrEmpty()) return 0
        val prefix = "file://${host.filesDir.path}/"
        if (!url.startsWith(prefix)) return -1
        val name = url.substring(prefix.length).substringBefore('?').substringBefore('#')
        return when (name) {
            "homepage.html", "homepage2.html" -> 1
            "bookmarks.html" -> 2
            "folder.html" -> 11
            "history.html" -> 3
            "catalog.html" -> 5
            "log.html" -> 6
            "res.html" -> 10
            "save.html" -> 7
            "about.html" -> 4
            "blank.html" -> 12
            "images.html" -> 13
            else -> -1
        }
    }

    /** u9.d.d == 1: the generated homepage document. */

    /** c8.s6.m8/n8: the full-screen favorite editor, then the fav_result refresh. */
    private fun openFavoriteEditor(url: String, title: String? = null) {
        if (url.isEmpty()) return
        parentFragmentManager.setFragmentResultListener("fav_result", viewLifecycleOwner) { _, result ->
            parentFragmentManager.clearFragmentResultListener("fav_result")
            if (result.getInt("result_id", -1) >= 0) refreshAfterFavoriteSaved()
        }
        dev.ujhhgtg.via.home.FavoriteEditorFragment.newInstance(url, title).show(childFragmentManager, "favorite_editor")
    }

    /** c8.s6.I5: internal documents reload so the saved favorite shows; web pages toast instead. */
    private fun refreshAfterFavoriteSaved() {
        val pageType = pageTypeOf(currentWebView()?.url)
        if (pageType > 0) reloadInternalDocument()
        else ViaToast.makeText(host, text(R.string.added_to_homepage), ViaToast.LENGTH_SHORT).show()
    }

    /** ua.y1: the visible internal document reloads after its backing data changed. */
    private fun reloadInternalDocument() {
        current()?.let { tab ->
            // The tab records the logical url (about:home) while the WebView
            // loads the generated file; both identify the home document.
            if (pageTypeOf(tab.page.url) == 1 || tab.url == "about:home") {
                HomeDocument(host, preferences).write(favorites.list(), night(), browserLayout.toolbarsShown)
                GeneratedDocumentState.clear(GeneratedDocumentState.HOME_CONTENT or GeneratedDocumentState.HOME_STYLE)
                tab.page.reload()
            } else {
                val kind = when (pageTypeOf(tab.page.url)) {
                    2 -> InternalDocuments.Kind.BOOKMARKS
                    3 -> InternalDocuments.Kind.HISTORY
                    4 -> InternalDocuments.Kind.ABOUT
                    5 -> InternalDocuments.Kind.CATALOG
                    7 -> InternalDocuments.Kind.SAVED_PAGES
                    11 -> InternalDocuments.Kind.FOLDER
                    12 -> InternalDocuments.Kind.BLANK
                    else -> null
                }
                if (kind != null) {
                    val folder = if (kind == InternalDocuments.Kind.FOLDER) tab.page.url!!.toUri().getQueryParameter("folder") else null
                    InternalDocuments.write(host, preferences, database, kind, folder)
                }
                if (pageTypeOf(tab.page.url) > 0) tab.page.reload()
            }
        }
    }

    /** c8.s6.R9/X2/S9: measure the homepage's first favorite row, then open the sort sheet below it. */
    private fun openFavoriteSortSheet(@Suppress("UNUSED_PARAMETER") url: String) {
        val webView = currentWebView() ?: return openFavoriteSortSheet(0, 0)
        webView.evaluate(
            "(function(){var a=document.getElementsByClassName(\"box\"),b=0,c=a.length;if(0<c){var d=a[0].getBoundingClientRect().top;for(i=0;i<c;i++)if(a[i].getBoundingClientRect().top==d)b++;else break;return (d>0?d<<6:0)|b}return b})();",
        ) { value ->
            var count = 0
            var height = 0
            val digits = value.trim().removePrefix("\"").removeSuffix("\"")
            if (digits.isNotEmpty() && digits.all(Char::isDigit)) runCatching {
                val packed = digits.toInt()
                val top = packed shr 6
                if (top > 0) {
                    val topPx = dp(top)
                    // X2 uses the full c8.f8.e canvas; f9 binds y0 to f8.n (the TOP toolbar).
                    val topBar = if (browserLayout.top.isVisible) browserLayout.top.height else 0
                    height = root.height - topPx - topBar + dp(54) + dp(4)
                }
                count = packed and 0x3F
            }
            host.runOnUiThread { openFavoriteSortSheet(count, height) }
        }
    }

    /** c8.s6.S9: the favoriteChanged sheet refreshes the home document when it closes. */
    private fun openFavoriteSortSheet(count: Int, height: Int) {
        reloadInternalDocumentOnFavoriteChanged()
        // qa.e1: a bottom sheet over the browser canvas, full width, height
        // bounded by the measured first-row top (b3's clamp) and 3/5 screen.
        val sheet = dev.ujhhgtg.via.home.FavoriteSortSheet.newInstance(count, height)
        // c8.s6.S9 adds qa.e1 to g6.i.a, the activity root, without hiding the browser.
        parentFragmentManager.beginTransaction().setReorderingAllowed(true)
            .setCustomAnimations(R.anim.sheet_enter, R.anim.sheet_hold, R.anim.sheet_hold, R.anim.sheet_exit)
            .add(R.id.fragment_container, sheet, "favorite_sort")
            .addToBackStack(null).commit()
    }

    private fun reloadInternalDocumentOnFavoriteChanged() {
        parentFragmentManager.setFragmentResultListener("favoriteChanged", viewLifecycleOwner) { _, _ ->
            parentFragmentManager.clearFragmentResultListener("favoriteChanged")
            reloadInternalDocument()
        }
    }

    /** c8.s6.sa/Aa/F3: download the image to cache, decode its QR code, offer open/search. */
    private fun decodeImageQrCode(src: String) {
        viewLifecycleOwner.launchIo({
            runCatching {
                val bitmap: android.graphics.Bitmap? = when {
                    src.startsWith("file://") -> BitmapFactory.decodeFile(src.removePrefix("file://"))
                    src.startsWith("data:") -> {
                        val payload = android.util.Base64.decode(src.substringAfter("base64,"), android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(payload, 0, payload.size)
                    }
                    src.startsWith("content://") -> host.contentResolver.openInputStream(src.toUri())?.use { BitmapFactory.decodeStream(it) }
                    else -> {
                        val response = dev.ujhhgtg.via.common.httpClient.get(src)
                        if (!response.status.isSuccess()) null
                        else response.bodyAsBytes().let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }
                }
                bitmap?.let { image -> dev.ujhhgtg.via.tools.QrBitmaps.decode(image).also { image.recycle() } }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
        }, { result ->
            dev.ujhhgtg.via.tools.QrResults.showImageResult(host, result) { value ->
                newTab(UrlResolver.resolveInput(value, preferences.effectiveSearchUrl()) ?: value)
            }
        })
    }

    /** c8.s6 case 16: delete the history entry behind the pressed row, then reload the document. */
    private fun confirmDeleteHistoryEntry(url: String) {
        if (url.isEmpty()) return
        if (history.deleteByUrl(url)) {
            GeneratedDocumentState.mark(GeneratedDocumentState.HISTORY)
            reloadInternalDocument()
        }
    }

    /** c8.s6 case 17: the Delete all confirmation, then the history document reloads. */
    private fun confirmClearHistory() {
        ViaDialog(host).title(R.string.action_delete_all).message(R.string.dialog_sure)
            .positive(android.R.string.ok) { _, _ -> history.clear(); GeneratedDocumentState.mark(GeneratedDocumentState.HISTORY); reloadInternalDocument() }
            .negative(android.R.string.cancel).show()
    }

    /** t9.a.a: the delete confirmation, then the home page refreshes. */
    private fun confirmDeleteHomeFavorite(url: String) {
        val repository = FavoritesRepository(database)
        val favorite = repository.findByUrl(url) ?: return
        ViaDialog(host).title(R.string.action_delete)
            .message(getString(R.string.delete_favorite_message, favorite.title.orEmpty().ifEmpty { url }))
            .positive(android.R.string.ok) {
                _, _ -> repository.deleteByUrl(url)
                // t9.a deletes through fa.b then marks w9.n's homepage bit
                // before the optional immediate reload callback.
                GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
                reloadHomeDocument()
            }
            .negative(android.R.string.cancel).show()
    }

    /** ua.y1: favorites changed, so the home document reloads. */
    private fun reloadHomeDocument() {
        reloadInternalDocument()
    }

    private fun refreshCustomizedHome() {
        val mask = GeneratedDocumentState.HOME_CONTENT or GeneratedDocumentState.HOME_STYLE
        if (!homeDocumentDirty && !GeneratedDocumentState.hasAny(mask)) return
        val homeTabs = tabs.all.filter { it.url == "about:home" || pageTypeOf(it.page.url) == 1 }
        if (homeTabs.isEmpty()) return
        val document = HomeDocument(host, preferences)
        document.write(favorites.list(), night(), browserLayout.toolbarsShown)
        GeneratedDocumentState.clear(mask)
        homeDocumentDirty = false
        homeTabs.forEach { tab ->
            tab.page.view.background = null
            tab.page.view.setBackgroundColor(Color.TRANSPARENT)
            tab.page.reload()
        }
    }



    private fun showInternal(tab: BrowserTab, url: String) {
        when (url) {
            "about:home", "v://home" -> {
                val document = HomeDocument(host, preferences)
                val file = document.write(favorites.list(), night(), browserLayout.toolbarsShown)
                GeneratedDocumentState.clear(GeneratedDocumentState.HOME_CONTENT or GeneratedDocumentState.HOME_STYLE)
                tab.page.view.background = null; tab.page.view.setBackgroundColor(Color.TRANSPARENT)
                tab.title = text(R.string.home); tabs.loadInternalPage(tab, file, "about:home")
            }
            "about:blank" -> {
                val file = InternalDocuments.write(host, preferences, database, InternalDocuments.Kind.BLANK)
                tabs.loadInternalPage(tab, file, "about:blank")
            }
            "about:bookmarks" -> {
                val file = InternalDocuments.write(host, preferences, database, InternalDocuments.Kind.BOOKMARKS)
                tabs.loadInternalPage(tab, file, "about:bookmarks")
            }
            else -> dispatchBrowserCommand(url)
        }
    }

    /** ua.r1/t1/h: retain the source tab while browsing generated pages and reuse the current internal tab. */
    private fun showNetworkLog(mediaOnly: Boolean) {
        val currentTab = current() ?: return
        val currentUrl = currentTab.page.url ?: currentTab.url
        if (!dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(host, currentUrl)) resourceSourceTabId = currentTab.id
        val source = tabs.all.firstOrNull { it.id == resourceSourceTabId }
        val resources = source?.let { tabs.resources(it) }.orEmpty()
        val available = if (mediaOnly) tabs.hasMediaResources(source) else resources.isNotEmpty()
        if (!available) {
            toast(if (mediaOnly) text(R.string.no_resource) + text(R.string.resource_sniffer_hint) else text(R.string.no_network_log))
            return
        }
        val file = dev.ujhhgtg.via.browser.ResourceDocument(host, preferences).write(resources, mediaOnly, night())
        if (dev.ujhhgtg.via.browser.ResourceDocument.isInternalPage(host, currentUrl)) tabs.navigate(currentTab, file)
        else tabs.createTab(file, select = true, insertIndex = tabs.indexOf(currentTab) + 1)
        attachSelected()
    }

    private inner class BrowserHost : TabController.Host {
        private val localNetworkErrorChecks = java.util.WeakHashMap<dev.ujhhgtg.via.engine.EnginePage, String>()

        private fun requestLocalNavigation(tab: BrowserTab, url: String, deferUntilResumed: Boolean = false,
            proceed: () -> Unit): Boolean {
            if (LocalNetworkAccess.hasPermission(host) || !LocalNetworkAccess.isKnownLocalUrl(host, url) ||
                !isAdded || isHidden || current()?.id != tab.id) return false
            if (!isVisible || !host.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                if (!deferUntilResumed) return false
                // buildView creates the selected Intent/home tab before onCreateView returns.
                // Wait once for that view to be usable instead of starting a blocked TCP request.
                val owner = viewLifecycleOwner
                val web = tab.page
                owner.lifecycle.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
                    override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) source.lifecycle.removeObserver(this)
                        if (event != androidx.lifecycle.Lifecycle.Event.ON_RESUME) return
                        source.lifecycle.removeObserver(this)
                        root.post {
                            if (!isAdded) return@post
                            // Restoring several tabs can change the selected tab before RESUME.
                            // Background tabs keep their ordinary load/error path without a prompt.
                            if (!isVisible || current()?.page !== web ||
                                !requestLocalNavigation(tab, url, proceed = proceed)) proceed()
                        }
                    }
                })
                return true
            }
            LocalNetworkAccess.request(host) {
                // On denial, let WebView display its normal blocked-network error. No automatic
                // permission retry follows: ViaActivity records this result until a new action.
                if (isAdded && current()?.id == tab.id) { proceed(); updateChrome() }
            }
            return true
        }

        override fun onBeforeNavigate(tab: BrowserTab, url: String, proceed: () -> Unit): Boolean {
            if (isVisible && current()?.id == tab.id) host.allowLocalNetworkPermissionRetry()
            return requestLocalNavigation(tab, url, deferUntilResumed = this@BrowserFragment.view == null, proceed = proceed)
        }

        override fun onBeforeRestore(tab: BrowserTab, url: String, proceed: () -> Unit): Boolean =
            requestLocalNavigation(tab, url, deferUntilResumed = this@BrowserFragment.view == null, proceed = proceed)

        override fun onError(tab: BrowserTab, error: dev.ujhhgtg.via.engine.LoadError) {
            if (!error.method.equals("GET", true) || !error.mayBeNetworkUnreachable ||
                LocalNetworkAccess.hasPermission(host) || host.localNetworkPermissionDeclined ||
                !isVisible || current()?.id != tab.id) return
            val web = tab.page
            val failedUrl = error.url
            if (localNetworkErrorChecks[web] == failedUrl) return
            localNetworkErrorChecks[web] = failedUrl
            LocalNetworkAccess.resolveLocalUrl(host, failedUrl) { local ->
                if (local && isAdded && isVisible && current()?.page === web && web.url == failedUrl) {
                    LocalNetworkAccess.request(host) { granted ->
                        if (granted && isAdded && isVisible && current()?.page === web && web.url == failedUrl) web.reload()
                    }
                }
            }
        }

        override fun onResourceAvailabilityChanged(tab: BrowserTab, hasMedia: Boolean) {
            if (current()?.id == tab.id) snifferButton.update(preferences.showSnifferButton, hasMedia, true)
        }
        override fun onCurrentPageChanged(tab: BrowserTab) {
            if (current()?.id == tab.id) attachSelected()
        }
        override fun onReaderCheckRequested() {
            val view = currentWebView() ?: return
            ReaderMode(host).detectPrepared(view) { state ->
                menuDialog?.updateReaderState(state)
                if (state == 5) ReaderMode(host).enter(view, preferences.readerThemeColor, preferences.readerTextSize, preferences.readerCustomCss.orEmpty()) { active ->
                    if (active) updateReaderColor(view, true)
                }
            }
        }
        override fun onPageCreated(tab: BrowserTab) {
            passwordForms.attach(tab.page, tabs.bridgeSecret); adMarker.attach(tab.page, tabs.bridgeSecret)
            installSelectionActions(tab.page)
        }
        override fun onPageStarted(tab: BrowserTab, url: String) {
            localNetworkErrorChecks.remove(tab.page)
            pageColors.onPageStarted(tab.page, tab.page.url ?: url)
            adMarker.onPageStarted(tab.page)
            showPasswordAssist(tab.page, false)
            if (current()?.id == tab.id) updateChrome()
        }
        override fun onPageFinished(tab: BrowserTab, url: String, title: String?) {
            pageColors.onPageFinished(tab.page, tab.page.url ?: url)
            if (privacyPolicy.mayRecord(url) && !isLocal(url) && history.record(url, title)) {
                GeneratedDocumentState.mark(GeneratedDocumentState.HISTORY)
            }
            if (current()?.id == tab.id) updateChrome()
        }
        // onPageFinished records the visit with its title, for both backends.
        override fun onVisited(tab: BrowserTab, url: String): Boolean = privacyPolicy.mayRecord(url) && !isLocal(url)
        override fun getVisited(tab: BrowserTab, urls: Array<String>): BooleanArray = history.visited(urls)
        override fun onTitleChanged(tab: BrowserTab, title: String) {
            // r4.f.f: open surfaces (tab sheet, strip) refresh the row live.
            tabs.notifyTabChanged(tab)
            if (current()?.id == tab.id) updateChrome()
        }
        override fun onIconChanged(tab: BrowserTab, icon: android.graphics.Bitmap?) {
            // e8.n0.t: publish the WebView icon immediately; rounded disk storage runs afterwards.
            tabs.notifyTabChanged(tab)
            if (icon == null) return
            val url = tab.page.url
            applicationIoScope.launch {
                try { dev.ujhhgtg.via.home.HomeIcons.save(host, url, icon) }
                catch (error: Exception) { Log.w("Via", "Cannot store page favicon", error) }
            }
        }
        override fun onTouchIconChanged(tab: BrowserTab, iconUrl: String) {
            // e8.n0.x: w3's homepage touch icon is independent of y0's favicon cache.
            val pageUrl = tab.page.url ?: return
            if (iconUrl.contains("favicon.ico") || pageUrl.startsWith("file://", true)) return
            applicationIoScope.launch {
                try { dev.ujhhgtg.via.home.TouchIconStore.download(host, iconUrl, pageUrl) }
                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) { Log.w("Via", "Cannot store page touch icon", error) }
            }
        }
        override fun onProgressChanged(tab: BrowserTab, progress: Int) {
            pageColors.onProgressChanged(tab.page, progress)
            if (current()?.id == tab.id) {
                // e8.n0.r reports generated pages as complete; c8.s6.A adds 20
                // before the original progress controller and loading icon consume it.
                val adjusted = (if (pageTypeOf(tab.page.url) > 0) 100 else progress) + 20
                this@BrowserFragment.progress.setPageProgress(adjusted)
                updateReloadButton(adjusted < 100)
            }
        }
        override fun onDownload(tab: BrowserTab, url: String, userAgent: String?, disposition: String?, mimeType: String?, size: Long, body: java.io.InputStream?) {
            val extensions = extensionHost
            if (body != null && extensions != null && dev.ujhhgtg.via.extensions.ExtensionFiles.isPackage(url, mimeType, disposition)) {
                val streamId = dev.ujhhgtg.via.downloads.DownloadStreamRegistry.register(host.applicationContext, body, ".xpi")
                extensions.installLink(dev.ujhhgtg.via.extensions.ExtensionHost.LinkInstall(url, userAgent, disposition, mimeType, size, streamId))
                return
            }
            requestDownload(tab, url, userAgent, disposition, mimeType, size, body)
        }
        override fun onBridgeCommand(tab: BrowserTab, command: Int): Int {
            // cmd(515) is queried synchronously by homepage gesture code; the other commands
            // schedule their shell action on the activity thread.
            val result = if (command == 515 && ::browserLayout.isInitialized && browserLayout.toolbarsShown) 1 else 0
            host.runOnUiThread {
                when (command) {
                    257 -> showBookmarks()
                    514 -> showAddressInput()
                    516 -> dev.ujhhgtg.via.tools.BrowserPrinting.print(host, currentWebView())
                    517 -> currentWebView()?.let { adMarker.start(it) }
                }
            }
            return result
        }
        override fun onBridgeDownload(tab: BrowserTab, url: String, name: String?, mime: String?) {
            host.runOnUiThread {
                if (resourceImageActions.acceptDownload(url, mime)) return@runOnUiThread
                val actual = mime ?: return@runOnUiThread
                val filename = dev.ujhhgtg.via.common.TransientState.get("dl")?.getString(url)
                val disposition = filename?.let { "attachment; filename*=UTF-8''${Uri.encode(it)}" }
                requestDownload(tab, actual, tab.page.userAgent, disposition, null, -1)
            }
        }
        override fun onBridgeMessage(tab: BrowserTab, token: String, json: String) {
            val message = runCatching { JSONObject(json) }.getOrNull() ?: return
            if (passwordForms.handleMessage(tab.page, message) || adMarker.handleMessage(tab.page, message)) return
            when (message.optInt("action")) {
                101 -> {
                    val runAt = message.optInt("runAt")
                    val from = message.optInt("from")
                    if (runAt != 0 && from != 0) host.runOnUiThread { tabs.injectDocumentPhase(from, runAt) }
                }
                104 -> {
                    val url = message.optString("url")
                    val name = message.optString("name")
                    if (url.isNotEmpty() && name.isNotEmpty()) dev.ujhhgtg.via.common.TransientState.builder()
                        .name("dl").expiresAfter(60).putString(url, name).save()
                }
                105 -> message.optString("text").takeIf { it.isNotEmpty() }?.let { value -> host.runOnUiThread { navigate(value) } }
                106 -> host.runOnUiThread { updateHomepageSuggestions(message.optString("text")) }
                107 -> message.optString("url").takeIf { it.isNotEmpty() }?.let { url ->
                    host.runOnUiThread { requestDownload(tab, url, tab.page.userAgent, null, null, -1) }
                    // Original smali's action107 falls through to the action108 translation branch.
                    host.runOnUiThread { showTranslation(fallback = true) }
                }
                108 -> host.runOnUiThread { showTranslation(fallback = true) }
            }
        }
        override fun onBridgeRecord(tab: BrowserTab, url: String, mime: String?) {
            val rule = dev.ujhhgtg.via.tools.AdMarker.rule(url, mime.orEmpty()) ?: return
            dev.ujhhgtg.via.browser.filter.FilterStore(host).appendCustom(rule)
            host.runOnUiThread { reloadTabPreferences() }
        }
        override fun onBridgeToast(tab: BrowserTab, text: String) = host.runOnUiThread { toast(text) }
        override fun onBridgeAddon(tab: BrowserTab, id: String) {
            host.runOnUiThread { installLegacyAddon(id) }
        }
        override fun installedAddonIds(tab: BrowserTab): String = ScriptStore(host).use { store ->
            store.list().mapNotNull { script ->
                script.homepageUrl?.takeIf { it.startsWith("https://app.viayoo.com/addons/") }
                    ?.substring(30)?.takeIf(android.text.TextUtils::isDigitsOnly)
            }.joinToString(",", "[", "]")
        }
        override fun onInternalUrl(tab: BrowserTab, url: String) { showInternal(tab, url) }
        override fun onExternalUrl(tab: BrowserTab, url: String) { if (url.startsWith("via://search")) navigate(url) else requestExternalApp(url) }
        override fun onNavigationRequest(tab: BrowserTab, url: String): Boolean {
            // c8.s6.g9 / p4.a.E: marking ads swallows navigation before the site policy.
            if (adMarker.isActive) return true
            fun localPermission() = requestLocalNavigation(tab, url) { tabs.navigate(tab, url, localNetworkChecked = true) }
            val source = tab.page.url ?: return localPermission()
            if (!URLUtil.isNetworkUrl(source) || !URLUtil.isNetworkUrl(url)) return localPermission()
            if (WebsitePermissions(preferences, siteConfigurations).redirectionAllowed(source)) return localPermission()
            val from = dev.ujhhgtg.via.browser.DocumentPolicy.navigationDomain(source)
            val destination = dev.ujhhgtg.via.browser.DocumentPolicy.navigationDomain(url)
            if (from.isNotEmpty() && destination.equals(from, ignoreCase = true)) return localPermission()
            if (!isVisible) return true
            val prompt = if (destination.isEmpty()) text(R.string.allow_page_redirection_msg)
                else getString(R.string.allow_page_redirection_to_msg, destination)
            ViaToast.show(host, prompt, actionText = text(R.string.allow_popup)) {
                if (current()?.id == tab.id) { tabs.navigate(tab, url); updateChrome() }
            }
            return true
        }
        override fun onCreateWindow(tab: BrowserTab, request: dev.ujhhgtg.via.engine.PopupRequest) {
            // c8.s6.K: the transport stays empty until the user permits a blocked popup.
            if (adMarker.isActive) { request.deny(); return }
            val url = current()?.url.orEmpty()
            val configuration = UrlResolver.siteKey(url)?.let(siteConfigurations::get)?.takeIf { it.isEnabled }
            val redirectionAllowed = configuration?.allowRedirection(preferences.webFlags)
                ?: (preferences.webFlags and 134217728 == 0)
            val prompt = when {
                preferences.webFlags and 32768 != 0 && !request.userGesture -> R.string.block_popup_message
                URLUtil.isNetworkUrl(url) && !redirectionAllowed -> R.string.allow_page_redirection_msg
                else -> null
            }
            if (prompt == null) { tabs.createPopupWindow(request); return }
            if (!isVisible) { request.deny(); return }
            ViaToast.show(host, prompt, actionText = R.string.allow_popup,
                onCancel = { request.deny() }) { tabs.createPopupWindow(request) }
        }
        override fun onPopupCreated(opener: BrowserTab, popup: BrowserTab) { attachSelected() }
        override fun onWindowClosed(tab: BrowserTab) { if (!restoring) attachSelected() }
        /** e8.b0.f: a hidden page declines immediately; the dialog is not dismissed by outside taps. */
        override fun onFormResubmission(tab: BrowserTab, request: dev.ujhhgtg.via.engine.FormResubmissionRequest) {
            if (!tab.page.view.isShown) { request.cancel(); return }
            ViaDialog(host).title(R.string.title_form_resubmission).message(R.string.message_form_resubmission)
                .cancelable(true).canceledOnTouchOutside(false)
                .onCancel { request.cancel() }
                .positive(android.R.string.ok) { _, _ -> request.resend() }
                .negative(android.R.string.cancel) { request.cancel() }.show()
        }
        override fun onHttpAuth(tab: BrowserTab, request: dev.ujhhgtg.via.engine.HttpAuthRequest) { showHttpAuth(request) }
        override fun onSslError(tab: BrowserTab, request: dev.ujhhgtg.via.engine.SslErrorRequest) =
            dev.ujhhgtg.via.browser.SslErrorDialogs.show(host, tab.page.url, preferences, request)
        override fun onFileChooser(tab: BrowserTab, request: dev.ujhhgtg.via.engine.FileChooserRequest): Boolean = chooseFile(request)
        override fun onGeolocationPrompt(tab: BrowserTab, request: dev.ujhhgtg.via.engine.LocationRequest) = requestLocation(request)
        override fun onAndroidPermissions(permissions: Array<String>, complete: (Boolean) -> Unit) {
            requestRuntimePermissions(permissions) { complete(permissions.all(::hasPermission)) }
        }
        override fun onPermissionRequest(tab: BrowserTab, request: dev.ujhhgtg.via.engine.MediaPermissionRequest) { requestMediaPermission(request) }
        override fun onPermissionRequestCanceled(tab: BrowserTab, request: dev.ujhhgtg.via.engine.MediaPermissionRequest) { if (pendingPermission == request) pendingPermission = null }
        override fun onShowFullscreen(tab: BrowserTab, request: dev.ujhhgtg.via.engine.FullscreenRequest) { showVideo(request) }
        override fun onHideFullscreen(tab: BrowserTab) { hideVideo() }
        override fun onUserScript(tab: BrowserTab, url: String) { installScript(url) }
    }

    /** c8.ua.C1/t and ka.g.m/p: home suggestions are remote results or recent queries. */
    private fun updateHomepageSuggestions(query: String) {
        homepageSuggestions?.cancel()
        homepageSuggestions = null
        val flags = preferences.searchSuggestion
        if (flags == 0) return
        val provider = SearchProviders(host, preferences, database).suggestionProvider()
        val language = java.util.Locale.getDefault().toLanguageTag()
        homepageSuggestions = viewLifecycleOwner.launchIo({
            if (query.isEmpty()) dev.ujhhgtg.via.search.SuggestionRepository(database).local(query, flags, emptyList()).map { it.input }
            else remoteHomepageSuggestions.query(query, provider, flags and 16 != 0, language)
        }, { suggestions ->
                val values = suggestions.joinToString(",") { "'$it'" }
                currentWebView()?.evaluate("javascript:try{OpenSuggestion.pushSuggestions([$values]);}catch(e){}", null)
            }, { error -> Log.w("ViaSuggestions", "Suggestion request failed", error) })
    }

    /** z8.t2.o/d/q: decode the addon payload and ask before installing or updating it. */
    private fun installLegacyAddon(payload: String) {
        val context = host.applicationContext
        viewLifecycleOwner.launchIo({
            val script = dev.ujhhgtg.via.browser.script.LegacyAddon.decode(payload)?.toScript()
            script?.let { value -> ScriptStore(context).use { value to it.findByIdentity(value.scriptId, value.downloadUrl) } }
        }, { candidate ->
                val (script, existing) = candidate ?: return@launchIo
                val version = script.version ?: existing?.version ?: "0.1"
                val title = if (existing == null) R.string.title_install_script
                else if (version == existing.version) R.string.title_reinstall_script else R.string.title_update_script
                val message = if (existing == null) getString(R.string.message_install_script, script.name, version)
                else if (version == existing.version) getString(R.string.message_reinstall_script, script.name, version)
                else getString(R.string.message_update_script, script.name, existing.version ?: "0.1", version)
                ViaDialog(host).title(title).message(message)
                    .positive(android.R.string.ok) { _, _ ->
                        viewLifecycleOwner.launchIo({
                            val manager = ScriptManager(ScriptStore(context))
                            try {
                                val saved = manager.update(script.copy(id = existing?.id ?: 0,
                                    enabled = existing?.enabled ?: script.enabled, userOverrides = existing?.userOverrides))
                                manager.ensureDependencies(saved)
                            } finally { manager.close() }
                        }, { dependenciesReady ->
                                toast(getString(if (dependenciesReady) R.string.toast_install_script_successfully else R.string.toast_install_script_failed_dependency_error, script.name))
                                reloadTabPreferences()
                            }, { error -> Log.e("ViaScripts", "Addon install failed", error); toast(getString(R.string.toast_install_script_failed_unknown, script.name)) })
                    }.negative(android.R.string.cancel).show()
            }, { error -> Log.e("ViaScripts", "Addon parse failed", error) })
    }

    /**
     * c8.s6.Xa: unless the system manager is selected, a blob: URL is read by the page (c8.a.a) and a wormhole
     * stream is fetched in-page; both return through window.via.download. The stream's file name is kept for 60s.
     */
    private fun interceptPageDownload(request: DownloadRequest, manager: dev.ujhhgtg.via.settings.ExternalDownloadManagers.Manager?): Boolean {
        val url = request.url
        val blob = manager?.packageName != "system" && url.startsWith("blob:")
        val wormhole = url.startsWith("https://wormhole.app/download-stream/")
        if (!blob && !wormhole) return false
        val web = currentWebView()
        if (web == null) { toast(text(R.string.cannot_download)); return true }
        toast(text(R.string.processing_blob_data))
        val secret = tabs.bridgeSecret
        if (blob) {
            web.evaluate("javascript:(function(){function d(a,b){var c=new FileReader;c.readAsDataURL(b);c.onloadend=function(){window.via.download(\"__SECRET__\",a,c.result)}}function e(a){var b=new XMLHttpRequest;b.open(\"GET\",a,!0);b.responseType=\"blob\";b.onload=function(c){200==this.status&&d(a,this.response)};b.send()}key=\"via-blob-test\";(function(a){window[key]&&window[key][a]?d(a,window[key][a]):e(a)})(\"__BLOB_URL__\")})();"
                .replace("__SECRET__", secret).replace("__BLOB_URL__", url), null)
            return true
        }
        val name = dev.ujhhgtg.via.downloads.DownloadFiles.name(url, request.contentDisposition, request.mimeType)
        if (name.isNotEmpty()) dev.ujhhgtg.via.common.TransientState.builder().name("dl").expiresAfter(60).putString(url, name).save()
        web.evaluate("javascript:(function(){(function(a){return fetch(a).then(function(c){return c.blob()}).then(function(c){return new Promise(function(d,e){var b=new FileReader;b.onloadend=function(){return d(b.result)};b.onerror=e;b.readAsDataURL(c)})})})(\"__URL__\").then(function(a){window.via.download(\"__SECRET__\",\"__URL__\",a)}).catch(function(a){})})();"
            .replace("__URL__", url).replace("__SECRET__", secret), null)
        return true
    }

    private fun requestDownload(tab: BrowserTab, url: String, userAgent: String?, disposition: String?, mime: String?, size: Long, body: java.io.InputStream? = null) =
        requestDownload(tab.url, url, userAgent, disposition, mime, size,
            body?.let { dev.ujhhgtg.via.downloads.DownloadStreamRegistry.register(host.applicationContext, it) })

    /** [streamId] is a body already staged in DownloadStreamRegistry, which the download then owns. */
    private fun requestDownload(referrer: String?, url: String, userAgent: String?, disposition: String?, mime: String?, size: Long,
        streamId: String?, fileName: String? = null) {
        val headers = url.toUri().host?.let { httpAuthorization[it] }?.let { mapOf("Authorization" to it) } ?: emptyMap()
        lifecycleScope.launch {
            val request = DownloadRequest(url, userAgent = userAgent, contentDisposition = disposition, referrer = referrer,
                cookies = dev.ujhhgtg.via.engine.Engines.backend.cookies.get(url), mimeType = mime, headers = headers,
                contentLength = size.coerceAtLeast(0), streamId = streamId, fileName = fileName)
            downloadDestinations.show(request, size)
        }.invokeOnCompletion { error ->
            // Includes a scope that was already cancelled, where the block never runs.
            if (error != null) streamId?.let(dev.ujhhgtg.via.downloads.DownloadStreamRegistry::close)
        }
    }

    /** c8.s6.cb/h4 delegates to the original c8.gb custom sign-in fragment. */
    private fun showHttpAuth(handler: dev.ujhhgtg.via.engine.HttpAuthRequest) {
        val host = handler.host
        val key = dev.ujhhgtg.via.passwords.HttpAuthDialogFragment.RESULT
        parentFragmentManager.setFragmentResultListener(key, viewLifecycleOwner) { _, result ->
            val username = result.getString("username").orEmpty()
            val password = result.getString("password").orEmpty()
            if (username.isEmpty() || password.isEmpty()) handler.cancel()
            else {
                handler.proceed(username, password)
                httpAuthorization[host] = dev.ujhhgtg.via.common.basicAuthorization(username, password)
                passwordForms.offer("https://$host", username, password)
            }
            parentFragmentManager.clearFragmentResultListener(key)
        }
        dev.ujhhgtg.via.passwords.HttpAuthDialogFragment.newInstance("https://$host")
            .show(parentFragmentManager, "HttpAuthDialog")
    }

    private fun chooseFile(params: dev.ujhhgtg.via.engine.FileChooserRequest): Boolean {
        fileCallback?.cancel(); fileCallback = params
        // c8.s6.X normalizes extension accept types before launching the Android chooser.
        val types = params.acceptTypes.flatMap { it.split(',') }.mapNotNull { value -> value.trim().takeIf(String::isNotEmpty)?.let { if (it.startsWith('.')) MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.drop(1).lowercase()) else it } }.distinct()
        val intent = params.createIntent().apply { type = types.singleOrNull() ?: "*/*"; if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray()) }
        return try { fileChooser.launch(Intent.createChooser(intent, params.title?.takeIf { it.isNotEmpty() } ?: getString(R.string.title_file_chooser))); true } catch (_: android.content.ActivityNotFoundException) { fileCallback?.cancel(); fileCallback = null; true }
    }

    private fun hasPermission(permission: String) = host.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun requestRuntimePermissions(permissions: Array<String>, result: () -> Unit) {
        val missing = permissions.filterNot(::hasPermission)
        if (missing.isEmpty()) result() else { permissionResult = result; runtimePermissions.launch(missing.toTypedArray()) }
    }

    private fun requestLocation(callback: dev.ujhhgtg.via.engine.LocationRequest) {
        val origin = callback.origin
        val policy = WebsitePermissions(preferences, siteConfigurations)
        val domain = policy.domain(origin)
        if (domain.isEmpty()) { callback.respond(allow = false, retain = false); return }
        fun grant(remember: Boolean, saveChoice: Boolean = false) {
            // e8.y0.h/I grants the WebView callback before requesting Android permissions.
            callback.respond(allow = true, retain = remember)
            if (saveChoice) policy.remember(origin, WebsitePermissions.Kind.LOCATION, true)
            requestRuntimePermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
                    toast(getString(R.string.site_is_using_your_location, domain))
            }
        }
        when (policy.mode(origin, WebsitePermissions.Kind.LOCATION)) {
            1 -> grant(true)
            2 -> callback.respond(allow = false, retain = false)
            else -> {
                ViaDialog(host).title(R.string.location)
                    .message(getString(R.string.site_request_location_permission, policy.domain(origin)))
                    .check(R.string.remember_my_choice, false)
                    .positive(R.string.allow) { _, result ->
                        grant(result.checked, result.checked)
                    }.negativeResult(R.string.action_dont_allow) { _, result ->
                        callback.respond(allow = false, retain = result.checked)
                        if (result.checked) policy.remember(origin, WebsitePermissions.Kind.LOCATION, false)
                    }.onCancel { callback.respond(allow = false, retain = false) }.show()
            }
        }
    }

    private fun requestMediaPermission(request: dev.ujhhgtg.via.engine.MediaPermissionRequest) {
        pendingPermission = request
        val policy = WebsitePermissions(preferences, siteConfigurations)
        val origin = request.origin
        val audio = dev.ujhhgtg.via.engine.MediaPermissionRequest.Resource.AUDIO_CAPTURE
        val video = dev.ujhhgtg.via.engine.MediaPermissionRequest.Resource.VIDEO_CAPTURE
        val kinds = mapOf(audio to WebsitePermissions.Kind.MICROPHONE, video to WebsitePermissions.Kind.CAMERA)
        val alreadyAllowed = request.resources.filter { it == dev.ujhhgtg.via.engine.MediaPermissionRequest.Resource.PROTECTED_MEDIA || kinds[it]?.let { kind -> policy.mode(origin, kind) == 1 } == true }.toHashSet()
        val asking = request.resources.filter { kinds[it]?.let { kind -> policy.mode(origin, kind) == 3 } == true }.toHashSet()
        fun grant(selected: Collection<dev.ujhhgtg.via.engine.MediaPermissionRequest.Resource>) {
            val androidPermissions = HashSet<String>()
            selected.forEach { when (it) {
                video -> androidPermissions += Manifest.permission.CAMERA
                audio -> {
                    androidPermissions += Manifest.permission.RECORD_AUDIO
                    androidPermissions += Manifest.permission.MODIFY_AUDIO_SETTINGS
                }
                else -> Unit
            } }
            requestRuntimePermissions(androidPermissions.toTypedArray()) {
                if (pendingPermission == request) {
                    val allowed = selected.filter { when (it) { video -> hasPermission(Manifest.permission.CAMERA); audio -> hasPermission(Manifest.permission.RECORD_AUDIO) || hasPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS); else -> true } }
                    if (allowed.isEmpty()) request.deny() else request.grant(allowed.toSet())
                    pendingPermission = null
                }
            }
        }
        if (asking.isEmpty()) { grant(alreadyAllowed); return }
        // c8.s6.qa: the site prompt lists requested permissions and allows/refuses
        // the set; it is not a platform multi-choice selector.
        val labels = asking.map {
            if (it == video) getString(R.string.permission_summary, text(R.string.permission_camera), text(R.string.permission_camera_description))
            else getString(R.string.permission_summary, text(R.string.permission_microphone), text(R.string.permission_microphone_description))
        }
        ViaDialog(host)
            .title(getString(R.string.message_permission_request, policy.domain(origin).ifEmpty { text(R.string.unknown_website) }))
            .message(labels.joinToString("\n") { getString(R.string.list_item_dash, it) })
            .check(R.string.remember_my_choice, false)
            .positive(R.string.allow) { _, result ->
                if (result.checked) asking.forEach { policy.remember(origin, kinds.getValue(it), true) }
                grant(alreadyAllowed + asking)
            }.negativeResult(R.string.action_dont_allow) { _, result ->
                if (result.checked) asking.forEach { policy.remember(origin, kinds.getValue(it), false) }
                grant(alreadyAllowed)
            }.onCancel { grant(alreadyAllowed) }.show()
    }

    private fun showTranslation(extended: Boolean = false, fallback: Boolean = false) {
        val translator = dev.ujhhgtg.via.browser.PageTranslation(host, { currentWebView() }, { currentWebView()?.url },
            tabs.bridgeSecret, openPage = { url ->
                val next = tabs.indexOf(current()) + 1
                tabs.createTab(url, select = true)
                tabs.move(tabs.size - 1, next.coerceAtMost(tabs.size - 1))
                attachSelected()
            }, openTextTranslation = ::showTextTranslation)
        if (extended || fallback) translator.show(extended) else translator.translate()
    }

    /** c8.s6.pb: reuse the open native text-translation pane, otherwise create it in the browser. */
    private fun showTextTranslation(text: String?) {
        val tag = dev.ujhhgtg.via.translation.TextTranslationFragment.TAG
        val existing = childFragmentManager.findFragmentByTag(tag) as? dev.ujhhgtg.via.translation.TextTranslationFragment
        if (existing?.view != null) { existing.replaceTextAndTranslate(text); return }
        var width = minOf(root.width, root.height).takeIf { it > 0 }
            ?: minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val metrics = resources.displayMetrics
        val screen = resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
        val tablet = screen in 3..4 && kotlin.math.hypot(metrics.widthPixels / metrics.xdpi.toDouble(), metrics.heightPixels / metrics.ydpi.toDouble()) >= 7
        if (tablet && browserLayout.mode != 0) width = minOf(width - dp(96), dp(384))
        val gravity = Gravity.END or if (browserLayout.mode == 1) Gravity.TOP else Gravity.BOTTOM
        val pane = dev.ujhhgtg.via.translation.TextTranslationFragment.newInstance(text, width, gravity,
            current()?.url?.let { isLocal(it) && isHome(it) } == true)
        childFragmentManager.beginTransaction().add(R.id.browser_overlay_container, pane, tag).addToBackStack(null).commit()
    }

    private fun showVideo(callback: dev.ujhhgtg.via.engine.FullscreenRequest) {
        val view = callback.view
        if (customView != null && customCallback != null) {
            runCatching { callback.exited() }
            return
        }
        videoShownAt = android.os.SystemClock.elapsedRealtime()
        if (rapidVideoHideCount > 1) preferences.videoOrientation = 0
        originalOrientation = host.requestedOrientation
        customView = view
        customCallback = callback
        view.keepScreenOn = true
        val controls = FullscreenVideoControls(host)
        controls.setBackgroundColor(Color.BLACK)
        controls.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
        videoContainer = controls
        videoControls = controls
        val decor = host.window.decorView as FrameLayout
        decor.addView(controls, FrameLayout.LayoutParams(-1, -1))
        shell.visibility = View.INVISIBLE
        // s6 onShowCustomView: T8(false) hides the floating button without animating.
        floatingToolbarButton?.hide(false)
        WindowInsetsHelper.setFullscreen(host.window, true)
        updateBrowserBackCallback()
        postVideoControllerBinding()
    }

    private fun hideVideo() {
        val view = customView
        val callback = customCallback
        if (view == null || callback == null) {
            runCatching { callback?.exited() }
            customCallback = null
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        rapidVideoHideCount = if (now - videoShownAt < 800L) rapidVideoHideCount + 1 else 0
        view.keepScreenOn = false
        // c8.s6.T removes the wrapper from the decor first, then clears its
        // children. This matters for the WebView-provided SurfaceView.
        videoContainer?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
            container.removeAllViews()
        }
        videoControls = null
        videoContainer = null
        customView = null
        customCallback = null
        runCatching { callback.exited() }
        host.requestedOrientation = originalOrientation; shell.visibility = View.VISIBLE
        showFloatingToolbarButton()
        WindowInsetsHelper.setFullscreen(host.window, appFullscreen || preferences.appFlags and 1 != 0)
        toolbarColors.rebindControls()
        updateBrowserBackCallback()
    }

    /** c8.s6.Z4/x4: allow the custom-view provider to finish layout before reading video metadata. */
    private fun postVideoControllerBinding() {
        val controls = videoControls ?: return
        val web = currentWebView() ?: return
        controls.setTitle(current()?.title)
        controls.setInPipMode(VideoPictureInPicture.isInPip(host))
        host.window.decorView.postDelayed({
            if (videoControls === controls && customView != null) {
                val controller = FullscreenVideoController(
                    controls, web,
                    !customMode && behavior.videoGestures,
                    !customMode,
                    originalOrientation,
                )
                controller.bind()
            }
        }, 150L)
    }

    /** tabs.create/update/remove from extensions, and the hand-off of a staged `.xpi` to the download dialog. */
    private inner class ExtensionTabs : dev.ujhhgtg.via.extensions.ExtensionHost.Tabs {
        private fun tabOf(page: dev.ujhhgtg.via.engine.EnginePage) = tabs.all.firstOrNull { it.page === page }
        override fun openTab(active: Boolean): dev.ujhhgtg.via.engine.EnginePage {
            val tab = tabs.createTab("", select = active, loadInitialUrl = false, insertIndex = tabs.indexOf(current()) + 1)
            if (active) attachSelected() else updateChrome()
            return tab.page
        }
        override fun openUrl(url: String) {
            parentFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            newTab(url)
        }
        override fun select(page: dev.ujhhgtg.via.engine.EnginePage) {
            tabOf(page)?.let { tabs.select(it.id); attachSelected() }
        }
        override fun close(page: dev.ujhhgtg.via.engine.EnginePage) { tabOf(page)?.let(::closeTab) }
        override fun download(link: dev.ujhhgtg.via.extensions.ExtensionHost.LinkInstall) =
            requestDownload(current()?.url, link.url, link.userAgent, link.disposition, link.mime, link.size, link.streamId)
        override fun download(download: dev.ujhhgtg.via.engine.ExtensionDownload) {
            // Gecko already fetched the body with the extension's request; stage it like a page download.
            val streamId = dev.ujhhgtg.via.downloads.DownloadStreamRegistry.register(host.applicationContext, download.body) { saved, bytes ->
                download.finish(when (saved) {
                    true -> dev.ujhhgtg.via.engine.ExtensionDownload.Outcome.COMPLETE
                    false -> dev.ujhhgtg.via.engine.ExtensionDownload.Outcome.FAILED
                    null -> dev.ujhhgtg.via.engine.ExtensionDownload.Outcome.CANCELED
                }, bytes)
            }
            requestDownload(null, download.url, null, download.disposition, download.mimeType, download.size, streamId, download.fileName)
        }
        override fun openSettings() = openSettings("settings_extensions")
    }

    private fun installScript(url: String) {
        val tab = current()
        ScriptInstaller(this).fromBrowserUrl(url, onDownload = tab?.let { { requestDownload(it, url, it.page.userAgent, null, null, 0) } }) {
            reloadTabPreferences()
        }
    }

    /** c8.s6.J9: a page's external-app request follows its effective site policy. */
    private fun requestExternalApp(url: String) {
        val intent = runCatching {
            when {
                android.net.MailTo.isMailTo(url) -> android.net.MailTo.parse(url).let { mail ->
                    Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", mail.to.orEmpty(), null)).apply {
                        mail.to?.takeIf { it.isNotEmpty() }?.let { putExtra(Intent.EXTRA_EMAIL, it.split(Regex("\\s*,\\s*")).toTypedArray()) }
                        mail.cc?.takeIf { it.isNotEmpty() }?.let { putExtra(Intent.EXTRA_CC, it.split(Regex("\\s*,\\s*")).toTypedArray()) }
                        mail.subject?.takeIf { it.isNotEmpty() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                        mail.body?.takeIf { it.isNotEmpty() }?.let { putExtra(Intent.EXTRA_TEXT, it) }
                    }
                }
                url.startsWith("tel:") || url.startsWith("sms:") -> Intent(Intent.ACTION_VIEW, url.toUri())
                else -> {
                    val normalized = if (url.startsWith("tg:") && !url.startsWith("tg://")) "tg://" + url.substring(3) else url
                    Intent.parseUri(normalized, Intent.URI_INTENT_SCHEME).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                }
            }
        }.getOrNull() ?: return
        val policy = WebsitePermissions(preferences, siteConfigurations).openAppMode(currentWebView()?.url ?: current()?.url.orEmpty())
        if (policy == 2 || parentFragmentManager.backStackEntryCount != 0) return
        val info = intent.resolveActivityInfo(host.packageManager, PackageManager.GET_META_DATA) ?: return
        val label = if (info.packageName == "android") "" else host.packageManager.getApplicationLabel(info.applicationInfo).toString()
        fun launch() { runCatching { startActivity(intent) }.onFailure { toast(text(R.string.toast_operation_failed)) } }
        if (policy == 1) { launch(); return }
        val prompt = if (label.isEmpty()) text(R.string.msg_intent) else getString(R.string.allow_website_to_open_app_message, label)
        ViaToast.show(host, prompt, actionText = text(android.R.string.ok), onActionLongClick = {
            ViaDialog(host).title(R.string.open_app).message(url)
                .positive(R.string.open) { _, _ -> launch() }
                .negative(android.R.string.cancel)
                .neutral(android.R.string.copy) { copy(url) }.show()
            true
        }) { launch() }
    }

    private fun copy(value: String) { (host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(text(R.string.hint_url), value)) }

    /** c8.s6.Q7: update only the toolbar controls that the source colors. */
    private fun applyToolbarControls(colors: ToolbarColorController.Controls) {
        toolbarControls = colors
        navigationBar.setAccentColor(colors.iconColor)
        address.setTextColor(colors.textColor)
        listOf(siteInfoButton, snifferButton, reloadButton).forEach { it.setColorFilter(colors.iconColor) }
        tabStrip.setContentColors(colors.iconColor, colors.textColor)
        searchStrip?.setColors(colors.iconColor, colors.textColor)
        customChrome?.setContentColor(colors.textColor)
        webContentFilter.color = colors.webFilterColor
        browserHost.foreground = webContentFilter
    }

    /** Q7/C5: window cover, bottom toolbar and optional search toolbar are separate layers. */
    private fun applyToolbarBackground(frame: ToolbarColorController.BackgroundFrame) {
        browserLayout.bottom.setBackgroundColor(frame.color)
        if (customMode) customChrome?.setBackgroundColor(frame.color)
        if (!frame.onlyToolbar) {
            currentBackgroundColor = frame.color
            nativeBackground.setCoverColor(frame.color)
            if (!frame.intermediate && browserBackgroundImage != null) {
                host.window.setBackgroundDrawable(windowFallbackColor(frame.color).toDrawable())
            }
            searchStrip?.setBackgroundColor(frame.color)
        }
    }

    /** f8.n uses an IME-stable image view when RenderEffect blur is enabled, otherwise the window. */
    private fun updateNativeBrowserBackground(dark: Boolean) {
        nativeBackground = BrowserBackgrounds.update(host, dark, currentBackgroundColor)
        root.background = null
        if (Build.VERSION.SDK_INT >= 31 && preferences.blurEffect) {
            val image = browserBackgroundImage ?: SettingsBackgroundView(host).also {
                browserBackgroundImage = it
                frame.addView(it, 0, FrameLayout.LayoutParams(-1, -1))
            }
            if (image.drawable !== nativeBackground) {
                image.setImageDrawable(nativeBackground)
                image.resetImageBounds()
            }
            host.window.setBackgroundDrawable(windowFallbackColor(currentBackgroundColor).toDrawable())
        } else {
            browserBackgroundImage?.let { image -> image.setImageDrawable(null); frame.removeView(image) }
            browserBackgroundImage = null
            host.window.setBackgroundDrawable(nativeBackground)
        }
    }

    /** f8.k keeps an opaque window behind the root image when a color transition finishes. */
    private fun windowFallbackColor(color: Int): Int = if (color != 0) color
        else preferences.urlBarColor.takeIf { it < 0 && it != -1 } ?: background()

    private fun applyAppearance() {
        if (!::shell.isInitialized) return
        val dark = night()
        val design = BackgroundDesign(preferences.backgroundHome, preferences.backgroundInfo, preferences.urlBarColor, preferences.disableHomeBackgroundDimming)
        updateNativeBrowserBackground(dark)
        browserLayout.top.setBackgroundColor(Color.TRANSPARENT)
        updateAddressSurface()
        address.typeface = preferences.selectedTypeface()
        navigationBar.tabCount.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val internal = currentWebView()?.url?.let { pageTypeOf(it) > 0 } == true
        when {
            nightApplied != null && nightApplied != dark -> toolbarColors.onThemeChanged(internal)
            backgroundDesign != null && backgroundDesign != design -> toolbarColors.onHomeBackgroundChanged(internal)
        }
        currentWebView()?.let(pageColors::onCurrentPageChanged)
        toolbarColors.rebindControls()
        backgroundDesign = design
        nightApplied = dark
        // R1 -> L0.a -> r4.d.D(flags=9) -> e8.n0.D re-emits F/A/t for
        // the selected page, so address and loading state refresh on return.
        currentWebView()?.let { web ->
            progress.setPageProgress((if (pageTypeOf(web.url) > 0) 100 else web.progress) + 20)
        }
        updateChrome()
    }

    private fun launchForResult(intent: Intent, request: Int) { pendingRequest = request; activityResult.launch(intent) }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::tabs.isInitialized) outState.putBundle("browser_tabs", tabs.saveState())
        outState.putInt("pending_request", pendingRequest)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        GeneratedDocumentState.flush(preferences)
        if (::tabs.isInitialized) tabs.flushFilterStatistics()
        downloadFeedback?.stop()
        if (::tabs.isInitialized) { tabs.pause(); if (!customMode) persistRestorableSessions() }
        clearExternalIntentOrigin()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        downloadFeedback?.start()
        if (::preferences.isInitialized && preferences.language != appliedLanguage) { host.recreate(); return }
        // c8.s6.R1: a hidden browser must not overwrite the visible fragment's window policy.
        if (::tabs.isInitialized && (parentFragmentManager.backStackEntryCount == 0 || !isHidden)) {
            tabs.resume()
            refreshCustomizedHome()
            refreshGeneratedDocuments()
            reloadTabPreferences()
            if (!customMode) WebDavSyncRuntime.onBrowserResumed(host)
            host.requestedOrientation = preferences.resolvedScreenOrientation()
            configureBrowserLayout()
            applyAppearance()
            WindowInsetsHelper.setFullscreen(host.window, appFullscreen || preferences.appFlags and 1 != 0)
            updateReadAloudControls()
        }
    }

    override fun onDestroyView() {
        pageGesture = null
        readAloudController?.removeListener(readAloudListener)
        readAloudControls?.dispose(); readAloudControls = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        extensionHost?.detach(); extensionHost = null
        runCatching { host.unregisterReceiver(videoReceiver) }
        networkMonitor?.unregisterNetworkCallback(networkCallback)
        networkMonitor = null
        if (readAloudTaskId != null) readAloudController?.stop()
        if (::toolbarColors.isInitialized) toolbarColors.cancelAnimation()
        menuSettings?.close(); adMarker.close(); passwordForms.dispose()
        fileCallback?.cancel(); fileCallback = null
        pendingPermission?.deny(); pendingPermission = null
        if (::tabs.isInitialized) tabs.destroy()
        // Captured Parcel state no longer needs a WebView; finish queued writes before releasing its database.
        worker.execute { database.close() }
        worker.shutdown()
        super.onDestroy()
    }

    override fun onConfigurationChanged(configuration: android.content.res.Configuration) {
        super.onConfigurationChanged(configuration)
        // s6.onConfigurationChanged -> a8 -> v0.c invalidates the gesture's cached viewport.
        if (configuration.orientation != pageGestureOrientation) {
            pageGestureOrientation = configuration.orientation
            pageGesture?.reset()
        }
        if (::browserLayout.isInitialized) { configureBrowserLayout(); applyAppearance() }
    }

    fun onKeyDown(event: KeyEvent): Boolean {
        if (!::tabs.isInitialized) return false
        val allowed = customView == null && !appFullscreen && childFragmentManager.backStackEntryCount == 0
        when (val command = BrowserActions.keyCommand(event, behavior.volumeScroll, allowed) ?: return false) {
            is BrowserActions.KeyCommand.Action -> performBrowserAction(command.id)
            is BrowserActions.KeyCommand.SelectTab -> tabs.selectAt(if (command.index < 0) tabs.size - 1 else command.index)?.also { attachSelected() }
            BrowserActions.KeyCommand.Menu -> showMenu()
            BrowserActions.KeyCommand.Home -> navigate(preferences.home)
            BrowserActions.KeyCommand.Dismiss -> { if (customView != null) hideVideo() else return false }
        }
        return true
    }

    /** c8.s6.kb / c8.f8.l: while a popup is open the content subtree is
     * invisible to accessibility and the pane takes focus. These sheets are not
     * modal, so the page behind them stays unblurred; only the menu and dialogs blur. */
    private fun onBrowserPopupOpened() {
        browserLayout.content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        browserLayout.content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        browserLayout.content.clearFocus()
    }

    /** c8.s6.V8 / c8.f8.i: restore after the popup closes. */
    private fun onBrowserPopupClosed() {
        browserLayout.content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        browserLayout.content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun dismissBrowserOverlay(): Boolean {
        if (childFragmentManager.backStackEntryCount == 0) return false
        onBrowserPopupClosed()
        childFragmentManager.popBackStackImmediate()
        return true
    }

    /** c8.s6.w9: overlays, video, game mode, then page history, tab closing and the exit gesture. */
    private fun goBack() {
        if (dismissBrowserOverlay()) return
        if (customView != null) { hideVideo(); return }
        if (::gameMode.isInitialized && gameMode.onBackPressed()) return
        if (adMarker.onBackPressed()) return
        if (!::tabs.isInitialized) { host.finish(); return }
        val tab = current()
        if (customMode) {
            when {
                tabs.goBack() -> Unit
                tabs.size > 1 -> tab?.let(::closeTab)
                else -> host.finish()
            }
            return
        }
        if (tab != null && !isHome(tab.page.url ?: tab.url)) {
            // s6.I8: a loading page is stopped before stepping back; otherwise the tab closes.
            if (tabs.canGoBack(tab)) {
                if (tab.page.progress < 100) tab.page.stopLoading()
                tabs.goBack(tab)
            } else closeTab(tab)
            return
        }
        if (tabs.size <= 1) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastExitBack > 1500L) {
                ViaToast.show(host, getString(R.string.message_exit))
                lastExitBack = now
                return
            }
            exitBrowser()
            return
        }
        tab?.let(::closeTab)
    }
    /** s6.T0: shared with game mode's own double-press window in the original. */
    private var lastExitBack = 0L
}

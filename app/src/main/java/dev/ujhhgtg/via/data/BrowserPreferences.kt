package dev.ujhhgtg.via.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.LocaleList
import androidx.core.content.edit
import dev.ujhhgtg.via.common.GeneratedDocumentState
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** The two stores used by Via's w9.k: persistent `settings`, plus the in-memory sync overlay. */
class BrowserPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val values: SharedPreferences = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var agreementLevel: Int get() = int("agreement2", 0); set(value) = putInt("agreement2", value)
    var webFlags: Int get() = int("webflag", 96495261); set(value) = putInt("webflag", value)
    var userAgentChoice: Int get() = int("uachoice", 0); set(value) = putInt("uachoice", value)
    var userAgent: String get() = string("uastring", "") ?: ""; set(value) = putString("uastring", value.replace(Regex("[\\t\\r\\n]"), " "))
    var home: String get() = string("home", "about:home") ?: "about:home"; set(value) = putString("home", value)
    var searchUrl: String get() = string("searchurl", "https://www.google.com/search?q=") ?: "https://www.google.com/search?q="; set(value) = putString("searchurl", value)
    var language: String? get() = string("language", null); set(value) = putString("language", value)
    var textSize: Int
        get() {
            val value = int("textsize", 100)
            // w9.k.D0 migrates the five legacy choices on first read, without a database migration.
            if (value > 5) return value
            return intArrayOf(130, 115, 100, 85, 70)[(if (value in 1..5) value else 3) - 1]
                .also { putInt("textsize", it) }
        }
        set(value) = putInt("textsize", value)
    var readerTextSize: Int get() = int("readertextsize", 17); set(value) = putInt("readertextsize", value)
    var nightMode: Int get() = int("nightmode2", 2); set(value) = putInt("nightmode2", value)
    /** w9.k.d/R0/m0: 0/1 force light/dark; 2/3 follow the current system light/dark state. */
    val isNightMode: Boolean get() = nightMode == 1 || nightMode == 3
    fun setNightMode(enabled: Boolean, systemNight: Boolean) {
        nightMode = if (enabled == systemNight) if (systemNight) 3 else 2 else if (enabled) 1 else 0
    }
    fun updateSystemNightMode(systemNight: Boolean): Boolean {
        val mode = nightMode
        if (mode == 0 || mode == 1 || (mode == 3) == systemNight) return false
        nightMode = if (systemNight) 3 else 2
        GeneratedDocumentState.initialize(this)
        GeneratedDocumentState.mark(GeneratedDocumentState.ALL_DOCUMENTS)
        return true
    }
    var nightFilter: Int get() = int("nightfilter", 77).coerceIn(0, 255); set(value) = putInt("nightfilter", value)
    var readerThemeColor: Int get() = int("readerthemecolor", 0); set(value) = putInt("readerthemecolor", value)
    var bookmarkViewMode: Int get() = int("bookmarksviewmode", 0); set(value) = putInt("bookmarksviewmode", value)
    var bookmarkOrder: Int get() = int("bookmarksorder", 0); set(value) = putInt("bookmarksorder", value)
    var clearDataOnExit: Int get() = int("cleardataonexit2", 0); set(value) = putInt("cleardataonexit2", value)
    var clearData: Int get() = int("cleardata2", 7); set(value) = putInt("cleardata2", value)
    var fullScreenMode: Int get() = int("fullscreenmode", 0); set(value) = putInt("fullscreenmode", value)
    var screenOrientation: Int get() = int("screenOrientation", 1); set(value) = putInt("screenOrientation", value)
    var videoOrientation: Int get() = int("videoorientation", 0); set(value) = putInt("videoorientation", value)
    var searchSuggestion: Int get() = int("searchsuggestion", 31); set(value) = putInt("searchsuggestion", value)
    var restoreClosedTabs: Int get() = int("restoreclosedtabs", 0); set(value) = putInt("restoreclosedtabs", value)
    var tabShortcut: Int get() = int("keytab", 5); set(value) = putInt("keytab", value)
    var homeShortcut: Int get() = int("keyhome", 4); set(value) = putInt("keyhome", value)
    var backShortcut: Int get() = int("keyback", 2); set(value) = putInt("keyback", value)
    var forwardShortcut: Int get() = int("keyforward", 3); set(value) = putInt("keyforward", value)
    var menuShortcut: Int get() = int("keymenu", 1); set(value) = putInt("keymenu", value)
    var gestureToolbarLeft: Int get() = int("gesturetoolbarleft", 10); set(value) = putInt("gesturetoolbarleft", value)
    var gestureToolbarRight: Int get() = int("gesturetoolbarright", 11); set(value) = putInt("gesturetoolbarright", value)
    var urlBoxMode: Int get() = int("urlbox", 0); set(value) = putInt("urlbox", value)
    var urlBarColor: Int get() = int("urlbarcolor", -1); set(value) = putInt("urlbarcolor", value)
    var fabSize: Int get() = int("fab", 80); set(value) = putInt("fab", value)
    var cloudServer: Int get() = int("cloudserver", if (Locale.getDefault().country.equals("CN", true)) 1 else 0); set(value) = putInt("cloudserver", value)
    var syncingChoice: Int get() = int("syncingchoice", 0); set(value) = putInt("syncingchoice", value)
    var dataVersion: Int get() = int("data_version", 0); set(value) = putInt("data_version", value)
    var changeLogCode: Int get() = int("changelogcode", 0); set(value) = putInt("changelogcode", value)
    var appUi: Int get() = int("appui2", -1); set(value) = putInt("appui2", value)
    // w9.a.f13317b: c0.f() is true for this build, hence 1024 | 226 = 1250.
    var appFlags: Int get() = int("appflag", 1250); set(value) = putInt("appflag", value)
    var webFlags2: Int get() = int("webflag2", 0); set(value) = putInt("webflag2", value)
    var adBlockedTimes: Int get() = int("adblockedtimes", 0); set(value) = putInt("adblockedtimes", value)
    var backgroundInfo: Int get() = int("bginfo", 0); set(value) = putInt("bginfo", value)
    var cloudTag: Int get() = int("cloudtag", -1); set(value) = putInt("cloudtag", value)
    var customInfo: Int get() = int("custominfo", 0); set(value) = putInt("custominfo", value)
    var dataChecker: Int get() = int("datachecker2", 0); set(value) = putInt("datachecker2", value)
    var duaChoice: Int get() = int("duachoice", 0); set(value) = putInt("duachoice", value)
    var favoritesInfo: Int get() = int("favinfo", 1638446); set(value) = putInt("favinfo", value)
    var hardwareShortcut: Int get() = int("hwshorcut", 0); set(value) = putInt("hwshorcut", value)
    var ignoredSslWarning: Int get() = int("ignoredsslwarning", 0); set(value) = putInt("ignoredsslwarning", value)
    var labFlags: Int get() = int("labflag", 0); set(value) = putInt("labflag", value)
    /** Experimental KernelSU-style settings root (Compose); the classic pages stay the default. */
    var ksuSettingsUi: Boolean get() = getBoolean("ksu_settings_ui", false); set(value) = putBoolean("ksu_settings_ui", value)
    var logoChoice: Int get() = int("logochioce", 0); set(value) = putInt("logochioce", value)
    var logoInfo: Int get() = int("logoinfo2", 193766400); set(value) = putInt("logoinfo2", value)
    var readAloudSpeedPercent: Int get() = int("readaloudspeed", 100); set(value) = putInt("readaloudspeed", value)
    /** Via's CN build resolves an unset search2 to Baidu (-2) through z8.v2.a(). */
    var searchMode: Int get() = int("search2", dev.ujhhgtg.via.search.BuiltinSearchProviders.DEFAULT_ID); set(value) = putInt("search2", value)
    var searchInfo: Int get() = int("searchinfo", 2490468); set(value) = putInt("searchinfo", value)
    var version: Int get() = int("version", 0); set(value) = putInt("version", value)
    var login: Boolean get() = getBoolean("login", false); set(value) = putBoolean("login", value)
    // w9.k.K2 -> w9.r.h: the default is the platform force-dark capability (API 29+).
    /** WebView's "Force dark mode for web contents". The key keeps its legacy name for saved settings and backups. */
    var forceDarkPages: Boolean get() = getBoolean("nightcss", true); set(value) = putBoolean("nightcss", value)
    /** Night mode leaves the customized homepage background color/image at its day appearance when set. */
    var disableHomeBackgroundDimming: Boolean get() = getBoolean("nightnobgdim", false); set(value) = putBoolean("nightnobgdim", value)
    var searchToolBarDisabled: String? get() = string("searchtoolbardisabled", null); set(value) = putString("searchtoolbardisabled", value)
    var searchToolBarOrder: String get() = string("searchtoolbarorder", "") ?: ""; set(value) = putString("searchtoolbarorder", value)
    var searchShortcuts: String get() = string("searchshortcuts", "") ?: ""; set(value) = putString("searchshortcuts", value)
    var displayedMenus: String? get() = string("displayedmenus", null); set(value) = putString("displayedmenus", value)
    var hiddenMenus: String? get() = string("hiddenmenus", null); set(value) = putString("hiddenmenus", value)

    /** w9.k.z1/z2: the element-menu ids hidden behind More options. */
    var hiddenContextMenus: String? get() = string("hiddenctxmenus", null); set(value) = putString("hiddenctxmenus", value)
    var skin: String get() = string("skin", "") ?: ""; set(value) = putString("skin", value)
    var cssTheme: String? get() = decodedString("csstheme"); set(value) = putEncodedString("csstheme", value?.trim())
    var readerCustomCss: String? get() = string("readercustomcss", null); set(value) = putString("readercustomcss", value)
    var uiFont: String get() = string("uifont", "") ?: ""; set(value) = putString("uifont", value)
    var downloadDirectory: String get() = string("downloaddir", "Download") ?: "Download"; set(value) = putString("downloaddir", value)
    var videoPlayer: String? get() = string("videoplayer", null); set(value) = putString("videoplayer", value)
    var backgroundHome: String? get() = decodedString("bghome"); set(value) = putEncodedString("bghome", value?.trim())
    var disabledAddons: String get() = string("disabledaddons", "") ?: ""; set(value) = putString("disabledaddons", value)
    var downloadManager: String? get() = string("dlmanager", null); set(value) = putString("dlmanager", value)
    var duaString: String? get() = string("duastring", null); set(value) = putString("duastring", value)
    var homeTag: String? get() = decodedString("taghome"); set(value) = putEncodedString("taghome", value?.trim())
    var languageUserName: String get() = string("username", "") ?: ""; set(value) = putString("username", value)
    val hasStoredPassword: Boolean get() = !string("userpsw", "").isNullOrEmpty()

    fun getBoolean(key: String, default: Boolean = false) = if (key.isEmpty()) default else values.getBoolean(key, default)
    fun getInt(key: String, default: Int = 0) = int(key, default)
    fun getLong(key: String, default: Long = 0L) = if (key.isEmpty()) default else values.getLong(key, default)
    fun getString(key: String, default: String? = null) = string(key, default)
    fun putBoolean(key: String, value: Boolean) { if (key.isNotEmpty()) values.edit {
        putBoolean(
            key,
            value
        )
    } }
    fun putInt(key: String, value: Int) { if (key.isNotEmpty()) values.edit { putInt(key, RemovedFeatureMigration.normalizeInt(key, value)) } }
    fun putLong(key: String, value: Long) { if (key.isNotEmpty()) values.edit {
        putLong(
            key,
            value
        )
    } }
    fun putString(key: String, value: String?) {
        if (key.isNotEmpty()) values.edit { putString(key, RemovedFeatureMigration.normalizeString(key, value)) } }
    private fun decodedString(key: String): String? = string(key, null)?.let { encoded ->
        runCatching { String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8).trim() }.getOrDefault("")
    }
    private fun putEncodedString(key: String, value: String?) = putString(key, value?.let {
        android.util.Base64.encodeToString(it.toByteArray(Charsets.UTF_8), android.util.Base64.DEFAULT)
    })

    fun javascriptEnabled() = webFlags and 8 != 0
    fun imagesEnabled() = webFlags and 16 != 0
    // w9.p/w9.a bit accessors used by the privacy and advanced settings fragments.
    var doNotTrack: Boolean get() = webFlags and 128 != 0; set(value) { webFlags = if (value) webFlags or 128 else webFlags and 128.inv() }
    var disableWebRtc: Boolean get() = webFlags and 536870912 != 0; set(value) { webFlags = if (value) webFlags or 536870912 else webFlags and 536870912.inv() }
    var doNotSellOrShare: Boolean get() = webFlags and 65536 != 0; set(value) { webFlags = if (value) webFlags or 65536 else webFlags and 65536.inv() }
    var saveData: Boolean get() = webFlags and 256 != 0; set(value) { webFlags = if (value) webFlags or 256 else webFlags and 256.inv() }
    var webPageDebug: Boolean get() = webFlags and 4096 != 0; set(value) { webFlags = if (value) webFlags or 4096 else webFlags and 4096.inv() }
    var safeBrowsing: Boolean get() = webFlags and 2097152 == 0; set(value) { webFlags = if (value) webFlags and 2097152.inv() else webFlags or 2097152 }
    // z7.d and w9.p.g/I: ad blocking is webflag bit 1; appflag bit 512 is the undo toast.
    var adBlocking: Boolean get() = webFlags and 1 != 0; set(value) { webFlags = flag(webFlags, 1, value) }
    var showUndoCloseTab: Boolean get() = appFlags and 512 != 0; set(value) { appFlags = flag(appFlags, 512, value) }
    var showSnifferButton: Boolean get() = appFlags and 4 != 0; set(value) { appFlags = flag(appFlags, 4, value) }
    var disablePredictiveBack: Boolean get() = appFlags and 524288 != 0; set(value) { appFlags = flag(appFlags, 524288, value) }
    var disableCustomTabs: Boolean get() = appFlags and 262144 != 0; set(value) { appFlags = flag(appFlags, 262144, value) }
    var scriptsEnabled: Boolean get() = webFlags and 268435456 == 0; set(value) { webFlags = flag(webFlags, 268435456, !value) }
    var scriptUpdateInterval: Long get() = getLong("updater_scripts", 0L); set(value) = putLong("updater_scripts", value)
    var extensionsEnabled: Boolean get() = getBoolean("extensions_enabled", true); set(value) = putBoolean("extensions_enabled", value)
    /** Extensions check for updates every day unless the user chooses otherwise. */
    var extensionUpdateInterval: Long get() = getLong("updater_extensions", 86_400_000L); set(value) = putLong("updater_extensions", value)
    var allowUnsignedExtensions: Boolean get() = labFlags and 8 != 0; set(value) { labFlags = flag(labFlags, 8, value) }
    var readerConfirmation: Boolean get() = appFlags and 8192 != 0; set(value) { appFlags = flag(appFlags, 8192, value) }
    var experimentalAvailable: Boolean get() = labFlags and 1 != 0; set(value) { labFlags = flag(labFlags, 1, value) }
    var blurEffect: Boolean get() = labFlags and 2 != 0; set(value) { labFlags = flag(labFlags, 2, value) }
    var showSettingsBackground: Boolean get() = labFlags and 4 != 0; set(value) { labFlags = flag(labFlags, 4, value) }

    /** hb.c4.C4: System=USER, Auto=FULL_SENSOR, Portrait, Landscape. */
    fun resolvedScreenOrientation(): Int = when (screenOrientation) { 2 -> 10; 3 -> 1; 4 -> 0; else -> 2 }
    val fontDirectory: File get() = appContext.getExternalFilesDir("font") ?: File(appContext.filesDir, "font")
    fun selectedTypeface(): Typeface = if (uiFont.isEmpty()) Typeface.DEFAULT else
        runCatching { Typeface.createFromFile(File(fontDirectory, File(uiFont).name)) }.getOrDefault(Typeface.DEFAULT)

    /** z8.t1: put the chosen locale first while retaining the system fallback locales. */
    fun localizedContext(context: Context): Context {
        val tag = language?.takeIf(String::isNotEmpty) ?: return context
        val configuration = Configuration(context.resources.configuration)
        // Shell.attachBaseContext leaves night mode unspecified in its locale override.
        configuration.uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()
        val existing = configuration.locales.toLanguageTags().split(',').filter { it != tag }
        configuration.setLocales(LocaleList.forLanguageTags((listOf(tag) + existing).joinToString(",")))
        return context.createConfigurationContext(configuration)
    }

    /** The exact portable-settings whitelist from w9.i and w9.k.D2/g1. */
    fun exportSettings(): JSONObject = JSONObject().apply {
        // w9.k.D2 reads the preference store. JSONObject's same-named methods
        // would otherwise shadow these getters inside this apply receiver.
        backupBooleanKeys.filter(values::contains).forEach { put(it, this@BrowserPreferences.getBoolean(it)) }
        backupStringKeys.filter(values::contains).forEach { put(it, this@BrowserPreferences.getString(it)) }
        backupIntKeys.filter(values::contains).forEach { put(it, this@BrowserPreferences.getInt(it)) }
        backupLongKeys.filter(values::contains).forEach { put(it, this@BrowserPreferences.getLong(it)) }
    }

    fun importSettings(json: JSONObject) {
        values.edit {
            backupBooleanKeys.filter { !json.isNull(it) }
                .forEach { putBoolean(it, json.optBoolean(it)) }
            backupStringKeys.filter { !json.isNull(it) }
                .forEach { putString(it, RemovedFeatureMigration.normalizeString(it, json.optString(it))) }
            backupIntKeys.filter { !json.isNull(it) }.forEach { putInt(it, RemovedFeatureMigration.normalizeInt(it, json.optInt(it))) }
            backupLongKeys.filter { !json.isNull(it) }.forEach { putLong(it, json.optLong(it)) }
        }
    }

    /** Resolve the selected z9 search provider and append a URL-encoded query exactly once. */
    fun effectiveSearchUrl(query: String? = null): String {
        val desktop = webFlags and 2048 != 0 // w9.p.l(), used by w9.k.X0()
        val template = dev.ujhhgtg.via.search.BuiltinSearchProviders.resolveTemplate(searchMode, desktop, searchUrl)
        if (query == null) return template
        // i6.g0.h: replace the first placeholder in priority %@, %s, %S; otherwise append.
        if (query.isEmpty() || template.isEmpty()) return query
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val index = listOf("%@", "%s", "%S").map(template::indexOf).firstOrNull { it >= 0 } ?: -1
        if (index == 0 && template.length == 2) return query
        return if (index >= 0) template.substring(0, index) + encoded + template.substring(index + 2) else template + encoded
    }
    private fun flag(value: Int, mask: Int, enabled: Boolean) = if (enabled) value or mask else value and mask.inv()

    companion object {
        private val backupBooleanKeys = arrayOf("nightcss", "nightnobgdim")
        private val backupIntKeys = arrayOf("appflag", "webflag", "fullscreenmode", "search2", "textsize", "uachoice", "screenOrientation", "keyback", "keyforward", "keyhome", "keytab", "keymenu", "gesturetoolbarleft", "gesturetoolbarright", "urlbox", "appui2", "logochioce", "urlbarcolor", "fab", "restoreclosedtabs", "cleardata2", "cleardataonexit2", "version", "nightfilter", "bookmarksorder", "favinfo", "logoinfo2", "searchinfo", "custominfo", "searchsuggestion", "readertextsize", "readerthemecolor", "videoorientation", "bookmarksviewmode", "labflag", "duachoice", "webflag2")
        private val backupLongKeys = arrayOf("updater_filter_subscriptions", "updater_scripts")
        private val backupStringKeys = arrayOf("home", "searchurl", "uastring", "csstheme", "taghome", "dlmanager", "language", "videoplayer", "displayedmenus", "readercustomcss", "searchtoolbardisabled", "duastring", "searchtoolbarorder", "hiddenmenus", "searchshortcuts")
    }

    private fun int(key: String, default: Int) = if (key.isEmpty()) default else RemovedFeatureMigration.normalizeInt(key, values.getInt(key, default))
    private fun string(key: String, default: String?) = if (key.isEmpty()) default else RemovedFeatureMigration.normalizeString(key, values.getString(key, default))
}

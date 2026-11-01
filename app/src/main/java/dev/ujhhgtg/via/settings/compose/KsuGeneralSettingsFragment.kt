package dev.ujhhgtg.via.settings.compose

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.settings.ExternalDownloadManagers
import dev.ujhhgtg.via.settings.ExternalVideoPlayers
import dev.ujhhgtg.via.settings.SettingsController
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.util.Locale

/** hb.c4's list, ported to the Material settings page; dialog-backed rows reuse [SettingsController]. */
class KsuGeneralSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_general

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private lateinit var handlers: SettingsController
    private var managers: List<ExternalDownloadManagers.Choice>? = null
    private var players: List<ExternalVideoPlayers.Choice>? = null
    private var agentTitle: String? = null
    private var pendingRequest = 0

    private val document = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!::handlers.isInitialized) return@registerForActivityResult
        handlers.onActivityResult(pendingRequest, result.resultCode, result.data)
        refresh()
    }
    private val preferenceChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handlers = SettingsController(
            requireActivity(),
            openPage = { (requireActivity() as Shell).openPage(it) },
            launchForResult = { intent, code -> pendingRequest = code; document.launch(intent) },
            scopeOwner = viewLifecycleOwner,
            exportScopeOwner = this,
        )
        handlers.restoreState(savedInstanceState)
        pendingRequest = savedInstanceState?.getInt("pending_request") ?: 0
        requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferenceChanged)
        parentFragmentManager.setFragmentResultListener(KsuExportSectionsSettingsFragment.RESULT, viewLifecycleOwner) { _, result ->
            handlers.exportBackupWithSections(result.getInt("mask"))
        }
        parentFragmentManager.setFragmentResultListener(KsuBookmarkTransferSettingsFragment.RESULT, viewLifecycleOwner) { _, result ->
            when (result.getString("action")) {
                "export" -> handlers.startExportBookmarks()
                "import" -> handlers.startImportBookmarks()
            }
        }
        refreshExternalApps()
        refreshAgentTitle()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pending_request", pendingRequest)
        if (::handlers.isInitialized) handlers.saveState(outState)
    }

    override fun onDestroyView() {
        requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(preferenceChanged)
        if (::handlers.isInitialized) handlers.close()
        super.onDestroyView()
    }

    override fun groups(): List<KsuGroup> {
        val orientationAllowed = Build.VERSION.SDK_INT < 36 || resources.configuration.smallestScreenWidthDp < 600
        val orientation = intArrayOf(R.string.follow_system, R.string.orientation_auto, R.string.orientation_portrait, R.string.orientation_landscape)
        val restored = intArrayOf(R.string.disable_restore, R.string.always_restore, R.string.ask_first)

        return listOf(
            KsuGroup(getString(R.string.settings_general), listOf(
                KsuNavRow(26, R.drawable.ic_ksu_sync, getString(R.string.sync)),
                KsuNavRow(2, R.drawable.ic_ksu_smartphone, getString(R.string.agent), agentTitle),
                KsuDropdownRow(9, R.drawable.ic_ksu_translate, getString(R.string.language),
                    options = listOf(getString(R.string.follow_system)) + languageTags().map { tag ->
                        Locale.forLanguageTag(tag).let { it.getDisplayName(it) }
                    }, selectedIndex = languageIndex()),
                KsuDropdownRow(10, R.drawable.ic_ksu_home, getString(R.string.home),
                    options = listOf(getString(R.string.default_set), getString(R.string.action_blank),
                        getString(R.string.action_bookmarks), getString(R.string.action_webpage)),
                    selectedIndex = homeIndex()),
                KsuNavRow(31, R.drawable.ic_ksu_search, getString(R.string.search_settings)),
                KsuNavRow(35, R.drawable.ic_ksu_font_download, getString(R.string.font), value = preferences.uiFont),
                KsuDropdownRow(13, R.drawable.ic_ksu_screen_rotation, getString(R.string.orientation),
                    options = orientation.map(::getString), selectedIndex = (preferences.screenOrientation - 1).coerceIn(0, 3),
                    enabled = orientationAllowed),
            )),
            KsuGroup(getString(R.string.settings_group_browsing), listOf(
                KsuNavRow(6, R.drawable.ic_ksu_dark_mode, getString(R.string.action_night)),
                KsuNavRow(30, R.drawable.ic_ksu_chrome_reader_mode, getString(R.string.reader_mode)),
                KsuNavRow(32, R.drawable.ic_ksu_view_agenda, getString(R.string.toolbars_settings)),
                KsuNavRow(28, R.drawable.ic_ksu_menu, getString(R.string.customize_menu)),
                KsuNavRow(37, R.drawable.ic_ksu_ads_click, getString(R.string.customize_context_menu)),
                KsuNavRow(19, R.drawable.ic_ksu_gesture, getString(R.string.settings_operation), getString(R.string.operation_hint)),
                KsuNavRow(4, R.drawable.ic_ksu_block, getString(R.string.block_ads)),
                KsuNavRow(5, R.drawable.ic_ksu_public, getString(R.string.site_conf)),
            )),
            KsuGroup(getString(R.string.settings_group_data), listOf(
                KsuNavRow(14, R.drawable.ic_ksu_download, getString(R.string.download), value = downloadTitle()),
                managers?.let { choices ->
                    KsuDropdownRow(15, R.drawable.ic_ksu_download_for_offline, getString(R.string.addon_download),
                        options = choices.map { it.label }, selectedIndex = choices.indexOfFirst { it.id == preferences.downloadManager.orEmpty() }.coerceAtLeast(0))
                } ?: KsuNavRow(15, R.drawable.ic_ksu_download_for_offline, getString(R.string.addon_download),
                    value = getString(R.string.built_in_download_manager), enabled = false),
                players?.let { choices ->
                    KsuDropdownRow(27, R.drawable.ic_ksu_play_circle, getString(R.string.external_video_player),
                        options = choices.map { it.label }, selectedIndex = choices.indexOfFirst { it.packageName == preferences.videoPlayer.orEmpty() }.coerceAtLeast(0))
                } ?: KsuNavRow(27, R.drawable.ic_ksu_play_circle, getString(R.string.external_video_player),
                    value = getString(R.string.player_system_sharing), enabled = false),
                KsuNavRow(20, R.drawable.ic_ksu_bookmark, getString(R.string.import_or_export_bookmarks)),
                KsuNavRow(21, R.drawable.ic_ksu_upload_file, getString(R.string.import_data)),
                KsuNavRow(22, R.drawable.ic_ksu_save_alt, getString(R.string.export_data), value = getString(R.string.export_data_description)),
                KsuDropdownRow(23, R.drawable.ic_ksu_restore, getString(R.string.restore_tabs),
                    options = restored.map(::getString), selectedIndex = preferences.restoreClosedTabs.coerceIn(0, 2)),
                KsuToggleRow(33, R.drawable.ic_ksu_undo, getString(R.string.show_toast_to_undo_closing_tab),
                    getString(R.string.show_toast_to_undo_closing_tab_description), preferences.showUndoCloseTab),
            )),
            KsuGroup(getString(R.string.settings_group_system), listOf(
                KsuNavRow(3, R.drawable.ic_ksu_delete_sweep, getString(R.string.clear_data)),
                KsuNavRow(18, R.drawable.ic_ksu_logout, getString(R.string.clear_data_on_exit)),
                KsuNavRow(36, R.drawable.ic_ksu_password, getString(R.string.password_manager)),
                KsuNavRow(24, R.drawable.ic_ksu_apps, getString(R.string.setting_default)),
            )),
        )
    }

    override fun onRow(row: KsuRow) {
        val shell = requireActivity() as Shell
        when (row.key) {
            2 -> shell.navigate(KsuUserAgentSettingsFragment())
            3 -> shell.navigate(KsuClearDataSettingsFragment())
            4 -> shell.navigate(KsuBlockAdsSettingsFragment())
            5 -> shell.navigate(KsuSiteConfSettingsFragment())
            6 -> shell.navigate(KsuNightModeSettingsFragment())
            9 -> handlers.route("language")
            10 -> handlers.route("home")
            13 -> handlers.route("orientation")
            14 -> handlers.route("download")
            15 -> handlers.route("addon_download")
            18 -> shell.navigate(KsuClearOnExitSettingsFragment())
            19 -> shell.navigate(KsuGesturesSettingsFragment())
            20 -> shell.navigate(KsuBookmarkTransferSettingsFragment())
            21 -> handlers.route("import_data")
            22 -> shell.navigate(KsuExportSectionsSettingsFragment())
            23 -> handlers.route("restore_tabs")
            24 -> handlers.route("setting_default")
            26 -> shell.openPage("user_syns")
            27 -> handlers.route("external_video_player")
            28 -> shell.navigate(KsuMenuCustomizationSettingsFragment())
            30 -> shell.navigate(KsuReaderSettingsFragment())
            31 -> shell.navigate(KsuSearchSettingsFragment())
            32 -> shell.navigate(KsuToolbarSettingsFragment())
            35 -> shell.navigate(KsuFontSettingsFragment())
            36 -> shell.navigate(KsuPasswordSettingsFragment())
            37 -> shell.navigate(KsuContextMenuSettingsFragment())
        }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        if (row.key == 33) preferences.showUndoCloseTab = checked
        refresh()
    }

    override fun onSelect(row: KsuDropdownRow, index: Int) {
        when (row.key) {
            9 -> applyLanguage(index)
            10 -> applyHome(index)
            13 -> {
                preferences.screenOrientation = index + 1
                if (Build.VERSION.SDK_INT < 36 || resources.configuration.smallestScreenWidthDp < 600) {
                    requireActivity().requestedOrientation = preferences.resolvedScreenOrientation()
                }
            }
            15 -> managers?.getOrNull(index)?.let { preferences.downloadManager = it.id }
            23 -> {
                preferences.restoreClosedTabs = index
                if (index != 0) dev.ujhhgtg.via.ui.ViaToast.makeText(requireContext(),
                    getString(R.string.restore_tabs_hint_settings), dev.ujhhgtg.via.ui.ViaToast.LENGTH_SHORT).show()
            }
            27 -> players?.getOrNull(index)?.let { preferences.videoPlayer = it.packageName }
        }
        refresh()
    }

    private fun languageTags(): List<String> = buildList {
        val parser = resources.getXml(R.xml.locales_config)
        parser.use { parser ->
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")?.let(::add)
                }
                parser.next()
            }
        }
    }.sorted()

    private fun languageIndex(): Int {
        val selected = preferences.language?.let { Locale.forLanguageTag(it).toLanguageTag() }
        return languageTags().indexOfFirst { Locale.forLanguageTag(it).toLanguageTag() == selected } + 1
    }

    private fun homeIndex(): Int = when {
        preferences.home == "about:blank" -> 1
        preferences.home == "about:bookmarks" -> 2
        preferences.home.startsWith("about:") -> 0
        else -> 3
    }

    private fun applyLanguage(index: Int) {
        if (index == languageIndex()) return
        val tags = languageTags()
        preferences.language = tags.getOrNull(index - 1)
        refresh()
        ViaDialog(requireActivity()).title(R.string.language).message(R.string.restart_to_take_effect)
            .positive(R.string.crash_restart) { _, _ ->
                startActivity(Intent.makeRestartActivityTask(android.content.ComponentName(requireActivity(), Shell::class.java)).setPackage(requireContext().packageName))
                kotlin.system.exitProcess(0)
            }.negative(android.R.string.cancel).show()
    }

    private fun applyHome(index: Int) {
        when (index) {
            0 -> preferences.home = "about:home"
            1 -> preferences.home = "about:blank"
            2 -> preferences.home = "about:bookmarks"
            else -> editHomepage()
        }
        refresh()
    }

    private fun editHomepage() {
        ViaDialog(requireActivity()).title(R.string.title_custom_homepage)
            .input(preferences.home.takeUnless { it.startsWith("about:") } ?: "https://m.baidu.com/?tn=&from=1022560v",
                getString(R.string.title_custom_homepage), 3)
            .positive(android.R.string.ok) { _, result ->
                val raw = result.edit?.firstOrNull()?.trim().orEmpty()
                val normalized = dev.ujhhgtg.via.browser.UrlResolver.normalizeInput(raw, null)
                if (normalized != null && normalized.contains("://")) preferences.home = normalized
                refresh()
            }.negative(android.R.string.cancel).show()
    }

    private fun refreshExternalApps() {
        if (view == null) return
        val context = requireContext().applicationContext
        viewLifecycleOwner.launchIo({ ExternalDownloadManagers.choices(context) }, { managers = it; refresh() }) {
            android.util.Log.w("ViaSettings", "Download manager query failed", it)
        }
        viewLifecycleOwner.launchIo({ ExternalVideoPlayers.choices(context) }, { players = it; refresh() }) {
            android.util.Log.w("ViaSettings", "Video player query failed", it)
        }
    }

    private fun refreshAgentTitle() {
        if (view == null) return
        val context = requireContext().applicationContext
        val selected = preferences.userAgentChoice
        viewLifecycleOwner.launchIo({
            if (selected > 0) {
                BrowserDatabase(context).use { database ->
                    SettingsDataRepository(database).find(selected)?.takeIf { it.type == SettingsData.USER_AGENT }?.title.orEmpty()
                }
            } else {
                val names = intArrayOf(R.string.default_set, R.string.agent_android_phone, R.string.agent_android_tablet,
                    R.string.agent_windows_chrome, R.string.agent_windows_ie11, R.string.agent_osx, R.string.agent_iphone,
                    R.string.agent_ipad, R.string.agent_symbian)
                names.getOrNull(-selected)?.let(context::getString).orEmpty()
            }
        }, { agentTitle = it; refresh() }) { android.util.Log.w("ViaSettings", "User agent title query failed", it) }
    }



    @SuppressLint("SdCardPath")
    private fun downloadTitle(): String {
        val directory = preferences.downloadDirectory
        if (!directory.startsWith("content://")) return "/sdcard/$directory"
        val uri = directory.toUri()
        runCatching {
            requireContext().contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf(String::isNotEmpty) else null
            }
        }.getOrNull()?.let { return it }
        val document = runCatching { DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)) }
            .getOrDefault(uri)
        return runCatching {
            requireContext().contentResolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: directory
    }
}

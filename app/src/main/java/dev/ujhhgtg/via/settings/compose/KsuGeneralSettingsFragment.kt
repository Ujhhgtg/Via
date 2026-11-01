package dev.ujhhgtg.via.settings.compose

import android.annotation.SuppressLint
import android.content.Context
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
        val manager = managers?.firstOrNull { it.id == preferences.downloadManager.orEmpty() } ?: managers?.firstOrNull()
        val player = players?.firstOrNull { it.packageName == preferences.videoPlayer.orEmpty() } ?: players?.firstOrNull()

        return listOf(
            KsuGroup(getString(R.string.settings_general), listOf(
                KsuNavRow(26, R.drawable.ic_ksu_sync, getString(R.string.sync)),
                KsuNavRow(2, R.drawable.ic_ksu_smartphone, getString(R.string.agent), agentTitle),
                KsuNavRow(9, R.drawable.ic_ksu_translate, getString(R.string.language), value = languageTitle()),
                KsuNavRow(10, R.drawable.ic_ksu_home, getString(R.string.home), value = homeTitle()),
                KsuNavRow(31, R.drawable.ic_ksu_search, getString(R.string.search_settings)),
                KsuNavRow(35, R.drawable.ic_ksu_font_download, getString(R.string.font), value = preferences.uiFont),
                KsuNavRow(13, R.drawable.ic_ksu_screen_rotation, getString(R.string.orientation),
                    value = getString(orientation[if (orientationAllowed) (preferences.screenOrientation - 1).coerceIn(0, 3) else 0]),
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
                KsuNavRow(15, R.drawable.ic_ksu_download_for_offline, getString(R.string.addon_download),
                    value = manager?.label ?: getString(R.string.built_in_download_manager), enabled = managers != null),
                KsuNavRow(27, R.drawable.ic_ksu_play_circle, getString(R.string.external_video_player),
                    value = player?.label ?: getString(R.string.player_system_sharing), enabled = players != null),
                KsuNavRow(20, R.drawable.ic_ksu_bookmark, getString(R.string.import_or_export_bookmarks)),
                KsuNavRow(21, R.drawable.ic_ksu_upload_file, getString(R.string.import_data)),
                KsuNavRow(22, R.drawable.ic_ksu_save_alt, getString(R.string.export_data), value = getString(R.string.export_data_description)),
                KsuNavRow(23, R.drawable.ic_ksu_restore, getString(R.string.restore_tabs),
                    value = getString(restored[preferences.restoreClosedTabs.coerceIn(0, 2)])),
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
            2 -> shell.openPage("agent")
            3 -> handlers.route("clear_data")
            4 -> shell.openPage("block_ads")
            5 -> shell.openPage("site_conf")
            6 -> shell.openPage("action_night")
            9 -> handlers.route("language")
            10 -> handlers.route("home")
            13 -> handlers.route("orientation")
            14 -> handlers.route("download")
            15 -> handlers.route("addon_download")
            18 -> handlers.route("clear_data_on_exit")
            19 -> shell.openPage("settings_operation")
            20 -> handlers.route("import_or_export_bookmarks")
            21 -> handlers.route("import_data")
            22 -> handlers.route("export_data")
            23 -> handlers.route("restore_tabs")
            24 -> handlers.route("setting_default")
            26 -> shell.openPage("user_syns")
            27 -> handlers.route("external_video_player")
            28 -> shell.openPage("customize_menu")
            30 -> shell.openPage("reader_mode")
            31 -> shell.openPage("search_settings")
            32 -> shell.openPage("toolbars_settings")
            35 -> shell.openPage("font")
            36 -> shell.openPage("password_manager")
            37 -> shell.openPage("customize_context_menu")
        }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        if (row.key == 33) preferences.showUndoCloseTab = checked
        refresh()
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

    private fun languageTitle(): String {
        val language = preferences.language.orEmpty()
        if (language.isEmpty()) return getString(R.string.follow_system)
        val locale = Locale.forLanguageTag(language)
        val name = locale.getDisplayLanguage(locale)
        val country = locale.getDisplayCountry(locale)
        return (if (country.isEmpty()) name else "$name($country)").takeUnless { it == language } ?: getString(R.string.follow_system)
    }

    private fun homeTitle(): String = when (preferences.home) {
        "about:home", "about:links" -> getString(R.string.default_set)
        "about:blank" -> getString(R.string.action_blank)
        "about:bookmarks" -> getString(R.string.action_bookmarks)
        else -> preferences.home
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

package dev.ujhhgtg.via.settings.compose

import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsSwipeBack
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** hb.z: the classic Advanced page, ported row for row with Material icons. */
class KsuAdvancedSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_advanced
    private val preferences by lazy { BrowserPreferences(requireContext()) }

    override fun groups(): List<KsuGroup> {
        GeneratedDocumentState.initialize(preferences)
        val rows = buildList {
            add(KsuToggleRow(4, R.drawable.ic_ksu_data_saver_on, getString(R.string.save_data),
                getString(R.string.save_data_description), preferences.saveData))
            add(KsuToggleRow(1, R.drawable.ic_ksu_travel_explore, getString(R.string.show_sniffer_btn_automatically),
                checked = preferences.showSnifferButton))
            add(KsuToggleRow(3, R.drawable.ic_ksu_bug_report, getString(R.string.web_page_debug),
                checked = preferences.webPageDebug))
            if (SettingsSwipeBack.predictiveBackSupported()) {
                add(KsuToggleRow(8, R.drawable.ic_ksu_gesture, getString(R.string.disable_predictive_back_gesture),
                    checked = preferences.disablePredictiveBack))
            }
            add(KsuToggleRow(7, R.drawable.ic_ksu_open_in_new, getString(R.string.disable_custom_tabs),
                getString(R.string.disable_custom_tabs_description), preferences.disableCustomTabs))
            add(KsuToggleRow(5, R.drawable.ic_ksu_gpp_maybe, getString(R.string.title_disable_safe_browsing),
                getString(R.string.title_disable_safe_browsing_description), !preferences.safeBrowsing))
            add(KsuNavRow(6, R.drawable.ic_ksu_https, getString(R.string.title_ignore_ssl_warnings),
                getString(R.string.title_ignore_ssl_warnings_description)))
            add(KsuToggleRow(9, R.drawable.ic_ksu_tune, getString(R.string.settings_ksu_ui),
                getString(R.string.settings_ksu_ui_summary), preferences.ksuSettingsUi))
        }
        return listOf(KsuGroup(null, rows))
    }

    override fun onRow(row: KsuRow) {
        if (row.key == 6) (requireActivity() as Shell).navigate(KsuSslWarningsSettingsFragment())
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        when (row.key) {
            4 -> preferences.saveData = checked
            1 -> preferences.showSnifferButton = checked
            3 -> {
                preferences.webPageDebug = checked
                if (checked) ViaDialog(requireActivity()).title(R.string.web_page_debug).message(R.string.web_page_debug_info)
                    .positive(android.R.string.ok).show()
            }
            5 -> preferences.safeBrowsing = !checked
            7 -> preferences.disableCustomTabs = checked
            8 -> preferences.disablePredictiveBack = checked
            9 -> {
                preferences.ksuSettingsUi = checked
                android.widget.Toast.makeText(requireContext(), R.string.settings_ksu_ui_toast, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        // hb.z.h3: only Save-Data, debugging and safe-browsing changes set n.y.
        if (row.key == 4 || row.key == 3 || row.key == 5) GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS)
        refresh()
    }

}

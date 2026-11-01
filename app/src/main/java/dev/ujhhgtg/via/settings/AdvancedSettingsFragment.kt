package dev.ujhhgtg.via.settings

import android.os.Bundle
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** hb.z: original row IDs/order, platform gates, flag inversions and warning entry. */
class AdvancedSettingsFragment : SettingsListFragment() {
    private lateinit var preferences: BrowserPreferences
    private lateinit var rows: SettingsRowsAdapter
    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.settings_advanced)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = BrowserPreferences(requireContext())
        GeneratedDocumentState.initialize(preferences)
        rows = SettingsRowsAdapter(::clicked)
        list.adapter = rows
        bindRows()
    }
    private fun bindRows() = rows.submit(buildList {
        add(SettingsToggleRow(4, getString(R.string.save_data), getString(R.string.save_data_description), preferences.saveData))
        add(SettingsToggleRow(1, getString(R.string.show_sniffer_btn_automatically), checked = preferences.showSnifferButton))
        add(SettingsToggleRow(3, getString(R.string.web_page_debug), checked = preferences.webPageDebug))
        if (predictiveBackSupported()) add(SettingsToggleRow(8, getString(R.string.disable_predictive_back_gesture), checked = preferences.disablePredictiveBack))
        // Original API21/API26 rows are unconditional on the user-approved minSdk29.
        add(SettingsToggleRow(7, getString(R.string.disable_custom_tabs), getString(R.string.disable_custom_tabs_description), preferences.disableCustomTabs))
        add(SettingsToggleRow(5, getString(R.string.title_disable_safe_browsing), getString(R.string.title_disable_safe_browsing_description), !preferences.safeBrowsing))
        add(SettingsRow(6, getString(R.string.title_ignore_ssl_warnings), getString(R.string.title_ignore_ssl_warnings_description)))
        add(SettingsToggleRow(9, getString(R.string.settings_ksu_ui), getString(R.string.settings_ksu_ui_summary), preferences.ksuSettingsUi))
    })
    private fun clicked(row: SettingsRow) {
        if (row.id == 6) { ignoredSslWarnings(); return }
        val enabled = !(row as SettingsToggleRow).checked
        when (row.id) {
            4 -> preferences.saveData = enabled
            1 -> preferences.showSnifferButton = enabled
            3 -> {
                preferences.webPageDebug = enabled
                if (enabled) ViaDialog(requireActivity()).title(R.string.web_page_debug).message(R.string.web_page_debug_info)
                    .positive(android.R.string.ok).show()
            }
            5 -> preferences.safeBrowsing = !enabled
            7 -> preferences.disableCustomTabs = enabled
            8 -> preferences.disablePredictiveBack = enabled
            9 -> {
                preferences.ksuSettingsUi = enabled
                android.widget.Toast.makeText(requireContext(), R.string.settings_ksu_ui_toast, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        // hb.z.h3: only Save-Data, debugging and safe-browsing changes set n.y.
        if (row.id == 4 || row.id == 3 || row.id == 5) GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS)
        bindRows()
    }
    private fun ignoredSslWarnings() {
        fun open() = (requireActivity() as Shell).navigate(SslWarningsSettingsFragment())
        if (preferences.ignoredSslWarning != 0) open()
        else ViaDialog(requireActivity()).title(R.string.title_ignore_ssl_warnings).message(R.string.message_ignore_ssl_warnings)
            .check(R.string.title_ignore_ssl_warnings_confirmation, false).checkRequired(true)
            .positive(android.R.string.ok) { _, _ -> open() }.negative(android.R.string.cancel).show()
    }
}

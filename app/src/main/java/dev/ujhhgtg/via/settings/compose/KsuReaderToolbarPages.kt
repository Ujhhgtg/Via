package dev.ujhhgtg.via.settings.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.reader.ReaderColorPalette
import dev.ujhhgtg.via.settings.TextEditorFragment
import dev.ujhhgtg.via.ui.behavior.BehaviorPreferences

/** hb.o5: the reader mode switches, palette, size and CSS entry, now Material. */
class KsuReaderSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.reader_mode

    private val preferences by lazy { BrowserPreferences(requireContext()) }

    override fun groups(): List<KsuGroup> {
        GeneratedDocumentState.initialize(preferences)
        val colors = ReaderColorPalette.colors
        val current = colors.indexOf(preferences.readerThemeColor).coerceAtLeast(0)
        return listOf(
            KsuGroup(null, listOf(
                KsuToggleRow(1, R.drawable.ic_ksu_chrome_reader_mode, getString(R.string.require_confirmation_to_enable_reader_mode),
                    checked = preferences.readerConfirmation),
                KsuNavRow(2, R.drawable.ic_ksu_palette, getString(R.string.appearance),
                    value = if (current == 0) getString(R.string.default_set) else "#${Integer.toHexString(colors[current])}"),
                KsuNavRow(3, R.drawable.ic_ksu_font_download, getString(R.string.size), value = "${preferences.readerTextSize}px"),
                KsuNavRow(4, R.drawable.ic_ksu_code, getString(R.string.custom_reader_css)),
            )),
        )
    }

    override fun onRow(row: KsuRow) {
        val shell = requireActivity() as Shell
        when (row.key) {
            2 -> shell.navigate(KsuReaderColorSettingsFragment())
            3 -> shell.navigate(KsuReaderSizeSettingsFragment())
            4 -> shell.navigate(KsuTextEditorSettingsFragment.newInstance(
                getString(R.string.custom_reader_css), preferences.readerCustomCss.orEmpty(), getString(R.string.custom_reader_css), true))
        }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        if (row.key != 1) return
        preferences.readerConfirmation = checked
        refresh()
    }
}

/** The five reader palette colours as Material choice rows. */
class KsuReaderColorSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.appearance
    private val preferences by lazy { BrowserPreferences(requireContext()) }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, ReaderColorPalette.colors.mapIndexed { index, color ->
            KsuCheckRow(color, R.drawable.ic_ksu_palette,
                if (index == 0) getString(R.string.default_set) else "#${Integer.toHexString(color)}",
                checked = preferences.readerThemeColor == color)
        }),
    )

    override fun onCheck(row: KsuCheckRow, checked: Boolean) {
        if (!checked) return
        preferences.readerThemeColor = row.key as Int
        GeneratedDocumentState.mark(GeneratedDocumentState.BLANK_AND_PAGE_STATE)
        refresh()
    }
}

/** The reader text size slider on its own page. */
class KsuReaderSizeSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.size
    private val preferences by lazy { BrowserPreferences(requireContext()) }

    override fun groups(): List<KsuGroup> = emptyList()

    override fun header(): (@Composable () -> Unit)? = {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 13.dp)) {
            Text(
                text = getString(if (java.util.Locale.getDefault().country == "CN") R.string.night_filter_preview_china
                else R.string.night_filter_preview),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Slider(
                value = preferences.readerTextSize.toFloat(),
                onValueChange = {
                    preferences.readerTextSize = it.toInt()
                    GeneratedDocumentState.mark(GeneratedDocumentState.BLANK_AND_PAGE_STATE)
                },
                valueRange = 10f..30f,
                steps = 19,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** hb.i1: the shared text editor page, returning the same edit_text_result contract. */
class KsuTextEditorSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.untitled

    private var title = ""
    private var text = ""
    private var placeholder = ""
    private var code = false

    override fun pageTitle(): String = title.ifEmpty { getString(titleRes) }

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        title = args.getString("title").orEmpty()
        text = args.getString("text").orEmpty()
        placeholder = args.getString("placeholder").orEmpty()
        code = args.getBoolean("code", false)
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuInputRow(1, R.drawable.ic_ksu_code, title.ifEmpty { getString(R.string.untitled) }, value = text,
                placeholder = placeholder.ifEmpty { null }, multiline = true),
        )),
    )

    override fun onInput(row: KsuInputRow, value: String) { text = value }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(R.string.action_save)) {
            parentFragmentManager.setFragmentResult(RESULT, android.os.Bundle().apply { putString("text", text) })
            onBack()
        },
    )

    companion object {
        const val RESULT = "edit_text_result"

        fun newInstance(title: String?, text: String?, placeholder: String?, code: Boolean) =
            KsuTextEditorSettingsFragment().apply {
                arguments = android.os.Bundle().apply {
                    putString("title", title)
                    putString("text", text)
                    putString("placeholder", placeholder)
                    putBoolean("code", code)
                }
            }
    }
}

/** hb.l7: toolbar mode/fullscreen/tab-bar/address-field switches (the preview stays classic). */
class KsuToolbarSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.toolbars_settings

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private val behavior by lazy { BehaviorPreferences(preferences) }

    override fun groups(): List<KsuGroup> {
        val modes = intArrayOf(R.string.ui_traditional, R.string.ui_simple_top, R.string.ui_simple_bottom, R.string.two_row_toolbar)
        val fullscreen = intArrayOf(R.string.fullscreen_0, R.string.fullscreen_1, R.string.fullscreen_2)
        val url = intArrayOf(R.string.hint_title, R.string.hint_url, R.string.url_box_domain)
        return listOf(
            KsuGroup(null, listOf(
                KsuDropdownRow(1, R.drawable.ic_ksu_view_agenda, getString(R.string.ui_mode),
                    options = modes.map(::getString), selectedIndex = preferences.appUi.takeIf { it in 0..3 } ?: 0),
                KsuDropdownRow(2, R.drawable.ic_ksu_open_in_new, getString(R.string.fullscreen),
                    options = fullscreen.map(::getString), selectedIndex = preferences.fullScreenMode.coerceIn(0, 2)),
                KsuToggleRow(3, R.drawable.ic_ksu_tab, getString(R.string.enable_tab_bar),
                    checked = behavior.tabBarEnabled && behavior.toolbarMode in 1..2),
                KsuDropdownRow(4, R.drawable.ic_ksu_link_off, getString(R.string.url_box),
                    options = url.map(::getString), selectedIndex = preferences.urlBoxMode.coerceIn(0, 2)),
                KsuToggleRow(5, R.drawable.ic_ksu_palette, getString(R.string.color_mode),
                    getString(R.string.color_mode_hint), behavior.colorToolbars),
            )),
        )
    }

    override fun onSelect(row: KsuDropdownRow, index: Int) {
        when (row.key) {
            1 -> behavior.toolbarMode = index
            2 -> preferences.fullScreenMode = index
            4 -> preferences.urlBoxMode = index
        }
        refresh()
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        when (row.key) {
            3 -> {
                // hb.l7.m3 checks the stored flag, not the mode-filtered row state.
                if (checked && behavior.toolbarMode !in 1..2) { behavior.toolbarMode = 1; behavior.tabBarEnabled = true }
                else behavior.tabBarEnabled = checked
            }
            5 -> behavior.colorToolbars = checked
        }
        refresh()
    }
}

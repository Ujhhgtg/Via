package dev.ujhhgtg.via.settings.compose

import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.behavior.BehaviorPreferences
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** i8.w: the context-menu visibility switches, now Material rows. */
class KsuContextMenuSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.customize_context_menu

    private val behavior by lazy { BehaviorPreferences(BrowserPreferences(requireContext())) }

    private val entries = listOf(
        0 to (R.string.action_open_in_background to R.drawable.ic_ksu_open_in_new),
        1 to (R.string.action_open_in_new to R.drawable.ic_ksu_open_in_new),
        2 to (R.string.action_view_image to R.drawable.ic_ksu_image),
        6 to (R.string.action_save_img to R.drawable.ic_ksu_save_alt),
        38 to (R.string.action_download_image to R.drawable.ic_ksu_download),
        33 to (R.string.share_image to R.drawable.ic_ksu_share),
        7 to (R.string.search_by_image to R.drawable.ic_ksu_search),
        8 to (R.string.action_pic_mode to R.drawable.ic_ksu_palette),
        22 to (R.string.action_page_info to R.drawable.ic_ksu_info),
        23 to (R.string.action_mark to R.drawable.ic_ksu_bookmark),
        29 to (R.string.action_copy_link_text to R.drawable.ic_ksu_content_copy),
        34 to (R.string.scan_qr_code to R.drawable.ic_ksu_qr_code_scanner),
        3 to (R.string.action_copy to R.drawable.ic_ksu_content_copy),
        31 to (R.string.action_share to R.drawable.ic_ksu_share),
    )

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, entries.map { (id, labelAndIcon) ->
            KsuToggleRow(id, labelAndIcon.second, getString(labelAndIcon.first), checked = behavior.contextMenuVisible(id))
        }),
    )

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        behavior.setContextMenuVisible(row.key as Int, checked)
        refresh()
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_restore, getString(R.string.action_reset)) {
            ViaDialog(requireActivity()).title(R.string.reset_to_default_settings).message(R.string.dialog_sure)
                .positive(R.string.action_reset) { _, _ -> behavior.resetContextMenus(); refresh() }
                .negative(android.R.string.cancel).show()
        },
    )
}

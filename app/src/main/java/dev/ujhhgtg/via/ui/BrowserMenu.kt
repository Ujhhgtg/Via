package dev.ujhhgtg.via.ui

import android.content.Context
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.skins.SkinResources

/** Browser menu actions and their application resources. */
object BrowserMenu {
    const val EXTENSIONS = 42
    data class Entry(val id: Int, val titleRes: Int, val icon: Int) {
        fun title(context: Context) = context.getString(titleRes)
        /** i8.l.b resolves the menu's ic_menu_* key before applying its active-state tint. */
        fun drawable(context: Context) = SkinResources.drawable(context, icon)
    }
    val entries = listOf(
        Entry(1, R.string.action_night, R.drawable.moon),
        Entry(2, R.string.action_bookmarks, R.drawable.star_wide),
        Entry(4, R.string.action_downloads, R.drawable.arrow_down),
        Entry(5, R.string.action_incognito, R.drawable.ghost),
        Entry(6, R.string.action_share, R.drawable.share),
        Entry(7, R.string.action_add_bookmark, R.drawable.star_plus),
        Entry(8, R.string.action_pcview, R.drawable.monitor),
        Entry(9, R.string.action_tools, R.drawable.briefcase),
        Entry(10, R.string.settings, R.drawable.nut),
        Entry(11, R.string.action_find, R.drawable.find),
        Entry(12, R.string.action_save, R.drawable.floppy),
        Entry(13, R.string.action_saved_pages, R.drawable.layers),
        Entry(14, R.string.action_translate, R.drawable.letter_a_boxed),
        Entry(15, R.string.action_source, R.drawable.code),
        Entry(16, R.string.action_fullscreen, R.drawable.expand),
        Entry(17, R.string.image_on, R.drawable.curved_arrow_boxed),
        Entry(18, R.string.resource_sniffer, R.drawable.music_note_boxed),
        Entry(19, R.string.agent, R.drawable.phone_boxed),
        Entry(20, R.string.network_log, R.drawable.pulse_boxed),
        Entry(23, R.string.action_mark, R.drawable.pencil),
        Entry(24, R.string.operation_reload, R.drawable.refresh),
        Entry(25, R.string.scan_qr_code, R.drawable.tab_square_dash),
        Entry(26, R.string.site_conf, R.drawable.globe_wireframe),
        Entry(27, R.string.settings_script, R.drawable.code_window),
        Entry(28, R.string.block_ads, R.drawable.minus_circle),
        Entry(29, R.string.size, R.drawable.letter_t_boxed),
        Entry(30, R.string.orientation, R.drawable.copy),
        Entry(31, R.string.clear_data, R.drawable.trash),
        Entry(32, R.string.print_or_pdf, R.drawable.printer),
        Entry(33, R.string.read_aloud, R.drawable.bars_circle),
        Entry(34, R.string.add_to_home_screen, R.drawable.square_plus),
        Entry(35, R.string.customize_menu, R.drawable.grid),
        Entry(36, R.string.reader_mode, R.drawable.panel_list),
        Entry(37, R.string.open_with, R.drawable.android_head),
        Entry(38, R.string.game_mode, R.drawable.gamepad),
        Entry(40, R.string.action_add_favorite, R.drawable.heart_plus),
        Entry(3, R.string.action_history, R.drawable.clock_outline),
        Entry(EXTENSIONS, R.string.settings_extensions, R.drawable.puzzle)
    ).associateBy { it.id }
    // w9.k.s0(): the non-CN default; the CN build's item 41 (report abuse) was removed.
    val defaults = listOf(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,25,34,33,30,28,23,29,31,35,EXTENSIONS)
    /** Retired ids still present in saved menu layouts; dropped instead of rendering as blank slots. */
    val removed = setOf(41)
}

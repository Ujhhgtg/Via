package dev.ujhhgtg.via.settings.compose

import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.behavior.BehaviorPreferences

/** hb.y0's ad-blocking home: the two switches, the built-in filter toggle and the filter pages. */
class KsuBlockAdsSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.block_ads

    private val preferences by lazy { BrowserPreferences(requireContext()) }

    override fun groups(): List<KsuGroup> {
        val off = !preferences.adBlocking
        return listOf(
            KsuGroup(null, listOf(
                KsuToggleRow(1, R.drawable.ic_ksu_block, getString(R.string.block_ads),
                    if (preferences.adBlocking) getString(R.string.adblock_info, preferences.adBlockedTimes, savedDataLabel())
                    else getString(R.string.disable), preferences.adBlocking),
                KsuToggleRow(2, R.drawable.ic_ksu_filter_alt, getString(R.string.enable_built_in_filters),
                    getString(R.string.enable_built_in_filters_description), preferences.appFlags and 64 != 0, enabled = !off),
                KsuNavRow(3, R.drawable.ic_ksu_description, getString(R.string.custom_filters),
                    getString(R.string.custom_filters_description), enabled = !off),
                KsuNavRow(4, R.drawable.ic_ksu_sort, getString(R.string.filter_subscriptions),
                    getString(R.string.filter_subscriptions_description), enabled = !off),
                KsuToggleRow(5, R.drawable.ic_ksu_open_in_new, getString(R.string.expand_web_content_automatically),
                    getString(R.string.expand_web_content_automatically_description),
                    checked = dev.ujhhgtg.via.browser.script.BuiltinExpandScript.enabled(requireContext()),
                    enabled = !off && preferences.webFlags and 0x10000000 == 0),
            )),
        )
    }

    override fun onRow(row: KsuRow) {
        val shell = requireActivity() as Shell
        when (row.key) {
            3 -> shell.navigate(KsuCustomFiltersSettingsFragment())
            4 -> shell.navigate(KsuFilterSubscriptionsSettingsFragment())
        }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        when (row.key) {
            1 -> preferences.adBlocking = checked
            2 -> preferences.appFlags = preferences.appFlags xor 64
            5 -> dev.ujhhgtg.via.browser.script.BuiltinExpandScript.setEnabled(requireContext(), checked)
        }
        reloadBrowser()
        refresh()
    }

    /** z8.b0.x/w: the preference stores KiB, and units change at 0.8 of the next unit. */
    private fun savedDataLabel(): String {
        val bytes = preferences.getLong("savedata") * 1024.0
        val (amount, unit) = when {
            bytes >= 858993459.2 -> bytes / 1073741824.0 to "GB"
            bytes >= 838860.8 -> bytes / 1048576.0 to "MB"
            bytes >= 819.2 -> bytes / 1024.0 to "KB"
            else -> bytes to "B"
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", amount, unit)
    }

    private fun reloadBrowser() { (activity as? Shell)?.browserReloadPreferences() }
}

/** hb.u4's gesture switches; the two previews stay on the classic page until they get a port. */
class KsuGesturesSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_operation

    private val behavior by lazy { BehaviorPreferences(BrowserPreferences(requireContext())) }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuToggleRow(1, R.drawable.ic_ksu_gesture, getString(R.string.back_forward_gesture), checked = behavior.backForwardGesture),
            KsuToggleRow(2, R.drawable.ic_ksu_volume_up, getString(R.string.volume), checked = behavior.volumeScroll),
            KsuToggleRow(4, R.drawable.ic_ksu_play_circle, getString(R.string.enable_video_player_gestures),
                getString(R.string.enable_video_player_gestures_description), behavior.videoGestures),
            KsuNavRow(3, R.drawable.ic_ksu_keyboard, getString(R.string.keyboard_shortcuts)),
        )),
    )

    override fun onRow(row: KsuRow) {
        if (row.key == 3) (requireActivity() as Shell).navigate(KsuKeyboardShortcutsSettingsFragment())
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        when (row.key) {
            1 -> behavior.backForwardGesture = checked
            2 -> behavior.volumeScroll = checked
            4 -> behavior.videoGestures = checked
        }
        refresh()
    }
}

/** ra.i's keyboard chord table, one Material row per shortcut. */
class KsuKeyboardShortcutsSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.keyboard_shortcuts

    private val shortcuts = listOf(
        "Ctrl + t" to R.string.action_new_tab,
        "Ctrl + Tab" to R.string.operation_nexttab,
        "Ctrl + Shift + Tab" to R.string.operation_lasttab,
        "Alt + ←" to R.string.operation_goback,
        "Alt + →" to R.string.operation_goforward,
        "Ctrl + w" to R.string.operation_closetab,
        "Alt + f" to R.string.desc_menu,
        "Ctrl + Shift + b" to R.string.action_bookmarks,
        "Ctrl + h" to R.string.action_history,
        "Ctrl + f" to R.string.action_find,
        "Ctrl + l" to R.string.operation_input,
        "F5 / Ctrl + r" to R.string.operation_reload,
        "Ctrl + u" to R.string.action_source,
        "Ctrl + d" to R.string.action_add_bookmark,
    )

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, shortcuts.mapIndexed { index, (chord, label) ->
            KsuNavRow(index, R.drawable.ic_ksu_keyboard, getString(label), value = chord)
        }),
    )
}

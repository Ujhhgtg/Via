package dev.ujhhgtg.via.settings.compose

import android.os.Bundle
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.ViaToast

/*
 * The rows that used to open a multi-select dialog now live on their own Material pages:
 * pick the items, confirm from the top bar, done.
 */

private val CLEAR_LABELS = intArrayOf(
    R.string.data_cache,
    R.string.data_form_data,
    R.string.data_history,
    R.string.recently_closed_tabs,
    R.string.data_storage,
    R.string.data_cookies,
    R.string.data_app_cache,
)
private val CLEAR_MASKS = intArrayOf(1, 2, 4, 64, 8, 16, 32)
private val CLEAR_ICONS = intArrayOf(
    R.drawable.ic_ksu_cached,
    R.drawable.ic_ksu_assignment,
    R.drawable.ic_ksu_history,
    R.drawable.ic_ksu_tab,
    R.drawable.ic_ksu_storage,
    R.drawable.ic_ksu_cookie,
    R.drawable.ic_ksu_cleaning_services,
)

private val EXPORT_LABELS = intArrayOf(
    R.string.action_bookmarks,
    R.string.action_favorites,
    R.string.action_history,
    R.string.recently_closed_tabs,
    R.string.settings,
    R.string.passwords,
)
private val EXPORT_MASKS = intArrayOf(2, 4, 8, 16, 1, 32)
private val EXPORT_ICONS = intArrayOf(
    R.drawable.ic_ksu_bookmark,
    R.drawable.ic_ksu_star,
    R.drawable.ic_ksu_history,
    R.drawable.ic_ksu_tab,
    R.drawable.ic_ksu_settings,
    R.drawable.ic_ksu_password,
)

private val SUGGESTION_LABELS = intArrayOf(
    R.string.action_favorites,
    R.string.action_bookmarks,
    R.string.title_open_tabs,
    R.string.action_history,
    R.string.search,
    R.string.search_history,
)
private val SUGGESTION_ICONS = intArrayOf(
    R.drawable.ic_ksu_star,
    R.drawable.ic_ksu_bookmark,
    R.drawable.ic_ksu_tab,
    R.drawable.ic_ksu_history,
    R.drawable.ic_ksu_search,
    R.drawable.ic_ksu_history,
)

/** hb.z.h / SettingsController.clearData: the category picker, now a page. */
class KsuClearDataSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.clear_data

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private var working: BooleanArray? = null

    private fun selection(): BooleanArray = working ?: BooleanArray(CLEAR_MASKS.size) { preferences.clearData and CLEAR_MASKS[it] != 0 }
        .also { working = it }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, CLEAR_LABELS.indices.map { index ->
            KsuCheckRow(index, CLEAR_ICONS[index], getString(CLEAR_LABELS[index]), checked = selection()[index])
        }),
    )

    override fun onCheck(row: KsuCheckRow, checked: Boolean) {
        selection()[(row.key as Int)] = checked
        refresh()
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(android.R.string.ok)) { confirm() },
    )

    private fun confirm() {
        val mask = CLEAR_MASKS.indices.filter { selection()[it] }.fold(0) { result, index -> result or CLEAR_MASKS[index] }
        preferences.clearData = mask
        BrowserDatabase(requireContext()).use { database ->
            if (dev.ujhhgtg.via.browser.BrowserDataCleaner(requireContext(), database).clearManual(mask)) {
                ViaToast.makeText(requireContext(), getString(R.string.data_cleared), ViaToast.LENGTH_SHORT).show()
            }
        }
        onBack()
    }
}

/** hb.z's clear-on-exit category picker, now a page. */
class KsuClearOnExitSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.clear_data_on_exit

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private var working: BooleanArray? = null

    private fun selection(): BooleanArray = working ?: BooleanArray(CLEAR_MASKS.size) { preferences.clearDataOnExit and CLEAR_MASKS[it] != 0 }
        .also { working = it }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, CLEAR_LABELS.indices.map { index ->
            KsuCheckRow(index, CLEAR_ICONS[index], getString(CLEAR_LABELS[index]), checked = selection()[index])
        }),
    )

    override fun onCheck(row: KsuCheckRow, checked: Boolean) {
        selection()[(row.key as Int)] = checked
        refresh()
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(android.R.string.ok)) { confirm() },
    )

    private fun confirm() {
        preferences.clearDataOnExit = CLEAR_MASKS.indices.filter { selection()[it] }
            .fold(0) { result, index -> result or CLEAR_MASKS[index] }
        onBack()
    }
}

/** The export page's section picker: returns the chosen sections to the caller. */
class KsuExportSectionsSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.export_data
    private var working: BooleanArray = booleanArrayOf(true, true, true, true, true, false)

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, EXPORT_LABELS.indices.map { index ->
            KsuCheckRow(index, EXPORT_ICONS[index], getString(EXPORT_LABELS[index]), checked = working[index])
        }),
    )

    override fun onCheck(row: KsuCheckRow, checked: Boolean) {
        working[(row.key as Int)] = checked
        refresh()
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(android.R.string.ok)) { confirm() },
    )

    private fun confirm() {
        val mask = EXPORT_MASKS.indices.filter { working[it] }.fold(0) { result, index -> result or EXPORT_MASKS[index] }
        if (mask == 0) {
            onBack()
            return
        }
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putInt("mask", mask) })
        onBack()
    }

    companion object {
        const val RESULT = "export_sections"
    }
}

/** hb.b6's six-bit address-bar suggestion mask, now a page. */
class KsuSearchSuggestionsSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.title_search_suggestions

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private var working: BooleanArray? = null

    private fun selection(): BooleanArray = working ?: BooleanArray(SUGGESTION_LABELS.size) {
        preferences.searchSuggestion and (1 shl it) != 0
    }.also { working = it }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, SUGGESTION_LABELS.indices.map { index ->
            KsuCheckRow(index, SUGGESTION_ICONS[index], getString(SUGGESTION_LABELS[index]), checked = selection()[index])
        }),
    )

    override fun onCheck(row: KsuCheckRow, checked: Boolean) {
        selection()[(row.key as Int)] = checked
        refresh()
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(android.R.string.ok)) { confirm() },
    )

    private fun confirm() {
        preferences.searchSuggestion = SUGGESTION_LABELS.indices.filter { selection()[it] }
            .fold(0) { result, index -> result or (1 shl index) }
        onBack()
    }
}

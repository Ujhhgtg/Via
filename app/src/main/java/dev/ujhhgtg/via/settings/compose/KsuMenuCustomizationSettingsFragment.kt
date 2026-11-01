package dev.ujhhgtg.via.settings.compose

import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.BrowserMenu
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.behavior.BehaviorPreferences

/**
 * i8.s: the main-menu customisation page. Rows keep the classic order (shown items first, then
 * hidden ones); the switch moves an item between the two lists and the long-press actions move it
 * up or down instead of the classic drag handle.
 */
class KsuMenuCustomizationSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.customize_menu

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private val behavior by lazy { BehaviorPreferences(preferences) }
    private val displayed = mutableListOf<Int>()
    private val hidden = mutableListOf<Int>()

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        load()
    }

    private fun load() {
        displayed.clear()
        hidden.clear()
        val available = BrowserMenu.entries.keys.toMutableSet()
        val shown = preferences.displayedMenus?.split(',')?.mapNotNull(String::toIntOrNull) ?: BrowserMenu.defaults
        for (id in shown) if (available.remove(id)) displayed.add(id)
        for (id in preferences.hiddenMenus.orEmpty().split(',').mapNotNull(String::toIntOrNull)) {
            if (available.remove(id)) hidden.add(id)
        }
        hidden.addAll(available.sorted())
        // i8.s.n3 keeps Settings visible even if an imported ordering hides it.
        if (hidden.remove(10) || 10 !in displayed) displayed.add(10)
        refresh()
    }

    override fun groups(): List<KsuGroup> {
        return listOf(KsuGroup(null, buildList {
            displayed.forEach { id ->
                add(KsuSwitchNavRow(id, R.drawable.ic_ksu_menu, title(id), getString(R.string.settings_group_general),
                    checked = true, enabled = id != 10, actions = actionsFor(id)))
            }
            hidden.forEach { id ->
                add(KsuSwitchNavRow(id, R.drawable.ic_ksu_menu, title(id), getString(R.string.action_hide),
                    checked = false, actions = actionsFor(id)))
            }
        }))
    }

    private fun title(id: Int): String = BrowserMenu.entries[id]?.title(requireContext()) ?: id.toString()

    private fun actionsFor(id: Int): List<KsuMenuAction> {
        if (id == 10) return emptyList()
        return listOf(KsuMenuAction(1, getString(R.string.move_up)), KsuMenuAction(2, getString(R.string.move_down)))
    }

    override fun onSwitchRow(row: KsuSwitchNavRow, checked: Boolean) {
        val id = row.key as? Int ?: return
        if (id == 10) return
        if (checked) {
            if (hidden.remove(id)) displayed.add(id)
        } else {
            if (displayed.remove(id)) hidden.add(0, id)
        }
        persist()
    }

    override fun onAction(row: KsuSwitchNavRow, action: KsuMenuAction) {
        val id = row.key as? Int ?: return
        val list = if (id in displayed) displayed else hidden
        val index = list.indexOf(id)
        val target = if (action.id == 1) index - 1 else index + 1
        if (index < 0 || target < 0 || target >= list.size) return
        list.removeAt(index)
        list.add(target, id)
        persist()
    }

    private fun persist() {
        behavior.reorderMenus(displayed, hidden)
        refresh()
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_restore, getString(R.string.action_reset)) {
            confirm(
                title = getString(R.string.reset_to_default_settings),
                message = getString(R.string.dialog_sure),
                confirmText = getString(R.string.action_reset),
                dismissText = getString(android.R.string.cancel),
            ) { behavior.resetMenus(); load() }
        },
    )
}

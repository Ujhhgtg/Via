package dev.ujhhgtg.via.settings.compose

import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.filter.FilterStore
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import kotlinx.coroutines.launch

/** z7.q0's custom filter rules: the classic code editor became a Material text page. */
class KsuCustomFiltersSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.custom_filters

    private val store by lazy { FilterStore(requireContext()) }
    private var text = ""
    private var initial = ""

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            text = runCatching { store.readCustom().trim() }.getOrDefault("")
            initial = text
            refresh()
        }
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuInputRow(1, R.drawable.ic_ksu_code, getString(R.string.custom_filters), value = text,
                placeholder = getString(R.string.custom_filters_description), multiline = true),
        )),
    )

    override fun onInput(row: KsuInputRow, value: String) { text = value }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(R.string.action_save)) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { store.writeCustom(text) }
                onBack()
            }
        },
    )
}

/** z7.q0: subscription list with its update interval, add menu and per-subscription actions. */
class KsuFilterSubscriptionsSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.filter_subscriptions

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private val store by lazy { FilterStore(requireContext()) }
    private var records = emptyList<FilterStore.Subscription>()

    private val intervals = longArrayOf(0, 1, 3, 7, 15)
    private val intervalLabels = intArrayOf(R.string.never, R.string.every_day, R.string.every_3_days, R.string.every_week, R.string.every_15_days)

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        records = if (store.subscribedFile.isFile) store.readSubscriptions() else store.defaultSubscriptions()
        refresh()
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, buildList {
            add(KsuDropdownRow(KEY_INTERVAL, R.drawable.ic_ksu_schedule, getString(R.string.update_interval),
                options = intervalLabels.map(::getString), selectedIndex = intervalIndex()))
            records.forEach { subscription ->
                add(KsuSwitchNavRow(
                    key = subscription.url,
                    icon = R.drawable.ic_ksu_filter_alt,
                    title = subscription.title.orEmpty().ifEmpty { subscription.url },
                    summary = subscription.url,
                    checked = subscription.enabled,
                    actions = listOf(
                        KsuMenuAction(1, getString(R.string.action_edit)),
                        KsuMenuAction(2, getString(R.string.custom_filters)),
                        KsuMenuAction(3, getString(R.string.action_delete)),
                    ),
                ))
            }
        }),
    )

    override fun onSelect(row: KsuDropdownRow, index: Int) {
        preferences.putLong("updater_filter_subscriptions", intervals[index] * 86_400_000L)
        refresh()
    }

    override fun onSwitchRow(row: KsuSwitchNavRow, checked: Boolean) {
        records = records.map { if (it.url == row.key) it.copy(enabled = checked) else it }
        persist()
    }

    override fun onAction(row: KsuSwitchNavRow, action: KsuMenuAction) {
        val subscription = records.firstOrNull { it.url == row.key } ?: return
        when (action.id) {
            2 -> (requireActivity() as Shell).navigate(KsuSubscriptionFiltersSettingsFragment.newInstance(subscription.filePath, subscription.title))
            1 -> edit(subscription)
            3 -> {
                records = records.filterNot { it.url == subscription.url }
                persist()
            }
        }
    }

    override fun topActions(): List<KsuTopAction> {
        val available = store.defaultSubscriptions().filter { candidate -> records.none { it.url == candidate.url } }
        return listOf(
            KsuTopAction(R.drawable.ic_ksu_add, getString(R.string.action_new), menu = buildList {
                available.forEachIndexed { index, candidate ->
                    add(KsuMenuAction(index, candidate.title.orEmpty().ifEmpty { candidate.url }))
                }
            }) { }
                .let { it },
        )
    }

    override fun onTopMenuAction(action: KsuMenuAction) {
        val available = store.defaultSubscriptions().filter { candidate -> records.none { it.url == candidate.url } }
        available.getOrNull(action.id)?.let {
            records = records + it
            persist()
        }
    }

    private fun edit(subscription: FilterStore.Subscription) {
        showInput(
            title = getString(R.string.action_edit),
            initial = subscription.url,
            hint = subscription.url,
        ) { raw ->
            val url = raw.trim()
            if (url.isEmpty()) return@showInput
            records = records.map { if (it.url == subscription.url) it.copy(url = url) else it }
            persist()
        }
    }

    private fun persist() {
        store.writeSubscriptions(records)
        refresh()
    }

    private fun intervalIndex() = intervals.indexOfLast {
        preferences.getLong("updater_filter_subscriptions") >= it * 86_400_000L
    }.coerceAtLeast(0)

    companion object {
        private const val KEY_INTERVAL = "filter_interval"
    }
}

/** One subscription's filter rules, read-only. */
class KsuSubscriptionFiltersSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.custom_filters

    private var title = ""
    private var lines = emptyList<String>()

    override fun pageTitle(): String = title.ifEmpty { getString(titleRes) }

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = arguments?.getString("title").orEmpty()
        val path = arguments?.getString("path").orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            lines = runCatching { java.io.File(path).readLines() }.getOrDefault(emptyList())
            refresh()
        }
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, lines.mapIndexed { index, line ->
            KsuNavRow(index, R.drawable.ic_ksu_filter_alt, line, value = null)
        }),
    )

    companion object {
        fun newInstance(path: String?, title: String?) = KsuSubscriptionFiltersSettingsFragment().apply {
            arguments = android.os.Bundle().apply {
                putString("path", path)
                putString("title", title)
            }
        }
    }
}

package dev.ujhhgtg.via.settings.compose

import android.os.Bundle
import android.util.Log
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.UrlResolver
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.search.SearchProvider
import dev.ujhhgtg.via.search.SearchProviders
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import org.json.JSONObject
import java.util.Locale

/*
 * hb.w5 / hb.c1 / hb.i6: the search engine list, its editor and the search toolbar page, ported to
 * the Material settings surface. Repository, preference keys and validation follow the classic
 * fragment; the only shape change is that rows are Compose cards.
 */

/** hb.w5: engine choices plus the add/edit/delete commands the classic page offered. */
class KsuSearchProvidersSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.search

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private var db: BrowserDatabase? = null
    private var store: SearchProviders? = null

    /** The classic page opened its database in onViewCreated; rows may compose before that. */
    private fun database(): BrowserDatabase = db ?: BrowserDatabase(requireContext()).also { db = it }

    private fun searchProviders(): SearchProviders =
        store ?: SearchProviders(requireContext(), preferences, database()).also { store = it }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener(RESULT_ENGINE, viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResult(key)
            val savedId = result.getInt(RESULT_ENGINE)
            // hb.w5's listener: re-apply the default so the stored template matches the new URL.
            if (savedId > 0 && preferences.searchMode == savedId) searchProviders().selectDefault(savedId)
            refresh()
        }
    }

    override fun onDestroyView() {
        db?.close()
        db = null
        store = null
        super.onDestroyView()
    }

    override fun groups(): List<KsuGroup> {
        val shortcuts = searchProviders().shortcuts()
        val rows = searchProviders().list().map { provider ->
            KsuRadioRow(
                key = provider.id,
                icon = R.drawable.ic_ksu_search,
                title = provider.name,
                summary = shortcuts[provider.id]?.takeIf { it.isNotEmpty() },
                selected = provider.id == preferences.searchMode,
                // Built-ins only opened the editor on long press; custom ones get edit/delete.
                actions = buildList {
                    add(KsuMenuAction(ACTION_EDIT, getString(R.string.action_edit)))
                    if (provider.id > 0) add(KsuMenuAction(ACTION_DELETE, getString(R.string.action_delete)))
                },
            )
        }
        return listOf(KsuGroup(null, rows))
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(
            icon = R.drawable.ic_ksu_add,
            description = getString(R.string.action_new),
            menu = listOf(KsuMenuAction(ACTION_NEW, getString(R.string.action_new))),
        ),
    )

    override fun onTopMenuAction(action: KsuMenuAction) {
        if (action.id == ACTION_NEW) edit(0)
    }

    override fun onRow(row: KsuRow) {
        (row.key as? Int)?.let { select(it) }
    }

    override fun onRadio(row: KsuRadioRow) {
        // Tapping a row always makes that engine the default, like the classic page.
        (row.key as? Int)?.let { select(it) }
    }

    override fun onRadioAction(row: KsuRadioRow, action: KsuMenuAction) {
        val id = row.key as? Int ?: return
        when (action.id) {
            ACTION_EDIT -> edit(id)
            ACTION_DELETE -> delete(id)
        }
    }

    private fun select(id: Int) {
        searchProviders().selectDefault(id)
        refresh()
    }

    private fun edit(id: Int) {
        (requireActivity() as Shell).navigate(KsuSearchProviderEditorSettingsFragment.newInstance(id))
    }

    private fun delete(id: Int) {
        // z8.v2.a(): this original CN build falls back to Baidu.
        if (preferences.searchMode == id) searchProviders().selectDefault(-2)
        SettingsDataRepository(database()).delete(id)
        val shortcuts = searchProviders().shortcuts().toMutableMap().apply { remove(id) }
        preferences.searchShortcuts = JSONObject(shortcuts.mapKeys { it.key.toString() }).toString()
        refresh()
    }

    companion object {
        private const val ACTION_NEW = 1
        private const val ACTION_EDIT = 1
        private const val ACTION_DELETE = 2
        private const val RESULT_ENGINE = "engine_result"
    }
}

/**
 * hb.c1 / tb.c: title, URL template and shortcut with the original validation, save path and
 * "engine_result" notification the list page listens for.
 */
class KsuSearchProviderEditorSettingsFragment : KsuPageFragment() {
    private var editingId = 0
    private var item = SettingsData()
    private lateinit var preferences: BrowserPreferences
    private var shortcuts = mutableMapOf<Int, String>()
    private var initialDigest: String? = null
    private var loaded = false
    private var titleValue = ""
    private var urlValue = ""
    private var shortcutValue = ""

    override val titleRes get() = if (editingId != 0) R.string.action_edit else R.string.action_new

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingId = arguments?.getInt("id", 0) ?: 0
        preferences = BrowserPreferences(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext().applicationContext
        viewLifecycleOwner.launchIo({
            BrowserDatabase(context).use { database ->
                val providers = SearchProviders(context, preferences, database)
                val existing = if (editingId > 0) SettingsDataRepository(database).find(editingId)
                else providers.list().firstOrNull { it.id == editingId }?.let { SettingsData(id = it.id, title = it.name) }
                Pair(existing, providers.shortcuts())
            }
        }, { (existing, values) ->
            shortcuts = values.toMutableMap()
            if (existing != null) {
                item = existing
                titleValue = existing.title.orEmpty()
                urlValue = existing.content.orEmpty()
                shortcutValue = shortcuts[existing.id].orEmpty()
                initialDigest = digest(existing.title, existing.content, shortcuts[existing.id].orEmpty())
            }
            loaded = true
            refresh()
        }) { Log.w("ViaSettings", "Unable to read search engine", it) }
    }

    override fun groups(): List<KsuGroup> {
        // Fields compose only after the row loads, so each input starts with its stored text.
        if (!loaded) return emptyList()
        val rows = buildList {
            add(KsuInputRow(KEY_TITLE, R.drawable.ic_ksu_description, getString(R.string.hint_title),
                value = titleValue, enabled = editingId >= 0))
            // A built-in engine only exposes its shortcut, exactly like the classic editor.
            if (editingId >= 0) add(KsuInputRow(KEY_URL, R.drawable.ic_ksu_https, getString(R.string.info_url),
                value = urlValue))
            add(KsuInputRow(KEY_SHORTCUT, R.drawable.ic_ksu_search, getString(R.string.search_shortcut),
                getString(R.string.search_shortcut_description), value = shortcutValue))
        }
        return listOf(KsuGroup(null, rows))
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(R.string.action_save)) { save(false) },
    )

    override fun onInput(row: KsuInputRow, text: String) {
        when (row.key) {
            KEY_TITLE -> titleValue = text
            KEY_URL -> urlValue = text
            KEY_SHORTCUT -> shortcutValue = text
        }
    }

    override fun onBack() = save(true)

    private fun titleText() = titleValue.replace(Regex("[\\t\\n\\r\\u00a0]+"), " ").trim()

    private fun urlText(): String? = if (editingId < 0) null else urlValue.trim().let {
        if (it.isNotEmpty() && ':' !in it) UrlResolver.normalizeInput(it) else it
    }

    private fun shortcutText() = shortcutValue.trim().lowercase(Locale.ROOT)

    private fun unchanged(): Boolean = digest(titleText(), urlText(), shortcutText())
        .let { initialDigest == null && it.isEmpty() || it == initialDigest }

    private fun save(confirm: Boolean) {
        if (unchanged()) { closeEditor(); return }
        if (confirm) {
            confirm(
                title = getString(R.string.message),
                message = getString(R.string.modified_content_has_not_been_saved_message),
                confirmText = getString(R.string.save_and_exit),
                dismissText = getString(R.string.exit),
            ) { save(false) }
            return
        }
        val shortcut = shortcutText()
        if (shortcut.codePointCount(0, shortcut.length) > 16 || shortcut.any(Character::isWhitespace)) {
            toast(R.string.search_shortcut_invalid); return
        }
        if (shortcut.isNotEmpty() && shortcuts.any { it.key != editingId && it.value == shortcut }) {
            toast(R.string.search_shortcut_duplicate); return
        }
        if (editingId < 0) { finishSave(editingId, shortcut); return }
        val title = titleText(); val url = urlText().orEmpty()
        if (title.isEmpty()) { toast(R.string.title_can_not_be_empty); return }
        if (url.isEmpty()) {
            toastMessage(getString(R.string.is_required, getString(R.string.info_url))); return
        }
        val lower = url.lowercase(Locale.ROOT)
        if (url.length >= 10 && listOf("via.search", "v://search", "via://search").any(lower::contains)) {
            toast(R.string.url_is_invalid); return
        }
        val context = requireContext().applicationContext
        viewLifecycleOwner.launchIo({
            val now = System.currentTimeMillis()
            item = item.copy(title = title, content = url, lastUpdatedAt = now,
                createdAt = if (item.id > 0) item.createdAt else now, type = SettingsData.SEARCH_ENGINE)
            BrowserDatabase(context).use { SettingsDataRepository(it).save(item) }
        }, { finishSave(it, shortcut) }) { Log.w("ViaSettings", "Unable to save search engine", it) }
    }

    private fun finishSave(id: Int, shortcut: String) {
        shortcuts[id] = shortcut
        preferences.searchShortcuts = JSONObject(shortcuts.mapKeys { it.key.toString() }).toString()
        parentFragmentManager.setFragmentResult(RESULT_ENGINE, Bundle().apply { putInt(RESULT_ENGINE, id) })
        closeEditor()
    }

    private fun toast(resource: Int) = ViaToast.makeText(requireContext(), resource, ViaToast.LENGTH_SHORT).show()

    private fun toastMessage(text: String) = ViaToast.makeText(requireContext(), text, ViaToast.LENGTH_SHORT).show()

    private fun closeEditor() = super.onBack()

    private fun digest(vararg values: String?): String {
        val source = values.joinToString("") { it ?: "__NULL__" }
        if (source.isEmpty()) return ""
        return java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
    }

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_URL = "url"
        private const val KEY_SHORTCUT = "shortcut"
        private const val RESULT_ENGINE = "engine_result"
        fun newInstance(id: Int) = KsuSearchProviderEditorSettingsFragment().apply {
            arguments = Bundle().apply { putInt("id", id) }
        }
    }
}

/**
 * hb.i6: the toolbar master switch plus one checkbox per engine, keeping the default engine
 * always enabled and the persisted disabled/order preference semantics.
 */
class KsuSearchToolbarSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.search_toolbar

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private val engines = mutableListOf<SearchProvider>()
    private val excluded = mutableSetOf<Int>()

    private val enabled get() = preferences.appFlags and 32768 == 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        excluded += preferences.searchToolBarDisabled.orEmpty().split(',').mapNotNull(String::toIntOrNull)
        val order = preferences.searchToolBarOrder.split(',').mapNotNull(String::toIntOrNull)
        val context = requireContext().applicationContext
        viewLifecycleOwner.launchIo({
            BrowserDatabase(context).use { database ->
                SearchProviders(context, preferences, database).list()
                    .sortedBy { order.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            }
        }, {
            engines.clear()
            engines += it
            refresh()
        }) { Log.w("ViaSettings", "Unable to read search engines", it) }
    }

    override fun groups(): List<KsuGroup> {
        val master = KsuGroup(null, listOf(
            KsuToggleRow(KEY_ENABLE, R.drawable.ic_ksu_view_agenda, getString(R.string.enable_search_toobar),
                checked = enabled),
        ))
        val list = engines.map { provider ->
            KsuCheckRow(
                key = engineKey(provider.id),
                icon = R.drawable.ic_ksu_search,
                title = provider.name,
                summary = if (provider.id == preferences.searchMode) getString(R.string.default_set) else null,
                checked = provider.id == preferences.searchMode || provider.id !in excluded,
                enabled = enabled,
            )
        }
        return listOfNotNull(master, list.takeIf { it.isNotEmpty() }?.let { KsuGroup(getString(R.string.search_engines), it) })
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        if (row.key != KEY_ENABLE) return
        preferences.appFlags = preferences.appFlags xor 32768
        refresh()
    }

    override fun onCheck(row: KsuCheckRow, checked: Boolean) {
        val id = engineId(row.key) ?: return
        // The default engine can not be switched off, like the classic row handler.
        if (id == preferences.searchMode) return
        setChecked(id, checked)
        persistSelection()
        refresh()
    }

    private fun setChecked(id: Int, checked: Boolean) {
        if (checked) excluded.remove(id) else excluded.add(id)
    }

    private fun persistSelection() {
        preferences.searchToolBarDisabled = engines.filter { it.id != preferences.searchMode && it.id in excluded }
            .joinToString(",") { it.id.toString() }
    }

    private fun engineKey(id: Int) = "engine:$id"

    private fun engineId(key: Any): Int? = (key as? String)?.removePrefix("engine:")?.toIntOrNull()

    companion object {
        private const val KEY_ENABLE = "search_toolbar_enabled"
    }
}

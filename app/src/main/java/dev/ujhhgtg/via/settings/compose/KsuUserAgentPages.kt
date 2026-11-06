package dev.ujhhgtg.via.settings.compose

import android.os.Bundle
import android.util.Log
import android.view.View
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.UserAgentPolicy
import dev.ujhhgtg.via.common.launchIo
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SettingsData
import dev.ujhhgtg.via.data.SettingsDataRepository
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.security.MessageDigest

/*
 * hb.w7 / hb.v1: the user agent list and its editor, ported to the Material settings surface.
 * Repository, preference keys, validation and the "ua_result" notification stay as in the classic
 * fragments; only the rows are now Compose cards.
 */

/** hb.w7: agent choices, the desktop choice, UA reduction and the add/edit/delete commands. */
class KsuUserAgentSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.agent

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private lateinit var database: BrowserDatabase
    private lateinit var repository: SettingsDataRepository

    /** Built-in agents first, then the saved rows, exactly like the classic `agents` list. */
    private val agents = mutableListOf<SettingsData>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = BrowserDatabase(requireContext())
        repository = SettingsDataRepository(database)
        agents += builtins()
        agents += repository.list(SettingsData.USER_AGENT)
        parentFragmentManager.setFragmentResultListener(RESULT, viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResult(key)
            val savedId = result.getInt(RESULT, 0)
            if (savedId > 0) refreshAgent(savedId)
        }
    }

    override fun onDestroyView() {
        if (::database.isInitialized) database.close()
        super.onDestroyView()
    }

    private fun builtins(): List<SettingsData> {
        val labels = intArrayOf(
            R.string.default_set, R.string.agent_android_phone, R.string.agent_android_tablet,
            R.string.agent_windows_chrome, R.string.agent_windows_ie11, R.string.agent_osx,
            R.string.agent_iphone, R.string.agent_ipad, R.string.agent_symbian,
        )
        return labels.mapIndexed { index, label ->
            SettingsData(
                id = -index,
                title = getString(label),
                type = SettingsData.USER_AGENT,
                content = UserAgentPolicy.resolve(-index, null, null, false, 0, null, false),
            )
        }
    }

    override fun groups(): List<KsuGroup> {
        val agentRows = agents.map { agent ->
            KsuRadioRow(
                key = agent.id,
                // Only the default entry keeps an icon; the choice itself is a radio button.
                icon = if (agent.id == 0) agentIcon(agent) else 0,
                title = agent.title.orEmpty(),
                selected = agent.id == preferences.userAgentChoice,
                // Only saved entries carry the edit/delete commands, as the classic long press did.
                actions = if (agent.id > 0) listOf(
                    KsuMenuAction(ACTION_EDIT, getString(R.string.action_edit)),
                    KsuMenuAction(ACTION_DELETE, getString(R.string.action_delete)),
                ) else emptyList(),
            )
        }
        val desktop = desktopChoices()
        val settings = listOf(
            KsuDropdownRow(
                key = KEY_DESKTOP,
                icon = R.drawable.ic_ksu_public,
                title = getString(R.string.user_agent_in_desktop_mode),
                options = desktop.map { it.title.orEmpty() },
                selectedIndex = desktop.indexOfFirst { it.id == preferences.duaChoice }.coerceAtLeast(0),
            ),
            KsuToggleRow(
                key = KEY_REDUCTION,
                icon = R.drawable.ic_ksu_data_saver_on,
                title = getString(R.string.user_agent_reduction),
                summary = getString(R.string.user_agent_reduction_description),
                checked = preferences.webFlags2 and 1 != 0,
            ),
        )
        return listOf(
            KsuGroup(null, agentRows),
            // The classic page dropped a "settings_advanced" heading before these two rows.
            KsuGroup(getString(R.string.settings_advanced), settings),
        )
    }

    /** Classic desktopChoice(): saved agents plus the default/Windows/macOS built-ins. */
    private fun desktopChoices(): List<SettingsData> =
        agents.filter { it.id > 0 || it.id in intArrayOf(0, -3, -4, -5) }

    private fun agentIcon(agent: SettingsData): Int = if (agent.id <= 0) {
        BUILTIN_ICONS.getOrNull(-agent.id) ?: R.drawable.ic_ksu_public
    } else R.drawable.ic_ksu_description

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_add, getString(R.string.action_new)) { edit(0) },
    )

    override fun onRadio(row: KsuRadioRow) {
        val id = row.key as? Int ?: return
        agents.firstOrNull { it.id == id }?.let { if (it.id != preferences.userAgentChoice) select(it) }
    }

    override fun onRadioAction(row: KsuRadioRow, action: KsuMenuAction) {
        val id = row.key as? Int ?: return
        when (action.id) {
            ACTION_EDIT -> edit(id)
            ACTION_DELETE -> delete(id)
        }
    }

    override fun onSelect(row: KsuDropdownRow, index: Int) {
        if (row.key != KEY_DESKTOP) return
        desktopChoices().getOrNull(index)?.let { selectDesktop(it); refresh() }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        if (row.key != KEY_REDUCTION) return
        preferences.webFlags2 = preferences.webFlags2 xor 1
        refresh()
    }

    private fun select(item: SettingsData) {
        preferences.userAgentChoice = item.id
        // hb.w7: only saved/custom choices also rewrite the resolved UA string.
        if (item.id > 0 || item.id <= -999) preferences.userAgent = item.content.orEmpty()
        refresh()
    }

    private fun selectDesktop(item: SettingsData) {
        preferences.duaChoice = item.id
        if (item.id > 0 || item.id <= -999) preferences.duaString = item.content
    }

    private fun edit(id: Int) {
        (requireActivity() as Shell).navigate(KsuUserAgentEditorSettingsFragment.newInstance(id))
    }

    private fun delete(id: Int) {
        if (id == preferences.userAgentChoice) agents.firstOrNull()?.let(::select)
        repository.delete(id)
        agents.removeAll { it.id == id }
        refresh()
    }

    /** hb.w7: re-reads a saved agent after the editor reports it through "ua_result". */
    private fun refreshAgent(id: Int) {
        val index = agents.indexOfFirst { it.id == id }
        viewLifecycleOwner.launchIo({ repository.find(id) }, { item ->
            if (item == null) return@launchIo
            if (index < 0) {
                agents += item
            } else {
                agents[index] = item
                if (id == preferences.userAgentChoice) select(item)
                if (id == preferences.duaChoice) selectDesktop(item)
            }
            refresh()
        }) { Log.w("ViaSettings", "Unable to reload saved user agent", it) }
    }

    companion object {
        private const val KEY_DESKTOP = "desktop_mode"
        private const val KEY_REDUCTION = "reduction"
        private const val ACTION_EDIT = 1
        private const val ACTION_DELETE = 2

        /** hb.v1's request key; the editor answers with the saved row id. */
        const val RESULT = "ua_result"

        private val BUILTIN_ICONS = intArrayOf(
            R.drawable.ic_ksu_home,
            R.drawable.ic_ksu_smartphone,
            R.drawable.ic_ksu_screen_rotation,
            R.drawable.ic_ksu_public,
            R.drawable.ic_ksu_public,
            R.drawable.ic_ksu_public,
            R.drawable.ic_ksu_smartphone,
            R.drawable.ic_ksu_screen_rotation,
            R.drawable.ic_ksu_smartphone,
        )
    }
}

/**
 * hb.v1: the user agent editor with the original fields, trimming, empty-title/empty-agent checks,
 * "modified content has not been saved" confirmation and the saved row id returned to the list.
 */
class KsuUserAgentEditorSettingsFragment : KsuPageFragment() {
    private var editingId = 0
    private var item = SettingsData()
    private var initialDigest: String? = null
    private var loaded = false
    private var titleValue = ""
    private var agentValue = ""

    override val titleRes get() = if (editingId > 0) R.string.action_edit else R.string.action_new

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingId = arguments?.getInt("id", 0) ?: 0
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (editingId <= 0) {
            loaded = true
            refresh()
            return
        }
        val context = requireContext().applicationContext
        viewLifecycleOwner.launchIo({
            BrowserDatabase(context).use { SettingsDataRepository(it).find(editingId) }
        }, { existing ->
            if (existing != null) {
                item = existing
                titleValue = existing.title.orEmpty()
                agentValue = existing.content.orEmpty()
                initialDigest = digest(existing.title, existing.content)
            }
            loaded = true
            refresh()
        }) { Log.w("ViaSettings", "Unable to read user agent", it) }
    }

    override fun groups(): List<KsuGroup> {
        // The fields compose only after the stored row arrived, so each input starts with its text.
        if (!loaded) return emptyList()
        return listOf(
            KsuGroup(null, listOf(
                KsuInputRow(
                    key = KEY_TITLE,
                    icon = R.drawable.ic_ksu_description,
                    title = getString(R.string.hint_title),
                    value = titleValue,
                    placeholder = getString(R.string.hint_title),
                ),
                KsuInputRow(
                    key = KEY_AGENT,
                    icon = R.drawable.ic_ksu_public,
                    title = getString(R.string.agent),
                    value = agentValue,
                    placeholder = getString(R.string.agent),
                ),
            )),
        )
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(R.string.action_save)) { save(false) },
    )

    override fun onInput(row: KsuInputRow, text: String) {
        // Local form state only: re-reading the page on every keystroke would fight the field.
        when (row.key) {
            KEY_TITLE -> titleValue = text
            KEY_AGENT -> agentValue = text
        }
    }

    override fun onBack() = save(true)

    private fun titleText(): String = buildString {
        titleValue.forEach { char ->
            if (!Character.isWhitespace(char)) append(if (char.code == 160) ' ' else char)
            else if (char != '\r') append(' ')
        }
    }.trim()

    private fun agentText(): String = agentValue.replace(Regex("[\\t\\n\\r]+"), " ").trim()

    private fun unchanged(): Boolean {
        val current = digest(titleText(), agentText())
        return initialDigest == null && current.isEmpty() || current == initialDigest
    }

    private fun save(confirm: Boolean) {
        val title = titleText()
        val agent = agentText()
        if (unchanged()) {
            closeEditor()
            return
        }
        if (confirm) {
            confirm(
                title = getString(R.string.message),
                message = getString(R.string.modified_content_has_not_been_saved_message),
                confirmText = getString(R.string.save_and_exit),
                dismissText = getString(R.string.exit),
            ) { save(false) }
            return
        }
        if (title.isEmpty()) {
            toast(R.string.title_can_not_be_empty)
            return
        }
        if (agent.isEmpty()) {
            toast(R.string.user_agent_can_not_be_empty)
            return
        }
        item = item.copy(title = title, content = agent)
        val context = requireContext().applicationContext
        viewLifecycleOwner.launchIo({
            val now = System.currentTimeMillis()
            BrowserDatabase(context).use { db ->
                val repository = SettingsDataRepository(db)
                if (item.id > 0) {
                    item = item.copy(lastUpdatedAt = now)
                    repository.save(item) > 0
                } else {
                    item = item.copy(lastUpdatedAt = now, createdAt = now, type = SettingsData.USER_AGENT)
                    val id = repository.save(item)
                    item = item.copy(id = id)
                    id > 0
                }
            }
        }, {
            parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putInt(RESULT, item.id) })
            closeEditor()
        }) { Log.w("ViaSettings", "Unable to save user agent", it) }
    }

    private fun closeEditor() = super.onBack()

    private fun toast(resource: Int) = ViaToast.makeText(requireContext(), resource, ViaToast.LENGTH_SHORT).show()

    private fun digest(vararg values: String?): String {
        val source = values.joinToString("") { it ?: "__NULL__" }
        if (source.isEmpty()) return ""
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
    }

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_AGENT = "agent"

        /** hb.v1: the result key the list page listens for. */
        private const val RESULT = KsuUserAgentSettingsFragment.RESULT

        fun newInstance(id: Int) = KsuUserAgentEditorSettingsFragment().apply {
            arguments = Bundle().apply { putInt("id", id) }
        }
    }
}

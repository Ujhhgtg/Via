package dev.ujhhgtg.via.settings.compose

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.view.View
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.script.ScriptInstaller
import dev.ujhhgtg.via.browser.script.ScriptManager
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.browser.script.UserScript
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.ScriptEditorFragment
import dev.ujhhgtg.via.settings.ScriptDetailsFragment
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.io.InputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/**
 * sa.d1's Scripts page on the Material surface: the master switch and update interval stay in
 * cards, each installed script becomes a row with its own switch, and the top bar keeps the
 * add/update actions.
 */
class KsuScriptSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_script

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private lateinit var store: ScriptStore
    private lateinit var manager: ScriptManager
    private lateinit var installer: ScriptInstaller
    private val worker = Executors.newSingleThreadExecutor()
    private var scripts = emptyList<UserScript>()
    private var updating = false

    private val importFile = registerForActivityResult(object : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent = super.createIntent(context, input)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, "content://com.android.externalstorage.documents/document/primary:Download".toUri())
    }) { uri ->
        if (uri == null) return@registerForActivityResult
        toast(R.string.toast_parsing_script)
        val context = requireContext().applicationContext
        worker.execute {
            val source = runCatching { readImportedSource(context.contentResolver.openInputStream(uri)) }.getOrNull()
            val parsed = source?.let { UserScript.parse(it) }
            val existing = parsed?.let { store.findByScriptId(it.scriptId) }
            onUi { installer.confirm(parsed, existing, ::onInstalled) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = ScriptStore(requireContext())
        manager = ScriptManager(store)
        installer = ScriptInstaller(this)
        parentFragmentManager.setFragmentResultListener(ScriptEditorFragment.RESULT, viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResult(key)
            refreshSavedScript(result.getInt("id"))
        }
        parentFragmentManager.setFragmentResultListener(ScriptDetailsFragment.RESULT, viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResult(key)
            refreshSavedScript(result.getInt("id"))
        }
        loadScripts()
    }

    override fun onDestroy() {
        if (::store.isInitialized) worker.execute { store.close() }
        worker.shutdown()
        super.onDestroy()
    }

    override fun topActions(): List<KsuTopAction> = buildList {
        add(KsuTopAction(R.drawable.ic_ksu_add, getString(R.string.add_script)) { addMenu() })
        if (scripts.any { it.downloadUrl != null }) {
            add(KsuTopAction(R.drawable.ic_ksu_sync, getString(R.string.update)) { updateScripts() })
        }
    }

    override fun groups(): List<KsuGroup> {
        val settings = listOf(
            KsuToggleRow(KEY_TOGGLE, R.drawable.ic_ksu_code, getString(R.string.enable_scripts), checked = preferences.scriptsEnabled),
            KsuNavRow(KEY_INTERVAL, R.drawable.ic_ksu_schedule, getString(R.string.update_interval), value = intervalLabels()[intervalIndex()]),
        )
        val installed = scripts.map { script ->
            KsuSwitchNavRow(
                key = script.id,
                icon = R.drawable.ic_ksu_description,
                title = script.name,
                summary = script.description?.takeIf { it.isNotBlank() } ?: script.version,
                checked = script.enabled,
                enabled = preferences.scriptsEnabled,
            )
        }
        return listOfNotNull(
            KsuGroup(null, settings),
            installed.takeIf { it.isNotEmpty() }?.let { KsuGroup(getString(R.string.settings_script), it) },
        )
    }

    override fun onRow(row: KsuRow) {
        when (row.key) {
            KEY_INTERVAL -> chooseInterval()
            else -> {
                val script = scripts.firstOrNull { it.id == row.key } ?: return
                if (script.content.isEmpty()) {
                    ViaDialog(requireActivity()).title(script.name).message(R.string.request_to_download_full_script)
                        .positive(android.R.string.ok) { _, _ -> script.downloadUrl?.let { importUrl(it, script.id) } }
                        .negative(android.R.string.cancel).show()
                } else openDetails(script.id)
            }
        }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        if (row.key != KEY_TOGGLE) return
        preferences.scriptsEnabled = checked
        (requireActivity() as Shell).browserReloadPreferences()
        refresh()
    }

    override fun onSwitchRow(row: KsuSwitchNavRow, checked: Boolean) {
        val script = scripts.firstOrNull { it.id == row.key } ?: return
        store.setEnabled(script.id, checked)
        scripts = scripts.map { if (it.id == script.id) it.copy(enabled = checked) else it }
        refresh()
    }

    private fun loadScripts() {
        worker.execute {
            val loaded = runCatching { store.list() }.getOrDefault(emptyList())
            onUi { scripts = loaded; refresh() }
        }
    }

    private fun refreshSavedScript(id: Int) {
        loadScripts()
        worker.execute { runBlocking { store.find(id)?.let { manager.ensureDependencies(it) } } }
    }

    private fun onInstalled(saved: UserScript) {
        scripts = if (scripts.any { it.id == saved.id }) scripts.map { if (it.id == saved.id) saved else it } else listOf(saved) + scripts
        refresh()
    }

    private fun addMenu() {
        ViaDialog(requireActivity()).items(
            arrayOf(getString(R.string.add_script), getString(R.string.import_script_from_url), getString(R.string.import_script_from_file)),
            onClick = { item ->
                when (item) {
                    0 -> openEditor()
                    1 -> importFromUrl()
                    2 -> runCatching { importFile.launch(arrayOf("text/plain", "application/javascript", "text/javascript")) }
                }
            },
        ).show()
    }

    private fun importFromUrl() {
        ViaDialog(requireActivity()).title(R.string.import_script_from_url).input("", getString(R.string.info_url), 1)
            .positive(android.R.string.ok) { _, result ->
                val url = result.edit?.firstOrNull()?.trim().orEmpty()
                if (android.webkit.URLUtil.isNetworkUrl(url)) importUrl(url) else toast(R.string.url_is_invalid)
            }.show()
    }

    private fun importUrl(url: String, id: Int = 0) = installer.fromSettingsUrl(url, id, ::onInstalled)

    private fun updateScripts() {
        if (updating) return
        val candidates = scripts.filter { it.enabled && it.downloadUrl?.let(android.webkit.URLUtil::isNetworkUrl) == true }
        if (candidates.isEmpty()) return
        val dialog = ViaDialog(requireActivity()).title(R.string.update)
        if (candidates.size == 1) dialog.message(getString(R.string.request_item_update, candidates[0].name))
        else dialog.multipleChoice(candidates.map { it.name }.toTypedArray(), IntArray(candidates.size) { it })
        dialog.positive(android.R.string.ok) { _, result ->
            val selected = if (candidates.size == 1) candidates else (result.selected ?: intArrayOf()).toList().mapNotNull(candidates::getOrNull)
            if (selected.isEmpty()) return@positive
            updating = true
            toast(R.string.update_pending)
            worker.execute {
                selected.forEach { script ->
                    val updated = runBlocking { runCatching { manager.updateFromNetwork(script.id) }.getOrNull() }
                    if (updated != null) onUi { scripts = scripts.map { if (it.id == updated.id) updated else it }; refresh() }
                }
                manager.cleanupResources()
                onUi { updating = false; toast(R.string.update_scripts_completed) }
            }
        }.negative(android.R.string.cancel).show()
    }

    private fun openEditor() {
        parentFragmentManager.setFragmentResultListener(ScriptEditorFragment.RESULT, viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResult(key)
            refreshSavedScript(result.getInt("id"))
        }
        (requireActivity() as Shell).navigate(ScriptEditorFragment.newInstance(-1))
    }

    private fun openDetails(id: Int) = (requireActivity() as Shell).navigate(ScriptDetailsFragment.newInstance(id))

    private fun intervalLabels() = arrayOf(R.string.never, R.string.every_day, R.string.every_3_days, R.string.every_week, R.string.every_15_days).map(::getString).toTypedArray()

    private fun intervalIndex() = INTERVAL_DAYS.indexOfLast { preferences.scriptUpdateInterval >= it * DAY }.coerceAtLeast(0)

    private fun chooseInterval() {
        ViaDialog(requireActivity()).title(R.string.update_interval).singleChoice(intervalLabels(), intervalIndex()) {
            preferences.scriptUpdateInterval = INTERVAL_DAYS[it] * DAY
            refresh()
        }.show()
    }

    private fun toast(resource: Int) = ViaToast.show(requireContext(), resource)

    private fun onUi(action: () -> Unit) { activity?.runOnUiThread { if (view != null) action() } }

    companion object {
        private const val KEY_TOGGLE = "scripts_enabled"
        private const val KEY_INTERVAL = "scripts_interval"
        private const val DAY = 86_400_000L
        private val INTERVAL_DAYS = longArrayOf(0, 1, 3, 7, 15)

        /** sa.d1.x3: keep all lines, abandon after 32 without a marker. */
        internal fun readImportedSource(input: InputStream?): String? {
            if (input == null) return null
            var found = false
            val text = StringBuilder()
            input.bufferedReader().use { reader ->
                var count = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    count++
                    if (!found && line.startsWith("// ==UserScript==")) found = true
                    if (!found && count > 32) break
                    text.append(line).append('\n')
                }
            }
            return if (found) text.toString() else ""
        }
    }
}

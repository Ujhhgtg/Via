package dev.ujhhgtg.via.settings.compose

import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.browser.script.UserScript
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import kotlinx.coroutines.launch

/** sa.d1's script editor: the metadata block plus code, parsed and stored on save. */
class KsuScriptEditorSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.action_edit

    private var scriptId = -1
    private var source = ""
    private var downloadUrl: String? = null
    private var loaded = false

    override fun pageTitle(): String = if (scriptId <= 0) getString(R.string.add_script) else getString(R.string.action_edit)

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scriptId = arguments?.getInt("id", -1) ?: -1
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val record = runCatching { ScriptStore(context).use { it.find(scriptId) } }.getOrNull()
            downloadUrl = record?.downloadUrl
            source = record?.content ?: TEMPLATE
            loaded = true
            refresh()
        }
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuInputRow(1, R.drawable.ic_ksu_code, getString(R.string.settings_script), value = source,
                placeholder = getString(R.string.title_add_metadata_block), multiline = true),
        )),
    )

    override fun onInput(row: KsuInputRow, value: String) { source = value }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(R.string.action_save)) { save() },
    )

    private fun save() {
        val parsed = UserScript.parse(source, downloadUrl)
        if (parsed == null) {
            showMessage(
                title = getString(R.string.title_add_metadata_block),
                message = getString(R.string.message_add_metadata_block),
                onOk = {
                    // hb.i1.a2: confirm inserts the default metadata block at the top.
                    source = METADATA + source
                    refresh()
                },
            )
            return
        }
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val savedId = runCatching {
                ScriptStore(context).use { store ->
                    val existing = store.find(scriptId)
                    val value = parsed.copy(id = scriptId, enabled = existing?.enabled ?: parsed.enabled)
                    if (existing == null) store.insert(value) else store.save(value)
                }
            }.getOrDefault(0)
            if (savedId > 0) {
                scriptId = savedId
                parentFragmentManager.setFragmentResult(RESULT, android.os.Bundle().apply { putInt("id", savedId) })
                onBack()
            } else {
                ViaToast.show(requireContext(), getString(R.string.toast_install_script_failed_unknown, parsed.name))
            }
        }
    }

    companion object {
        const val RESULT = "script_result_v2"
        private const val TEMPLATE = "// ==UserScript==\n// @name \n// @match https://*/*\n// ==/UserScript==\n"

        /** hb.i1: the default metadata block prepended on confirm. */
        private const val METADATA = "// ==UserScript==\n" +
            "// @name         New Userscript\n" +
            "// @namespace    https://viayoo.com/\n" +
            "// @version      0.1\n" +
            "// @description  try to take over the world!\n" +
            "// @author       You\n" +
            "// @run-at       document-end\n" +
            "// @match        https://*/*\n" +
            "// @grant        none\n" +
            "// ==/UserScript==\n\n"

        private fun newInstance(id: Int) = KsuScriptEditorSettingsFragment().apply {
            arguments = android.os.Bundle().apply { putInt("id", id) }
        }

        fun edit(id: Int) = newInstance(id)
    }
}

/** One script's metadata, patterns, source and resources. */
class KsuScriptDetailsSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_script

    private val store by lazy { ScriptStore(requireContext()) }
    private var script: UserScript? = null

    override fun pageTitle(): String = script?.name ?: getString(titleRes)

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener(KsuScriptEditorSettingsFragment.RESULT, viewLifecycleOwner) { key, _ ->
            parentFragmentManager.clearFragmentResult(key)
            load()
        }
        load()
    }

    private fun load() {
        val id = arguments?.getInt("id", -1) ?: -1
        viewLifecycleOwner.lifecycleScope.launch {
            script = runCatching { store.find(id) }.getOrNull()
            refresh()
        }
    }

    override fun onDestroy() {
        store.close()
        super.onDestroy()
    }

    override fun groups(): List<KsuGroup> {
        val script = script ?: return emptyList()
        val info = buildList {
            add(KsuNavRow(INFO, R.drawable.ic_ksu_code, script.name, listOfNotNull(script.version, script.description).joinToString(" · ").ifEmpty { null }))
            if (script.matches.isNotEmpty()) {
                script.matches.forEachIndexed { index, pattern ->
                    add(KsuNavRow(100 + index, R.drawable.ic_ksu_public, pattern, getString(R.string.match)))
                }
            }
            script.excludes.forEachIndexed { index, pattern ->
                add(KsuNavRow(200 + index, R.drawable.ic_ksu_block, pattern, getString(R.string.exclude)))
            }
            add(KsuNavRow(SOURCE, R.drawable.ic_ksu_description, getString(R.string.edit_source_code)))
            script.resources.keys.forEachIndexed { index, name ->
                add(KsuNavRow(300 + index, R.drawable.ic_ksu_folder, name, getString(R.string.settings_script)))
            }
        }
        return listOf(KsuGroup(null, info))
    }

    override fun onRow(row: KsuRow) {
        val script = script ?: return
        when (row.key) {
            SOURCE -> (requireActivity() as Shell).navigate(KsuScriptEditorSettingsFragment.edit(script.id))
            else -> if ((row.key as? Int ?: 0) >= 300) {
                val name = script.resources.keys.toList().getOrNull((row.key as Int) - 300)
                name?.let { (requireActivity() as Shell).navigate(KsuScriptResourceSettingsFragment.newInstance(script.id, it)) }
            } else if (row.key == INFO) {
                showMessage(
                    script.name,
                    listOfNotNull(
                        script.version?.let { "Version: $it" },
                        script.description,
                        script.downloadUrl?.let { "Source: $it" },
                    ).joinToString("\n"),
                )
            }
        }
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_sync, getString(R.string.update)) {
            val script = script ?: return@KsuTopAction
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { dev.ujhhgtg.via.browser.script.ScriptManager(store).updateFromNetwork(script.id) }
                load()
            }
        },
    )

    companion object {
        private const val INFO = 1
        private const val SOURCE = 2

        fun newInstance(id: Int) = KsuScriptDetailsSettingsFragment().apply {
            arguments = android.os.Bundle().apply { putInt("id", id) }
        }
    }
}

/** One script resource, read-only. */
class KsuScriptResourceSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_script
    private var name = ""
    private var content = ""

    override fun pageTitle(): String = name.ifEmpty { getString(titleRes) }

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        name = arguments?.getString("name").orEmpty()
        val id = arguments?.getInt("id", -1) ?: -1
        viewLifecycleOwner.lifecycleScope.launch {
            val script = runCatching { ScriptStore(requireContext()).use { it.find(id) } }.getOrNull()
            content = script?.resources?.get(name).orEmpty()
            refresh()
            ScriptStore(requireContext()).use { }
        }
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuInputRow(1, R.drawable.ic_ksu_code, name, value = content, multiline = true, enabled = false),
        )),
    )

    companion object {
        fun newInstance(id: Int, name: String) = KsuScriptResourceSettingsFragment().apply {
            arguments = android.os.Bundle().apply {
                putInt("id", id)
                putString("name", name)
            }
        }
    }
}

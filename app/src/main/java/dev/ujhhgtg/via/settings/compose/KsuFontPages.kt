package dev.ujhhgtg.via.settings.compose

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.fonts.FontEntry
import dev.ujhhgtg.via.fonts.FontRepository
import dev.ujhhgtg.via.ui.ViaToast

/** hb.j2: imported fonts as Material rows, with rename/delete menus and the import picker. */
class KsuFontSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.font

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private val repository by lazy { FontRepository(preferences.fontDirectory) }
    private val fonts = mutableListOf<FontEntry>()
    private var selected = ""

    private val importer = registerForActivityResult(object : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                "content://com.android.externalstorage.documents/document/primary:Download".toUri(),
            )
    }) { uri -> uri?.let(::importFont) }

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener(KsuFontRenameSettingsFragment.RESULT, viewLifecycleOwner) { key, result ->
            parentFragmentManager.clearFragmentResult(key)
            val name = result.getString("name") ?: return@setFragmentResultListener
            val old = result.getString("old") ?: return@setFragmentResultListener
            val font = fonts.firstOrNull { it.name == old } ?: return@setFragmentResultListener
            val replacement = repository.rename(font.name, name) ?: return@setFragmentResultListener
            val index = fonts.indexOf(font)
            if (index >= 0) fonts[index] = replacement
            if (selected == font.name) select(replacement)
            refresh()
        }
        reload()
    }

    private fun reload() {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = runCatching { repository.list() }.getOrDefault(emptyList())
            selected = preferences.uiFont
            fonts.clear()
            fonts.addAll(list)
            refresh()
        }
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, fonts.map { font ->
            KsuSwitchNavRow(
                key = font.hashCode(),
                icon = R.drawable.ic_ksu_font_download,
                title = font.name.ifEmpty { getString(R.string.default_set) },
                checked = font.name == selected,
                actions = if (font.name.isEmpty()) emptyList() else listOf(
                    KsuMenuAction(1, getString(R.string.action_edit)),
                    KsuMenuAction(2, getString(R.string.action_delete)),
                ),
            )
        }),
    )

    override fun onSwitchRow(row: KsuSwitchNavRow, checked: Boolean) {
        if (!checked) return
        val font = fonts.firstOrNull { it.hashCode() == row.key } ?: return
        select(font)
        refresh()
    }

    override fun onAction(row: KsuSwitchNavRow, action: KsuMenuAction) {
        val font = fonts.firstOrNull { it.hashCode() == row.key } ?: return
        when (action.id) {
            1 -> (requireActivity() as Shell).navigate(KsuFontRenameSettingsFragment.newInstance(font.name))
            2 -> delete(font)
        }
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_add, getString(R.string.action_new)) {
            try {
                importer.launch(arrayOf("font/ttf", "font/otf", "font/woff", "font/woff2"))
            } catch (error: ActivityNotFoundException) {
                android.util.Log.w("ViaFonts", "Unable to open font picker", error)
                ViaToast.makeText(requireContext(), R.string.toast_operation_failed, ViaToast.LENGTH_SHORT).show()
            }
        },
    )

    private fun select(font: FontEntry) {
        selected = font.name
        preferences.uiFont = selected
    }

    /** hb.j2.t3 deletes the visible row before touching the stored file. */
    private fun delete(font: FontEntry) {
        if (!fonts.remove(font)) return
        if (font.name == selected && fonts.isNotEmpty()) select(fonts[0])
        refresh()
        repository.delete(font.name)
    }

    private fun importFont(uri: Uri) {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { repository.importFont(context.contentResolver, uri) }.getOrNull()
            val font = result?.font
            if (font == null) {
                ViaToast.makeText(requireContext(), getString(R.string.unable_to_add_font, result?.error),
                    ViaToast.LENGTH_LONG).show()
            } else reload()
        }
    }
}

/** The rename prompt as a Material page: one text field and a confirm action. */
class KsuFontRenameSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.rename

    private var value = ""
    private var oldName = ""
    private var stem = ""
    private var suffix = ""

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        oldName = arguments?.getString("name").orEmpty()
        val dot = oldName.lastIndexOf('.')
        stem = if (dot > 0) oldName.substring(0, dot) else oldName
        suffix = if (dot > 0) oldName.substring(dot + 1) else ""
        value = stem
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuInputRow(1, R.drawable.ic_ksu_font_download, getString(R.string.rename), value = stem,
                placeholder = getString(R.string.rename), enabled = true),
        )),
    )

    override fun onInput(row: KsuInputRow, text: String) {
        value = text
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(android.R.string.ok)) {
            val name = value.trim()
            if (name.isNotEmpty() && !name.equals(stem, ignoreCase = true)) {
                parentFragmentManager.setFragmentResult(RESULT, android.os.Bundle().apply {
                    putString("old", oldName)
                    putString("name", if (suffix.isEmpty()) name else "$name.$suffix")
                })
            }
            onBack()
        },
    )

    companion object {
        const val RESULT = "font_rename"

        fun newInstance(name: String) = KsuFontRenameSettingsFragment().apply {
            arguments = android.os.Bundle().apply { putString("name", name) }
        }
    }
}

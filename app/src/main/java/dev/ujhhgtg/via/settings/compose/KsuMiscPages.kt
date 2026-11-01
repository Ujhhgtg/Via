package dev.ujhhgtg.via.settings.compose

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.engine.Engines
import dev.ujhhgtg.via.engine.ExtensionInfo
import dev.ujhhgtg.via.engine.ExtensionInstallError
import dev.ujhhgtg.via.engine.ExtensionManager
import dev.ujhhgtg.via.extensions.ExtensionHost
import dev.ujhhgtg.via.extensions.ExtensionPermissions
import dev.ujhhgtg.via.settings.ExtensionSettingsFragment
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.util.Locale
import kotlinx.coroutines.launch

/*
 * Pages that had no dedicated Material surface yet: the extension detail page, the night filter
 * preview, and the bookmark import/export split.
 */

/** One extension: metadata, required/optional permissions and its update/uninstall actions. */
class KsuExtensionDetailsSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_extensions

    private enum class Kind { PERMISSION, ORIGIN, DATA }

    private lateinit var manager: ExtensionManager
    private var extension: ExtensionInfo? = null
    private var closing = false
    private val optional = linkedMapOf<Int, Pair<String, Kind>>()
    private val listener: () -> Unit = { load() }

    override fun pageTitle(): String = extension?.name ?: getString(titleRes)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = requireNotNull(Engines.backend.extensions)
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        manager.addListener(listener)
        load()
    }

    override fun onDestroyView() {
        manager.removeListener(listener)
        super.onDestroyView()
    }

    private fun load() {
        if (view == null) return
        val id = arguments?.getString("id") ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            extension = runCatching { manager.list() }.getOrDefault(emptyList()).firstOrNull { it.id == id }
            if (extension != null) refresh()
            else if (!closing && isAdded) {
                closing = true
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun groups(): List<KsuGroup> {
        val extension = extension ?: return emptyList()
        val groups = mutableListOf<KsuGroup>()
        groups += KsuGroup(null, buildList {
            add(KsuNavRow(INFO, R.drawable.ic_ksu_extension, extension.name,
                listOfNotNull(extension.version.takeIf(String::isNotEmpty), extension.creator).joinToString(" · ").ifEmpty { null }))
            extension.description?.let { add(KsuNavRow(DESCRIPTION, R.drawable.ic_ksu_description, it)) }
            if (extension.optionsUrl != null) add(KsuNavRow(OPTIONS, R.drawable.ic_ksu_settings, getString(R.string.extension_options)))
            extension.homepageUrl?.let { add(KsuNavRow(HOMEPAGE, R.drawable.ic_ksu_public, getString(R.string.info_homepage_url), it)) }
            if (extension.amoUrl != null) add(KsuNavRow(AMO, R.drawable.ic_ksu_open_in_new, getString(R.string.extension_amo_page)))
        })

        val required = ExtensionPermissions.lines(requireContext(), extension.permissions, extension.origins)
        if (required.isNotEmpty()) {
            groups += KsuGroup(getString(R.string.extension_permissions),
                required.map { KsuNavRow(it, R.drawable.ic_ksu_verified, it) })
        }

        optional.clear()
        var key = 100
        val choices = extension.optionalPermissions.mapNotNull { name ->
            ExtensionPermissions.lines(requireContext(), listOf(name), emptyList()).firstOrNull()?.let { Triple(name, Kind.PERMISSION, it) }
        } + extension.optionalOrigins.map { origin ->
            Triple(origin, Kind.ORIGIN, ExtensionPermissions.lines(requireContext(), emptyList(), listOf(origin)).first())
        } + extension.optionalDataCollection.mapNotNull { name ->
            ExtensionPermissions.dataLines(requireContext(), listOf(name)).firstOrNull()?.let { Triple(name, Kind.DATA, it) }
        }
        if (choices.isNotEmpty()) {
            groups += KsuGroup(getString(R.string.extension_optional_permissions), choices.map { (name, kind, label) ->
                val granted = name in when (kind) {
                    Kind.PERMISSION -> extension.grantedOptionalPermissions
                    Kind.ORIGIN -> extension.grantedOptionalOrigins
                    Kind.DATA -> extension.grantedOptionalDataCollection
                }
                optional[++key] = name to kind
                KsuToggleRow(key, R.drawable.ic_ksu_verified, label, checked = granted)
            })
        }
        return groups
    }

    override fun onRow(row: KsuRow) {
        val extension = extension ?: return
        when (row.key) {
            OPTIONS -> manager.openOptions(extension.id)
            HOMEPAGE -> extension.homepageUrl?.let { (requireActivity() as Shell).openRecordFromRecords(it, 1) }
            AMO -> extension.amoUrl?.let { (requireActivity() as Shell).openRecordFromRecords(it, 1) }
        }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        val extension = extension ?: return
        val (name, kind) = optional[row.key as? Int ?: return] ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                manager.setOptionalPermission(extension.id, name.takeIf { kind == Kind.PERMISSION },
                    name.takeIf { kind == Kind.ORIGIN }, checked, name.takeIf { kind == Kind.DATA })
            }
            load()
        }
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_sync, getString(R.string.update)) { update() },
        KsuTopAction(R.drawable.ic_ksu_delete_sweep, getString(R.string.uninstall)) {
            extension?.let { ExtensionSettingsFragment.confirmUninstall(this, manager, it) { load() } }
        },
    )

    private fun update() {
        val extension = extension ?: return
        ViaToast.show(requireContext(), R.string.update_pending)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updated = manager.update(extension.id)
                ViaToast.makeText(requireContext(),
                    if (updated == null) getString(R.string.extension_up_to_date, extension.name)
                    else getString(R.string.update_extensions_completed), ViaToast.LENGTH_SHORT).show()
            } catch (error: ExtensionInstallError) {
                if (error.kind != ExtensionInstallError.Kind.CANCELED && isAdded) {
                    ViaDialog(requireActivity()).title(R.string.extension_install_failed)
                        .message(ExtensionHost.errorMessage(requireContext(), error))
                        .positive(android.R.string.ok).show()
                }
            }
            load()
        }
    }

    companion object {
        private const val INFO = 1
        private const val DESCRIPTION = 2
        private const val OPTIONS = 3
        private const val HOMEPAGE = 4
        private const val AMO = 5

        fun newInstance(id: String) = KsuExtensionDetailsSettingsFragment().apply {
            arguments = Bundle().apply { putString("id", id) }
        }
    }
}

/** hb.p4: the night-mode switches plus its filter preview, now Material. */
class KsuNightModeSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.action_night

    private val preferences by lazy { BrowserPreferences(requireContext()) }

    override fun groups(): List<KsuGroup> {
        GeneratedDocumentState.initialize(preferences)
        val rows = buildList {
            add(KsuNavRow(1, R.drawable.ic_ksu_dark_mode, getString(R.string.night_filter_for_web_contents),
                value = "${preferences.nightFilter}"))
            if (Engines.backend.capabilities.algorithmicDarkening) {
                add(KsuToggleRow(2, R.drawable.ic_ksu_palette, getString(R.string.force_dark_mode_for_web_contents),
                    getString(R.string.force_dark_mode_for_web_contents_description), preferences.forceDarkPages))
            }
            add(KsuToggleRow(3, R.drawable.ic_ksu_home, getString(R.string.disable_home_background_dimming),
                getString(R.string.disable_home_background_dimming_description), preferences.disableHomeBackgroundDimming))
        }
        return listOf(KsuGroup(null, rows))
    }

    override fun onRow(row: KsuRow) {
        if (row.key == 1) (requireActivity() as Shell).navigate(KsuNightFilterSettingsFragment())
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        when (row.key) {
            2 -> {
                preferences.forceDarkPages = checked
                GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS)
            }
            3 -> {
                preferences.disableHomeBackgroundDimming = checked
                GeneratedDocumentState.mark(GeneratedDocumentState.HOME_STYLE or GeneratedDocumentState.PAGE_SETTINGS)
            }
        }
        refresh()
    }
}

/** The night filter's live preview and slider, moved out of the classic dialog. */
class KsuNightFilterSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.night_filter_for_web_contents

    private val preferences by lazy { BrowserPreferences(requireContext()) }

    override fun groups(): List<KsuGroup> = emptyList()

    override fun header(): (@Composable () -> Unit)? = {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 13.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
                Text(
                    text = getString(if (Locale.getDefault().country.equals("CN", true)) R.string.night_filter_preview_china else R.string.night_filter_preview),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 16.dp),
                )
            }
            Slider(
                value = preferences.nightFilter.toFloat(),
                onValueChange = {
                    preferences.nightFilter = it.toInt()
                    GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS)
                },
                valueRange = 0f..154f,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** hb.c4's bookmark import/export, replacing the two-item dialog. */
class KsuBookmarkTransferSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.import_or_export_bookmarks

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuNavRow(1, R.drawable.ic_ksu_save_alt, getString(R.string.export_bookmarks)),
            KsuNavRow(2, R.drawable.ic_ksu_upload_file, getString(R.string.import_bookmarks)),
        )),
    )

    override fun onRow(row: KsuRow) {
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString("action", if (row.key == 1) "export" else "import") })
        onBack()
    }

    companion object {
        const val RESULT = "bookmark_transfer"
    }
}

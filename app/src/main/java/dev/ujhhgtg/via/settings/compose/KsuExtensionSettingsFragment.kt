package dev.ujhhgtg.via.settings.compose

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.engine.DisabledReason
import dev.ujhhgtg.via.engine.Engines
import dev.ujhhgtg.via.engine.ExtensionInfo
import dev.ujhhgtg.via.engine.ExtensionInstallError
import dev.ujhhgtg.via.engine.ExtensionManager
import dev.ujhhgtg.via.extensions.ExtensionFiles
import dev.ujhhgtg.via.extensions.ExtensionHost
import dev.ujhhgtg.via.settings.ExtensionDetailsFragment
import dev.ujhhgtg.via.settings.ExtensionSettingsFragment
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import kotlinx.coroutines.launch

/** The Extensions page on the Material surface: settings cards plus one switch row per extension. */
class KsuExtensionSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_extensions

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private lateinit var manager: ExtensionManager
    private var extensions = emptyList<ExtensionInfo>()
    private var updating = false
    private val listener: () -> Unit = { load() }

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                val installed = ExtensionHost.installFile(context, manager, uri)
                toast(getString(R.string.extension_installed, installed.name))
            } catch (error: ExtensionInstallError) {
                if (error.kind != ExtensionInstallError.Kind.CANCELED) failed(error)
            } catch (error: Throwable) {
                failed(ExtensionInstallError(ExtensionInstallError.Kind.CORRUPT, null, error))
            }
            load()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = requireNotNull(Engines.backend.extensions)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        manager.addListener(listener)
        load()
    }

    override fun onDestroyView() {
        manager.removeListener(listener)
        super.onDestroyView()
    }

    override fun topActions(): List<KsuTopAction> = buildList {
        add(KsuTopAction(R.drawable.ic_ksu_add, getString(R.string.find_extensions), menu = listOf(
            KsuMenuAction(1, getString(R.string.find_extensions)),
            KsuMenuAction(2, getString(R.string.install_extension_from_file)),
        )))
        if (extensions.isNotEmpty()) add(KsuTopAction(R.drawable.ic_ksu_sync, getString(R.string.update)) { updateExtensions() })
    }

    override fun groups(): List<KsuGroup> {
        val settings = listOf(
            KsuToggleRow(KEY_TOGGLE, R.drawable.ic_ksu_extension, getString(R.string.enable_extensions), checked = preferences.extensionsEnabled),
            KsuDropdownRow(KEY_INTERVAL, R.drawable.ic_ksu_schedule, getString(R.string.update_interval),
                options = intervalLabels().toList(), selectedIndex = intervalIndex()),
            KsuToggleRow(KEY_UNSIGNED, R.drawable.ic_ksu_verified_user, getString(R.string.allow_unsigned_extensions),
                getString(R.string.allow_unsigned_extensions_description), preferences.allowUnsignedExtensions),
        )
        val installed = extensions.map { extension ->
            KsuSwitchNavRow(
                key = extension.id,
                icon = R.drawable.ic_ksu_extension,
                title = extension.name,
                summary = summary(extension),
                checked = extension.userEnabled,
                enabled = preferences.extensionsEnabled && extension.disabledReason == null,
                actions = buildList {
                    add(KsuMenuAction(1, getString(R.string.action_edit)))
                    add(KsuMenuAction(2, getString(R.string.update)))
                    if (extension.optionsUrl != null) add(KsuMenuAction(3, getString(R.string.extension_options)))
                    if (extension.homepageUrl != null) add(KsuMenuAction(4, getString(R.string.home)))
                    if (extension.amoUrl != null) add(KsuMenuAction(5, getString(R.string.action_copy)))
                    add(KsuMenuAction(6, getString(R.string.uninstall)))
                },
            )
        }
        return listOfNotNull(
            KsuGroup(null, settings),
            installed.takeIf { it.isNotEmpty() }?.let { KsuGroup(getString(R.string.settings_extensions), it) },
        )
    }

    private fun summary(extension: ExtensionInfo): String = when (extension.disabledReason) {
        DisabledReason.UNSIGNED -> getString(R.string.extension_disabled_unsigned)
        DisabledReason.BLOCKLISTED -> getString(R.string.extension_disabled_blocklisted)
        DisabledReason.INCOMPATIBLE -> getString(R.string.extension_disabled_incompatible)
        null -> extension.version
    }

    override fun onRow(row: KsuRow) {
        if (row.key == KEY_INTERVAL) return
        (extensions.firstOrNull { it.id == row.key })?.let { openDetails(it.id) }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        when (row.key) {
            KEY_TOGGLE -> {
                preferences.extensionsEnabled = checked
                manager.configure(preferences.extensionsEnabled, preferences.allowUnsignedExtensions)
            }
            KEY_UNSIGNED -> {
                preferences.allowUnsignedExtensions = checked
                manager.configure(preferences.extensionsEnabled, preferences.allowUnsignedExtensions)
            }
            else -> return
        }
        refresh()
    }

    override fun onSwitchRow(row: KsuSwitchNavRow, checked: Boolean) {
        val extension = extensions.firstOrNull { it.id == row.key } ?: return
        lifecycleScope.launch {
            runCatching { manager.setEnabled(extension.id, checked) }
            load()
        }
    }

    private fun load() {
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            extensions = runCatching { manager.list() }.getOrDefault(emptyList())
            refresh()
        }
    }


    private fun updateExtensions() {
        if (updating) return
        val candidates = extensions.filter { it.userEnabled }
        if (candidates.isEmpty()) return
        val dialog = ViaDialog(requireActivity()).title(R.string.update)
        if (candidates.size == 1) dialog.message(getString(R.string.request_item_update, candidates[0].name))
        else dialog.multipleChoice(candidates.map { it.name }.toTypedArray(), IntArray(candidates.size) { it })
        dialog.positive(android.R.string.ok) { _, result ->
            val selected = if (candidates.size == 1) candidates else (result.selected ?: intArrayOf()).toList().mapNotNull(candidates::getOrNull)
            update(selected)
        }.negative(android.R.string.cancel).show()
    }

    private fun update(selected: List<ExtensionInfo>) {
        if (selected.isEmpty() || updating) return
        updating = true
        toast(R.string.update_pending)
        lifecycleScope.launch {
            var updated = 0
            selected.forEach { extension ->
                try {
                    if (manager.update(extension.id) != null) updated++
                } catch (error: ExtensionInstallError) {
                    if (error.kind != ExtensionInstallError.Kind.CANCELED) failed(error)
                }
            }
            updating = false
            if (updated == 0 && selected.size == 1) toast(getString(R.string.extension_up_to_date, selected[0].name))
            else toast(R.string.update_extensions_completed)
            load()
        }
    }


    override fun onTopMenuAction(action: KsuMenuAction) {
        when (action.id) {
            1 -> (requireActivity() as Shell).openRecordFromRecords(AMO, 1)
            2 -> try {
                importFile.launch(arrayOf(ExtensionFiles.MIME, "application/zip", "application/octet-stream"))
            } catch (_: ActivityNotFoundException) {
                toast(R.string.toast_operation_failed)
            }
        }
    }

    override fun onAction(row: KsuSwitchNavRow, action: KsuMenuAction) {
        val extension = extensions.firstOrNull { it.id == row.key } ?: return
        when (action.id) {
            1 -> openDetails(extension.id)
            2 -> update(listOf(extension))
            3 -> manager.openOptions(extension.id)
            4 -> extension.homepageUrl?.let { (requireActivity() as Shell).openRecordFromRecords(it, 1) }
            5 -> copyLink(extension.amoUrl)
            6 -> ExtensionSettingsFragment.confirmUninstall(this, manager, extension) { load() }
        }
    }

    private fun copyLink(url: String?) {
        if (url.isNullOrEmpty()) return
        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("", url))
        toast(R.string.toast_copy_url_successful)
    }

    private fun intervalLabels() = arrayOf(R.string.never, R.string.every_day, R.string.every_3_days, R.string.every_week, R.string.every_15_days).map(::getString).toTypedArray()

    private fun intervalIndex() = INTERVAL_DAYS.indexOfLast { preferences.extensionUpdateInterval >= it * DAY }.coerceAtLeast(0)

    override fun onSelect(row: KsuDropdownRow, index: Int) {
        if (row.key != KEY_INTERVAL) return
        preferences.extensionUpdateInterval = INTERVAL_DAYS[index] * DAY
        refresh()
    }

    private fun openDetails(id: String) = (requireActivity() as Shell).navigate(KsuExtensionDetailsSettingsFragment.newInstance(id))

    private fun failed(error: ExtensionInstallError) {
        if (!isAdded) return
        ViaDialog(requireActivity()).title(R.string.extension_install_failed)
            .message(ExtensionHost.errorMessage(requireContext(), error)).positive(android.R.string.ok).show()
    }

    private fun toast(resource: Int) = ViaToast.show(requireContext(), resource)

    private fun toast(text: String) = ViaToast.makeText(requireContext(), text, ViaToast.LENGTH_SHORT).show()

    companion object {
        private const val KEY_TOGGLE = "extensions_enabled"
        private const val KEY_INTERVAL = "extensions_interval"
        private const val KEY_UNSIGNED = "extensions_unsigned"
        private const val DAY = 86_400_000L
        private val INTERVAL_DAYS = longArrayOf(0, 1, 3, 7, 15)
        private const val AMO = "https://addons.mozilla.org/android/"
    }
}

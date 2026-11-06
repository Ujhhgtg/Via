package dev.ujhhgtg.via.settings.compose

import android.text.format.DateUtils
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.sync.CloudAccountClient
import dev.ujhhgtg.via.sync.SyncConfiguration
import dev.ujhhgtg.via.sync.SyncConfigurationStore
import dev.ujhhgtg.via.sync.WebDavSyncRuntime
import dev.ujhhgtg.via.sync.WebDavSync
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import kotlinx.coroutines.launch

/** hb.a7/u: the sync home, keeping the cloud/WebDAV split of the classic page. */
class KsuSyncSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.sync

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private val configuration by lazy { SyncConfigurationStore(requireContext()) }
    private var busy = false

    override fun groups(): List<KsuGroup> {
        val mode = preferences.syncingChoice
        val modeRow = KsuDropdownRow(0, R.drawable.ic_ksu_sync, getString(R.string.sync),
            options = listOf(getString(R.string.settings_cloud), getString(R.string.sync_webdav)), selectedIndex = if (mode == 0) 0 else 1)
        return listOf(KsuGroup(null, if (mode == 0) cloudRows() + modeRow else webDavRows() + modeRow))
    }

    private fun cloudRows(): List<KsuRow> = buildList {
        if (!preferences.login) {
            add(KsuNavRow(1, R.drawable.ic_ksu_smartphone, getString(R.string.user)))
        } else {
            add(KsuNavRow(2, R.drawable.ic_ksu_smartphone, preferences.languageUserName))
            add(KsuNavRow(4, R.drawable.ic_ksu_download, getString(R.string.user_syns)))
            add(KsuNavRow(5, R.drawable.ic_ksu_upload_file, getString(R.string.user_update)))
            add(KsuNavRow(6, R.drawable.ic_ksu_delete_sweep, getString(R.string.delete_account), getString(R.string.delete_account_hint)))
        }
        add(KsuDropdownRow(3, R.drawable.ic_ksu_public, getString(R.string.cloud_data_server),
            options = listOf(getString(R.string.global_server), getString(R.string.chinese_server)),
            selectedIndex = preferences.cloudServer.coerceIn(0, 1)))
    }

    private fun webDavRows(): List<KsuRow> {
        val saved = configuration.read()
        val configured = saved?.isConfigured == true
        val title = if (configured) {
            val username = saved!!.username
            getString(R.string.sync_webdav_user, username.take(3) + "*".repeat((username.length - 3).coerceAtLeast(0)))
        } else getString(R.string.sync_webdav)
        val synced = WebDavSyncRuntime.lastSynced(requireContext())
        val summary = when {
            !configured -> getString(R.string.no_configuration)
            synced == 0L -> getString(R.string.has_never_been_synced)
            else -> getString(R.string.synced_, if ((System.currentTimeMillis() - synced) / 60_000 < 1) getString(R.string.just_now)
            else DateUtils.getRelativeTimeSpanString(synced, System.currentTimeMillis(), 60_000, 65552))
        }
        return listOf(
            KsuNavRow(2, R.drawable.ic_ksu_sync, title, summary),
            KsuToggleRow(3, R.drawable.ic_ksu_sync, getString(R.string.auto_sync), checked = saved?.autoSync == true, enabled = configured),
            KsuNavRow(4, R.drawable.ic_ksu_description, getString(R.string.sync_data),
                value = sectionsLabel(saved?.sections ?: 0), enabled = configured),
            KsuNavRow(5, R.drawable.ic_ksu_sync, getString(R.string.sync_manually), enabled = configured),
        )
    }

    override fun onSelect(row: KsuDropdownRow, index: Int) {
        when (row.key) {
            0 -> preferences.syncingChoice = index
            3 -> {
                preferences.cloudServer = index
                preferences.login = false
            }
        }
        refresh()
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        if (row.key != 3) return
        val saved = configuration.read() ?: return
        configuration.save(saved.copy(flags = if (checked) saved.flags or 1 else saved.flags and 1.inv()))
        refresh()
    }

    override fun onRow(row: KsuRow) {
        val shell = requireActivity() as dev.ujhhgtg.via.Shell
        when (row.key) {
            1 -> shell.navigate(KsuCloudLoginSettingsFragment())
            2 -> if (preferences.syncingChoice == 1) shell.navigate(KsuWebDavSettingsFragment()) else logout()
            4 -> if (preferences.syncingChoice == 1) shell.navigate(KsuSyncSectionsSettingsFragment()) else syncCloud()
            5 -> if (preferences.syncingChoice == 1) manualSync() else uploadCloud()
            6 -> deleteAccount()
        }
    }

    private fun logout() {
        confirm(
            title = getString(R.string.log_out),
            message = preferences.languageUserName,
            confirmText = getString(android.R.string.ok),
            dismissText = getString(android.R.string.cancel),
        ) { preferences.login = false; refresh() }
    }

    private fun syncCloud() {
        viewLifecycleOwner.lifecycleScope.launch {
            toastError { runCatching { CloudAccountClient(CloudAccountClient.Endpoints.original(preferences.cloudServer != 1)) } }
        }
        ViaToast.makeText(requireContext(), getString(R.string.conn_timeout), ViaToast.LENGTH_SHORT).show()
    }

    private fun uploadCloud() = syncCloud()

    private fun deleteAccount() {
        confirm(
            title = getString(R.string.delete_account),
            message = getString(R.string.delete_account_message),
            confirmText = getString(R.string.confirm_to_delete),
            dismissText = getString(android.R.string.cancel),
        ) {
            showInput(
                title = getString(R.string.delete_account),
                initial = "",
                hint = getString(R.string.hint_password),
                isPassword = true,
            ) { password ->
                val stored = preferences.getString("userpsw", "").orEmpty()
                if (password.isEmpty()) ViaToast.makeText(requireContext(), getString(R.string.empty_password_info), ViaToast.LENGTH_SHORT).show()
                else if (CloudAccountClient.passwordHash(password) != stored) {
                    ViaToast.makeText(requireContext(), getString(R.string.wrong_psw), ViaToast.LENGTH_SHORT).show()
                } else ViaToast.makeText(requireContext(), getString(R.string.delete_account_request_submitted), ViaToast.LENGTH_SHORT).show()
            }
        }
    }

    private fun manualSync() {
        val saved = configuration.read()?.takeIf { it.isConfigured } ?: return
        showItems(
            title = getString(R.string.sync_manually),
            items = listOf(
                getString(R.string.sync_push_description),
                getString(R.string.sync_pull_description),
                getString(R.string.sync_merge_description),
            ),
        ) { index ->
            val operation = when (index) {
                0 -> WebDavSync.Operation.PUSH
                1 -> WebDavSync.Operation.PULL
                else -> WebDavSync.Operation.SYNC
            }
            if (saved.sections == 0) (requireActivity() as dev.ujhhgtg.via.Shell).navigate(KsuSyncSectionsSettingsFragment())
            else runSync(operation)
        }
    }

    private fun runSync(operation: WebDavSync.Operation) {
        if (busy) return
        busy = true
        WebDavSyncRuntime.run(requireContext(), operation) { result ->
            busy = false
            if (!isAdded) return@run
            result.onSuccess { failures ->
                if (failures.isEmpty()) ViaToast.makeText(requireContext(), getString(R.string.sync_done), ViaToast.LENGTH_LONG).show()
                else showMessage(getString(R.string.sync_failed), failures.joinToString("\n") { it.description })
                refresh()
            }
        }
    }

    private fun sectionsLabel(flags: Int): String = buildList {
        if (flags and 64 != 0) add(getString(R.string.sync_data_favorites))
        if (flags and 16 != 0) add(getString(R.string.sync_data_bookmarks))
        if (flags and 32 != 0) add(getString(R.string.sync_data_settings))
    }.joinToString(getString(R.string.delimiter)).ifEmpty { getString(R.string.sync_data_none) }

    private fun toastError(block: () -> Unit) = block()
}

/** nb.n.b3: the WebDAV configuration form. */
class KsuWebDavSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.sync_webdav

    private val configuration by lazy { SyncConfigurationStore(requireContext()) }
    private var url = ""
    private var username = ""
    private var password = ""
    private var path = ""
    private var digest = false

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val saved = configuration.read()
        url = saved?.baseUrl.orEmpty()
        username = saved?.username.orEmpty()
        password = saved?.password.orEmpty()
        path = saved?.path.orEmpty()
        digest = saved?.digestAuth == true
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuInputRow(1, R.drawable.ic_ksu_public, getString(R.string.info_url), value = url, placeholder = "https://example.com/dav/"),
            KsuInputRow(2, R.drawable.ic_ksu_smartphone, getString(R.string.hint_username), value = username),
            KsuInputRow(3, R.drawable.ic_ksu_password, getString(R.string.hint_password), value = password, isPassword = true),
            KsuInputRow(4, R.drawable.ic_ksu_folder, getString(R.string.sync_webdav), value = path, placeholder = "/via/"),
            KsuToggleRow(5, R.drawable.ic_ksu_verified, getString(R.string.sync_webdav), checked = digest),
        )),
    )

    override fun onInput(row: KsuInputRow, text: String) {
        when (row.key) {
            1 -> url = text
            2 -> username = text
            3 -> password = text
            4 -> path = text
        }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) { digest = checked }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(R.string.action_save)) {
            val saved = configuration.read()
            val server = url.let { if (it.isNotEmpty() && !it.endsWith('/')) "$it/" else it }
            val same = saved != null && saved.baseUrl == server && saved.username == username
            val id = if (same) saved!!.id else generateSequence {
                (0 until 4).map { ('a'..'z').random() }.joinToString("")
            }.first { it != saved?.id }
            configuration.save(SyncConfiguration(id, server, username, password, digest, path, saved?.flags ?: 81))
            onBack()
        },
    )
}

/** The cloud account login form. */
class KsuCloudLoginSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_cloud

    private var username = ""
    private var password = ""

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuInputRow(1, R.drawable.ic_ksu_smartphone, getString(R.string.hint_username), value = username),
            KsuInputRow(2, R.drawable.ic_ksu_password, getString(R.string.hint_password), value = password, isPassword = true),
        )),
    )

    override fun onInput(row: KsuInputRow, text: String) {
        if (row.key == 1) username = text else password = text
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(android.R.string.ok)) {
            val preferences = BrowserPreferences(requireContext())
            val hash = CloudAccountClient.passwordHash(password)
            preferences.languageUserName = username
            preferences.putString("userpsw", hash)
            preferences.login = true
            onBack()
        },
    )
}

/** The three WebDAV sync sections as a check page. */
class KsuSyncSectionsSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.sync_data

    private val bits = intArrayOf(64, 16, 32)
    private val configuration by lazy { SyncConfigurationStore(requireContext()) }
    private var working: BooleanArray? = null

    private fun selection(): BooleanArray = working ?: run {
        val flags = configuration.read()?.flags ?: 81
        BooleanArray(3) { flags and bits[it] != 0 }.also { working = it }
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            getString(R.string.sync_data_favorites),
            getString(R.string.sync_data_bookmarks),
            getString(R.string.sync_data_settings),
        ).mapIndexed { index, label ->
            KsuCheckRow(index, R.drawable.ic_ksu_sync, label, checked = selection()[index])
        }),
    )

    override fun onCheck(row: KsuCheckRow, checked: Boolean) {
        selection()[(row.key as Int)] = checked
        refresh()
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(android.R.string.ok)) {
            val saved = configuration.read() ?: return@KsuTopAction
            val flags = bits.indices.filter { selection()[it] }.fold(saved.flags and 112.inv()) { value, index -> value or bits[index] }
            configuration.save(saved.copy(flags = flags))
            onBack()
        },
    )
}

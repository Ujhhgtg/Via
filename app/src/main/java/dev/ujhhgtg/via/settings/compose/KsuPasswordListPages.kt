package dev.ujhhgtg.via.settings.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.passwords.PasswordPageOperations
import dev.ujhhgtg.via.passwords.PasswordRecord
import dev.ujhhgtg.via.passwords.PasswordRepository
import java.util.UUID
import kotlinx.coroutines.launch

/** va.r0: saved passwords as Material groups, one group per site. */
class KsuPasswordListSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.passwords

    private val operations by lazy { PasswordPageOperations(this) }
    private val repository get() = operations.repository
    private var records = emptyList<PasswordRecord>()

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener(KsuPasswordEditorSettingsFragment.RESULT, viewLifecycleOwner) { key, _ ->
            parentFragmentManager.clearFragmentResult(key)
            load()
        }
        load()
    }

    private fun load() {
        operations.work({ repository.list() }) {
            records = it
            refresh()
        }
    }

    override fun groups(): List<KsuGroup> {
        val byName = records.groupBy { it.name }
        return byName.map { (name, entries) ->
            KsuGroup(name, entries.map { record ->
                KsuSwitchNavRow(
                    key = record.id,
                    icon = R.drawable.ic_ksu_password,
                    title = record.username,
                    checked = false,
                    enabled = true,
                    actions = listOf(
                        KsuMenuAction(1, getString(R.string.copy_username)),
                        KsuMenuAction(2, getString(R.string.copy_password)),
                        KsuMenuAction(3, getString(R.string.action_delete)),
                    ),
                )
            })
        }
    }

    override fun onRow(row: KsuRow) {
        val id = row.key as? String ?: return
        operations.authentication.authenticate(getString(R.string.view_password), getString(R.string.unlock_device_to_view_password)) {
            (requireActivity() as Shell).navigate(KsuPasswordEditorSettingsFragment.newInstance(id))
        }
    }

    override fun onSwitchRow(row: KsuSwitchNavRow, checked: Boolean) = Unit

    override fun onAction(row: KsuSwitchNavRow, action: KsuMenuAction) {
        val id = row.key as? String ?: return
        val record = records.firstOrNull { it.id == id } ?: return
        when (action.id) {
            1 -> copy(record.username, sensitive = false)
            2 -> operations.authentication.authenticate(getString(R.string.copy_password),
                getString(R.string.unlock_device_to_copy_password)) {
                operations.work({ repository.get(id, true)?.password }) { password -> password?.let { copy(it, sensitive = true) } }
            }
            3 -> operations.work({ repository.delete(id) }) { load() }
        }
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_add, getString(R.string.action_new)) {
            operations.authentication.authenticate(getString(R.string.view_password), getString(R.string.unlock_device_to_view_password)) {
                (requireActivity() as Shell).navigate(KsuPasswordEditorSettingsFragment.newInstance(null))
            }
        },
    )

    private fun copy(value: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText("", value)
        if (sensitive) clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }

    override fun onDestroyView() {
        operations.destroyView()
        super.onDestroyView()
    }
}

/** va.s: the secure editor as a Material form, with the same save semantics. */
class KsuPasswordEditorSettingsFragment : KsuPageFragment() {
    override val titleRes get() = if (id.isEmpty()) R.string.action_new else R.string.action_edit

    private val operations by lazy { PasswordPageOperations(this) }
    private val repository get() = operations.repository
    private var original: PasswordRecord? = null
    private var id = ""
    private var name = ""
    private var username = ""
    private var password = ""
    private var note = ""

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        id = arguments?.getString("id").orEmpty()
        if (id.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val record = runCatching { repository.get(id, true) }.getOrNull() ?: return@launch
            original = record
            name = record.name
            username = record.username
            password = record.password.orEmpty()
            note = record.note.orEmpty()
            refresh()
        }
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuInputRow(1, R.drawable.ic_ksu_public, getString(R.string.hint_url), value = name, placeholder = getString(R.string.hint_url)),
            KsuInputRow(2, R.drawable.ic_ksu_smartphone, getString(R.string.hint_username), value = username,
                placeholder = getString(R.string.hint_username)),
            KsuInputRow(3, R.drawable.ic_ksu_password, getString(R.string.hint_password), value = password,
                placeholder = getString(R.string.hint_password), isPassword = true),
            KsuInputRow(4, R.drawable.ic_ksu_description, getString(R.string.note), value = note, placeholder = getString(R.string.note)),
        )),
    )

    override fun onInput(row: KsuInputRow, text: String) {
        when (row.key) {
            1 -> name = text
            2 -> username = text
            3 -> password = text
            4 -> note = text
        }
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_check, getString(R.string.action_save)) { save() },
    )

    private fun save() {
        val now = System.currentTimeMillis()
        val base = original
        val record = PasswordRecord(
            id = if (base?.id.isNullOrEmpty()) UUID.randomUUID().toString().lowercase() else base!!.id,
            name = name.trim(),
            username = username.trim(),
            password = password,
            note = note.trim(),
            updatedAt = now,
            createdAt = base?.createdAt ?: now,
        )
        operations.work({ repository.save(record) }) { saved ->
            if (saved) {
                parentFragmentManager.setFragmentResult(RESULT, android.os.Bundle().apply { putString("result", record.id) })
                onBack()
            }
        }
    }

    override fun onDestroyView() {
        operations.destroyView()
        super.onDestroyView()
    }

    companion object {
        const val RESULT = "password_editor_result"

        fun newInstance(id: String?) = KsuPasswordEditorSettingsFragment().apply {
            if (!id.isNullOrEmpty()) arguments = android.os.Bundle().apply { putString("id", id) }
        }
    }
}

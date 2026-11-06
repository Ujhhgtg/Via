package dev.ujhhgtg.via.settings.compose

import android.app.Activity
import android.content.Intent
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.passwords.PasswordPageOperations
import dev.ujhhgtg.via.passwords.PasswordRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** va.c1: the password settings home, with the same import/export pipeline as the classic page. */
class KsuPasswordSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.password_manager

    private val operations by lazy { PasswordPageOperations(this) }
    private val repository get() = operations.repository

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        operations.restoreState(savedInstanceState)
        operations.onDocumentResult = ::onDocumentResult
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        operations.saveState(outState)
    }

    override fun onDestroyView() {
        operations.destroyView()
        super.onDestroyView()
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuToggleRow(1, R.drawable.ic_ksu_password, getString(R.string.offer_to_save_passwords),
                checked = repository.offerToSave),
            KsuNavRow(2, R.drawable.ic_ksu_description, getString(R.string.passwords)),
            KsuNavRow(5, R.drawable.ic_ksu_block, getString(R.string.sites_that_never_save_passwords)),
            KsuNavRow(3, R.drawable.ic_ksu_upload_file, getString(R.string.import_passwords),
                getString(R.string.import_passwords_description)),
            KsuNavRow(4, R.drawable.ic_ksu_save_alt, getString(R.string.export_passwords),
                getString(R.string.export_passwords_description)),
        )),
    )

    override fun onRow(row: KsuRow) {
        val shell = requireActivity() as Shell
        when (row.key) {
            2 -> shell.navigate(KsuPasswordListSettingsFragment())
            5 -> shell.navigate(KsuPasswordIgnoredSitesSettingsFragment())
            3 -> operations.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT)
            4 -> exportPasswords()
        }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        if (row.key != 1) return
        repository.offerToSave = checked
        refresh()
    }

    private fun exportPasswords() {
        operations.work({ repository.list().isEmpty() }) { empty ->
            if (empty) operations.toast(getString(R.string.no_passwords))
            else operations.authentication.authenticate(
                getString(R.string.export_passwords), getString(R.string.unlock_device_to_export_passwords)) {
                val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
                operations.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, "${getString(R.string.app_name)}_${getString(R.string.passwords)}_$date.csv"), EXPORT)
            }
        }
    }

    private fun onDocumentResult(request: Int, result: Int, data: Intent?) {
        val uri = data?.data?.takeIf { result == Activity.RESULT_OK } ?: return
        when (request) {
            // va.c1.k3/g3: an unrecognized header yields -1 and stays silent.
            IMPORT -> operations.work({
                runCatching { requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use(repository::importCsv) ?: -1 }
                    .getOrElse { if (it is IllegalArgumentException) -1 else throw it }
            }) { count ->
                if (count > 0) operations.toast(getString(R.string.passwords_import, count))
                else if (count < 0) operations.toast(getString(R.string.failed_to_import_passwords))
            }
            EXPORT -> operations.work({
                requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use(repository::exportCsv)
                    ?: error("Cannot write CSV")
            }) { operations.toast(getString(R.string.export_successfully)) }
        }
    }

    companion object {
        private const val IMPORT = 6210
        private const val EXPORT = 6211
    }
}

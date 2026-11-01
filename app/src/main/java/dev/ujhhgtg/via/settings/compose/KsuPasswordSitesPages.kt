package dev.ujhhgtg.via.settings.compose

import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.passwords.PasswordCsv
import dev.ujhhgtg.via.passwords.PasswordPageOperations
import dev.ujhhgtg.via.passwords.PasswordPickerFragment

/** va.f: the sites that never save passwords, now a Material list with an add/edit page. */
class KsuPasswordIgnoredSitesSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.sites_that_never_save_passwords

    private val operations by lazy { PasswordPageOperations(this) }
    private val repository get() = operations.repository
    private var sites = emptyList<String>()

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener(KsuPasswordIgnoredSiteEditorSettingsFragment.RESULT, viewLifecycleOwner) { key, _ ->
            parentFragmentManager.clearFragmentResult(key)
            loadSites()
        }
        loadSites()
    }

    private fun loadSites() {
        operations.work({ repository.ignoredSites().sortedWith { a, b -> PasswordPickerFragment.compareDomains(a, b) } }) {
            sites = it
            refresh()
        }
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, sites.map { site ->
            KsuNavRow(site.hashCode(), R.drawable.ic_ksu_block, site, site)
        }),
    )

    override fun onRow(row: KsuRow) {
        val site = sites.firstOrNull { it.hashCode() == row.key } ?: return
        (requireActivity() as Shell).navigate(KsuPasswordIgnoredSiteEditorSettingsFragment.newInstance(site))
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_add, getString(R.string.action_new)) {
            (requireActivity() as Shell).navigate(KsuPasswordIgnoredSiteEditorSettingsFragment.newInstance(null))
        },
    )

    override fun onDestroyView() {
        operations.destroyView()
        super.onDestroyView()
    }
}

/** The add/edit prompt as a page: one domain field plus a delete action when editing. */
class KsuPasswordIgnoredSiteEditorSettingsFragment : KsuPageFragment() {
    override val titleRes get() = if (previous == null) R.string.action_new else R.string.action_edit

    private val operations by lazy { PasswordPageOperations(this) }
    private val repository get() = operations.repository
    private var previous: String? = null
    private var value = ""

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previous = arguments?.getString("site")
        value = previous.orEmpty()
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuInputRow(1, R.drawable.ic_ksu_public, getString(R.string.sites_that_never_save_passwords),
                value = previous.orEmpty(), placeholder = "www.example.com"),
        )),
    )

    override fun onInput(row: KsuInputRow, text: String) { value = text }

    override fun topActions(): List<KsuTopAction> = buildList {
        add(KsuTopAction(R.drawable.ic_ksu_check, getString(android.R.string.ok)) { save() })
        if (previous != null) add(KsuTopAction(R.drawable.ic_ksu_delete_sweep, getString(R.string.action_delete)) { remove() })
    }

    private fun save() {
        val name = PasswordCsv.normalizeName(value)
        if (name.isEmpty() || name == previous) {
            onBack()
            return
        }
        operations.work({
            previous?.let { repository.setSavingAllowed(it, true) }
            repository.setSavingAllowed(name, false)
        }) {
            parentFragmentManager.setFragmentResult(RESULT, android.os.Bundle())
            onBack()
        }
    }

    private fun remove() {
        val site = previous ?: return
        operations.work({ repository.setSavingAllowed(site, true) }) {
            parentFragmentManager.setFragmentResult(RESULT, android.os.Bundle())
            onBack()
        }
    }

    override fun onDestroyView() {
        operations.destroyView()
        super.onDestroyView()
    }

    companion object {
        const val RESULT = "password_ignored_sites"

        fun newInstance(site: String?) = KsuPasswordIgnoredSiteEditorSettingsFragment().apply {
            arguments = android.os.Bundle().apply { site?.let { putString("site", it) } }
        }
    }
}

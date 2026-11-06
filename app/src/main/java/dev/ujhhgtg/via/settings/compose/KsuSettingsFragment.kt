package dev.ujhhgtg.via.settings.compose

import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell

/**
 * The Compose settings root. It mirrors the classic page's ids and destinations; every row has a
 * Material icon, and the last card carries the switch that turns the port back off.
 */
class KsuSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings

    override fun groups(): List<KsuGroup> {
        val context = requireContext()
        val features = buildList {
            add(KsuNavRow(9, R.drawable.ic_ksu_code, context.getString(R.string.settings_script)))
            if (dev.ujhhgtg.via.engine.Engines.backend.extensions != null) {
                add(KsuNavRow(13, R.drawable.ic_ksu_extension, context.getString(R.string.settings_extensions)))
            }
        }
        return listOf(
            KsuGroup(context.getString(R.string.settings_group_general), listOf(
                KsuNavRow(1, R.drawable.ic_ksu_settings, context.getString(R.string.settings_general)),
                KsuNavRow(8, R.drawable.ic_ksu_palette, context.getString(R.string.settings_skin)),
                KsuNavRow(2, R.drawable.ic_ksu_security, context.getString(R.string.settings_privacy)),
                KsuNavRow(3, R.drawable.ic_ksu_build, context.getString(R.string.settings_advanced)),
            )),
            KsuGroup(context.getString(R.string.settings_group_features), features),
            KsuGroup(context.getString(R.string.settings_group_about), listOf(
                KsuNavRow(5, R.drawable.ic_ksu_info, context.getString(R.string.settings_about)),
            )),
        )
    }

    override fun onRow(row: KsuRow) {
        val id = row.key as? Int ?: return
        val shell = requireActivity() as Shell
        when (id) {
            1 -> shell.navigate(KsuGeneralSettingsFragment())
            2 -> shell.navigate(KsuPrivacySettingsFragment())
            3 -> shell.navigate(KsuAdvancedSettingsFragment())
            5 -> shell.navigate(KsuAboutSettingsFragment())
            8 -> shell.openPage("homepage_customization")
            9 -> shell.navigate(KsuScriptSettingsFragment())
            13 -> shell.navigate(KsuExtensionSettingsFragment())
        }
    }

    companion object {}
}

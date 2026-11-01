package dev.ujhhgtg.via.settings.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.AboutSettingsFragment
import dev.ujhhgtg.via.settings.AdvancedSettingsFragment
import dev.ujhhgtg.via.settings.ExtensionSettingsFragment
import dev.ujhhgtg.via.settings.GeneralSettingsFragment
import dev.ujhhgtg.via.settings.PrivacySettingsFragment
import dev.ujhhgtg.via.settings.ScriptSettingsFragment
import dev.ujhhgtg.via.settings.SettingsSwipeBack
import dev.ujhhgtg.via.ui.SwipeBackLayout
import dev.ujhhgtg.via.ui.dp

/**
 * Compose-hosted replacement for the classic settings root ([dev.ujhhgtg.via.settings.SettingsFragment]),
 * ported from KernelSU's Material (M3E) settings page. Sub-pages are still the classic fragments,
 * so both surfaces share the same destinations and only the root page differs.
 */
class KsuSettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val content = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val preferences = remember { BrowserPreferences(context) }
                val groups = remember { groups() }
                ViaKsuTheme(darkTheme = preferences.isNightMode) {
                    KsuSettingsScreen(
                        groups = groups,
                        ksuUiEnabled = true,
                        onOpen = ::open,
                        onKsuUiChanged = { enabled ->
                            BrowserPreferences(context).ksuSettingsUi = enabled
                            // Turning the port off leaves no reason to stay on this page.
                            if (!enabled && !parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack()
                        },
                    )
                }
            }
        }
        return SwipeBackLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            attach(this@KsuSettingsFragment, content)
            setEdgeSize(-2)
            setScrollThresholdSize(context.dp(200f).toFloat())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SettingsSwipeBack.install(this, view as SwipeBackLayout, { true }) { onBack() }
    }

    private fun onBack() { if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack() }

    private fun open(id: Int) {
        val shell = requireActivity() as Shell
        when (id) {
            1 -> shell.navigate(GeneralSettingsFragment())
            2 -> shell.navigate(PrivacySettingsFragment())
            3 -> shell.navigate(AdvancedSettingsFragment())
            5 -> shell.navigate(AboutSettingsFragment())
            8 -> shell.openPage("homepage_customization")
            9 -> shell.navigate(ScriptSettingsFragment())
            13 -> shell.navigate(ExtensionSettingsFragment())
        }
    }

    /** Same rows and ids as the classic root; the section titles are the only new strings. */
    private fun groups(): List<KsuSettingsGroup> {
        val context = requireContext()
        val general = buildList {
            add(KsuSettingsEntry(1, R.drawable.ic_ksu_settings, context.getString(R.string.settings_general)))
            add(KsuSettingsEntry(8, R.drawable.ic_ksu_palette, context.getString(R.string.settings_skin)))
            add(KsuSettingsEntry(2, R.drawable.ic_ksu_security, context.getString(R.string.settings_privacy)))
            add(KsuSettingsEntry(3, R.drawable.ic_ksu_build, context.getString(R.string.settings_advanced)))
        }
        val features = buildList {
            add(KsuSettingsEntry(9, R.drawable.ic_ksu_code, context.getString(R.string.settings_script)))
            if (dev.ujhhgtg.via.engine.Engines.backend.extensions != null) {
                add(KsuSettingsEntry(13, R.drawable.ic_ksu_extension, context.getString(R.string.settings_extensions)))
            }
        }
        val about = listOf(KsuSettingsEntry(5, R.drawable.ic_ksu_info, context.getString(R.string.settings_about)))
        return listOf(
            KsuSettingsGroup(context.getString(R.string.settings_group_general), general),
            KsuSettingsGroup(context.getString(R.string.settings_group_features), features),
            KsuSettingsGroup(context.getString(R.string.settings_group_about), about),
        )
    }
}

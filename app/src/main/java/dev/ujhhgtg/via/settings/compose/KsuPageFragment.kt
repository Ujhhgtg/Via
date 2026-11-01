package dev.ujhhgtg.via.settings.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.SettingsSwipeBack
import dev.ujhhgtg.via.ui.SwipeBackLayout
import dev.ujhhgtg.via.ui.dp

/**
 * Hosts one ported KernelSU-style settings page in Compose inside the same swipe-back container
 * the classic pages use. Subclasses describe rows in [groups] and react in [onRow]/[onToggle]/
 * [onSwitchRow];
 * calling [refresh] afterwards re-reads them once the underlying preference changed.
 */
abstract class KsuPageFragment : Fragment() {
    private var revision by mutableIntStateOf(0)

    @get:StringRes
    protected abstract val titleRes: Int

    protected abstract fun groups(): List<KsuGroup>

    protected open fun topActions(): List<KsuTopAction> = emptyList()

    protected open fun onRow(row: KsuRow) = Unit

    protected open fun onToggle(row: KsuToggleRow, checked: Boolean) = Unit

    protected open fun onSwitchRow(row: KsuSwitchNavRow, checked: Boolean) = Unit

    protected open fun onSelect(row: KsuDropdownRow, index: Int) = Unit

    protected open fun onAction(row: KsuSwitchNavRow, action: KsuMenuAction) = Unit

    protected open fun onCheck(row: KsuCheckRow, checked: Boolean) = Unit

    /** Composable content rendered above the cards, e.g. the About page's version header. */
    protected open fun header(): (@Composable () -> Unit)? = null

    /** Re-reads the page's rows, e.g. after a switch or a preference-backed title changed. */
    protected fun refresh() { revision++ }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val content = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val revisionKey = revision
                val preferences = remember { BrowserPreferences(context) }
                ViaKsuTheme(darkTheme = preferences.isNightMode) {
                    KsuSettingsPage(
                        title = stringResource(titleRes),
                        groups = remember(revisionKey) { groups() },
                        onBack = ::onBack,
                        onRowClick = ::onRow,
                        onToggle = ::onToggle,
                        onSwitchRow = ::onSwitchRow,
                        onSelect = ::onSelect,
                        onAction = ::onAction,
                        onCheck = ::onCheck,
                        topActions = remember(revisionKey) { topActions() },
                        header = header(),
                    )
                }
            }
        }
        return SwipeBackLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            attach(this@KsuPageFragment, content)
            setEdgeSize(-2)
            setScrollThresholdSize(context.dp(200f).toFloat())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SettingsSwipeBack.install(this, view as SwipeBackLayout, { true }) { onBack() }
    }

    protected open fun onBack() {
        if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack()
    }
}

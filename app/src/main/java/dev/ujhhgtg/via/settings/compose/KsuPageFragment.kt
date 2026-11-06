package dev.ujhhgtg.via.settings.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
    private var confirmRequest by mutableStateOf<ConfirmRequest?>(null)
    private var inputRequest by mutableStateOf<InputRequest?>(null)
    private var messageRequest by mutableStateOf<MessageRequest?>(null)
    private var itemsRequest by mutableStateOf<ItemsRequest?>(null)
    private var multiChoiceRequest by mutableStateOf<MultiChoiceRequest?>(null)

    /** A KernelSU-style confirmation dialog, rendered above the page. */
    protected class ConfirmRequest(
        val title: String,
        val message: String?,
        val confirmText: String,
        val dismissText: String?,
        val onConfirm: () -> Unit,
    )

    protected class InputRequest(
        val title: String,
        val initial: String,
        val hint: String?,
        val isPassword: Boolean,
        val onConfirm: (String) -> Unit,
    )

    protected class MessageRequest(val title: String, val message: String, val okText: String, val onOk: () -> Unit)

    protected class ItemsRequest(val title: String, val items: List<String>, val onSelect: (Int) -> Unit)

    protected class MultiChoiceRequest(
        val title: String,
        val items: List<String>,
        val selected: Set<Int>,
        val onConfirm: (Set<Int>) -> Unit,
    )

    protected fun confirm(title: String, message: String?, confirmText: String, dismissText: String? = null, onConfirm: () -> Unit) {
        confirmRequest = ConfirmRequest(title, message, confirmText, dismissText, onConfirm)
    }

    /** MD3 input dialog; the confirm callback receives the field text. */
    protected fun showInput(title: String, initial: String, hint: String? = null, isPassword: Boolean = false, onConfirm: (String) -> Unit) {
        inputRequest = InputRequest(title, initial, hint, isPassword, onConfirm)
    }

    protected fun showMessage(title: String, message: String, okText: String = getString(android.R.string.ok), onOk: (() -> Unit)? = null) {
        messageRequest = MessageRequest(title, message, okText) { messageRequest = null; onOk?.invoke() }
    }

    protected fun showItems(title: String, items: List<String>, onSelect: (Int) -> Unit) {
        itemsRequest = ItemsRequest(title, items, onSelect)
    }

    protected fun showMultiChoice(title: String, items: List<String>, selected: Set<Int>, onConfirm: (Set<Int>) -> Unit) {
        multiChoiceRequest = MultiChoiceRequest(title, items, selected, onConfirm)
    }

    @get:StringRes
    protected abstract val titleRes: Int

    /** Overridable so detail pages can show, for example, an extension name. */
    protected open fun pageTitle(): String = getString(titleRes)

    protected abstract fun groups(): List<KsuGroup>

    protected open fun topActions(): List<KsuTopAction> = emptyList()

    protected open fun onRow(row: KsuRow) = Unit

    protected open fun onToggle(row: KsuToggleRow, checked: Boolean) = Unit

    protected open fun onSwitchRow(row: KsuSwitchNavRow, checked: Boolean) = Unit

    protected open fun onSelect(row: KsuDropdownRow, index: Int) = Unit

    protected open fun onAction(row: KsuSwitchNavRow, action: KsuMenuAction) = Unit

    protected open fun onCheck(row: KsuCheckRow, checked: Boolean) = Unit

    protected open fun onRadio(row: KsuRadioRow) = Unit

    protected open fun onRadioAction(row: KsuRadioRow, action: KsuMenuAction) = Unit

    protected open fun onInput(row: KsuInputRow, text: String) = Unit

    protected open fun onTopMenuAction(action: KsuMenuAction) = Unit

    /** Composable content rendered above the cards, e.g. the About page's version header. */
    protected open fun header(): (@Composable () -> Unit)? = null

    /** Re-reads the page's rows, e.g. after a switch or a preference-backed title changed. */
    protected fun refresh() { revision++ }

    /** Pages with their own horizontal drag control (sliders) turn the swipe-back gesture off. */
    protected open fun swipeBackEnabled(): Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val content = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val revisionKey = revision
                val preferences = remember { BrowserPreferences(context) }
                ViaKsuTheme(darkTheme = preferences.isNightMode) {
                    KsuSettingsPage(
                        title = pageTitle(),
                        groups = remember(revisionKey) { groups() },
                        onBack = ::onBack,
                        onRowClick = ::onRow,
                        onToggle = ::onToggle,
                        onSwitchRow = ::onSwitchRow,
                        onSelect = ::onSelect,
                        onAction = ::onAction,
                        onCheck = ::onCheck,
                        onRadio = ::onRadio,
                        onRadioAction = ::onRadioAction,
                        onInput = ::onInput,
                        onTopMenuAction = ::onTopMenuAction,
                        topActions = remember(revisionKey) { topActions() },
                        header = header(),
                    )
                    confirmRequest?.let { request ->
                        KsuConfirmDialog(
                            title = request.title,
                            message = request.message,
                            confirmText = request.confirmText,
                            dismissText = request.dismissText,
                            onConfirm = { confirmRequest = null; request.onConfirm() },
                            onDismiss = { confirmRequest = null },
                        )
                    }
                    inputRequest?.let { request ->
                        KsuInputDialog(
                            title = request.title,
                            initial = request.initial,
                            hint = request.hint,
                            isPassword = request.isPassword,
                            confirmText = getString(android.R.string.ok),
                            onConfirm = { inputRequest = null; request.onConfirm(it) },
                            onDismiss = { inputRequest = null },
                        )
                    }
                    messageRequest?.let { request ->
                        KsuMessageDialog(
                            title = request.title,
                            message = request.message,
                            okText = request.okText,
                            onOk = request.onOk,
                        )
                    }
                    itemsRequest?.let { request ->
                        KsuItemsDialog(
                            title = request.title,
                            items = request.items,
                            onSelect = { itemsRequest = null; request.onSelect(it) },
                            onDismiss = { itemsRequest = null },
                        )
                    }
                    multiChoiceRequest?.let { request ->
                        KsuMultiChoiceDialog(
                            title = request.title,
                            items = request.items,
                            selected = request.selected,
                            okText = getString(android.R.string.ok),
                            onConfirm = { multiChoiceRequest = null; request.onConfirm(it) },
                            onDismiss = { multiChoiceRequest = null },
                        )
                    }
                }
            }
        }
        return SwipeBackLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            attach(this@KsuPageFragment, content)
            setEdgeSize(-2)
            setScrollThresholdSize(context.dp(200f).toFloat())
            setGestureEnabled(swipeBackEnabled())
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

package dev.ujhhgtg.via.settings.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.ujhhgtg.via.R

/*
 * KernelSU's expressive menus (manager/.../ui/component/material/ExpressiveMenu.kt):
 * a DropdownMenuPopup wrapped in one rounded group, anchored at the press position, with
 * selectable items carrying a check on the current choice.
 */

@Composable
fun KsuExpressiveMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorOffset: IntOffset = IntOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints())
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            layout(width, 0) {
                placeable.place(anchorOffset.x, anchorOffset.y)
            }
        },
    ) {
        DropdownMenuPopup(expanded = true, onDismissRequest = onDismissRequest) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                modifier = Modifier.verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

/** The KernelSU selection list: one rounded group of rows with a check on the current item. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KsuSelectableMenu(
    items: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    items.forEachIndexed { index, text ->
        SelectableDropdownMenuItem(
            text = { Text(text) },
            selected = index == selectedIndex,
            onClick = {
                onSelect(index)
                onDismiss()
            },
            shapes = MenuDefaults.itemShape(index = index, count = items.size),
            selectedLeadingIcon = {
                // KernelSU lets the menu item's own content colour drive the check; tinting it
                // with primary (light + saturated in dark mode) read as a stray accent.
                Icon(
                    painter = painterResource(R.drawable.ic_ksu_check),
                    contentDescription = null,
                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                )
            },
        )
    }
}

/** Tracks where inside a row the finger landed, so the menu opens under the touch point. */
fun Modifier.trackPressPosition(onPress: (Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPress(down.position)
    }
}

/** A top-bar action; when it carries menu items the expressive menu opens under the button. */
@Composable
fun KsuTopBarAction(action: KsuTopAction, onMenuAction: (KsuMenuAction) -> Unit) {
    if (action.menu.isEmpty()) {
        IconButton(onClick = action.onClick) {
            Icon(painterResource(action.icon), action.description)
        }
        return
    }
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(IntOffset.Zero) }
    Box(Modifier.trackPressPosition { anchor = it.round() }) {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(action.icon), action.description)
        }
        KsuExpressiveMenu(expanded = expanded, onDismissRequest = { expanded = false }, anchorOffset = anchor) {
            action.menu.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = { expanded = false; onMenuAction(item) },
                    shape = MenuDefaults.itemShape(index = index, count = action.menu.size).shape,
                )
            }
        }
    }
}

/** KernelSU's install-module dialog shape: an extra-large M3 alert with a plain text button row. */
@Composable
fun KsuConfirmDialog(
    title: String,
    message: String?,
    confirmText: String,
    dismissText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = dismissText?.let { { TextButton(onClick = onDismiss) { Text(it) } } },
    )
}

/** MD3 input dialog: an extra-large AlertDialog with one OutlinedTextField. */
@Composable
fun KsuInputDialog(
    title: String,
    initial: String,
    hint: String?,
    isPassword: Boolean,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = hint?.let { { Text(it) } },
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                ),
                shape = MaterialTheme.shapes.large,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** MD3 message dialog. */
@Composable
fun KsuMessageDialog(
    title: String,
    message: String,
    okText: String,
    onOk: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onOk,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onOk) { Text(okText) } },
    )
}

/** MD3 single-choice list dialog. */
@Composable
fun KsuItemsDialog(
    title: String,
    items: List<String>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, item ->
                    ListItem(
                        headlineContent = { Text(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** MD3 multi-choice dialog with checkboxes. */
@Composable
fun KsuMultiChoiceDialog(
    title: String,
    items: List<String>,
    selected: Set<Int>,
    okText: String,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var checked by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checked = if (index in checked) checked - index else checked + index
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = index in checked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(item)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(checked) }) { Text(okText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

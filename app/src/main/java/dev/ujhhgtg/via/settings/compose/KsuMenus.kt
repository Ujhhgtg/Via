package dev.ujhhgtg.via.settings.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box as LayoutBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
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
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints())
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            layout(width, 0) {
                placeable.place(anchorOffset.x, anchorOffset.y)
            }
        },
    ) {
        DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismissRequest) {
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
                Icon(
                    painter = painterResource(R.drawable.ic_ksu_check),
                    contentDescription = null,
                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                    tint = MaterialTheme.colorScheme.primary,
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

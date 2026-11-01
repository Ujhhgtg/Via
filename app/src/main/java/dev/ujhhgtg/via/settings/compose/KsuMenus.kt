package dev.ujhhgtg.via.settings.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
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

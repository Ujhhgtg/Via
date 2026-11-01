package dev.ujhhgtg.via.settings.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.ujhhgtg.via.R

/*
 * KernelSU's Manager settings surface, ported for Via: the same segmented cards
 * (manager/.../ui/component/material/SegmentedList.kt), expressive switch and top bar colours.
 */

private val LocalListItemShapes = compositionLocalOf<ListItemShapes?> { null }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun defaultSegmentedColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceBright,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun defaultSingleSegmentedShape(index: Int, count: Int): ListItemShapes {
    val base = ListItemDefaults.segmentedShapes(index, count)
    return if (count == 1) base.copy(shape = MaterialTheme.shapes.large) else base
}

/**
 * One card of rows: rows are painted as segments with the first/last one carrying the outer
 * radius, exactly like KernelSU's settings groups.
 */
@Composable
fun KsuSegmentedColumn(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: KsuSegmentedScope.() -> Unit,
) {
    val items = KsuSegmentedScope().apply(content).items
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrEmpty()) Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, itemContent ->
                CompositionLocalProvider(
                    LocalListItemShapes provides defaultSingleSegmentedShape(index, items.size),
                ) { itemContent() }
            }
        }
    }
}

@DslMarker
annotation class KsuSegmentedScopeDsl

/** Collects the rows of a [KsuSegmentedColumn] so the card can hand out first/last shapes. */
@KsuSegmentedScopeDsl
class KsuSegmentedScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) { items += content }
}

/** A row that opens another page, optionally showing its current value before the edge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KsuNavItem(
    @DrawableRes icon: Int? = null,
    title: String,
    summary: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    SegmentedListItem(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        enabled = enabled,
        leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        trailingContent = value?.takeIf { it.isNotEmpty() }?.let {
            {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        supportingContent = summary?.let { { Text(it) } },
        verticalAlignment = Alignment.CenterVertically,
        colors = defaultSegmentedColors(),
        content = { Text(title) },
    )
}

/** A row with a trailing expressive switch, as used by KernelSU's switches group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KsuSwitchItem(
    @DrawableRes icon: Int? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    SegmentedListItem(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(!checked)
        },
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        enabled = enabled,
        leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        trailingContent = {
            ExpressiveSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
            )
        },
        supportingContent = summary?.let { { Text(it) } },
        verticalAlignment = Alignment.CenterVertically,
        colors = defaultSegmentedColors(),
        interactionSource = interactionSource,
        content = { Text(title) },
    )
}

/** KernelSU's expressive switch: a check/close icon rides in the thumb. */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = expressiveSwitchColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    showThumbIcon: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = thumbContent ?: if (showThumbIcon && (checked || enabled)) {
            {
                Icon(
                    painter = painterResource(if (checked) R.drawable.ic_ksu_check else R.drawable.ic_ksu_close),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else null,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Composable
fun expressiveSwitchColors(
    checkedIconColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedIconColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledCheckedThumbColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
    disabledCheckedTrackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledCheckedIconColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledUncheckedThumbColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
    disabledUncheckedTrackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.12f),
    disabledUncheckedBorderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledUncheckedIconColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
): SwitchColors = SwitchDefaults.colors(
    checkedIconColor = checkedIconColor,
    uncheckedIconColor = uncheckedIconColor,
    disabledCheckedThumbColor = disabledCheckedThumbColor,
    disabledCheckedTrackColor = disabledCheckedTrackColor,
    disabledCheckedIconColor = disabledCheckedIconColor,
    disabledUncheckedThumbColor = disabledUncheckedThumbColor,
    disabledUncheckedTrackColor = disabledUncheckedTrackColor,
    disabledUncheckedBorderColor = disabledUncheckedBorderColor,
    disabledUncheckedIconColor = disabledUncheckedIconColor,
)

@Composable
fun expressiveTopAppBarColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    scrolledContainerColor: Color = containerColor,
): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = containerColor,
    scrolledContainerColor = scrolledContainerColor,
)

/** A row whose switch toggles the item while tapping the row opens it (Scripts / Extensions). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KsuSwitchNavItem(
    @DrawableRes icon: Int? = null,
    title: String,
    summary: String? = null,
    value: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    actions: List<KsuMenuAction> = emptyList(),
    onAction: (KsuMenuAction) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    var menuExpanded by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf(IntOffset.Zero) }
    Box(Modifier.trackPressPosition { menuAnchor = it.round() }) {
        SegmentedListItem(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            },
            onLongClick = actions.takeIf { it.isNotEmpty() }?.let {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuExpanded = true
                }
            },
            shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
            enabled = enabled,
            leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
            trailingContent = {
                ExpressiveSwitch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange,
                    interactionSource = interactionSource,
                )
            },
            supportingContent = summary?.let { { Text(it) } },
            verticalAlignment = Alignment.CenterVertically,
            colors = defaultSegmentedColors(),
            interactionSource = interactionSource,
            content = { Text(title) },
        )
        if (actions.isNotEmpty()) {
            KsuExpressiveMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, anchorOffset = menuAnchor) {
                actions.forEachIndexed { index, action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = { menuExpanded = false; onAction(action) },
                        shape = MenuDefaults.itemShape(index = index, count = actions.size).shape,
                    )
                }
            }
        }
    }
}

/** KernelSU's dropdown row: the current choice sits on the trailing edge and opens the menu. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KsuDropdownItem(
    @DrawableRes icon: Int? = null,
    title: String,
    summary: String? = null,
    items: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(IntOffset.Zero) }
    Box(Modifier.trackPressPosition { anchor = it.round() }) {
        SegmentedListItem(
            onClick = { if (items.isNotEmpty()) expanded = true },
            shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
            enabled = enabled,
            leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = {
                Text(
                    text = items.getOrNull(selectedIndex).orEmpty(),
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            verticalAlignment = Alignment.CenterVertically,
            colors = defaultSegmentedColors(),
            content = { Text(title) },
        )
        if (items.isNotEmpty()) {
            KsuExpressiveMenu(expanded = expanded, onDismissRequest = { expanded = false }, anchorOffset = anchor) {
                KsuSelectableMenu(items = items, selectedIndex = selectedIndex,
                    onDismiss = { expanded = false }, onSelect = onSelect)
            }
        }
    }
}

/** A checkbox row, mirroring KernelSU's SegmentedCheckboxItem. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KsuCheckItem(
    @DrawableRes icon: Int? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    SegmentedListItem(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(!checked)
        },
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        enabled = enabled,
        leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        trailingContent = {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
            )
        },
        supportingContent = summary?.let { { Text(it) } },
        verticalAlignment = Alignment.CenterVertically,
        colors = defaultSegmentedColors(),
        interactionSource = interactionSource,
        content = { Text(title) },
    )
}

/** A form row: title plus an inline single-line text field, like KernelSU's SegmentedTextField. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KsuInputItem(
    @DrawableRes icon: Int? = null,
    title: String,
    summary: String? = null,
    value: String,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardNumeric: Boolean = false,
    multiline: Boolean = false,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    var text by remember(title) { mutableStateOf(value) }
    SegmentedListItem(
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        enabled = enabled,
        leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        supportingContent = {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onValueChange(it) },
                enabled = enabled,
                singleLine = !multiline,
                maxLines = if (multiline) 8 else 1,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        keyboardNumeric -> KeyboardType.Number
                        isPassword -> KeyboardType.Password
                        else -> KeyboardType.Text
                    },
                ),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                            )
                        }
                        inner()
                    }
                },
            )
        },
        verticalAlignment = Alignment.CenterVertically,
        colors = defaultSegmentedColors(),
        content = { Text(title) },
    )
}

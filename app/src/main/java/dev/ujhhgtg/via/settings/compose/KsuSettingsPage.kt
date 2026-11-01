package dev.ujhhgtg.via.settings.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ujhhgtg.via.R

/** A row of a ported settings page; every row carries its own Material icon. */
sealed interface KsuRow {
    val key: Any
    @get:DrawableRes val icon: Int
    val title: String
    val summary: String?
}

/** A row that opens something, optionally showing its current value on the trailing edge. */
data class KsuNavRow(
    override val key: Any,
    override val icon: Int,
    override val title: String,
    override val summary: String? = null,
    val value: String? = null,
    val enabled: Boolean = true,
) : KsuRow

/** A list row whose switch edits the item while tapping the row opens it. */
data class KsuSwitchNavRow(
    override val key: Any,
    override val icon: Int,
    override val title: String,
    override val summary: String? = null,
    val value: String? = null,
    val checked: Boolean,
    val enabled: Boolean = true,
) : KsuRow

/** A row with a trailing expressive switch. */
data class KsuToggleRow(
    override val key: Any,
    override val icon: Int,
    override val title: String,
    override val summary: String? = null,
    val checked: Boolean,
    val enabled: Boolean = true,
) : KsuRow

/** A trailing top-bar action, such as the Scripts page's add and update entries. */
data class KsuTopAction(
    @DrawableRes val icon: Int,
    val description: String,
    val onClick: () -> Unit,
)

/** A titled card of rows; a null title renders the card without a section header. */
data class KsuGroup(val title: String?, val rows: List<KsuRow>)

/**
 * The KernelSU Material (M3E) settings page used by every ported settings screen: a collapsing
 * large top app bar with a back button, over cards of segmented icon rows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KsuSettingsPage(
    title: String,
    groups: List<KsuGroup>,
    onBack: () -> Unit,
    onRowClick: (KsuRow) -> Unit,
    onToggle: (KsuToggleRow, Boolean) -> Unit,
    onSwitchRow: (KsuSwitchNavRow, Boolean) -> Unit = { _, _ -> },
    topActions: List<KsuTopAction> = emptyList(),
    header: (@Composable () -> Unit)? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_ksu_arrow_back), stringResource(R.string.navigation_up))
                    }
                },
                actions = {
                    topActions.forEach { action ->
                        IconButton(onClick = action.onClick) {
                            Icon(painterResource(action.icon), action.description)
                        }
                    }
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            header?.invoke()
            groups.forEach { group ->
                KsuSegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 13.dp),
                    title = group.title,
                ) {
                    group.rows.forEach { row ->
                        item {
                            when (row) {
                                is KsuToggleRow -> KsuSwitchItem(
                                    icon = row.icon,
                                    title = row.title,
                                    summary = row.summary,
                                    checked = row.checked,
                                    enabled = row.enabled,
                                    onCheckedChange = { onToggle(row, it) },
                                )
                                is KsuSwitchNavRow -> KsuSwitchNavItem(
                                    icon = row.icon,
                                    title = row.title,
                                    summary = row.summary,
                                    value = row.value,
                                    checked = row.checked,
                                    enabled = row.enabled,
                                    onCheckedChange = { onSwitchRow(row, it) },
                                    onClick = { onRowClick(row) },
                                )
                                is KsuNavRow -> KsuNavItem(
                                    icon = row.icon,
                                    title = row.title,
                                    summary = row.summary,
                                    value = row.value,
                                    enabled = row.enabled,
                                    onClick = { onRowClick(row) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

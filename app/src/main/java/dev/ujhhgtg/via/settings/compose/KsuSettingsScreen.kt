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
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ujhhgtg.via.R

/** One navigable settings row, mirroring the ids of the classic [dev.ujhhgtg.via.settings.SettingsFragment]. */
data class KsuSettingsEntry(
    val id: Int,
    @DrawableRes val icon: Int,
    val title: String,
    val summary: String? = null,
)

/** A titled card of rows; a null title renders the card without a section header. */
data class KsuSettingsGroup(
    val title: String?,
    val entries: List<KsuSettingsEntry>,
)

/**
 * The KernelSU Material (M3E) settings page: a collapsing large top app bar over cards of
 * segmented rows. Only the root page is ported for now; the row ids match the classic page so
 * both surfaces open the same destinations.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KsuSettingsScreen(
    groups: List<KsuSettingsGroup>,
    ksuUiEnabled: Boolean,
    onOpen: (Int) -> Unit,
    onKsuUiChanged: (Boolean) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
            groups.forEach { group ->
                KsuSegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 13.dp),
                    title = group.title,
                ) {
                    group.entries.forEach { entry ->
                        item { KsuClickableItem(entry.icon, entry.title, entry.summary) { onOpen(entry.id) } }
                    }
                }
            }
            KsuSegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 13.dp),
            ) {
                item {
                    KsuSwitchItem(
                        icon = R.drawable.ic_ksu_tune,
                        title = stringResource(R.string.settings_ksu_ui),
                        summary = stringResource(R.string.settings_ksu_ui_summary),
                        checked = ksuUiEnabled,
                        onCheckedChange = onKsuUiChanged,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

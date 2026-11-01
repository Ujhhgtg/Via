package dev.ujhhgtg.via.settings.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.settings.TextZoomDialogs
import dev.ujhhgtg.via.settings.TextZoomRepository

/** jb.k5: per-site text zoom, with the global slider on top and one row per exception. */
class KsuWebTextSizeSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.size

    private val repository by lazy { TextZoomRepository(requireContext()) }

    override fun groups(): List<KsuGroup> {
        val rows = buildList {
            add(KsuToggleRow(3, R.drawable.ic_ksu_font_download, getString(R.string.force_enable_zoom),
                checked = repository.forceZoom))
            if (!dev.ujhhgtg.via.engine.Engines.backend.capabilities.perPageTextZoom) return@buildList
            add(KsuNavRow(2, R.drawable.ic_ksu_add, getString(R.string.add_site_exception)))
            val exceptions = repository.exceptions()
            exceptions.keys.sortedWith(TextZoomRepository.domainOrder).forEach { domain ->
                add(KsuNavRow(domain.hashCode(), R.drawable.ic_ksu_public, domain,
                    value = String.format(java.util.Locale.ROOT, "%d%%", exceptions.getValue(domain))))
            }
        }
        return listOf(KsuGroup(null, rows))
    }

    override fun header(): (@Composable () -> Unit)? = {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 13.dp)) {
            Text(
                text = TextZoomDialogs.sample(requireContext()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Slider(
                value = repository.global.toFloat(),
                onValueChange = { repository.global = it.toInt(); refresh() },
                valueRange = 50f..200f,
                steps = 29,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    override fun onRow(row: KsuRow) {
        if (row.key == 2) {
            TextZoomDialogs.editException(requireActivity(), repository, "", repository.global, true) { refresh() }
            return
        }
        val domain = row.key as? Int ?: return
        val exceptions = repository.exceptions()
        val match = exceptions.keys.firstOrNull { it.hashCode() == domain } ?: return
        TextZoomDialogs.editException(requireActivity(), repository, match, exceptions.getValue(match), true) { refresh() }
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        if (row.key != 3) return
        repository.forceZoom = checked
        refresh()
    }
}

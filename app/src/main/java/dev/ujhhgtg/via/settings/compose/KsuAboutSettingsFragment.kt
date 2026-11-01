package dev.ujhhgtg.via.settings.compose

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.ujhhgtg.via.BuildConfig
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.settings.DebuggingInfoFragment
import dev.ujhhgtg.via.settings.OpenSourceLicensesFragment
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.util.Locale

/** hb.d: the About page with the original version header, now rendered with Material rows. */
class KsuAboutSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.settings_about

    override fun header(): (@Composable () -> Unit)? = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.settings_about_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clickable { (requireActivity() as Shell).navigate(DebuggingInfoFragment()) },
            )
            Text(
                text = getString(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 2.dp, start = 24.dp, end = 24.dp)
                    .clickable { versionDialog() },
            )
        }
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, listOf(
            KsuNavRow(4, R.drawable.ic_ksu_terminal, getString(R.string.open_source_repository)),
            KsuNavRow(1, R.drawable.ic_ksu_description, getString(R.string.open_source_licenses)),
        )),
    )

    override fun onRow(row: KsuRow) {
        when (row.key) {
            1 -> (requireActivity() as Shell).navigate(OpenSourceLicensesFragment())
            4 -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/Ujhhgtg/Via".toUri())) }
        }
    }

    private fun versionDialog() {
        ViaDialog(requireActivity()).title(BuildConfig.VERSION_NAME)
            .message(if (Locale.getDefault().country.equals("CN", true)) "莫忧世事兼身事，须著人间比梦间。" else "Less is more.")
            .positive(android.R.string.ok).show()
    }
}

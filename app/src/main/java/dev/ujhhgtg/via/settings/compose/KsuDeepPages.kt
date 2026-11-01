package dev.ujhhgtg.via.settings.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withJson
import dev.ujhhgtg.via.BuildConfig
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.search.SearchProviders
import dev.ujhhgtg.via.settings.SearchProvidersFragment
import dev.ujhhgtg.via.settings.SearchToolbarSettingsFragment
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.util.Date
import java.util.Locale

/** hb.g4: the six ignored-SSL-warning bits, now Material switches. */
class KsuSslWarningsSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.title_ignore_ssl_warnings

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private var flags = 0

    private val labels = intArrayOf(
        R.string.ssl_warning_certificate_untrusted,
        R.string.ssl_warning_certificate_date_invalid,
        R.string.ssl_warning_certificate_expired,
        R.string.ssl_warning_certificate_domain_mismatch,
        R.string.ssl_warning_certificate_not_yet_valid,
        R.string.ssl_warning_certificate_invalid,
    )
    private val icons = intArrayOf(
        R.drawable.ic_ksu_verified,
        R.drawable.ic_ksu_event,
        R.drawable.ic_ksu_event_busy,
        R.drawable.ic_ksu_link_off,
        R.drawable.ic_ksu_schedule,
        R.drawable.ic_ksu_error,
    )

    override fun groups(): List<KsuGroup> {
        flags = preferences.ignoredSslWarning
        return listOf(
            KsuGroup(null, labels.indices.map { index ->
                val flag = 1 shl index
                KsuToggleRow(flag, icons[index], getString(labels[index]), checked = flags and flag != 0)
            }),
        )
    }

    override fun onToggle(row: KsuToggleRow, checked: Boolean) {
        val flag = row.key as Int
        flags = if (checked) flags or flag else flags and flag.inv()
        preferences.ignoredSslWarning = flags
        refresh()
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_info, getString(R.string.help)) {
            ViaDialog(requireActivity()).title(R.string.title_ignore_ssl_warnings).message(R.string.message_ignore_ssl_warnings)
                .positive(android.R.string.ok).show()
        },
    )
}

/** Library attributions generated at build time by the AboutLibraries Gradle plugin. */
class KsuLicensesSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.open_source_licenses

    private val libraries by lazy {
        Libs.Builder().withJson(requireContext(), R.raw.aboutlibraries).build().libraries.sortedBy { it.name.lowercase() }
    }

    override fun groups(): List<KsuGroup> = listOf(
        KsuGroup(null, libraries.mapIndexed { index, library ->
            val author = library.organization?.name ?: library.developers.firstOrNull()?.name
            val licenses = library.licenses.joinToString { it.name }
            KsuNavRow(
                key = index,
                icon = R.drawable.ic_ksu_description,
                title = library.name,
                summary = listOfNotNull(author, licenses.takeIf { it.isNotEmpty() }).joinToString(" · ").ifEmpty { null },
            )
        }),
    )

    override fun onRow(row: KsuRow) {
        val library = libraries.getOrNull(row.key as? Int ?: return) ?: return
        val url = library.website ?: library.scm?.url ?: library.licenses.firstOrNull()?.url ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }
}

/** hb.n0's diagnostics body, rendered as a selectable monospace card. */
class KsuDebuggingInfoSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.debugging_info

    private fun info(): String {
        val context = requireContext()
        val metrics = resources.displayMetrics
        return buildString {
            append("Device: ${Build.BRAND} ${Build.MODEL}(${Build.PRODUCT})\n")
            append("Package Name: ${context.packageName}\n")
            append("App Version: ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})\n")
            append("Snapshot Time: ${DateFormat.format("yyyy-MM-dd kk:mm:ss", Date())}\n")
            append("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(" ")}\n")
            append("Display Size: ${metrics.heightPixels}x${metrics.widthPixels}\n")
            append("Android Version: ${Build.VERSION.RELEASE} API${Build.VERSION.SDK_INT}\n")
            append("Language: ${Locale.getDefault().toLanguageTag()}\n")
            dev.ujhhgtg.via.engine.Engines.backend.versionInfo().forEach { (label, value) -> append("$label: $value\n") }
        }.trimEnd()
    }

    override fun groups(): List<KsuGroup> = emptyList()

    override fun header(): (@Composable () -> Unit)? = {
        Surface(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 13.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceBright,
        ) {
            SelectionContainer {
                Text(
                    text = info(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }

    override fun topActions(): List<KsuTopAction> = listOf(
        KsuTopAction(R.drawable.ic_ksu_content_copy, getString(android.R.string.copy)) {
            (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("", info()))
            ViaToast.makeText(requireContext(), getString(R.string.toast_copy_text_successful), ViaToast.LENGTH_SHORT).show()
        },
    )
}

/** hb.b6: the search home, now Material; it leads to the suggestion page and the toolbar page. */
class KsuSearchSettingsFragment : KsuPageFragment() {
    override val titleRes = R.string.search_settings

    private val preferences by lazy { BrowserPreferences(requireContext()) }
    private lateinit var database: BrowserDatabase
    private lateinit var providers: SearchProviders

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = BrowserDatabase(requireContext())
        providers = SearchProviders(requireContext(), preferences, database)
    }

    override fun onDestroyView() {
        if (::database.isInitialized) database.close()
        super.onDestroyView()
    }

    private fun suggestionSummary(): String = listOf(
        R.string.action_favorites,
        R.string.action_bookmarks,
        R.string.title_open_tabs,
        R.string.action_history,
        R.string.search,
        R.string.search_history,
    ).filterIndexed { index, _ -> preferences.searchSuggestion and (1 shl index) != 0 }
        .map(::getString)
        .joinToString("/")

    override fun groups(): List<KsuGroup> {
        val selected = if (::providers.isInitialized) {
            providers.list().firstOrNull { it.id == preferences.searchMode }?.name ?: providers.name(preferences.searchMode)
        } else ""
        return listOf(
            KsuGroup(null, listOf(
                KsuNavRow(1, R.drawable.ic_ksu_search, getString(R.string.search), value = selected),
                KsuNavRow(2, R.drawable.ic_ksu_travel_explore, getString(R.string.title_search_suggestions),
                    value = suggestionSummary().ifEmpty { null }),
                KsuNavRow(3, R.drawable.ic_ksu_view_agenda, getString(R.string.search_toolbar),
                    value = getString(if (preferences.appFlags and 32768 == 0) R.string.enabled else R.string.disabled)),
            )),
        )
    }

    override fun onRow(row: KsuRow) {
        val shell = requireActivity() as Shell
        when (row.key) {
            1 -> shell.navigate(SearchProvidersFragment())
            2 -> shell.navigate(KsuSearchSuggestionsSettingsFragment())
            3 -> shell.navigate(SearchToolbarSettingsFragment())
        }
    }
}

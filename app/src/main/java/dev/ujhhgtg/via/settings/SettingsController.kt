package dev.ujhhgtg.via.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.browser.script.ScriptResources
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.browser.script.UserScript
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.common.applicationIoScope
import dev.ujhhgtg.via.common.launchIo
import kotlinx.coroutines.launch
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDataBackup
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.passwords.PasswordAuthenticator
import dev.ujhhgtg.via.passwords.PasswordRepository
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.io.File
import java.util.Locale

/** Retained settings actions; document-picker results return through [onActivityResult]. */
class SettingsController(
    private val activity: FragmentActivity,
    private val openPage: (String) -> Unit,
    private val launchForResult: (Intent, Int) -> Unit,
    private val scopeOwner: LifecycleOwner = activity as LifecycleOwner,
    private val exportScopeOwner: LifecycleOwner = scopeOwner,
) : ContextWrapper(activity), AutoCloseable {
    private val preferences = BrowserPreferences(this)
    private val database = BrowserDatabase(this)

    private val chooseDirectoryRequest = 4101
    private val importBookmarksRequest = 4102
    private val exportBookmarksRequest = 4103
    private val importBackupRequest = 4105
    private val exportBackupRequest = 4106
    private var backupSections = 31
    private var backupPassword: String? = null
    private val passwordAuthenticator by lazy { PasswordAuthenticator(activity, launchForResult) }
    private val scriptStore by lazy { ScriptStore(this) }
    private var closed = false
    private var awaitingAuthentication = false
    private var transferDialog: ViaDialog? = null
    private var requestedOrientation: Int
        get() = activity.requestedOrientation
        set(value) { activity.requestedOrientation = value }

    fun saveState(out: Bundle) {
        out.putInt("backup_sections", backupSections)
    }

    fun restoreState(state: Bundle?) {
        if (state == null) return
        backupSections = state.getInt("backup_sections", 31)
    }

    override fun close() {
        if (closed) return
        closed = true
        transferDialog?.dismiss()
        passwordAuthenticator.dispose()
        scriptStore.close()
        database.close()
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (awaitingAuthentication && passwordAuthenticator.onActivityResult(requestCode, resultCode)) return true
        if (requestCode != chooseDirectoryRequest && requestCode != importBookmarksRequest &&
            requestCode != exportBookmarksRequest && requestCode != importBackupRequest &&
            requestCode != exportBackupRequest) return false
        if (resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true
        when (requestCode) {
            chooseDirectoryRequest -> {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
                preferences.downloadDirectory = uri.toString()
            }
            importBookmarksRequest -> importBookmarks(uri)
            exportBookmarksRequest -> exportBookmarks(uri)
            importBackupRequest -> importBackup(uri)
            exportBackupRequest -> exportBackup(uri)
        }
        return true
    }

    private fun launchDocument(intent: Intent, requestCode: Int) = launchForResult(intent, requestCode)
    private fun runOnUiThread(action: () -> Unit) = activity.runOnUiThread { if (!closed) action() }

    fun route(key: String) {
        when (key) {
            "home", "homepage" -> homepage()
            "homepage_customization" -> openPage("homepage_customization")
            "language" -> language()
            "orientation" -> orientation()
            "restore_tabs" -> restoreTabs()
            "download", "download_location" -> chooseDirectory()
            "addon_download", "download_manager" -> downloadManager()
            "external_video_player" -> videoPlayer()
            "clear_data", "clear_browsing_data" -> clearData()
            "clear_data_on_exit" -> clearOnExit()
            "import_or_export_bookmarks" -> bookmarkTransfer()
            "import_data" -> importBackupRequest()
            "export_data" -> exportBackupOptions()
            // z8.b0.W -> f1.j: API 24+ only opens the default-apps settings, ignoring a missing activity.
            "setting_default" -> runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            "terms_of_use" -> openUrl("https://viayoo.com/zh-cn/docs/terms-of-use.html")
            "privacy_policy" -> openUrl("https://viayoo.com/zh-cn/docs/privacy-policy.html")
            "open_source_licenses" -> openUrl("https://github.com/tuyafeng/Via")
            "help_us_translate" -> openUrl("https://github.com/tuyafeng/Via/tree/master/app/src/main/res")
            "join_telegram_group" -> openUrl("https://t.me/viayoo")
            "join_qq_group" -> openUrl("https://viayoo.com/contact/qqgroup/")
            "email_me" -> startActivity(Intent(Intent.ACTION_SENDTO, "mailto:yafengtu@gmail.com".toUri()))
            "wechat_official_account" -> openUrl("https://viayoo.com/contact/wechat/")
            "about", "debugging_info" -> about()
            else -> unavailable(key)
        }
    }

    private fun homepage() {
        val labels = arrayOf(getString(R.string.default_set), getString(R.string.action_blank), getString(R.string.action_bookmarks), getString(R.string.action_webpage))
        val current = when (preferences.home) { "about:home", "about:links" -> 0; "about:blank" -> 1; "about:bookmarks" -> 2; else -> 3 }
        ViaDialog(activity).title(R.string.home).singleChoice(labels, current) { which ->
            if (which < 3) preferences.home = arrayOf("about:home", "about:blank", "about:bookmarks")[which]
            else editHomepage()
        }.show()
    }

    private fun editHomepage() {
        ViaDialog(activity).title(R.string.title_custom_homepage)
            .input(preferences.home.takeUnless { it.startsWith("about:") } ?: "https://m.baidu.com/?tn=&from=1022560v", getString(R.string.title_custom_homepage), 3)
            .positive(android.R.string.ok) { _, result ->
                val raw = result.edit?.firstOrNull()?.trim().orEmpty()
                val normalized = dev.ujhhgtg.via.browser.UrlResolver.normalizeInput(raw, null)
                if (normalized != null && normalized.contains("://")) preferences.home = normalized
            }.negative(android.R.string.cancel).show()
    }

    private fun restoreTabs() {
        ViaDialog(activity).title(R.string.restore_tabs).singleChoice(
            arrayOf(getString(R.string.disable_restore), getString(R.string.always_restore), getString(R.string.ask_first)), preferences.restoreClosedTabs
        ) { which ->
            preferences.restoreClosedTabs = which
            if (which != 0) ViaToast.makeText(this, getString(R.string.restore_tabs_hint_settings), ViaToast.LENGTH_SHORT).show()
        }.show()
    }

    private fun language() {
        val tags = buildList {
            val parser = resources.getXml(R.xml.locales_config)
            parser.use { parser ->
                while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale") {
                        parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")?.let(::add)
                    }
                    parser.next()
                }
            }
        }.sorted()
        val labels = arrayOf(getString(R.string.follow_system), *tags.map { tag -> Locale.forLanguageTag(tag).let { it.getDisplayName(it) } }.toTypedArray())
        val selected = preferences.language?.let { Locale.forLanguageTag(it).toLanguageTag() }
        val current = tags.indexOfFirst { Locale.forLanguageTag(it).toLanguageTag() == selected } + 1
        ViaDialog(activity).title(R.string.language).singleChoice(labels, current) { which ->
            if (which != current) {
                preferences.language = tags.getOrNull(which - 1)
                ViaDialog(activity).title(R.string.language).message(R.string.restart_to_take_effect)
                    .positive(R.string.crash_restart) { _, _ ->
                        startActivity(Intent.makeRestartActivityTask(android.content.ComponentName(activity, dev.ujhhgtg.via.Shell::class.java)).setPackage(packageName))
                        kotlin.system.exitProcess(0)
                    }.negative(android.R.string.cancel).show()
            }
        }.show()
    }

    private fun orientation() {
        ViaDialog(activity).title(R.string.orientation).singleChoice(
            arrayOf(getString(R.string.follow_system), getString(R.string.orientation_auto), getString(R.string.orientation_portrait), getString(R.string.orientation_landscape)), preferences.screenOrientation - 1
        ) { which ->
            preferences.screenOrientation = which + 1
            if (Build.VERSION.SDK_INT < 36 || resources.configuration.smallestScreenWidthDp < 600) requestedOrientation = preferences.resolvedScreenOrientation()
        }.show()
    }

    private fun downloadManager() {
        val choices = ExternalDownloadManagers.choices(this)
        ViaDialog(activity).title(R.string.addon_download).singleChoice(choices.map { it.label }.toTypedArray(), choices.indexOfFirst { it.id == preferences.downloadManager.orEmpty() }.coerceAtLeast(0)) { index ->
            preferences.downloadManager = choices[index].id
        }.show()
    }

    private fun videoPlayer() {
        val choices = ExternalVideoPlayers.choices(this)
        ViaDialog(activity).title(R.string.external_video_player).message(R.string.external_video_player_description)
            .singleChoice(choices.map { it.label }.toTypedArray(), choices.indexOfFirst { it.packageName == preferences.videoPlayer.orEmpty() }.coerceAtLeast(0)) { index ->
                preferences.videoPlayer = choices[index].packageName
            }.show()
    }

    private fun chooseDirectory() {
        if (preferences.downloadDirectory == android.os.Environment.DIRECTORY_DOWNLOADS) chooseDirectoryDocument()
        else ViaDialog(activity).title(R.string.download)
            .items(arrayOf(getString(R.string.action_default_location), getString(R.string.action_choose_folder)), onClick = { which ->
                if (which == 0) preferences.downloadDirectory = android.os.Environment.DIRECTORY_DOWNLOADS
                else chooseDirectoryDocument()
            }).show()
    }

    private fun chooseDirectoryDocument() = launchDocument(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        if (preferences.downloadDirectory.startsWith("content://")) putExtra(DocumentsContract.EXTRA_INITIAL_URI, preferences.downloadDirectory.toUri())
    }, chooseDirectoryRequest)

    private fun clearOnExit() {
        val selected = dataMasks.indices.filter { preferences.clearDataOnExit and dataMasks[it] != 0 }.toIntArray()
        ViaDialog(activity).title(R.string.clear_data_on_exit).multipleChoice(dataLabels(), selected)
            .negative(android.R.string.cancel).positive(android.R.string.ok) { _, result ->
                preferences.clearDataOnExit = (result.selected ?: intArrayOf()).fold(0) { mask, index -> mask or dataMasks[index] }
            }.show()
    }

    private fun clearData() {
        val selected = dataMasks.indices.filter { preferences.clearData and dataMasks[it] != 0 }.toIntArray()
        ViaDialog(activity).title(R.string.clear_data).multipleChoice(dataLabels(), selected)
            .negative(android.R.string.cancel).positive(android.R.string.ok) { _, result ->
                val checked = BooleanArray(dataMasks.size) { it in (result.selected ?: intArrayOf()) }
                preferences.clearData = mask(checked); clearSelected(checked)
            }.show()
    }

    private fun dataLabels() = arrayOf(
        getString(R.string.data_cache),
        getString(R.string.data_form_data),
        getString(R.string.data_history),
        getString(R.string.recently_closed_tabs),
        getString(R.string.data_storage),
        getString(R.string.data_cookies),
        getString(R.string.data_app_cache),
    )
    private val dataMasks = intArrayOf(1, 2, 4, 64, 8, 16, 32)
    private fun mask(values: BooleanArray): Int = values.indices.filter { values[it] }.fold(0) { result, i -> result or dataMasks[i] }

    /** z8.h0.a shares the original category implementation with the startup policy. */
    private fun clearSelected(selected: BooleanArray) {
        if (dev.ujhhgtg.via.browser.BrowserDataCleaner(this, database).clearManual(mask(selected))) {
            ViaToast.makeText(this, getString(R.string.data_cleared), ViaToast.LENGTH_SHORT).show()
        }
    }

    private fun bookmarkTransfer() {
        ViaDialog(activity).title(R.string.import_or_export_bookmarks)
            .items(arrayOf(getString(R.string.export_bookmarks), getString(R.string.import_bookmarks)), onClick = { which ->
                if (which == 0) exportRequest() else importRequest()
            }).show()
    }

    private fun importRequest() = launchDocument(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/html"))
        addCategory(Intent.CATEGORY_OPENABLE)
    }, importBookmarksRequest)

    private fun exportRequest() = launchDocument(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        type = "text/html"
        putExtra(Intent.EXTRA_TITLE, "${getString(R.string.app_name)}_${getString(R.string.action_bookmarks)}_${exportDate()}.html")
    }, exportBookmarksRequest)

    private fun importBookmarks(uri: Uri) = runTransfer(work = {
        runCatching { BrowserDatabase(applicationContext).use { db ->
            contentResolver.openInputStream(uri)?.use { BookmarkRepository(db).importHtml(it) } ?: 0
        } }.getOrDefault(0)
    }) { count ->
        GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
        ViaToast.makeText(this, if (count <= 0) getString(R.string.import_bookmark_error) else getString(R.string.bookmarks_imported, count), ViaToast.LENGTH_LONG).show()
    }

    private fun exportBookmarks(uri: Uri) = runTransfer(exporting = true, work = {
        runCatching { BrowserDatabase(applicationContext).use { db ->
            contentResolver.openOutputStream(uri)?.use { BookmarkRepository(db).exportHtml(it) } ?: 0
        } }.getOrDefault(0)
    }) { count ->
        ViaToast.makeText(this, getString(if (count == 0) R.string.no_bookmark else R.string.bookmarks_exported_successfully), ViaToast.LENGTH_SHORT).show()
    }

    private fun importBackupRequest() = launchDocument(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "text/plain"))
        addCategory(Intent.CATEGORY_OPENABLE)
    }, importBackupRequest)

    private fun exportBackupOptions() {
        val labels = arrayOf(getString(R.string.action_bookmarks), getString(R.string.action_favorites), getString(R.string.action_history), getString(R.string.recently_closed_tabs), getString(R.string.settings), getString(R.string.passwords))
        val masks = intArrayOf(2, 4, 8, 16, 1, 32)
        ViaDialog(activity).title(R.string.export_data).multipleChoice(labels, intArrayOf(0, 1, 2, 3, 4))
            .negative(android.R.string.cancel).positive(android.R.string.ok) { _, result ->
                val selected = result.selected ?: return@positive
                if (selected.isEmpty()) return@positive
                exportBackupWithSections(selected.fold(0) { mask, index -> mask or masks[index] })
            }.show()
    }

    /** Continues the export flow with sections chosen on the Material selection page. */
    fun exportBackupWithSections(sections: Int) {
        backupSections = sections
        if (backupSections and 32 != 0 && PasswordRepository.get(this).list().isEmpty()) backupSections = backupSections and 32.inv()
        if (backupSections == 0) return
        backupPassword = null
        if (backupSections and 32 == 0) exportBackupRequest()
        else {
            awaitingAuthentication = true
            passwordAuthenticator.authenticate(getString(R.string.export_data), getString(R.string.unlock_device_to_export_data),
                onCancel = { awaitingAuthentication = false }) {
                awaitingAuthentication = false
                backupPasswordDialog(exporting = true) { password -> backupPassword = password; exportBackupRequest() }
            }
        }
    }

    private fun backupPasswordDialog(exporting: Boolean, accepted: (String) -> Unit) {
        ViaDialog(activity).title(if (exporting) R.string.enter_password else R.string.password_required)
            .message(if (exporting) R.string.enter_password_for_exporting_data else R.string.password_required_for_importing_data)
            .input("", getString(R.string.hint_password), 1)
            .negative(android.R.string.cancel).positive(android.R.string.ok) { _, result ->
                accepted(result.edit?.firstOrNull().orEmpty().ifEmpty { "3_=cZHGxZ#FZgZA@" })
            }.show()
    }

    private fun exportDate(): String {
        val locale = Locale.getDefault()
        val pattern = when (locale.country) { "US" -> "MMddyyyy"; "UK" -> "ddMMyyyy"; else -> "yyyyMMdd" }
        return java.text.SimpleDateFormat(pattern, locale).format(java.util.Date())
    }

    private fun exportBackupRequest() = launchDocument(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_TITLE, "${getString(R.string.app_name)}_${getString(R.string.data_file)}_${exportDate()}.zip")
    }, exportBackupRequest)

    private fun exportBackup(uri: Uri) {
        val password = backupPassword
        backupPassword = null
        val sections = backupSections
        runTransfer(exporting = true, work = {
            BrowserDatabase(applicationContext).use { db ->
                val extraEntries = linkedMapOf<String, ByteArray>()
                if (sections and 1 != 0) extraEntries.putAll(SettingsRecordsCodec(applicationContext, db).exportArchiveEntries())
                if (sections and 32 != 0) extraEntries.putAll(PasswordRepository.get(applicationContext).exportEncrypted(requireNotNull(password)))
                contentResolver.openOutputStream(uri)?.use { BrowserDataBackup(db).exportZip(it, extraEntries, sections) } != null
            }
        }) { success -> if (success) ViaToast.makeText(this, getString(R.string.data_exported_successfully), ViaToast.LENGTH_SHORT).show() }
    }

    private fun importBackup(uri: Uri) {
        val encrypted = runCatching {
            contentResolver.openInputStream(uri)?.use { input -> java.util.zip.ZipInputStream(input).use { zip ->
                var found = false
                while (true) { val entry = zip.nextEntry ?: break; if (!entry.isDirectory && entry.name.endsWith(".enc")) { found = true; break }; zip.closeEntry() }
                found
            } } ?: false
        }.getOrDefault(false)
        if (encrypted) backupPasswordDialog(exporting = false) { performBackupImport(uri, it) }
        else performBackupImport(uri, null)
    }

    private fun performBackupImport(uri: Uri, password: String?) = runTransfer(work = {
        var name: String? = null
        var size = 0L
        if (uri.scheme.equals("file", true)) {
            uri.path?.let { filePath -> File(filePath).let { name = it.name; size = it.length() } }
        } else contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) { name = cursor.getString(0); size = cursor.getLong(1) }
        }
        if (name == null || size <= 0L) return@runTransfer 2
        if (size > 67_108_864L) return@runTransfer 3
        val restored = BrowserDatabase(applicationContext).use { db ->
            contentResolver.openInputStream(uri)?.use { input ->
                if (name!!.lowercase(Locale.ROOT).endsWith(".zip")) {
                    val entries = linkedMapOf<String, ByteArray>()
                    BrowserDataBackup(db).importZip(input) { file, bytes -> entries[file] = bytes }
                    entries["settings.txt"]?.let { require(SettingsRecordsCodec(applicationContext, db).importRecords(it.toString(Charsets.UTF_8), entries = entries)) }
                    if (password != null && entries.containsKey("info.enc") && entries.containsKey("pass.enc")) {
                        runCatching { PasswordRepository.get(applicationContext).importEncrypted(entries.getValue("info.enc"), entries.getValue("pass.enc"), password) }
                            .onFailure { runOnUiThread { ViaToast.makeText(this, getString(R.string.wrong_psw), ViaToast.LENGTH_LONG).show() } }
                    }
                    true
                } else {
                    val source = input.bufferedReader().use { it.lineSequence().joinToString("") }
                    dev.ujhhgtg.via.sync.LegacyCloudData(applicationContext, db).restore(source)
                }
            } ?: false
        }
        if (restored) 0 else 1
    }) { status ->
        ViaToast.makeText(this, getString(when (status) { 0 -> R.string.import_data_succeed; 3 -> R.string.import_data_failed_file_too_large; else -> R.string.import_data_failed }), ViaToast.LENGTH_SHORT).show()
        if (status == 0) {
            requestedOrientation = preferences.resolvedScreenOrientation()
            prepareDependencies(scriptStore.list())
        }
    }

    /** hb.c4: imports use the view scope; exports explicitly survive until Fragment ON_DESTROY. */
    private fun <T : Any> runTransfer(exporting: Boolean = false, work: () -> T, accepted: (T) -> Unit) {
        val progress = transferDialog ?: ViaDialog(activity).progress(R.string.wait_a_moment).cancelable(false).canceledOnTouchOutside(false).also { transferDialog = it }
        progress.show()
        val owner = if (exporting) exportScopeOwner else scopeOwner
        owner.launchIo(work, { result ->
                progress.dismiss(); transferDialog = null; accepted(result)
            }, { error -> progress.dismiss(); transferDialog = null; android.util.Log.w("ViaSettings", "Data transfer failed", error) })
    }

    private fun prepareDependencies(scripts: List<UserScript>) {
        val dependencies = scripts.filter { it.requires.isNotEmpty() || it.resources.isNotEmpty() }
        if (dependencies.isEmpty()) return
        applicationIoScope.launch {
            val resources = ScriptResources(applicationContext)
            val failed = dependencies.filterNot { resources.ensure(it) }
            if (failed.isNotEmpty()) runOnUiThread {
                ViaToast.makeText(this@SettingsController, failed.joinToString("\n") { getString(R.string.toast_install_script_failed_dependency_error, it.name) }, ViaToast.LENGTH_LONG).show()
            }
        }
    }

    private fun about() = AlertDialog.Builder(this)
        .setTitle(getString(R.string.settings_about))
        .setMessage("${getString(R.string.app_name)}\n${packageManager.getPackageInfo(packageName, 0).versionName}\n$packageName")
        .setPositiveButton(android.R.string.ok, null).show()

    private fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    private fun unavailable(key: String) = AlertDialog.Builder(this)
        .setTitle(getString(R.string.settings))
        .setMessage(getString(R.string.toast_operation_failed))
        .setPositiveButton(android.R.string.ok, null).show()
}

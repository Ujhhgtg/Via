package dev.ujhhgtg.via.downloads

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A foreground owner for split selection, system permission/confirmation and the final install result. */
class SplitPackageInstallerActivity : FragmentActivity() {
    private var prepared: DownloadPackageArchive.Prepared? = null
    private var selected = emptyList<ApkPart>()
    private var sessionId = -1
    private var installedPackage: String? = null
    private val device get() = ApkSplitSelection.Device(Build.SUPPORTED_ABIS.toList(), Resources.getSystem().displayMetrics.densityDpi,
        Resources.getSystem().configuration.locales.let { locales -> (0 until locales.size()).map { locales[it].toLanguageTag() } }, Build.VERSION.SDK_INT)
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) install()
        else finish()
    }
    private val obbDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) finish()
        else importObb(uri)
    }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(BrowserPreferences(base).localizedContext(base))

    override fun onCreate(state: Bundle?) {
        val preferences = BrowserPreferences(this)
        preferences.updateSystemNightMode(resources.configuration.uiMode and 0x30 == 0x20)
        setTheme(if (preferences.isNightMode) R.style.Theme_Via_PackageInstaller_Dark else R.style.Theme_Via_PackageInstaller)
        super.onCreate(state)
        setContentView(FrameLayout(this))
        if (intent.action == ACTION_RESULT) { handleResult(intent); return }
        val source = intent.data ?: run { finish(); return }
        lifecycleScope.launch {
            try {
                prepared = withContext(Dispatchers.IO) { DownloadPackageArchive.prepare(applicationContext, source) }
                chooseBase()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(getString(R.string.package_archive_failed, error.message.orEmpty())) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_RESULT) handleResult(intent)
    }

    private fun chooseBase() {
        val parts = prepared?.parts ?: return
        val bases = parts.filter { it.isBase }
        val best = ApkSplitSelection.bestBase(parts, device)
        if (best == null) { showFailure(getString(R.string.package_no_compatible_apk)); return }
        if (bases.size == 1) { chooseSplits(best); return }
        ViaDialog(this).title(R.string.package_choose_app)
            .singleChoice(bases.map { "${it.manifest.packageName}\n${it.entryName}" }.toTypedArray(), bases.indexOf(best))
            .positive(android.R.string.ok) { _, result ->
                val base = bases[result.selected?.firstOrNull() ?: bases.indexOf(best)]
                chooseSplits(base)
            }.negative(android.R.string.cancel) { finish() }.onCancel { finish() }.show()
    }

    private fun chooseSplits(base: ApkPart) {
        val parts = ApkSplitSelection.compatible(prepared!!.parts, base)
        val splits = parts.filter { !it.isBase }.sortedWith(compareBy<ApkPart> { it.group }.thenBy { it.manifest.splitName })
        val recommended = ApkSplitSelection.recommended(parts, base, device)
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageArchiveInfo(base.file.path, 0)
        val application = info?.applicationInfo?.apply { sourceDir = base.file.path; publicSourceDir = sourceDir }
        val label = application?.loadLabel(packageManager)
            ?.toString() ?: base.manifest.packageName
        ViaDialog(this).title(R.string.package_choose_splits)
            .titleIcon(application?.loadIcon(packageManager))
            .message(getString(R.string.package_base_required, label, base.manifest.packageName))
            .multipleChoice(splits.map(::splitLabel).toTypedArray(), splits.indices.filter { splits[it] in recommended }.toIntArray())
            .positive(R.string.install) { _, result ->
                selected = listOf(base) + (result.selected ?: intArrayOf()).map { splits[it] }
                if (!ApkSplitSelection.valid(selected, Build.VERSION.SDK_INT)) {
                    ViaToast.show(this, R.string.package_invalid_selection)
                    chooseSplits(base)
                } else requestPermissionAndInstall()
            }.negative(android.R.string.cancel) { finish() }.onCancel { finish() }.show()
    }

    private fun splitLabel(part: ApkPart): String {
        val detail = when {
            ApkSplitSelection.abi(part) != null -> getString(R.string.package_split_architecture, ApkSplitSelection.abi(part))
            ApkSplitSelection.density(part) != null -> getString(R.string.package_split_density, part.qualifier)
            ApkSplitSelection.language(part) != null -> getString(R.string.package_split_language,
                java.util.Locale.forLanguageTag(ApkSplitSelection.language(part)!!).getDisplayName(java.util.Locale.getDefault()))
            else -> getString(R.string.package_split_feature)
        }
        return "${part.manifest.splitName}\n$detail · ${DownloadPresentation.size(part.file.length())}"
    }

    private fun requestPermissionAndInstall() {
        if (packageManager.canRequestPackageInstalls()) { install(); return }
        ViaDialog(this).title(R.string.install).message(R.string.package_install_permission_required)
            .positive(R.string.grant) { _, _ ->
                runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:$packageName".toUri())) }
                    .onFailure { showFailure(getString(R.string.package_install_permission_required)) }
            }.negative(android.R.string.cancel) { finish() }.onCancel { finish() }.show()
    }

    private fun install() {
        if (selected.isEmpty()) { showFailure(getString(R.string.package_invalid_selection)); return }
        lifecycleScope.launch {
            try {
                sessionId = withContext(Dispatchers.IO) { SplitPackageSession.stage(applicationContext, selected) }
                installedPackage = selected.first().manifest.packageName
                val callback = Intent(this@SplitPackageInstallerActivity, SplitPackageInstallerActivity::class.java)
                    .setAction(ACTION_RESULT).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_DIRECTORY, prepared!!.directory.path).putExtra(EXTRA_PACKAGE, installedPackage)
                    .putExtra(EXTRA_SESSION, sessionId)
                // Session.commit fills in its status extras, so this explicit callback must be mutable.
                val options = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic().apply {
                    @Suppress("DEPRECATION")
                    val mode = if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                        else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    setPendingIntentCreatorBackgroundActivityStartMode(mode)
                }.toBundle() else null
                val pending = PendingIntent.getActivity(this@SplitPackageInstallerActivity, sessionId, callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, options)
                @SuppressLint("RequestInstallPackagesPolicy")
                packageManager.packageInstaller.openSession(sessionId).use { it.commit(pending.intentSender) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { showFailure(getString(R.string.package_install_failed, error.message.orEmpty())) }
        }
    }

    private fun handleResult(result: Intent) {
        sessionId = result.getIntExtra(EXTRA_SESSION, -1)
        installedPackage = result.getStringExtra(EXTRA_PACKAGE)
        if (prepared == null) {
            val directory = result.getStringExtra(EXTRA_DIRECTORY)?.let(::File)
            if (directory != null && directory.canonicalFile.parentFile == cacheDir.canonicalFile && directory.name.startsWith("package-install-"))
                prepared = DownloadPackageArchive.Prepared(directory, emptyList())
        }
        val status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (packageInstallOutcome(status, message)) {
            PackageInstallOutcome.CONFIRMATION -> {
                val confirm = IntentCompat.getParcelableExtra(result, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) showFailure(getString(R.string.package_install_failed, ""))
                else runCatching { startActivity(confirm) }.onFailure { showFailure(getString(R.string.package_install_failed, it.message.orEmpty())) }
            }
            PackageInstallOutcome.SUCCESS -> {
                sessionId = -1
                if (obbFiles().isEmpty()) finish() else importObb(null)
            }
            PackageInstallOutcome.ABORTED -> {
                sessionId = -1
                finish()
            }
            PackageInstallOutcome.FAILURE -> {
                sessionId = -1
                showFailure(getString(R.string.package_install_failed, message.orEmpty()))
            }
        }
    }

    private fun obbFiles() = installedPackage?.let { prepared?.obbFiles(it) }.orEmpty()

    private fun importObb(tree: Uri?) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val name = installedPackage ?: throw IOException("Missing package name")
                    if (tree == null) {
                        val directory = File(Environment.getExternalStorageDirectory(), "Android/obb/$name")
                        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("OBB folder is inaccessible")
                        obbFiles().forEach { source -> source.inputStream().use { input -> File(directory, source.name).outputStream().use { copy(input, it) } } }
                    } else {
                        val id = DocumentsContract.getTreeDocumentId(tree)
                        val path = id.substringAfter(':').trimEnd('/')
                        val root = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        val directory = when (path) {
                            "Android/obb/$name" -> root
                            "Android/obb" -> child(tree, root, name, DocumentsContract.Document.MIME_TYPE_DIR)
                            else -> throw IOException(getString(R.string.package_obb_wrong_directory, name))
                        }
                        obbFiles().forEach { source ->
                            val target = child(tree, directory, source.name, "application/octet-stream")
                            source.inputStream().use { input ->
                                contentResolver.openOutputStream(target, "wt")?.use { copy(input, it) } ?: throw IOException("Cannot write OBB")
                            }
                        }
                    }
                }
                finish()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                ViaDialog(this@SplitPackageInstallerActivity).title(R.string.package_obb_title)
                    .message(if (tree == null) getString(R.string.package_obb_permission_required, installedPackage)
                        else getString(R.string.package_obb_import_failed, error.message.orEmpty()))
                    .positive(R.string.grant) { _, _ ->
                        val initial = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Android/obb/$installedPackage")
                        runCatching { obbDirectory.launch(initial) }.onFailure { showFailure(getString(R.string.package_obb_import_failed, it.message.orEmpty())) }
                    }.negative(R.string.package_skip_obb) { finish() }
                    .onCancel { finish() }.show()
            }
        }
    }

    private fun child(tree: Uri, parent: Uri, name: String, mime: String): Uri {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(1) == name)
                return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
        }
        return DocumentsContract.createDocument(contentResolver, parent, mime, name) ?: throw IOException("Cannot create OBB file")
    }

    private suspend fun copy(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) return
            output.write(buffer, 0, count)
        }
    }

    private fun showFailure(message: String) {
        ViaDialog(this).title(R.string.install).message(message).positive(android.R.string.ok).onDismiss { finish() }.show()
    }

    override fun onDestroy() {
        if (isFinishing) {
            if (sessionId >= 0) runCatching { packageManager.packageInstaller.abandonSession(sessionId) }
            prepared?.close()
        }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_RESULT = "dev.ujhhgtg.via.PACKAGE_INSTALL_RESULT"
        private const val EXTRA_DIRECTORY = "install_directory"
        private const val EXTRA_PACKAGE = "install_package"
        private const val EXTRA_SESSION = "install_session"
        fun intent(context: Context, uri: Uri) = Intent(context, SplitPackageInstallerActivity::class.java)
            .setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

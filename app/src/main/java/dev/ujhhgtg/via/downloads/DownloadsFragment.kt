package dev.ujhhgtg.via.downloads

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.ujhhgtg.via.ui.ViaToast
import androidx.activity.OnBackPressedCallback
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.Shell
import dev.ujhhgtg.via.settings.SettingsPageFragment
import dev.ujhhgtg.via.settings.SettingsToolbar
import dev.ujhhgtg.via.tools.PdfViewerFragment
import dev.ujhhgtg.via.ui.SwipeBackLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** mark.via.download.b1 host with original edit-mode back interception and file-opening fallback. */
class DownloadsFragment : SettingsPageFragment() {
    private var screen: DownloadScreen? = null
    private var editingBack: OnBackPressedCallback? = null
    private var pendingDestination: DownloadRecord? = null
    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            BrowserPreferences(requireContext()).downloadDirectory = uri.toString()
            pendingDestination?.let { screen?.resumeRecord(it, prompt = false) }
            pendingDestination = null
        }
    }

    override fun configureToolbar(toolbar: SettingsToolbar) = toolbar.setTitle(R.string.action_downloads)

    override fun createContent(inflater: LayoutInflater, container: ViewGroup?): View = DownloadScreen(requireContext()).apply {
        screen = this
        onOpen = { open(it) }
        onReselectDirectory = { record ->
            pendingDestination = null
            val directory = BrowserPreferences(context).downloadDirectory
            if (directory.startsWith("content://")) ViaDialog(requireActivity())
                .title(R.string.title_permission_denied).message(R.string.message_reselect_download_location)
                .positive(android.R.string.ok) { _, _ ->
                    pendingDestination = record
                    runCatching { directoryPicker.launch(directory.toUri()) }
                }.negative(android.R.string.cancel).show()
        }
        onOpenWith = { record -> open(record, choose = true) }
        onRangeSelectionChanged = { moving -> (view as? SwipeBackLayout)?.setGestureEnabled(!moving && !editing) }
        onEditingChanged = { editing ->
            editingBack?.isEnabled = editing
            (view as? SwipeBackLayout)?.setGestureEnabled(!editing)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editingBack = object : OnBackPressedCallback(screen?.editing == true) {
            override fun handleOnBackPressed() { screen?.finishEditing() }
        }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
    }

    override fun onToolbarBack() { if (screen?.finishEditing() != true) super.onToolbarBack() }
    override fun allowPredictiveBack() = screen?.editing != true

    private fun previewPdf(record: DownloadRecord) {
        val uri = DownloadFiles.uri(requireContext(), record) ?: return
        (activity as? Shell)?.navigate(PdfViewerFragment.newInstance(uri, record.name))
    }

    private fun open(record: DownloadRecord, choose: Boolean = false) {
        val context = requireContext()
        if (!DownloadFiles.exists(context, record)) { ViaToast.makeText(context, R.string.file_does_not_exist, ViaToast.LENGTH_SHORT).show(); return }
        val uri = DownloadFiles.uri(context, record) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val kind = try { withContext(Dispatchers.IO) { DownloadPackageArchive.kind(context.applicationContext, uri) } }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { DownloadPackageArchive.Kind.OTHER }
            if (choose) {
                DownloadOpenWith.show(requireActivity(), record, kind == DownloadPackageArchive.Kind.APK) { previewPdf(record) }
                return@launch
            }
            if (kind == DownloadPackageArchive.Kind.BUNDLE) {
                startActivity(SplitPackageInstallerActivity.intent(context, uri)); return@launch
            }
            val intent = if (kind == DownloadPackageArchive.Kind.APK) Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                else DownloadFiles.openIntent(context, record) ?: return@launch
            if (runCatching { startActivity(intent) }.isSuccess) return@launch
            if (runCatching { startActivity(Intent.createChooser(intent, record.name)) }.isSuccess) return@launch
            if (record.mimeType == "application/pdf") previewPdf(record)
            else ViaToast.makeText(context, R.string.open_file_failed, ViaToast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() { editingBack = null; screen = null; super.onDestroyView() }
}

package dev.ujhhgtg.via.downloads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import dev.ujhhgtg.via.ui.ViaToast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.attachClearTextButton
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.common.LocalNetworkAccess
import dev.ujhhgtg.via.common.ViaIntents
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** mark.via.download.b1, com.tuyafeng.support.widget.b/a0 and layout/z.xml. */
@SuppressLint("ViewConstructor")
class DownloadScreen(context: Context, private val coordinator: DownloadCoordinator = DownloadCoordinator.get(context)) : LinearLayout(context) {
    private val textColor = color(R.attr.viaPrimaryTextColor, Color.BLACK)
    private val secondary = color(R.attr.viaSecondaryTextColor, Color.DKGRAY)
    private val accent = color(R.attr.viaAccentColor, 0xff6f8de1.toInt())
    private val face = BrowserPreferences(context).selectedTypeface()
    private val adapter = TasksAdapter()
    private var previews: DownloadPreviews? = null
    private val qrText = HashMap<Long, String>()
    private val qrJob = SupervisorJob()
    private val qrScope = CoroutineScope(qrJob + Dispatchers.Main.immediate)
    private var qrGeneration = 0
    private var actionDialog: ViaDialog? = null
    private val list = dev.ujhhgtg.via.settings.SettingsRecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = this@DownloadScreen.adapter
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply { supportsChangeAnimations = false }
        addItemDecoration(DownloadDateDecoration(context) { this@DownloadScreen.adapter.records })
    }
    private val empty = TextView(context).apply {
        text = "¯\\_(ツ)_/¯"; contentDescription = context.getString(R.string.empty_hint)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.download_empty_size).toFloat()); gravity = Gravity.CENTER; setTextColor(secondary); typeface = face
    }
    private val filters = RadioGroup(context).apply { orientation = HORIZONTAL; setPaddingRelative(dp(12), dp(2), dp(12), dp(2)) }
    private val bottom = object : LinearLayout(context) {
        private val line = Paint().apply { color = 0x30808080 }
        init { setWillNotDraw(false); orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawRect(0f, 0f, width.toFloat(), 2f, line) }
    }
    private var query = ""
    private var filter = 0
    /** b1's checked-id switch: All, Audio, Video, APK, Archives, Documents, Images, Others. */
    private val filterCategories = mapOf(
        R.id.download_filter_all to 0, R.id.download_filter_audio to 1, R.id.download_filter_video to 2,
        R.id.download_filter_apk to 3, R.id.download_filter_archives to 5, R.id.download_filter_documents to 6,
        R.id.download_filter_images to 7, R.id.download_filter_others to 8,
    )
    var editing = false
        private set
    private val selectedIds = linkedSetOf<Long>()
    private val changeListener: (DownloadRecord) -> Unit = { refresh() }
    var onOpen: ((DownloadRecord) -> Unit)? = null
    var onOpenWith: ((DownloadRecord) -> Unit)? = null
    var onNew: (() -> Unit)? = null
    var onReselectDirectory: ((DownloadRecord) -> Unit)? = null
    var onEditingChanged: ((Boolean) -> Unit)? = null

    private val range = dev.ujhhgtg.via.settings.SettingsRangeSelection(context,
        selectable = { editing && it in adapter.records.indices },
        checked = { adapter.records.getOrNull(it)?.id in selectedIds },
        update = { index, checked -> adapter.records.getOrNull(index)?.let { if (checked) selectedIds.add(it.id) else selectedIds.remove(it.id); adapter.notifyItemChanged(index); updateBottom(adapter.records) } },
        changed = { moving -> onRangeSelectionChanged?.invoke(moving) })
    var onRangeSelectionChanged: ((Boolean) -> Unit)? = null

    init {
        orientation = VERTICAL
        list.addOnItemTouchListener(range)
        val search = EditText(context).apply {
            setSingleLine(); hint = context.getString(R.string.search_hint)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat()); typeface = face
            if (android.os.Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
            inputType = 1; imeOptions = 6
            setTextColor(textColor); setHintTextColor(color(R.attr.viaDisabledTextColor, Color.GRAY))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { setColor(0x20808080); cornerRadius = dp(18).toFloat() }
            compoundDrawablePadding = dp(12)
            attachClearTextButton(color(R.attr.viaSubtleColor, Color.GRAY))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty().trim(); selectedIds.clear(); refresh() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        addView(search, LayoutParams(-1, -2).apply { setMargins(dp(14), dp(10), dp(14), dp(4)) })
        // b1.v3 order is All, Documents, Archives, APK, Images, Video, Audio, Others.
        listOf(R.id.download_filter_all to R.string.download_filter_all, R.id.download_filter_documents to R.string.download_filter_documents,
            R.id.download_filter_archives to R.string.download_filter_archives, R.id.download_filter_apk to R.string.download_filter_apk,
            R.id.download_filter_images to R.string.download_filter_images, R.id.download_filter_video to R.string.download_filter_video,
            R.id.download_filter_audio to R.string.download_filter_audio, R.id.download_filter_others to R.string.download_filter_others).forEach { (filterId, label) ->
            filters.addView(RadioButton(context).apply {
                id = filterId; text = context.getString(label); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.download_small_size).toFloat()); typeface = face
                buttonDrawable = null; isSingleLine = true; minWidth = dp(48); minHeight = dp(28)
                minimumHeight = dp(28); minimumWidth = dp(48); gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
                setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, textColor)))
                background = filterBackground()
            }, RadioGroup.LayoutParams(-2, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        filters.check(R.id.download_filter_all)
        filters.setOnCheckedChangeListener { _, id ->
            // b1's listener keeps the current category for an unknown id and only reloads on a change.
            val category = filterCategories[id] ?: filter
            if (category != filter) { filter = category; selectedIds.clear(); refresh() }
        }
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(filters) }, LayoutParams(-1, dp(36)))
        addView(FrameLayout(context).apply {
            addView(list, FrameLayout.LayoutParams(-1, -1)); addView(empty, FrameLayout.LayoutParams(-1, -1))
        }, LayoutParams(-1, 0, 1f))
        addView(bottom, LayoutParams(-1, dp(48)))
        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        previews = DownloadPreviews(context) { id ->
            val position = adapter.records.indexOfFirst { it.id == id }
            if (position >= 0) adapter.notifyItemChanged(position, 2)
        }
        coordinator.addListener(changeListener); refresh()
        val loader = previews
        list.post {
            // b1.d4/t3 enables previews after attachment, then rebinds only the
            // icons of visible rows rather than reconstructing the whole list.
            if (loader != null && previews === loader && list.isAttachedToWindow) {
                loader.enabled = true
                val manager = list.layoutManager as? LinearLayoutManager
                val first = manager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                val last = manager?.findLastVisibleItemPosition() ?: RecyclerView.NO_POSITION
                if (first != RecyclerView.NO_POSITION && last >= first) adapter.notifyItemRangeChanged(first, last - first + 1, 2)
            }
        }
    }
    override fun onDetachedFromWindow() { range.finish(); coordinator.removeListener(changeListener); previews?.close(); previews = null; qrJob.cancelChildren(); qrText.clear(); actionDialog = null; super.onDetachedFromWindow() }

    fun finishEditing(): Boolean {
        if (!editing) return false
        range.finish()
        editing = false; selectedIds.clear(); onEditingChanged?.invoke(false); refresh(); return true
    }

    fun refresh() {
        val all = coordinator.list()
        val records = all.filter { (filter == 0 || DownloadPresentation.category(it) == filter) && (query.isEmpty() || it.name.contains(query)) }
        selectedIds.retainAll(records.map { it.id }.toSet())
        if (editing && records.isEmpty()) { range.finish(); editing = false; onEditingChanged?.invoke(false) }
        empty.visibility = if (records.isEmpty()) VISIBLE else GONE
        list.visibility = if (records.isEmpty()) GONE else VISIBLE
        filters.visibility = if (records.isEmpty() && filter == 0) GONE else VISIBLE
        adapter.records = records
        adapter.notifyDataSetChanged()
        list.invalidateItemDecorations()
        updateBottom(records)
    }

    private inner class TasksAdapter : RecyclerView.Adapter<TaskHolder>() {
        var records = emptyList<DownloadRecord>()
        init { setHasStableIds(true) }
        override fun getItemCount() = records.size
        override fun getItemId(position: Int) = records[position].id
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TaskHolder(LayoutInflater.from(context).inflate(R.layout.download_task, parent, false))
        override fun onBindViewHolder(holder: TaskHolder, position: Int) = holder.bind(records[position])
        override fun onBindViewHolder(holder: TaskHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) { holder.bind(records[position]); return }
            // DownloadTaskViewDelegate.D uses these exact OR comparisons in smali.
            val mask = payloads.filterIsInstance<Int>().fold(0) { flags, payload -> flags or payload }
            if (mask == 0) return
            val record = records[position]
            if (mask or 1 == 1 && record.isActive) holder.bindProgress(record)
            if (mask or 2 == 2) holder.bindIcon(record)
        }
    }

    private inner class TaskHolder(row: View) : RecyclerView.ViewHolder(row) {
        private val icon = row.findViewById<ImageView>(R.id.download_icon)
        private val name = row.findViewById<TextView>(R.id.download_name).apply { typeface = face }
        private val detail = row.findViewById<TextView>(R.id.download_detail).apply { typeface = face }
        private val progress = row.findViewById<ProgressBar>(R.id.download_progress).apply {
            progressDrawable = LayerDrawable(arrayOf(
                GradientDrawable().apply { setColor(0x30808080); cornerRadius = dp(2).toFloat() },
                ClipDrawable(GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(color(R.attr.viaDarkAccentColor, accent), accent)).apply { cornerRadius = dp(2).toFloat() }, Gravity.START, ClipDrawable.HORIZONTAL)
            )).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
        }
        private val toggle = row.findViewById<ImageView>(R.id.download_toggle)
        private val selected = row.findViewById<CheckBox>(R.id.download_selected)
        fun bindIcon(record: DownloadRecord) {
            val preview = previews?.icon(record)
            if (preview != null) { icon.clearColorFilter(); icon.setImageDrawable(preview) }
            else { icon.setImageResource(downloadIcon(record)); icon.setColorFilter(textColor) }
        }
        fun bindProgress(record: DownloadRecord) {
            detail.text = DownloadPresentation.detail(context, record, coordinator.speed(record.id))
            progress.progress = if (record.totalSize > 0) (record.downloadedSize * 1000 / record.totalSize).toInt() else 1000
        }
        fun bind(record: DownloadRecord) {
            bindIcon(record)
            name.text = record.name.ifEmpty { record.url.orEmpty() }
            bindProgress(record)
            progress.visibility = if (record.isActive) VISIBLE else GONE
            toggle.visibility = if (editing || record.isComplete) GONE else VISIBLE
            toggle.setImageResource(if (record.isActive) R.drawable.pause_circle else R.drawable.download_circle)
            toggle.contentDescription = context.getString(if (record.isActive) R.string.download_action_pause else R.string.download_action_resume)
            toggle.setOnClickListener { if (record.isActive) DownloadService.start(context, DownloadService.ACTION_PAUSE, record.id) else resumeRecord(record) }
            selected.visibility = if (editing) VISIBLE else GONE
            selected.isChecked = record.id in selectedIds
            itemView.isSelected = selected.isChecked
            // DownloadTaskViewDelegate.C: x.r.f0 (state description) is "Selected"/"Not selected" while editing, cleared otherwise.
            ViewCompat.setStateDescription(itemView, if (!editing) null
                else context.getString(if (selected.isChecked) R.string.accessibility_selected else R.string.accessibility_not_selected))
            itemView.setOnClickListener {
                if (editing) { if (!selectedIds.add(record.id)) selectedIds.remove(record.id); refresh() }
                else if (record.isComplete) onOpen?.invoke(record)
            }
            itemView.setOnLongClickListener { if (editing) range.start(bindingAdapterPosition) else { showActions(record, it); true } }
        }
    }

    private fun updateBottom(visible: List<DownloadRecord>) {
        bottom.removeAllViews()
        if (editing) {
            val allSelected = visible.isNotEmpty() && selectedIds.size == visible.size
            bottom.addView(action(context.getString(if (allSelected) R.string.cancel_all else R.string.select_all)) {
                if (allSelected) selectedIds.clear() else selectedIds.addAll(visible.map { it.id }); refresh()
            })
            bottom.addView(action(if (selectedIds.isEmpty()) context.getString(R.string.action_delete) else context.getString(R.string.delete_hint, selectedIds.size)) {
                confirmDelete(visible.filter { it.id in selectedIds }, true)
            }.apply { isEnabled = selectedIds.isNotEmpty(); if (isEnabled) setTextColor(0xffd64146.toInt()) })
            bottom.addView(View(context), LayoutParams(0, 1, 1f))
            bottom.addView(action(context.getString(R.string.done)) { finishEditing() })
        } else {
            bottom.addView(action(context.getString(R.string.action_new)) { onNew?.invoke() ?: (context as? Activity)?.let { DownloadConfirmation.showManual(it, coordinator) { request ->
                resumeRecord(DownloadRecord(name = request.fileName.orEmpty(), url = request.url, mimeType = request.mimeType, path = request.directory, chunks = 8, flags = 1))
            } } })
            bottom.addView(View(context), LayoutParams(0, 1, 1f))
            bottom.addView(action(context.getString(R.string.action_edit)) { editing = true; onEditingChanged?.invoke(true); refresh() }.apply { isEnabled = visible.isNotEmpty() })
        }
    }

    private fun showActions(record: DownloadRecord, anchor: View) {
        val activity = context as? Activity ?: return
        val actions = mutableListOf<Pair<Int, () -> Unit>>()
        if (record.isComplete) {
            actions += R.string.open_with to { onOpenWith?.invoke(record) }
            actions += R.string.rename to { rename(record) }
        }
        if (!record.isActive && !record.url.isNullOrEmpty()) actions += R.string.action_re_download to {
            ViaDialog(activity).title(R.string.action_re_download).message(activity.getString(R.string.message_re_download, record.name))
                .positive(android.R.string.ok) { _, _ -> redownloadRecord(record) }.negative(android.R.string.cancel).show()
        }
        // b1.b4 -> g6.n.b: an unlabeled plain-text clip.
        if (!record.url.isNullOrEmpty()) actions += R.string.action_copy_download_link to {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(null, record.url))
            ViaToast.makeText(context, R.string.toast_copy_url_successful, ViaToast.LENGTH_SHORT).show()
        }
        qrText[record.id]?.takeIf(String::isNotEmpty)?.let { actions += R.string.scan_qr_code to { showQr(record, it) } }
        val qrPosition = actions.size
        actions += R.string.action_share to { share(record) }
        actions += R.string.action_delete to { confirmDelete(listOf(record), false) }
        fun rows() = actions.mapIndexed { index, pair -> ViaDialog.Item(index, context.getString(pair.first)) }
        val dialog = ViaDialog(activity).items(rows(), onClick = { actions[it].second() })
        actionDialog = dialog
        dialog.showAnchored(anchor)
        if (qrText[record.id] == null && record.isComplete && record.totalSize <= 204800 && record.mimeType?.startsWith("image/") == true) {
            val generation = ++qrGeneration
            val app = context.applicationContext
            qrScope.launch {
                try {
                    val decoded = withContext(Dispatchers.IO) {
                        val bitmap = record.fileUri?.let { uri -> app.contentResolver.openInputStream(uri)?.use(android.graphics.BitmapFactory::decodeStream) }
                        if (bitmap == null) "" else try {
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            dev.ujhhgtg.via.tools.QrCodec.decodeImage(bitmap.width, bitmap.height, pixels).orEmpty()
                        } finally { if (!bitmap.isRecycled) bitmap.recycle() }
                    }
                    qrText[record.id] = decoded
                    if (decoded.isNotEmpty() && generation == qrGeneration && actionDialog === dialog && dialog.isShowing()) {
                        actions.add(qrPosition, R.string.scan_qr_code to { showQr(record, decoded) })
                        dialog.replaceItems(rows())
                    }
                } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) { android.util.Log.w("ViaDownloads", "Image QR decode failed", error) }
            }
        }
    }

    /** b1.w4/e3/I3: original decoded text dialog and explicit browser Intent. */
    private fun showQr(record: DownloadRecord, decoded: String) {
        val activity = context as? Activity ?: return
        val dialog = ViaDialog(activity).title(record.name).message(decoded).positive(android.R.string.ok)
        if (android.webkit.URLUtil.isNetworkUrl(decoded)) dialog.neutral(R.string.open) {
            context.startActivity(Intent(Intent.ACTION_VIEW, decoded.toUri())
                .setClass(context, dev.ujhhgtg.via.Shell::class.java).putExtra(ViaIntents.EXTRA_INTENT, ViaIntents.MARKER_BROWSER))
        } else dialog.neutral(R.string.action_copy_text) {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(null, decoded))
            ViaToast.show(context, R.string.toast_copy_text_successful)
        }
        dialog.show()
    }

    /** b1.a3/F3/t4: destination preparation precedes service commands and survives folder re-selection. */
    fun resumeRecord(record: DownloadRecord, redownload: Boolean = false, prompt: Boolean = true) {
        LocalNetworkAccess.check(context, record.url.orEmpty()) { allowed ->
            if (!isAttachedToWindow) return@check
            if (allowed) resumePermitted(record, redownload, prompt) else ViaToast.show(context, R.string.title_permission_denied)
        }
    }

    private fun resumePermitted(record: DownloadRecord, redownload: Boolean, prompt: Boolean) {
        val prepared = DownloadFiles.prepare(context, record, BrowserPreferences(context).downloadDirectory)
        when (prepared.status) {
            2 -> { ViaToast.show(context, R.string.download_failed); return }
            3 -> { if (prompt) onReselectDirectory?.invoke(record); return }
        }
        if (record.id == 0L) {
            val ready = prepared.record
            runCatching { coordinator.enqueue(DownloadRequest(ready.url.orEmpty(), fileName = ready.name,
                mimeType = ready.mimeType, path = ready.path, fileUri = ready.fileUri, directory = ready.path)) }
                .onFailure { ViaToast.show(context, R.string.download_failed) }
        } else {
            if (prepared.status == 1) coordinator.saveDestination(prepared.record)
            DownloadService.start(context, if (redownload) DownloadService.ACTION_REDOWNLOAD else DownloadService.ACTION_RESUME, record.id)
        }
    }

    private fun redownloadRecord(record: DownloadRecord) {
        LocalNetworkAccess.check(context, record.url.orEmpty()) { allowed ->
            if (!isAttachedToWindow) return@check
            if (allowed) redownloadPermitted(record) else ViaToast.show(context, R.string.title_permission_denied)
        }
    }

    private fun redownloadPermitted(record: DownloadRecord) {
        var retry = record
        if (DownloadFiles.exists(context, record) && !DownloadFiles.remove(context, record)) {
            val current = BrowserPreferences(context).downloadDirectory
            if (current == record.path) return
            retry = record.copy(path = current, fileUri = null)
        }
        resumePermitted(retry, redownload = true, prompt = true)
    }

    private fun confirmDelete(records: List<DownloadRecord>, multiple: Boolean) {
        if (records.isEmpty()) return
        val activity = context as? Activity ?: return
        val dialog = ViaDialog(activity).title(R.string.action_delete)
        if (multiple) dialog.message(context.getString(R.string.message_delete_selected_download_tasks, records.size))
        else dialog.message(if (records[0].isComplete) R.string.message_delete_downloaded_task else R.string.message_delete_downloading_task)
        if (multiple || records[0].isComplete) dialog.check(R.string.message_delete_downloaded_file, false)
        dialog.negative(android.R.string.cancel).positive(android.R.string.ok) { _, result ->
            records.forEach { record ->
                if (coordinator.delete(record.id, result.checked || !record.isComplete)) {
                    // b1.c4 -> K3 waits 500ms, even after the Service has stopped.
                    list.postDelayed({ DownloadService.cancelTaskNotification(context, record.id) }, 500L)
                }
            }
            if (multiple) finishEditing() else refresh()
        }.show()
    }

    private fun rename(record: DownloadRecord) {
        val activity = context as? Activity ?: return
        val dialog = ViaDialog(activity).title(R.string.rename).input(record.name, record.name, 1, 1)
            .positive(android.R.string.ok) { _, result ->
                val name = result.edit?.firstOrNull()?.trim().orEmpty()
                if (name.isNotEmpty() && !record.name.equals(name, true)) runCatching { coordinator.rename(record.id, name) }
                    .onFailure { ViaToast.makeText(context, R.string.download_failed, ViaToast.LENGTH_SHORT).show() }
            }.negative(android.R.string.cancel)
        dialog.edit(1)?.setOnFocusChangeListener { view, focused -> if (focused) DownloadConfirmation.selectBaseName(view as EditText) }
        dialog.show()
    }

    private fun share(record: DownloadRecord) {
        val uri = DownloadFiles.uri(context, record)
        val fileIntent = uri?.takeIf { DownloadFiles.exists(context, record) }?.let {
            Intent(Intent.ACTION_SEND).setType(record.mimeType ?: "*/*").putExtra(Intent.EXTRA_STREAM, it)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri(record.name, it) }
        }
        val intent = fileIntent ?: record.url?.takeIf { it.isNotEmpty() }?.let { Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, it) } ?: return
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }

    private fun filterBackground(): StateListDrawable {
        fun shape(stroke: Int? = null, fill: Int? = null, width: Int = 1) = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat(); stroke?.let { setStroke(dp(width), it) }; fill?.let { setColor(it) }
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(fill = 0x30808080))
            addState(intArrayOf(android.R.attr.state_checked), shape(stroke = accent))
            addState(intArrayOf(android.R.attr.state_focused), shape(stroke = 0x60808080, width = 3))
            addState(intArrayOf(), shape(stroke = 0x30808080))
        }
    }
    private fun downloadIcon(record: DownloadRecord): Int = when {
        record.isAndroidPackage -> R.drawable.robot
        record.mimeType?.startsWith("audio/") == true -> R.drawable.music_note
        record.mimeType?.startsWith("video/") == true -> R.drawable.play_boxed
        record.mimeType?.startsWith("image/") == true -> R.drawable.image_frame
        record.mimeType?.startsWith("text/") == true || record.mimeType?.contains("document") == true || record.mimeType == "application/pdf" -> R.drawable.document
        record.mimeType?.contains("zip") == true || record.mimeType?.contains("compress") == true -> R.drawable.mouse
        else -> R.drawable.page
    }
    private fun action(title: String, clicked: () -> Unit) = TextView(context).apply {
        text = title; gravity = Gravity.CENTER; typeface = face
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf((textColor and 0xffffff) or 0x80000000.toInt(), textColor)))
        setPadding(dp(16), 0, dp(16), 0); minHeight = dp(48); isClickable = true; isFocusable = true
        background = ContextCompat.getDrawable(context, R.drawable.rounded_rect_ripple); setOnClickListener { clicked() }
    }
    private fun color(attribute: Int, fallback: Int) = settingsColor(context, attribute, fallback)
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}

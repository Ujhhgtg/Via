package dev.ujhhgtg.via.downloads

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.ujhhgtg.via.ui.ViaToast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dialog.ViaDialog

/** mark.via.download.p1/r1/w1: Via's application grid and built-in PDF preview entry. */
internal object DownloadOpenWith {
    private data class Option(val title: CharSequence, val icon: Drawable?, val open: () -> Unit)

    fun show(activity: Activity, record: DownloadRecord, detectedApk: Boolean = false, previewPdf: () -> Unit) {
        if (!DownloadFiles.exists(activity, record)) { ViaToast.makeText(activity, R.string.file_does_not_exist, ViaToast.LENGTH_SHORT).show(); return }
        val uri = DownloadFiles.uri(activity, record) ?: return
        val mime = if (detectedApk) "application/vnd.android.package-archive" else DownloadFiles.resolvedMime(record).takeIf { it != "application/octet-stream" }
            ?: activity.contentResolver.getType(uri)?.takeIf { it != "application/octet-stream" }
            ?: DownloadFiles.resolvedMime(record)
        fun dp(value: Int) = (activity.resources.displayMetrics.density * value + .5f).toInt()
        val options = mutableListOf<Option>()
        if (mime == "application/pdf") {
            val icon = ContextCompat.getDrawable(activity, R.drawable.document)?.mutate()?.apply { setTint(settingsColor(activity, R.attr.viaPrimaryTextColor, 0xff444444.toInt())) }
            options += Option(activity.getString(R.string.open_option_pdf_preview), icon?.let { InsetDrawable(it, dp(4)) }, previewPdf)
        }
        val intent = Intent(if (mime == "application/vnd.android.package-archive") Intent.ACTION_INSTALL_PACKAGE else Intent.ACTION_VIEW)
            .setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val manager = activity.packageManager
        @Suppress("DEPRECATION")
        val matches = manager.queryIntentActivities(intent, 0).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.loadLabel(manager).toString() })
        matches.forEach { match ->
            val info = match.activityInfo ?: return@forEach
            val explicit = Intent(intent).setComponent(ComponentName(info.packageName, info.name))
            options += Option(match.loadLabel(manager), match.loadIcon(manager)) {
                runCatching { activity.startActivity(explicit) }.onFailure { ViaToast.makeText(activity, R.string.open_file_failed, ViaToast.LENGTH_SHORT).show() }
            }
        }
        val dialog = ViaDialog(activity).title(R.string.open_with).negative(android.R.string.cancel)
        // i.xml / p1.m3: the empty state is the layout's own "No available ways" text, not the shrug.
        if (options.isEmpty()) dialog.customView(TextView(activity).apply {
            setText(R.string.open_file_no_available_options)
            setTextAppearance(R.style.Via_Dialog_SecondaryText); gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }) else {
            val available = minOf(activity.window.decorView.width, activity.window.decorView.height) - dp(72)
            val columns = maxOf(1, available / dp(95))
            dialog.customView(RecyclerView(activity).apply {
                layoutManager = GridLayoutManager(activity, columns)
                setPadding(dp(8), 0, dp(8), 0); clipToPadding = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addItemDecoration(object : RecyclerView.ItemDecoration() {
                    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) { outRect.set(dp(4), dp(4), dp(4), dp(4)) }
                })
                adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun getItemCount() = options.size
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = object : RecyclerView.ViewHolder(LayoutInflater.from(activity).inflate(R.layout.download_open_option, parent, false)) {}
                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                        val option = options[position]
                        holder.itemView.findViewById<ImageView>(R.id.download_open_icon).setImageDrawable(option.icon)
                        holder.itemView.findViewById<TextView>(R.id.download_open_title).apply { text = option.title; typeface = BrowserPreferences(activity).selectedTypeface() }
                        holder.itemView.setOnClickListener { dialog.dismiss(); option.open() }
                    }
                }
            })
        }
        dialog.show()
    }
}

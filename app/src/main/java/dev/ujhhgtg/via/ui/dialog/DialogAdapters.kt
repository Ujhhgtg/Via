package dev.ujhhgtg.via.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor

/** w5.c, including original arbitrary item IDs and accent/center options. */
internal class DialogItemsAdapter(private val context: Context, var items: List<ViaDialog.Item>) : BaseAdapter() {
    var accent = false
    var centered = false
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].id.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val text = (convertView ?: LayoutInflater.from(context).inflate(R.layout.via_dialog_item, parent, false)) as TextView
        if (convertView == null) {
            text.setTextColor(settingsColor(context, if (accent) R.attr.viaAccentColor else R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
            if (centered) text.gravity = android.view.Gravity.CENTER
            text.typeface = BrowserPreferences(context).selectedTypeface()
        }
        text.text = getItem(position).text
        return text
    }
}

/** w5.d and w5.b: selected rows are held by adapter, not ListView choice mode. */
internal class DialogChoiceAdapter(
    private val labels: List<String>, selected: IntArray?, val multiple: Boolean, val highlightOnly: Boolean = false,
) : BaseAdapter() {
    private var selectedItem = selected?.firstOrNull() ?: -1
    private val selectedItems = selected?.toMutableSet() ?: mutableSetOf()
    fun selected() = if (multiple) selectedItems.sorted().toIntArray() else intArrayOf(selectedItem)
    override fun getCount() = labels.size
    override fun getItem(position: Int) = labels[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context).inflate(
            if (highlightOnly) R.layout.via_dialog_item else R.layout.via_dialog_choice, parent, false)
        val text = if (highlightOnly) view as TextView else view.findViewById<CheckBox>(R.id.dialog_choice)
        if (convertView == null) {
            text.typeface = BrowserPreferences(parent.context).selectedTypeface()
            if (highlightOnly) text.gravity = android.view.Gravity.CENTER
        }
        text.isEnabled = false; text.text = labels[position]
        if (!highlightOnly && '\n' in labels[position]) {
            text.setSingleLine(false)
            text.ellipsize = null
        }
        val checked = if (multiple) position in selectedItems else position == selectedItem
        if (text is CheckBox) text.isChecked = checked else text.setBackgroundColor(if (checked) 0x30808080 else 0)
        text.isFocusable = false; text.isClickable = false
        return view
    }

    fun bind(list: ListView, onClick: ((Int) -> Unit)?, onLongClick: ((Int) -> Boolean)?) {
        list.adapter = this
        list.setOnItemClickListener { _, _, position, _ ->
            if (multiple) {
                if (!selectedItems.remove(position)) selectedItems.add(position)
                updateVisible(list, position)
            } else if (position != selectedItem) {
                val previous = selectedItem
                selectedItem = position
                updateVisible(list, previous); updateVisible(list, position)
            }
            onClick?.invoke(position)
        }
        list.setOnItemLongClickListener { _, _, position, _ -> onLongClick?.invoke(position) ?: false }
    }
    private fun updateVisible(list: ListView, position: Int) {
        if (position in list.firstVisiblePosition..list.lastVisiblePosition) {
            val view = list.getChildAt(position - list.firstVisiblePosition)
            getView(position, view, list)
        }
    }
}

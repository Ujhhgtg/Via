package dev.ujhhgtg.via.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dp
import dev.ujhhgtg.via.skins.setSkinImageResource

internal fun settingsColor(context: Context, attribute: Int, fallback: Int): Int {
    val attributes = context.obtainStyledAttributes(intArrayOf(attribute))
    return try { attributes.getColor(0, fallback) } finally { attributes.recycle() }
}

/**
 * x7.o.f13685f (drawable p, square mask) for full-width list rows; buttons
 * use [controlRipple], whose drawable o mask is the 18dp rounded rectangle.
 */
internal fun settingsRipple(context: Context): Drawable? = ContextCompat.getDrawable(context, R.drawable.flat_ripple)

/** x7.o.f13682e (drawable o): the rounded-rectangle ripple for buttons. */
internal fun controlRipple(context: Context): Drawable? = ContextCompat.getDrawable(context, R.drawable.rounded_rect_ripple)

/** a6.o / ib.r: a platform CheckBox with Via's original choice indicator. */
internal fun settingsCheckBox(context: Context, label: CharSequence, checked: Boolean = false,
    enabled: Boolean = true, changed: ((Boolean) -> Unit)? = null): android.widget.CheckBox =
    android.widget.CheckBox(context).apply {
        text = label; isChecked = checked; isEnabled = enabled; isClickable = changed != null
        setPadding(context.dp(16f), context.dp(12f), context.dp(16f), context.dp(12f))
        context.obtainStyledAttributes(intArrayOf(R.attr.viaChoiceIndicator)).let { attrs ->
            try { buttonDrawable = attrs.getDrawable(0) } finally { attrs.recycle() }
        }
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
        textSize = resources.getDimensionPixelSize(R.dimen.settings_row_title_size) / resources.displayMetrics.scaledDensity
        typeface = BrowserPreferences(context).selectedTypeface()
        background = settingsRipple(context)
        changed?.let { setOnCheckedChangeListener { _, value -> it(value) } }
    }

/** com.tuyafeng.support.widget.z: title, navigation control, spacer and two-pixel divider. */
@SuppressLint("ViewConstructor")
class SettingsToolbar(context: Context, onBack: () -> Unit) : LinearLayout(context) {
    val titleView = TextView(context)
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x30808080
        style = Paint.Style.FILL
    }

    init {
        orientation = HORIZONTAL
        setWillNotDraw(false)
        addView(ImageView(context).apply {
            setSkinImageResource(R.drawable.chevron_left)
            setColorFilter(settingsColor(context, R.attr.viaSubtleColor, Color.BLACK))
            contentDescription = context.getString(R.string.navigation_up)
            setPadding(context.dp(13f), 0, context.dp(13f), 0)
            background = controlRipple(context)
            setOnClickListener { onBack() }
        }, LayoutParams(context.dp(48f), -1).apply {
            val margin = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
            setMargins(margin, margin, margin, margin)
        })
        titleView.apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_toolbar_title_size).toFloat())
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
            setSingleLine(); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            textDirection = TEXT_DIRECTION_LOCALE
            typeface = Typeface.create(BrowserPreferences(context).selectedTypeface(), Typeface.BOLD)
        }
        addView(titleView, LayoutParams(0, -2, 24f).apply { gravity = Gravity.CENTER_VERTICAL })
        addView(Space(context), LayoutParams(0, context.dp(54f), 1f))
    }

    fun addAction(icon: Int?, label: Int, clicked: (View) -> Unit): View {
        val view = if (icon != null) ImageView(context).apply {
            setSkinImageResource(icon); setColorFilter(settingsColor(context, R.attr.viaSubtleColor, Color.BLACK))
            contentDescription = context.getString(label)
        } else TextView(context).apply {
            setText(label)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
            setSingleLine(); maxLines = 1; setLines(1); ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER; isAllCaps = true; typeface = BrowserPreferences(context).selectedTypeface()
        }
        view.setPadding(context.dp(13f), 0, context.dp(13f), 0)
        view.background = controlRipple(context)
        view.setOnClickListener(clicked)
        addView(view, LayoutParams(if (icon == null) -2 else context.dp(48f), -1).apply {
            val margin = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
            setMargins(margin, margin, margin, margin)
        })
        return view
    }

    fun setTitle(resource: Int) = titleView.setText(resource)
    fun setTitle(title: CharSequence?) { titleView.text = title; titleView.visibility = if (title == null) GONE else VISIBLE
    }
    fun setContentColor(color: Int) {
        for (i in 0 until childCount) when (val child = getChildAt(i)) {
            is TextView -> child.setTextColor(color)
            is ImageView -> child.setColorFilter(color)
        }
    }
    fun setDividerColor(color: Int) { if (divider.color != color) { divider.color = color; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, measuredHeight - 2f, measuredWidth.toFloat(), measuredHeight.toFloat(), divider)
    }
}

/** a6.d/q, preserving identity separately from content for a6.k's DiffUtil contract. */
open class SettingsRow(
    val id: Int,
    val title: String,
    open val summary: String? = null,
    val disabled: Boolean = false,
    val highlighted: Boolean = false,
) {
    override fun equals(other: Any?): Boolean = other is SettingsRow && javaClass == other.javaClass &&
        id == other.id && title == other.title && summary == other.summary && disabled == other.disabled && highlighted == other.highlighted
    override fun hashCode(): Int = (((31 * (31 * title.hashCode() + id) + summary.hashCode()) * 31 + disabled.hashCode()) * 31 + highlighted.hashCode())
}

/** a6.n, including descriptions selected by the checkbox state. */
open class SettingsToggleRow(
    id: Int, title: String, summary: String? = null, val checked: Boolean,
    disabled: Boolean = false, val checkedSummary: String? = null, val uncheckedSummary: String? = null,
    val titleEllipsize: TextUtils.TruncateAt = TextUtils.TruncateAt.END,
) : SettingsRow(id, title, summary, disabled) {
    override val summary: String? = summary ?: if (checked) checkedSummary else uncheckedSummary
    override fun equals(other: Any?) = super.equals(other) && other is SettingsToggleRow && checked == other.checked && titleEllipsize == other.titleEllipsize
    override fun hashCode() = (super.hashCode() * 31 + checked.hashCode()) * 31 + titleEllipsize.hashCode()
}

/** a6.g and a6.l, keeping distinct adapter identities like the original. */
class SettingsChoiceRow(id: Int, title: String, checked: Boolean, summary: String? = null) : SettingsToggleRow(id, title, summary, checked)
class SettingsHeadingRow(title: String) : SettingsRow(0, title)
/** A row whose title wraps to its full length instead of ending in an ellipsis. */
class SettingsTextRow(id: Int, text: String) : SettingsRow(id, text)
class SettingsEmptyRow : SettingsRow(Int.MIN_VALUE, "")
class ReaderColorRow(val color: Int) : SettingsRow(0, "") {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/** a6.r/s: the actual vertical two-text settings row; summaries disappear when empty. */
private class SettingsRowView(context: Context) : LinearLayout(context) {
    val title = TextView(context).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat()); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        textDirection = TEXT_DIRECTION_LOCALE
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
        typeface = BrowserPreferences(context).selectedTypeface()
    }
    val summary = TextView(context).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat()); maxLines = 3; ellipsize = TextUtils.TruncateAt.END
        textDirection = TEXT_DIRECTION_LOCALE
        setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, Color.GRAY))
        typeface = BrowserPreferences(context).selectedTypeface()
    }

    init {
        orientation = VERTICAL
        setPadding(context.dp(16f), context.dp(20f), context.dp(16f), context.dp(20f))
        background = settingsRipple(context)
        addView(title, LayoutParams(-1, -2))
        addView(summary, LayoutParams(-2, -2))
    }

    fun bind(item: SettingsRow) {
        // Recycled views switch between single-line titles and full-length text rows.
        val wrap = item is SettingsTextRow
        title.maxLines = if (wrap) Int.MAX_VALUE else 1
        title.ellipsize = if (wrap) null else TextUtils.TruncateAt.END
        title.text = item.title
        summary.text = item.summary
        summary.visibility = if (item.summary.isNullOrEmpty()) GONE else VISIBLE
        title.alpha = if (item.disabled) .5f else 1f
        summary.alpha = title.alpha
        isEnabled = !item.disabled
        background = if (item.highlighted) 0x30808080.toDrawable() else settingsRipple(context)
    }
}

/** a6.o/p, including the original non-interactive 20dp checkbox on a clickable row. */
@SuppressLint("ViewConstructor")
private class SettingsToggleView(context: Context, private val leading: Boolean = false) : android.widget.RelativeLayout(context) {
    val title = TextView(context).apply {
        id = generateViewId(); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat()); setSingleLine(); maxLines = 1
        ellipsize = TextUtils.TruncateAt.END; textDirection = TEXT_DIRECTION_LOCALE
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, Color.BLACK))
        typeface = BrowserPreferences(context).selectedTypeface()
    }
    private val summary = TextView(context).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat()); maxLines = 3; ellipsize = TextUtils.TruncateAt.END
        textDirection = TEXT_DIRECTION_LOCALE
        setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, Color.GRAY))
        typeface = BrowserPreferences(context).selectedTypeface()
    }
    private val checkBox = android.widget.CheckBox(context).apply {
        id = generateViewId(); isClickable = false; isFocusable = false
        val attrs = context.obtainStyledAttributes(intArrayOf(R.attr.viaChoiceIndicator))
        try { buttonDrawable = attrs.getDrawable(0) } finally { attrs.recycle() }
    }
    init {
        setPadding(context.dp(16f), context.dp(20f), context.dp(16f), context.dp(20f))
        background = settingsRipple(context)
        if (leading) summary.typeface = Typeface.DEFAULT
        addView(title, LayoutParams(-1, -2).apply {
            addRule(if (leading) ALIGN_PARENT_END else ALIGN_PARENT_START)
            addRule(if (leading) END_OF else START_OF, checkBox.id)
        })
        addView(summary, LayoutParams(-1, -2).apply {
            addRule(if (leading) ALIGN_PARENT_END else ALIGN_PARENT_START)
            addRule(if (leading) END_OF else START_OF, checkBox.id); addRule(BELOW, title.id)
        })
        addView(checkBox, LayoutParams(-2, context.dp(20f)).apply {
            addRule(if (leading) ALIGN_PARENT_START else ALIGN_PARENT_END); addRule(CENTER_VERTICAL)
            if (leading) marginEnd = context.dp(18f) else marginStart = context.dp(12f)
        })
    }
    fun bind(item: SettingsToggleRow) {
        title.text = item.title; summary.text = item.summary
        title.ellipsize = item.titleEllipsize
        summary.visibility = if (item.summary.isNullOrEmpty()) GONE else VISIBLE
        checkBox.isChecked = item.checked
        if (leading) alpha = if (item.disabled) .5f else 1f
        else { title.alpha = if (item.disabled) .5f else 1f; summary.alpha = title.alpha; checkBox.alpha = title.alpha }
        isEnabled = !item.disabled
    }
}

/** y5.f with a6.s/p binding and a6.k diff identity; no replacement navigation model. */
class SettingsRowsAdapter(private val onClick: (SettingsRow) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows = emptyList<SettingsRow>()
    var onLongClick: ((View, SettingsRow) -> Boolean)? = null
    var onColorSelected: ((Int) -> Unit)? = null
    var onTextSizeChanged: ((Int) -> Unit)? = null
    var onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
    /** hb.j2 supplies each font choice's typeface after the shared a6.j binding. */
    var onRowBound: ((View, SettingsRow) -> Unit)? = null

    override fun getItemCount() = rows.size
    override fun getItemId(position: Int) = rows[position].id.toLong()
    override fun getItemViewType(position: Int) = when (rows[position]) {
        is SearchToolbarRow -> 6
        is SettingsEmptyRow -> 7
        is SettingsChoiceRow -> 2
        is SettingsToggleRow -> 1
        is SettingsHeadingRow -> 3
        is ReaderColorRow -> 4
        is TextSizeSampleRow -> 5
        else -> 0
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(
        (when (viewType) {
            1 -> SettingsToggleView(parent.context)
            2 -> SettingsToggleView(parent.context, true)
            3 -> TextView(parent.context).apply {
                setPadding(context.dp(16f), context.dp(12f), context.dp(16f), context.dp(4f))
                setTextColor(settingsColor(context, R.attr.viaAccentColor, Color.BLUE))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat())
                typeface = Typeface.create(BrowserPreferences(context).selectedTypeface(), Typeface.BOLD)
                textDirection = View.TEXT_DIRECTION_LOCALE
            }
            4 -> dev.ujhhgtg.via.reader.ReaderColorPalette(parent.context, 0,
                BrowserPreferences(parent.context).isNightMode
            ) { onColorSelected?.invoke(it) }
            5 -> TextSizeSampleView(parent.context) { onTextSizeChanged?.invoke(it) }
            6 -> SearchToolbarRowView(parent.context)
            7 -> TextView(parent.context).apply {
                gravity = android.view.Gravity.CENTER
                setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, Color.GRAY))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
                layoutParams = RecyclerView.LayoutParams(-1, -1)
                text = "¯\\_(ツ)_/¯"
            }
            else -> SettingsRowView(parent.context)
        }).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
    ) {}
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = rows[position]
        val view = holder.itemView
        when (view) {
            is SearchToolbarRowView -> { view.bind(item as SearchToolbarRow); view.onDrag = { onStartDrag?.invoke(holder) } }
            is SettingsToggleView -> view.bind(item as SettingsToggleRow)
            is SettingsRowView -> view.bind(item)
            is TextView -> view.text = item.title
            is dev.ujhhgtg.via.reader.ReaderColorPalette -> view.select((item as ReaderColorRow).color)
            is TextSizeSampleView -> view.bind(item as TextSizeSampleRow)
        }
        if (item is SettingsHeadingRow || item is SettingsEmptyRow || item is ReaderColorRow || item is TextSizeSampleRow) { view.setOnClickListener(null); view.setOnLongClickListener(null) }
        else { view.setOnClickListener { onClick(item) }; view.setOnLongClickListener { onLongClick?.invoke(view, item) ?: false } }
        onRowBound?.invoke(view, item)
    }

    fun rowAt(position: Int): SettingsRow? = rows.getOrNull(position)
    fun move(from: Int, to: Int) {
        rows = rows.toMutableList().apply { add(to, removeAt(from)) }
        notifyItemMoved(from, to)
    }

    fun submit(next: List<SettingsRow>) {
        val previous = rows
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(old: Int, new: Int) = if (previous[old] is ReaderColorRow) previous[old] === next[new]
                else previous[old].javaClass == next[new].javaClass && previous[old].id == next[new].id
            override fun areContentsTheSame(old: Int, new: Int) = previous[old] == next[new]
            override fun getChangePayload(old: Int, new: Int): Any? = if (previous[old].javaClass == next[new].javaClass) Any() else null
        })
        rows = next
        diff.dispatchUpdatesTo(this)
    }
}

internal fun bindSettingsChoicePreview(view: View, typeface: Typeface, alpha: Float) {
    (view as? SettingsToggleView)?.title?.let { title ->
        title.typeface = typeface
        title.alpha = alpha
    }
}

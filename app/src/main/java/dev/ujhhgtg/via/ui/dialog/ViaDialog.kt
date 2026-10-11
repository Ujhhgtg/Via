package dev.ujhhgtg.via.ui.dialog

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isNotEmpty
import androidx.fragment.app.FragmentActivity
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.SwipeBackLayout
import dev.ujhhgtg.via.ui.dp
import java.lang.ref.WeakReference
import kotlin.math.hypot
import kotlin.math.min

/** w5.k: Via's own Dialog, original layout, adapters, result collection and sizing. */
class ViaDialog(private val activity: Activity) {
    data class Item(val id: Int, val text: String)
    data class Result(val selected: IntArray?, val checked: Boolean, val edit: Array<String>?)
    private val host = activity.findViewById<View>(android.R.id.content)
    private val hostHeight = host?.height?.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
    private val hostWidth = host?.width?.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
    private val hostTop = if (host != null) IntArray(2).also(host::getLocationOnScreen)[1] else 0
    private var dialog: Dialog? = null
    private var content: View? = null
    private var blockedSwipe: WeakReference<SwipeBackLayout>? = null
    private var maximumWidth = 0
    private var title: String? = null
    private var titleIcon: Drawable? = null
    private var message: CharSequence? = null
    private var checkLabel: String? = null
    private var checked: Boolean? = null
    private var requireCheck = false
    private var adapter: ListAdapter? = null
    private var itemClick: ((Int) -> Unit)? = null
    private var itemLongClick: ((Int) -> Boolean)? = null
    private var custom: View? = null
    private var inputMode = false
    private var positive: String? = null
    private var positiveClick: ((View, Result) -> Unit)? = null
    private var negative: String? = null
    private var negativeClick: ((View) -> Unit)? = null
    private var neutral: String? = null
    private var neutralClick: ((View) -> Unit)? = null
    private var cancelable = true
    private var outsideCancelable = true
    private var dismissListener: DialogInterface.OnDismissListener? = null
    private var cancelListener: DialogInterface.OnCancelListener? = null
    private var blur = -1
    private var centered = false

    fun title(resource: Int) = title(activity.getString(resource))
    fun title(value: String) = apply { title = value }
    fun titleIcon(value: Drawable?) = apply { titleIcon = value }
    fun message(resource: Int) = message(activity.getString(resource))
    fun message(value: CharSequence) = apply { message = value }
    fun customView(view: View) = apply { custom = view }
    fun maximumWidth(value: Int) = apply { maximumWidth = value }
    fun centered(value: Boolean) = apply { centered = value }
    fun cancelable(value: Boolean) = apply { cancelable = value }
    fun canceledOnTouchOutside(value: Boolean) = apply { outsideCancelable = value }
    fun blurMode(value: Int) = apply { blur = value }
    fun onDismiss(listener: DialogInterface.OnDismissListener?) = apply { dismissListener = listener }
    fun onCancel(listener: DialogInterface.OnCancelListener?) = apply { cancelListener = listener }
    fun positive(resource: Int, callback: ((View, Result) -> Unit)? = null) = positive(activity.getString(resource), callback)
    fun positive(text: String, callback: ((View, Result) -> Unit)? = null) = apply { positive = text; positiveClick = callback }
    fun negative(resource: Int, callback: ((View) -> Unit)? = null) = negative(activity.getString(resource), callback)
    fun negative(text: String, callback: ((View) -> Unit)? = null) = apply { negative = text; negativeClick = callback }
    fun negativeResult(resource: Int, callback: ((View, Result) -> Unit)?) = negative(resource, callback?.let { handler -> { view -> handler(view, result()) } })
    fun neutral(resource: Int, callback: ((View) -> Unit)? = null) = neutral(activity.getString(resource), callback)
    fun neutral(text: String, callback: ((View) -> Unit)? = null) = apply { neutral = text; neutralClick = callback }
    fun check(resource: Int, value: Boolean) = check(activity.getString(resource), value)
    fun check(text: String, value: Boolean) = apply { checkLabel = text; checked = value }
    fun checkRequired(value: Boolean) = apply { requireCheck = value }
    fun note(resource: Int) = apply { checkLabel = activity.getString(resource) }

    fun items(labels: Array<String>, accent: Boolean = false, onClick: ((Int) -> Unit)? = null, onLongClick: ((Int) -> Boolean)? = null) =
        items(labels.mapIndexed { index, text -> Item(index, text) }, accent, onClick, onLongClick)
    fun items(items: List<Item>, accent: Boolean = false, onClick: ((Int) -> Unit)? = null, onLongClick: ((Int) -> Boolean)? = null) = apply {
        adapter = DialogItemsAdapter(activity, items).also { it.accent = accent }
        itemClick = onClick; itemLongClick = onLongClick
    }
    fun replaceItems(items: List<Item>): Boolean {
        val current = adapter as? DialogItemsAdapter ?: return false
        current.items = items; current.notifyDataSetChanged(); return true
    }
    fun multipleChoice(labels: Array<String>, selected: IntArray) = apply { adapter = DialogChoiceAdapter(labels.toList(), selected, true) }
    fun singleChoice(labels: Array<String>, selected: Int, onClick: ((Int) -> Unit)? = null) = apply {
        adapter = DialogChoiceAdapter(labels.toList(), intArrayOf(selected), false); itemClick = onClick
    }
    fun highlightedChoice(labels: Array<String>, selected: Int, onClick: ((Int) -> Unit)? = null) = apply {
        adapter = DialogChoiceAdapter(labels.toList(), intArrayOf(selected),
            multiple = false,
            highlightOnly = true
        ); itemClick = onClick
    }

    fun input(text: String?, hint: String?, lines: Int, id: Int = -1) = apply {
        inputMode = true
        // w5.k.g builds these dimensions through h6.a.k, which truncates
        // applyDimension's float; XML dimensions elsewhere still round normally.
        fun inputDp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics).toInt()
        val body = (custom as? ViewGroup) ?: LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }.also { custom = it }
        val edit = EditText(activity).apply {
            this.id = id
            setText(text); this.hint = hint
            maxLines = lines; minLines = lines; setLines(lines); ellipsize = TextUtils.TruncateAt.END
            inputType = if (lines > 1) 671745 else 524289
            imeOptions = if (lines > 1) 1 else 5
            setTextColor(settingsColor(activity, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
            setHintTextColor(settingsColor(activity, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            gravity = Gravity.TOP; setSelectAllOnFocus(lines <= 3)
            if (Build.VERSION.SDK_INT >= 35) isLocalePreferredLineHeightForMinimumUsed = false
            setBackgroundResource(R.drawable.via_dialog_input)
            typeface = BrowserPreferences(activity).selectedTypeface()
            layoutParams = FrameLayout.LayoutParams(if (lines == 1) -2 else -1, -2).apply {
                if (lines != 1) { marginStart = inputDp(12); marginEnd = inputDp(12); bottomMargin = inputDp(12) }
            }
            val padding = inputDp(if (lines == 1) 0 else 8); setPaddingRelative(paddingStart, padding, paddingEnd, padding)
        }
        if (lines != 1) body.addView(edit)
        else body.addView(HorizontalScrollView(activity).apply {
            isFillViewport = true; isHorizontalScrollBarEnabled = false; isVerticalScrollBarEnabled = false
            overScrollMode = if (Build.VERSION.SDK_INT >= 32) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
            setBackgroundResource(R.drawable.via_dialog_input)
            val padding = inputDp(8); setPaddingRelative(paddingStart, padding, paddingEnd, padding)
            edit.setBackgroundColor(0)
            addView(edit)
        }, FrameLayout.LayoutParams(-1, -2).apply { marginStart = inputDp(16); marginEnd = inputDp(16); bottomMargin = inputDp(12) })
    }
    fun edit(id: Int): EditText? = custom?.findViewById(id)

    fun progress(resource: Int) = progress(activity.getString(resource))
    fun progress(text: String) = apply {
        if (text.isNotEmpty()) custom = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPaddingRelative(activity.dp(16f), activity.dp(4f), activity.dp(16f), activity.dp(4f))
            addView(ProgressBar(activity).apply {
                // w5.k.Y really calls false. The platform circular style is
                // indeterminateOnly; ProgressBar therefore retains its animation.
                isIndeterminate = false
                indeterminateDrawable = ContextCompat.getDrawable(activity, R.drawable.via_dialog_progress)
                setPaddingRelative(0, 0, 0, 0)
            })
            addView(TextView(activity).apply {
                setPaddingRelative(activity.dp(16f), 0, 0, 0)
                setTextAppearance(R.style.Via_Dialog_Text)
                this.text = text
            }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_VERTICAL })
        }
    }

    fun show() {
        if (dialog == null) {
            val units = if (inputMode) 8 else 6
            var width = min(hostWidth, hostHeight) - activity.dp(72f)
            val metrics = activity.resources.displayMetrics
            val size = activity.resources.configuration.screenLayout and 15
            val tablet = size in 3..4 && hypot(metrics.widthPixels / metrics.xdpi, metrics.heightPixels / metrics.ydpi) >= 7f
            if ((tablet || hostWidth > hostHeight) && width > activity.dp(units * 60f)) width = activity.dp((units - 1) * 60f)
            if (maximumWidth > 0) width = min(width, maximumWidth)
            val height = hostHeight / 7 * 4
            val created = create(width, height, false)
            dialog = created
            created.setCancelable(cancelable); created.setCanceledOnTouchOutside(cancelable && outsideCancelable)
            prepareDismiss(created); created.setOnCancelListener(cancelListener)
            created.window?.let { window ->
                window.attributes = window.attributes.apply { this.width = width; gravity = Gravity.CENTER }
                applyWindow(window, false)
            }
        }
        showIfVisible()
    }

    fun showAt(x: Int, y: Int) {
        if (dialog == null) {
            var width = min(activity.dp(240f), min(hostHeight, hostWidth) / 7 * if (inputMode) 5 else 4)
            if (maximumWidth > 0) width = min(width, maximumWidth)
            val height = hostHeight * 3 / 5
            val created = create(width, height, true)
            dialog = created
            created.window?.let { window ->
                window.decorView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                var left = if (x > hostWidth shr 1) x - width else x
                if (left <= 10) left = activity.dp(12f)
                else if (left + width >= hostWidth - 10) left = hostWidth - width - activity.dp(12f)
                val bottom = y > hostHeight shr 1
                var top = if (bottom) hostHeight - y else y
                if (hostTop > 0) top = if (bottom) top + hostTop else top - hostTop
                if (Build.VERSION.SDK_INT >= 30) {
                    val insets = activity.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.windowInsets
                    if (insets != null) {
                        val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                        val visible = insets.isVisible(WindowInsets.Type.statusBars())
                        val amount = if (visible) { if (bottom) bars.bottom else bars.top }
                            else if (Build.VERSION.SDK_INT >= 35) { if (!bottom) bars.top else 0 }
                            else if (bottom) bars.bottom else 0
                        top -= amount
                    }
                }
                window.attributes = window.attributes.apply {
                    gravity = (if (bottom) Gravity.BOTTOM else Gravity.TOP) or Gravity.START
                    this.x = left; this.y = top; this.width = width
                    flags = flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
                }
            }
            created.setCancelable(true); created.setCanceledOnTouchOutside(true)
            prepareDismiss(created)
            created.window?.let { applyWindow(it, true) }
        }
        showIfVisible()
    }
    fun showAnchored(view: View?, x: Int = view?.width ?: 0, y: Int = (view?.height ?: 0) / 2) {
        if (view == null) { show(); return }
        val position = IntArray(2); view.getLocationOnScreen(position)
        showAt(position[0] + x, position[1] + y)
    }

    private fun showIfVisible() {
        val current = (activity as? FragmentActivity)?.supportFragmentManager?.fragments?.firstOrNull { it.isVisible }
        if (current == null || (current.isVisible && !current.parentFragmentManager.isStateSaved)) dialog?.show()
    }

    private fun create(width: Int, height: Int, anchored: Boolean): Dialog {
        val dialog = Dialog(activity, R.style.Via_Dialog)
        val root = LayoutInflater.from(activity).inflate(R.layout.via_dialog, host as? ViewGroup, false)
        content = root
        val compact = anchored && (adapter?.count ?: 0) > 0
        title?.let { value -> root.findViewById<TextView>(R.id.dialog_title).apply {
            visibility = View.VISIBLE; text = value
            titleIcon?.let { icon ->
                val size = activity.dp(24f)
                icon.setBounds(0, 0, size, size)
                compoundDrawablePadding = activity.dp(8f)
                setCompoundDrawablesRelative(icon, null, null, null)
            }
            if (compact) setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            typeface = Typeface.DEFAULT_BOLD
            if (centered) gravity = Gravity.CENTER
            setLines(1); maxLines = 2
        } }
        message?.let { value -> root.findViewById<TextView>(R.id.dialog_message).apply {
            visibility = View.VISIBLE; text = value
            (parent as ViewGroup).apply { isHorizontalFadingEdgeEnabled = false; isVerticalFadingEdgeEnabled = true }
            setFadingEdgeLength(activity.dp(12f))
            if (compact) setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_summary_size).toFloat())
            measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val available = if ((adapter?.count ?: 0) == 0) height else height / 3
            if (available in 1..<measuredHeight) root.findViewById<View>(R.id.dialog_message_scroll).layoutParams.height = available
            else if (adapter == null && custom == null) minHeight = activity.dp(76f)
            if (value is SpannableString) movementMethod = LinkMovementMethod.getInstance()
            if (centered) gravity = Gravity.CENTER
        } }
        adapter?.takeIf { it.count > 0 }?.let { adapter -> root.findViewById<ListView>(R.id.dialog_list).apply {
            isHorizontalScrollBarEnabled = false; isVerticalScrollBarEnabled = false
            (parent as MaxHeightLayout).maximumHeight = if (message != null) height * 2 / 3 else height
            visibility = View.VISIBLE; selector = ContextCompat.getDrawable(activity, R.drawable.flat_ripple)
            if (adapter is DialogChoiceAdapter && !adapter.highlightOnly) {
                val selected = adapter.selected()
                if (selected.size == 1) post { smoothScrollToPositionFromTop(selected[0], 0) }
                adapter.bind(this, if (adapter.multiple || itemClick == null) null else { position -> itemClick?.invoke(position); dismiss() }, itemLongClick)
            } else {
                this.adapter = adapter
                if (adapter is DialogChoiceAdapter) adapter.selected().takeIf { it.size == 1 }?.let { selected -> post { smoothScrollToPositionFromTop(selected[0], 0) } }
                if (adapter is DialogItemsAdapter) { if (!message.isNullOrEmpty()) adapter.accent = true; if (centered) adapter.centered = true }
                setOnItemClickListener { _, _, position, _ -> itemClick?.invoke(position); dismiss() }
                setOnItemLongClickListener { _, _, position, _ -> itemLongClick?.invoke(position) ?: false }
            }
            // The ListView must share its parent's viewport. A taller explicit child is clipped
            // by MaxHeightLayout, so it thinks the hidden final rows are already on screen.
            val available = (parent as MaxHeightLayout).maximumHeight
            val row = adapter.getView(0, null, this)
            row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            if (row.measuredHeight * adapter.count > available) layoutParams.height = available
        } }
        custom?.let { root.findViewById<FrameLayout>(R.id.dialog_custom).apply { visibility = View.VISIBLE; addView(it) } }
        checkLabel?.let { value -> root.findViewById<TextView>(R.id.dialog_check_label).apply { visibility = View.VISIBLE; text = value } }
        if (checkLabel != null && checked != null) {
            val box = root.findViewById<CheckBox>(R.id.dialog_check).apply {
                visibility = View.VISIBLE; isChecked = checked == true
                setOnCheckedChangeListener { _, value -> checked = value }
            }
            root.findViewById<View>(R.id.dialog_check_row).apply { isClickable = true; isFocusable = true; setOnClickListener { box.isChecked = !box.isChecked } }
        }
        positive?.let { value -> root.findViewById<TextView>(R.id.dialog_positive).apply {
            visibility = View.VISIBLE; text = value
            setOnClickListener { view ->
                val result = result()
                if (!requireCheck || result.checked) { positiveClick?.invoke(view, result); dismiss() }
                else { shake(root.findViewById(R.id.dialog_check)); shake(root.findViewById(R.id.dialog_check_label)) }
            }
        } }
        negative?.let { value -> root.findViewById<TextView>(R.id.dialog_negative).apply { visibility = View.VISIBLE; text = value; setOnClickListener { negativeClick?.invoke(it); dismiss() } } }
        neutral?.let { value -> root.findViewById<TextView>(R.id.dialog_neutral).apply { visibility = View.VISIBLE; text = value; setOnClickListener { neutralClick?.invoke(it); dismiss() } } }
        applyTypeface(root)
        if ((adapter?.count ?: 0) > 0) {
            // Header, message and buttons also consume height. On short windows or with larger
            // text, shrink the list's actual viewport rather than letting the window clip it.
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val usableHeight = if (Build.VERSION.SDK_INT >= 30) {
                val metrics = activity.getSystemService(WindowManager::class.java).currentWindowMetrics
                val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                metrics.bounds.height() - bars.top - bars.bottom
            } else hostHeight
            val overflow = root.measuredHeight - (usableHeight - activity.dp(24f))
            if (overflow > 0) {
                val list = root.findViewById<ListView>(R.id.dialog_list)
                val wrapper = list.parent as MaxHeightLayout
                val viewport = maxOf(1, wrapper.maximumHeight - overflow)
                wrapper.maximumHeight = viewport
                list.layoutParams.height = viewport
            }
        }
        dialog.setContentView(root)
        return dialog
    }

    private fun result(): Result {
        val values = if (inputMode && custom is ViewGroup) buildList {
            val group = custom as ViewGroup
            for (i in 0 until group.childCount) {
                var child = group.getChildAt(i)
                if (child is ViewGroup && child.isNotEmpty()) child = child.getChildAt(0)
                if (child is EditText) {
                    val text = child.text
                    text.getSpans(0, text.length, MetricAffectingSpan::class.java).forEach(text::removeSpan)
                    add(text.toString())
                }
            }
        }.toTypedArray() else null
        return Result((adapter as? DialogChoiceAdapter)?.takeUnless { it.highlightOnly }?.selected(), checked == true, values)
    }
    private fun applyTypeface(view: View) {
        if (view is ListView || view is androidx.recyclerview.widget.RecyclerView || view is dev.ujhhgtg.via.settings.SettingsToolbar) return
        if (view is TextView) view.typeface = Typeface.create(BrowserPreferences(activity).selectedTypeface(), view.typeface?.style ?: Typeface.NORMAL)
        if (view is ViewGroup) for (i in 0 until view.childCount) applyTypeface(view.getChildAt(i))
    }
    private fun shake(view: View) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, activity.dp(24f).toFloat(), 0f).apply {
            repeatCount = 2; repeatMode = ObjectAnimator.RESTART; duration = 280L
            interpolator = PathInterpolator(.2f, .2f, .8f, .8f); start()
        }
    }
    private fun findSwipe(view: View?): SwipeBackLayout? {
        if (view is SwipeBackLayout) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findSwipe(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun prepareDismiss(dialog: Dialog) {
        val current = (activity as? FragmentActivity)?.supportFragmentManager?.fragments?.firstOrNull { it.isVisible }
        val swipe = findSwipe(current?.view)
        if (swipe == null || !swipe.isGestureEnabled()) { blockedSwipe = null; dialog.setOnDismissListener(dismissListener) }
        else {
            swipe.setGestureEnabled(false); blockedSwipe = WeakReference(swipe)
            dialog.setOnDismissListener { dismissed -> dismissListener?.onDismiss(dismissed); blockedSwipe?.get()?.setGestureEnabled(true) }
        }
    }
    private fun applyWindow(window: Window, anchored: Boolean) {
        if (WindowInsetsHelper.isFullscreen(activity.window)) WindowInsetsHelper.setFullscreen(window, true)
        DialogWindowBlur.apply(activity, window, if (blur == -1) { if (anchored) 2 else 1 } else blur)
    }
    fun dismiss() { dialog?.dismiss() }
    fun isShowing() = dialog?.isShowing == true
}

package com.minhnhat.reclaim.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.minhnhat.reclaim.R

/**
 * Bộ công cụ dựng View thuần (không thư viện ngoài). Màu lấy từ tài nguyên nên tự đổi theo chế độ sáng/tối.
 */

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun Context.linearParams(
    width: Int,
    height: Int,
    weight: Float = 0f,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0
): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, height, weight).apply {
    setMargins(dp(left), dp(top), dp(right), dp(bottom))
}

fun Context.roundedBackground(fillColorRes: Int, radiusDp: Int, strokeColorRes: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(getColor(fillColorRes))
        if (strokeColorRes != 0) setStroke(dp(1), getColor(strokeColorRes))
    }

/** Nền bo góc chỉ ở phía trên (dùng cho bottom sheet). */
fun Context.topRoundedBackground(fillColorRes: Int, radiusDp: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        val r = dp(radiusDp).toFloat()
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        setColor(getColor(fillColorRes))
    }

/** Hiệu ứng gợn sóng bo góc phủ lên [content]. */
fun Context.rippleOver(content: Drawable?, radiusDp: Int): RippleDrawable {
    val mask = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(Color.WHITE)
    }
    return RippleDrawable(ColorStateList.valueOf(getColor(R.color.ripple)), content, mask)
}

fun Context.label(
    text: CharSequence,
    sizeSp: Float = 16f,
    colorRes: Int = R.color.text_primary,
    bold: Boolean = false
): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    setTextColor(getColor(colorRes))
    if (bold) typeface = Typeface.DEFAULT_BOLD
}

fun Context.icon(drawableRes: Int, tintRes: Int, sizeDp: Int = 24): ImageView = ImageView(this).apply {
    setImageResource(drawableRes)
    setColorFilter(getColor(tintRes))
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

fun Context.card(paddingDp: Int = 16): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBackground(R.color.surface, 16, R.color.outline)
    setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
}

fun Context.filledButton(text: CharSequence, onClick: () -> Unit): TextView = TextView(this).apply {
    this.text = text
    textSize = 15f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    setTextColor(getColor(R.color.on_green))
    setPadding(dp(20), dp(10), dp(20), dp(10))
    minHeight = dp(40)
    background = rippleOver(roundedBackground(R.color.green_primary, 20), 20)
    setOnClickListener { onClick() }
}

fun Context.textButton(text: CharSequence, onClick: () -> Unit): TextView = TextView(this).apply {
    this.text = text
    textSize = 15f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    setTextColor(getColor(R.color.green_primary))
    setPadding(dp(16), dp(10), dp(16), dp(10))
    minHeight = dp(40)
    background = rippleOver(null, 20)
    setOnClickListener { onClick() }
}

fun Context.greenSwitch(): Switch = Switch(this).apply {
    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
    thumbTintList = ColorStateList(
        states, intArrayOf(getColor(R.color.switch_thumb_on), getColor(R.color.switch_thumb_off))
    )
    trackTintList = ColorStateList(
        states, intArrayOf(getColor(R.color.green_primary), getColor(R.color.switch_track_off))
    )
    // Track mặc định của framework mang alpha thấp nên màu bật nhìn rất nhạt: dùng chế độ SRC để ra màu đặc.
    trackTintMode = PorterDuff.Mode.SRC
    trackDrawable?.alpha = 255
}

fun Context.greenCheckBox(): CheckBox = CheckBox(this).apply {
    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
    buttonTintList = ColorStateList(
        states, intArrayOf(getColor(R.color.green_primary), getColor(R.color.text_secondary))
    )
}

/** Tiêu đề lớn của mỗi màn hình kèm dòng mô tả ngắn. */
fun Context.screenHeader(title: CharSequence, subtitle: CharSequence): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(12))
        addView(label(title, 26f, bold = true))
        addView(label(subtitle, 14f, R.color.text_secondary), linearParams(MATCH, WRAP, top = 4))
    }

fun Context.sectionLabel(text: CharSequence): TextView =
    label(text, 13f, R.color.green_primary, bold = true).apply {
        setPadding(dp(4), dp(20), dp(4), dp(8))
    }

/** Hộp cảnh báo màu cam kèm nút hành động (nút có thể bỏ trống). */
fun Context.warningBanner(message: CharSequence, actionText: CharSequence?, onAction: () -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundedBackground(R.color.warning_container, 14)
        setPadding(dp(14), dp(12), dp(10), dp(12))
        addView(icon(R.drawable.ic_warning, R.color.warning, 22))
        addView(
            label(message, 13.5f, R.color.warning),
            linearParams(0, WRAP, 1f, left = 10, right = 8)
        )
        if (actionText != null) addView(textButton(actionText, onAction))
    }

fun EditText.onTextChanged(block: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            block(s?.toString().orEmpty())
        }

        override fun afterTextChanged(s: Editable?) {}
    })
}

/** ScrollView có chiều cao tối đa (cho phần nội dung của bottom sheet). */
@SuppressLint("ViewConstructor") // tạo bằng mã, không inflate từ XML
class BoundedScrollView(context: Context, private val maxHeightPx: Int) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST)
        )
    }
}

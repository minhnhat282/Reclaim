package com.minhnhat.reclaim.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.minhnhat.reclaim.R
import com.minhnhat.reclaim.data.PinManager

/** Màn hình nhập PIN phủ lên toàn bộ app khi đã đặt PIN. */
@SuppressLint("ViewConstructor") // tạo bằng mã, không inflate từ XML
class PinGateView(context: Context, private val pin: PinManager, private val onUnlocked: () -> Unit) :
    LinearLayout(context) {

    private val input: EditText
    private val error: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(context.getColor(R.color.bg))
        isClickable = true
        setPadding(context.dp(32), 0, context.dp(32), 0)

        val icon = ImageView(context).apply { setImageResource(R.drawable.ic_sprout) }
        addView(icon, LayoutParams(context.dp(120), context.dp(120)))
        addView(context.label(context.getString(R.string.gate_title), 22f, bold = true), context.linearParams(WRAP, WRAP, top = 8))

        input = EditText(context).apply {
            hint = context.getString(R.string.gate_hint)
            gravity = Gravity.CENTER
            textSize = 22f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
            setTextColor(context.getColor(R.color.text_primary))
            setHintTextColor(context.getColor(R.color.text_secondary))
            background = context.roundedBackground(R.color.surface, 14, R.color.outline)
            setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
        }
        addView(input, context.linearParams(MATCH, WRAP, top = 24))

        error = context.label(context.getString(R.string.pin_err_wrong), 13.5f, R.color.error).apply {
            visibility = INVISIBLE
        }
        addView(error, context.linearParams(WRAP, WRAP, top = 8))

        addView(context.filledButton(context.getString(R.string.gate_unlock)) { submit() }, context.linearParams(MATCH, WRAP, top = 16))
    }

    private fun submit() {
        val ok = pin.attempt(input.text.toString())
        if (ok) {
            pin.touch()
            onUnlocked()
            return
        }
        val remaining = pin.lockoutRemainingMs()
        error.text = if (remaining > 0) {
            context.getString(R.string.pin_err_locked, ((remaining + 999) / 1000).toInt())
        } else {
            context.getString(R.string.pin_err_wrong)
        }
        error.visibility = VISIBLE
        input.setText("")
    }

    fun focusInput() {
        input.requestFocus()
    }
}

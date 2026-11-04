package com.minhnhat.reclaim.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.minhnhat.reclaim.R

/** Các hộp thoại tự vẽ, cùng phong cách với phần còn lại của app. */
object Dialogs {

    fun newDialog(context: Context): Dialog = Dialog(context, R.style.Theme_Reclaim_Dialog)

    /** Biến hộp thoại thành bottom sheet bám đáy màn hình, rộng hết chiều ngang. */
    fun asBottomSheet(dialog: Dialog) {
        dialog.window?.apply {
            setLayout(MATCH, WRAP)
            setGravity(Gravity.BOTTOM)
        }
    }

    /** Thẻ nổi giữa màn hình, cách mép 24dp. */
    private fun centered(context: Context, card: View): View = FrameLayout(context).apply {
        setPadding(context.dp(24), 0, context.dp(24), 0)
        addView(card, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    /** Hộp chọn một mục trong danh sách dài, tự cuộn tới mục đang chọn (dùng cho chọn giờ). */
    fun pick(context: Context, title: String, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val dialog = newDialog(context)
        val card = context.card(0)
        card.addView(
            context.label(title, 18f, bold = true).apply { setPadding(context.dp(20), context.dp(18), context.dp(20), context.dp(8)) }
        )
        val list = ListView(context).apply {
            divider = null
            dividerHeight = 0
            adapter = PickerAdapter(context, labels, selected)
            setOnItemClickListener { _, _, position, _ ->
                dialog.dismiss()
                onPick(position)
            }
        }
        card.addView(list, LinearLayout.LayoutParams(MATCH, context.dp(300)))
        list.setSelectionFromTop((selected - 3).coerceAtLeast(0), 0)
        dialog.setContentView(centered(context, card))
        dialog.window?.setLayout(MATCH, WRAP)
        dialog.show()
    }

    /**
     * Form nhập số dạng mật khẩu (PIN). [onSubmit] nhận giá trị các ô; trả về thông báo lỗi để giữ
     * hộp thoại mở, hoặc null để đóng.
     */
    fun pinForm(
        context: Context,
        title: String,
        hints: List<String>,
        positive: String,
        onSubmit: (List<String>) -> String?
    ) {
        val dialog = newDialog(context)
        val card = context.card(20)
        card.addView(context.label(title, 18f, bold = true))

        val fields = hints.map { hint ->
            EditText(context).apply {
                this.hint = hint
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                filters = arrayOf(InputFilter.LengthFilter(8))
                setTextColor(context.getColor(R.color.text_primary))
                setHintTextColor(context.getColor(R.color.text_secondary))
                background = context.roundedBackground(R.color.bg, 12, R.color.outline)
                setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
            }
        }
        fields.forEach { card.addView(it, context.linearParams(MATCH, WRAP, top = 12)) }

        val error = context.label("", 13f, R.color.error).apply { visibility = View.GONE }
        card.addView(error, context.linearParams(MATCH, WRAP, top = 8))

        val buttons = LinearLayout(context).apply { gravity = Gravity.END }
        buttons.addView(context.textButton(context.getString(R.string.action_cancel)) { dialog.dismiss() })
        buttons.addView(
            context.filledButton(positive) {
                val message = onSubmit(fields.map { it.text.toString() })
                if (message == null) {
                    dialog.dismiss()
                } else {
                    error.text = message
                    error.visibility = View.VISIBLE
                }
            },
            context.linearParams(WRAP, WRAP, left = 8)
        )
        card.addView(buttons, context.linearParams(MATCH, WRAP, top = 16))

        dialog.setContentView(centered(context, card))
        dialog.window?.apply {
            setLayout(MATCH, WRAP)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        dialog.show()
        fields.firstOrNull()?.requestFocus()
    }

    /**
     * Hướng dẫn cấp quyền bằng ADB (WRITE_SECURE_SETTINGS cho thang độ xám): các bước, lệnh chọn/sao chép được,
     * và lưu ý cách tắt thủ công nếu màn hình kẹt đen trắng.
     */
    fun adbGuide(context: Context, command: String) {
        val dialog = newDialog(context)
        val card = context.card(20)
        card.addView(context.label(context.getString(R.string.adb_title), 18f, bold = true))
        card.addView(
            context.label(context.getString(R.string.adb_steps), 14f, R.color.text_secondary),
            context.linearParams(MATCH, WRAP, top = 10)
        )
        val box = context.label(command, 13f).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            background = context.roundedBackground(R.color.bg, 12, R.color.outline)
            setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
        }
        card.addView(box, context.linearParams(MATCH, WRAP, top = 12))
        card.addView(
            context.label(context.getString(R.string.adb_note), 13f, R.color.warning),
            context.linearParams(MATCH, WRAP, top = 12)
        )

        val buttons = LinearLayout(context).apply { gravity = Gravity.END }
        buttons.addView(context.textButton(context.getString(R.string.adb_copy)) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(
                ClipData.newPlainText("adb", command)
            )
            Toast.makeText(context, R.string.adb_copied, Toast.LENGTH_SHORT).show()
        })
        buttons.addView(
            context.filledButton(context.getString(R.string.adb_close)) { dialog.dismiss() },
            context.linearParams(WRAP, WRAP, left = 8)
        )
        card.addView(buttons, context.linearParams(MATCH, WRAP, top = 16))

        val scroll = ScrollView(context).apply { addView(card) }
        dialog.setContentView(centered(context, scroll))
        dialog.window?.setLayout(MATCH, WRAP)
        dialog.show()
    }

    private class PickerAdapter(
        private val context: Context,
        private val labels: List<String>,
        private val selected: Int
    ) : BaseAdapter() {
        override fun getCount(): Int = labels.size
        override fun getItem(position: Int): Any = labels[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val isSelected = position == selected
            val text = (convertView as? TextView) ?: TextView(context)
            text.text = labels[position]
            text.textSize = 16f
            text.setPadding(context.dp(20), context.dp(12), context.dp(20), context.dp(12))
            text.setTextColor(context.getColor(if (isSelected) R.color.green_primary else R.color.text_primary))
            text.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            text.setBackgroundColor(if (isSelected) context.getColor(R.color.green_container) else Color.TRANSPARENT)
            return text
        }
    }
}

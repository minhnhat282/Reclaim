package com.minhnhat.reclaim.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.minhnhat.reclaim.R
import com.minhnhat.reclaim.domain.DayWindow
import com.minhnhat.reclaim.domain.WeeklySchedule

/** Bottom sheet hẹn giờ mở khóa cho một ứng dụng. */
object ScheduleSheet {

    fun show(context: Context, appLabel: String, initial: WeeklySchedule, onSave: (WeeklySchedule) -> Unit) {
        val dialog = Dialogs.newDialog(context)
        val editor = ScheduleEditor(context, initial) {}

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = context.topRoundedBackground(R.color.surface, 24)
            setPadding(context.dp(20), context.dp(12), context.dp(20), context.dp(12))
        }

        val handle = View(context).apply { background = context.roundedBackground(R.color.outline, 2) }
        root.addView(handle, LinearLayout.LayoutParams(context.dp(40), context.dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL })

        root.addView(context.label(context.getString(R.string.sheet_title), 20f, bold = true), context.linearParams(MATCH, WRAP, top = 16))
        root.addView(context.label(appLabel, 15f, R.color.green_primary, bold = true), context.linearParams(MATCH, WRAP, top = 2))
        root.addView(context.label(context.getString(R.string.sheet_desc), 13.5f, R.color.text_secondary), context.linearParams(MATCH, WRAP, top = 8, bottom = 8))

        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.5f).toInt()
        val scroll = BoundedScrollView(context, maxHeight).apply { addView(editor.view) }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(
            context.textButton(context.getString(R.string.sheet_copy_monday)) {
                val monday = editor.schedule.days[0]
                // Sao chép nguyên khung Thứ Hai (kể cả trạng thái tích/không tích), không tự bật thêm ngày nào.
                val copy = DayWindow(monday.enabled, monday.startMin, monday.endMin)
                editor.replace(WeeklySchedule(List(7) { copy }))
            },
            context.linearParams(WRAP, WRAP, top = 4).apply { gravity = Gravity.START }
        )

        val buttons = LinearLayout(context).apply { gravity = Gravity.END }
        buttons.addView(context.textButton(context.getString(R.string.action_cancel)) { dialog.dismiss() })
        buttons.addView(
            context.filledButton(context.getString(R.string.action_save)) {
                onSave(editor.schedule)
                dialog.dismiss()
            },
            context.linearParams(WRAP, WRAP, left = 8)
        )
        root.addView(buttons, context.linearParams(MATCH, WRAP, top = 8))

        dialog.setContentView(root)
        Dialogs.asBottomSheet(dialog)
        dialog.show()
    }
}

package com.minhnhat.reclaim.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.minhnhat.reclaim.R
import com.minhnhat.reclaim.domain.DayWindow
import com.minhnhat.reclaim.domain.WeeklySchedule
import com.minhnhat.reclaim.domain.formatMinutes

/**
 * Bộ chỉnh lịch mở khóa theo tuần: mỗi ngày một dòng gồm ô tích, tên ngày, giờ bắt đầu → giờ kết thúc.
 * Ngày không tích = khóa cả ngày. Dùng chung cho khóa app (trong bottom sheet) và đa phương tiện.
 */
class ScheduleEditor(
    private val context: Context,
    initial: WeeklySchedule,
    private val onChange: (WeeklySchedule) -> Unit
) {
    var schedule: WeeklySchedule = initial
        private set

    val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val dayNames: Array<String> = context.resources.getStringArray(R.array.day_names)
    private val rows = ArrayList<DayRow>()

    init {
        for (index in 0 until 7) {
            val row = DayRow(index)
            rows.add(row)
            view.addView(row.root)
        }
        bindAll()
    }

    /** Thay toàn bộ lịch (ví dụ "dùng giờ Thứ Hai cho cả tuần"). */
    fun replace(newSchedule: WeeklySchedule) {
        schedule = newSchedule
        bindAll()
        onChange(schedule)
    }

    private fun bindAll() {
        rows.forEachIndexed { index, row -> row.bind(schedule.days[index]) }
    }

    private fun update(index: Int, window: DayWindow) {
        schedule = schedule.withDay(index, window)
        rows[index].bind(window)
        onChange(schedule)
    }

    private fun pickStart(index: Int) {
        val window = schedule.days[index]
        val options = (0..DayWindow.MINUTES_PER_DAY - STEP step STEP).toList()
        val selected = options.indexOf(window.startMin).coerceAtLeast(0)
        Dialogs.pick(context, context.getString(R.string.pick_start), options.map { formatMinutes(it) }, selected) { i ->
            val start = options[i]
            val end = if (window.endMin > start) window.endMin else minOf(start + 60, DayWindow.MINUTES_PER_DAY)
            update(index, DayWindow(window.enabled, start, end))
        }
    }

    private fun pickEnd(index: Int) {
        val window = schedule.days[index]
        val first = (window.startMin / STEP + 1) * STEP
        val options = (first..DayWindow.MINUTES_PER_DAY step STEP).toList()
        val selected = options.indexOf(window.endMin).let { if (it >= 0) it else options.size - 1 }
        Dialogs.pick(context, context.getString(R.string.pick_end), options.map { formatMinutes(it) }, selected) { i ->
            update(index, DayWindow(window.enabled, window.startMin, options[i]))
        }
    }

    /** Ô giờ bo góc có mũi tên xổ xuống, bấm để mở danh sách chọn. */
    private fun timeField(onClick: () -> Unit): TextView = TextView(context).apply {
        textSize = 16f
        setTextColor(context.getColor(R.color.text_primary))
        gravity = Gravity.CENTER_VERTICAL
        minWidth = context.dp(88)
        setPadding(context.dp(12), context.dp(10), context.dp(6), context.dp(10))
        background = context.rippleOver(context.roundedBackground(R.color.surface, 10, R.color.outline), 10)
        val arrow = context.getDrawable(R.drawable.ic_arrow_drop_down)!!.mutate()
        arrow.setTint(context.getColor(R.color.text_secondary))
        arrow.setBounds(0, 0, context.dp(20), context.dp(20))
        setCompoundDrawablesRelative(null, null, arrow, null)
        setOnClickListener { onClick() }
    }

    private inner class DayRow(private val index: Int) {
        private var binding = false

        private val check = context.greenCheckBox().apply {
            setOnCheckedChangeListener { _, checked ->
                if (!binding) update(index, schedule.days[index].copy(enabled = checked))
            }
        }
        private val start = timeField { pickStart(index) }
        private val end = timeField { pickEnd(index) }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(56)
            addView(check, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(
                context.label(dayNames[index], 16f, bold = true),
                context.linearParams(0, WRAP, 1f, left = 4)
            )
            addView(start)
            addView(context.label("→", 16f, R.color.text_secondary), context.linearParams(WRAP, WRAP, left = 8, right = 8))
            addView(end)
        }

        fun bind(window: DayWindow) {
            binding = true
            check.isChecked = window.enabled
            start.text = formatMinutes(window.startMin)
            end.text = formatMinutes(window.endMin)
            val alpha = if (window.enabled) 1f else 0.4f
            start.alpha = alpha
            end.alpha = alpha
            start.isEnabled = window.enabled
            end.isEnabled = window.enabled
            binding = false
        }
    }

    private companion object {
        /** Bước chọn giờ (phút). */
        const val STEP = 15
    }
}

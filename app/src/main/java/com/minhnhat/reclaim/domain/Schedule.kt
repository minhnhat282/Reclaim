package com.minhnhat.reclaim.domain

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Khung giờ MỞ KHÓA của một ngày. Giờ tính bằng phút trong ngày.
 * Bắt buộc start < end <= 1440 nên không có giờ qua đêm (khóa qua đêm là phần bù mặc định).
 */
data class DayWindow(val enabled: Boolean, val startMin: Int, val endMin: Int) {
    init {
        require(startMin in 0 until MINUTES_PER_DAY) { "startMin ngoài khoảng" }
        require(endMin in (startMin + 1)..MINUTES_PER_DAY) { "endMin ngoài khoảng" }
    }

    fun contains(minuteOfDay: Int): Boolean =
        enabled && minuteOfDay >= startMin && minuteOfDay < endMin

    companion object {
        const val MINUTES_PER_DAY = 1440
        val DEFAULT = DayWindow(enabled = false, startMin = 7 * 60, endMin = 18 * 60)

        /** Ép dữ liệu hỏng (đọc từ đĩa) về giá trị hợp lệ thay vì làm app crash. */
        fun sanitized(enabled: Boolean, startMin: Int, endMin: Int): DayWindow {
            val s = startMin.coerceIn(0, MINUTES_PER_DAY - 1)
            val e = endMin.coerceIn(s + 1, MINUTES_PER_DAY)
            return DayWindow(enabled, s, e)
        }
    }
}

/**
 * Lịch mở khóa theo tuần: 7 phần tử, chỉ số 0 = Thứ Hai ... 6 = Chủ Nhật (theo [DayOfWeek.ordinal]).
 * Không ngày nào được bật = khóa 24/7.
 */
data class WeeklySchedule(val days: List<DayWindow>) {
    init {
        require(days.size == 7) { "Lịch phải có đúng 7 ngày" }
    }

    val hasAnyWindow: Boolean get() = days.any { it.enabled }
    val enabledDayCount: Int get() = days.count { it.enabled }

    fun withDay(index: Int, window: DayWindow): WeeklySchedule =
        copy(days = days.toMutableList().also { it[index] = window })

    fun isUnlockedAt(time: LocalDateTime): Boolean =
        days[time.dayOfWeek.ordinal].contains(time.hour * 60 + time.minute)

    /** Mốc đầu/cuối khung giờ gần nhất sau [time] (trong 8 ngày tới), hoặc null nếu không có khung nào. */
    fun nextBoundaryAfter(time: LocalDateTime): LocalDateTime? {
        var best: LocalDateTime? = null
        val today = time.toLocalDate()
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            val window = days[date.dayOfWeek.ordinal]
            if (!window.enabled) continue
            for (minute in intArrayOf(window.startMin, window.endMin)) {
                val candidate = date.atStartOfDay().plusMinutes(minute.toLong())
                if (candidate.isAfter(time) && (best == null || candidate.isBefore(best))) {
                    best = candidate
                }
            }
        }
        return best
    }

    /** Mốc làm trạng thái mở/khóa thực sự đổi (bỏ qua mốc nối liền như 24:00 → 00:00). */
    fun nextStateChangeAfter(time: LocalDateTime): LocalDateTime? {
        val current = isUnlockedAt(time)
        var cursor = time
        repeat(MAX_STEPS) {
            val boundary = nextBoundaryAfter(cursor) ?: return null
            if (isUnlockedAt(boundary) != current) return boundary
            cursor = boundary
        }
        return null
    }

    companion object {
        private const val MAX_STEPS = 32
        val DEFAULT = WeeklySchedule(List(7) { DayWindow.DEFAULT })
    }
}

/** 0..1440 → "HH:mm" (1440 hiển thị là 24:00). */
fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)


package com.minhnhat.reclaim.domain

/** Tùy chọn chung. */
data class Options(
    val blockVolumeKeys: Boolean = true,
    val restoreVolume: Boolean = false,
    val tamperGuard: Boolean = false
)

/**
 * Hạn chế đa phương tiện: [enabled] là công tắc tổng, [schedule] là lịch MỞ KHÓA.
 * [grayscale] là tùy chọn con: chuyển màn hình sang đen trắng đúng trong giờ hạn chế (cùng lịch).
 */
data class MediaRule(
    val enabled: Boolean = false,
    val schedule: WeeklySchedule = WeeklySchedule.DEFAULT,
    val grayscale: Boolean = false
)

/** Toàn bộ cấu hình người dùng. Bất biến: mỗi thay đổi tạo một bản mới. */
data class Config(
    val lockedPackages: Set<String> = emptySet(),
    val appSchedules: Map<String, WeeklySchedule> = emptyMap(),
    val media: MediaRule = MediaRule(),
    val options: Options = Options()
) {
    fun scheduleOf(pkg: String): WeeklySchedule = appSchedules[pkg] ?: WeeklySchedule.DEFAULT

    fun hasSchedule(pkg: String): Boolean = appSchedules[pkg]?.hasAnyWindow == true

    fun withLocked(pkg: String, locked: Boolean): Config =
        copy(lockedPackages = if (locked) lockedPackages + pkg else lockedPackages - pkg)

    /** Lịch không có ngày nào được bật thì xóa hẳn (tương đương khóa 24/7). */
    fun withSchedule(pkg: String, schedule: WeeklySchedule): Config =
        copy(appSchedules = if (schedule.hasAnyWindow) appSchedules + (pkg to schedule) else appSchedules - pkg)
}

package com.minhnhat.reclaim.domain

import java.time.Duration
import java.time.LocalDateTime

/**
 * Toàn bộ logic "đang khóa hay không" nằm ở đây. Hàm thuần: nhận cấu hình + thời điểm, không đọc
 * đồng hồ, không chạm Android, nên unit test được. Mọi nơi gọi luôn tự tính theo giờ hiện tại,
 * không dựa vào cờ do timer đặt, vì vậy timer/alarm trễ cũng không làm sai kết quả.
 */
object PolicyEvaluator {

    fun isAppLocked(config: Config, pkg: String, now: LocalDateTime): Boolean {
        if (pkg !in config.lockedPackages) return false
        val schedule = config.appSchedules[pkg] ?: return true
        return !schedule.isUnlockedAt(now)
    }

    fun isMediaRestricted(config: Config, now: LocalDateTime): Boolean {
        return config.media.enabled && !config.media.schedule.isUnlockedAt(now)
    }

    /** Thang độ xám chỉ bật khi hạn chế đa phương tiện đang bật, tùy chọn được tích và đang trong giờ hạn chế. */
    fun isGrayscaleActive(config: Config, now: LocalDateTime): Boolean =
        config.media.grayscale && isMediaRestricted(config, now)

    /** Số ms đến lúc cần đánh giá lại trạng thái khóa của [pkg]; null nếu không có mốc nào. */
    fun millisUntilAppChange(config: Config, pkg: String, now: LocalDateTime): Long? {
        val schedule = config.appSchedules[pkg] ?: return null
        val next = schedule.nextBoundaryAfter(now) ?: return null
        return Duration.between(now, next).toMillis() + SAFETY_MS
    }

    fun millisUntilMediaChange(config: Config, now: LocalDateTime): Long? {
        if (!config.media.enabled) return null
        val next = config.media.schedule.nextBoundaryAfter(now) ?: return null
        return Duration.between(now, next).toMillis() + SAFETY_MS
    }

    private const val SAFETY_MS = 200L
}

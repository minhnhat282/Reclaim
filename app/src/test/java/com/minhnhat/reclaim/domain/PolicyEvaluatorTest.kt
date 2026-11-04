package com.minhnhat.reclaim.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PolicyEvaluatorTest {

    // 2026-11-02 là Thứ Hai
    private val monday = LocalDateTime.of(2026, 11, 2, 0, 0)

    private fun at(dayOffset: Int, hour: Int, minute: Int = 0): LocalDateTime =
        monday.plusDays(dayOffset.toLong()).withHour(hour).withMinute(minute)

    /** Thứ Hai-Thứ Sáu mở khóa từ [start] đến [end]; cuối tuần khóa cả ngày. */
    private fun weekdays(start: Int, end: Int) = WeeklySchedule(
        List(7) { i -> if (i < 5) DayWindow(true, start, end) else DayWindow.DEFAULT }
    )

    private fun config(schedule: WeeklySchedule? = null) = Config(
        lockedPackages = setOf("a"),
        appSchedules = if (schedule != null) mapOf("a" to schedule) else emptyMap()
    )

    @Test
    fun withoutSchedule_lockedAllDay() {
        assertTrue(PolicyEvaluator.isAppLocked(config(), "a", at(0, 12)))
        assertTrue(PolicyEvaluator.isAppLocked(config(), "a", at(3, 3)))
    }

    @Test
    fun packageNotInLockedSet_neverLocked() {
        assertFalse(PolicyEvaluator.isAppLocked(config(), "b", at(0, 12)))
    }

    @Test
    fun windowBoundaries_startInclusive_endExclusive() {
        val c = config(weekdays(7 * 60, 18 * 60))
        assertTrue(PolicyEvaluator.isAppLocked(c, "a", at(0, 6, 59)))
        assertFalse(PolicyEvaluator.isAppLocked(c, "a", at(0, 7, 0)))
        assertFalse(PolicyEvaluator.isAppLocked(c, "a", at(0, 17, 59)))
        assertTrue(PolicyEvaluator.isAppLocked(c, "a", at(0, 18, 0)))
    }

    @Test
    fun dayWithoutWindow_lockedAllDay() {
        val c = config(weekdays(7 * 60, 18 * 60))
        assertTrue(PolicyEvaluator.isAppLocked(c, "a", at(5, 12))) // Thứ Bảy
    }

    @Test
    fun scheduleWithNoEnabledDay_isLockedAllDay() {
        assertTrue(PolicyEvaluator.isAppLocked(config(WeeklySchedule.DEFAULT), "a", at(0, 12)))
    }

    @Test
    fun windowEndingAtMidnight_coversLastMinute() {
        val c = config(weekdays(0, 1440))
        assertFalse(PolicyEvaluator.isAppLocked(c, "a", at(0, 23, 59)))
        assertFalse(PolicyEvaluator.isAppLocked(c, "a", at(1, 0, 0)))
    }

    @Test
    fun mediaDisabled_neverRestricted() {
        val c = Config(media = MediaRule(enabled = false))
        assertFalse(PolicyEvaluator.isMediaRestricted(c, at(0, 12)))
    }

    @Test
    fun mediaEnabled_followsSchedule() {
        val c = Config(media = MediaRule(enabled = true, schedule = weekdays(7 * 60, 18 * 60)))
        assertFalse(PolicyEvaluator.isMediaRestricted(c, at(0, 12)))
        assertTrue(PolicyEvaluator.isMediaRestricted(c, at(0, 20)))
        assertTrue(PolicyEvaluator.isMediaRestricted(c, at(6, 12))) // Chủ Nhật
    }

    @Test
    fun nextStateChange_pointsAtNextWindowEdge() {
        val s = weekdays(7 * 60, 18 * 60)
        assertEquals(at(0, 18), s.nextStateChangeAfter(at(0, 12)))
        assertEquals(at(1, 7), s.nextStateChangeAfter(at(0, 19)))
        // Thứ Sáu tối → Thứ Hai 07:00 (cuối tuần khóa cả ngày)
        assertEquals(at(7, 7), s.nextStateChangeAfter(at(4, 19)))
    }

    @Test
    fun nextStateChange_nullWhenNoWindow() {
        assertNull(WeeklySchedule.DEFAULT.nextStateChangeAfter(at(0, 12)))
    }

    @Test
    fun contiguousWindows_midnightIsNotAStateChange() {
        val s = weekdays(0, 1440)
        // Thứ Hai 12:00: mở khóa liền mạch đến hết Thứ Sáu, đổi trạng thái đầu tiên là Thứ Bảy 00:00
        assertEquals(at(5, 0), s.nextStateChangeAfter(at(0, 12)))
    }

    @Test
    fun grayscale_offWhenOptionOrMasterIsOff() {
        val s = weekdays(7 * 60, 18 * 60)
        // Tùy chọn tắt: không bao giờ bật dù đang trong giờ hạn chế
        assertFalse(PolicyEvaluator.isGrayscaleActive(Config(media = MediaRule(true, s, grayscale = false)), at(0, 20)))
        // Công tắc tổng tắt: không bật dù tùy chọn được tích
        assertFalse(PolicyEvaluator.isGrayscaleActive(Config(media = MediaRule(false, s, grayscale = true)), at(0, 20)))
    }

    @Test
    fun grayscale_followsMediaSchedule() {
        val c = Config(media = MediaRule(true, weekdays(7 * 60, 18 * 60), grayscale = true))
        assertFalse(PolicyEvaluator.isGrayscaleActive(c, at(0, 12)))  // giờ được phép
        assertTrue(PolicyEvaluator.isGrayscaleActive(c, at(0, 18, 0))) // đúng giờ bắt đầu hạn chế
        assertTrue(PolicyEvaluator.isGrayscaleActive(c, at(0, 6, 59)))
        assertTrue(PolicyEvaluator.isGrayscaleActive(c, at(5, 12)))   // Thứ Bảy khóa cả ngày
    }

    @Test
    fun grayscale_allDayWhenNoWindowEnabled() {
        val c = Config(media = MediaRule(true, WeeklySchedule.DEFAULT, grayscale = true))
        assertTrue(PolicyEvaluator.isGrayscaleActive(c, at(2, 12)))
    }
}

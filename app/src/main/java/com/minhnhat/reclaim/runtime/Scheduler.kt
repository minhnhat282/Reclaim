package com.minhnhat.reclaim.runtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.minhnhat.reclaim.domain.Config
import com.minhnhat.reclaim.platform.Compat
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Đặt báo thức đúng mốc đổi trạng thái của lịch đa phương tiện (đánh thức được cả khi máy ngủ sâu).
 * Khóa app không cần báo thức: chỉ app đang ở foreground mới quan trọng và nó tự hẹn giờ trong tiến trình.
 */
class Scheduler(context: Context) {
    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)

    fun scheduleMedia(config: Config, now: LocalDateTime) {
        val operation = pendingIntent()
        val next = if (config.media.enabled) config.media.schedule.nextBoundaryAfter(now) else null
        if (next == null) {
            alarmManager.cancel(operation)
            return
        }
        val atMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 200
        Compat.setExactAlarm(alarmManager, atMillis, operation)
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_MEDIA,
        Intent(context, SystemEventsReceiver::class.java).setAction(SystemEventsReceiver.ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private companion object {
        const val REQUEST_MEDIA = 1
    }
}

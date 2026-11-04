package com.minhnhat.reclaim.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.minhnhat.reclaim.R
import com.minhnhat.reclaim.ui.MainActivity

/** Thông báo thường trực của GuardService (bắt buộc với foreground service). */
object Notifications {
    const val CHANNEL_GUARD = "guard"
    const val ID_GUARD = 1001

    enum class GuardNote { NORMAL, FALLBACK, FALLBACK_NO_PERMISSION }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_GUARD,
            context.getString(R.string.channel_guard),
            NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    fun guard(context: Context, note: GuardNote): Notification {
        val (title, text) = when (note) {
            GuardNote.NORMAL -> R.string.notif_normal_title to R.string.notif_normal_text
            GuardNote.FALLBACK -> R.string.notif_fallback_title to R.string.notif_fallback_text
            GuardNote.FALLBACK_NO_PERMISSION -> R.string.notif_noperm_title to R.string.notif_noperm_text
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(context, CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_stat_reclaim)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }
}

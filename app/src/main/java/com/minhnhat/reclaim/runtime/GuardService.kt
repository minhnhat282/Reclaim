package com.minhnhat.reclaim.runtime

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.minhnhat.reclaim.blocking.UsageStatsBlocker
import com.minhnhat.reclaim.domain.Config
import com.minhnhat.reclaim.graph
import com.minhnhat.reclaim.platform.Compat
import com.minhnhat.reclaim.platform.Notifications
import com.minhnhat.reclaim.platform.Notifications.GuardNote

/**
 * Foreground service luôn chạy khi có tính năng đang cấu hình. Nhiệm vụ:
 *  1. Giữ tiến trình sống (OEM khó giết hơn).
 *  2. Chạy chế độ dự phòng khóa app (UsageStats + overlay) khi Trợ năng bị tắt.
 *  3. Cho MediaGuard một chỗ bám để giữ các listener âm thanh.
 */
class GuardService : Service() {

    private lateinit var fallback: UsageStatsBlocker
    private var note = GuardNote.NORMAL

    private val stateListener: () -> Unit = { refresh() }
    private val configListener: (Config) -> Unit = { refresh() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        fallback = UsageStatsBlocker(this, graph)
        Compat.startForeground(this, Notifications.ID_GUARD, Notifications.guard(this, note))
        graph.serviceState.addListener(stateListener)
        graph.configStore.addListener(configListener)
        graph.mediaGuard.acquire(HOLDER)
        refresh()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Mỗi lần được startForegroundService phải gọi lại startForeground.
        Compat.startForeground(this, Notifications.ID_GUARD, Notifications.guard(this, note))
        refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        graph.serviceState.removeListener(stateListener)
        graph.configStore.removeListener(configListener)
        fallback.stop()
        graph.mediaGuard.release(HOLDER)
        super.onDestroy()
    }

    /** Chọn chế độ theo trạng thái Trợ năng và cập nhật thông báo cho khớp. */
    private fun refresh() {
        val config = graph.configStore.current
        val needFallback = !graph.serviceState.accessibilityConnected && config.lockedPackages.isNotEmpty()
        val fallbackRunning = if (needFallback) fallback.start() else {
            fallback.stop()
            false
        }
        val newNote = when {
            !needFallback -> GuardNote.NORMAL
            fallbackRunning -> GuardNote.FALLBACK
            else -> GuardNote.FALLBACK_NO_PERMISSION
        }
        if (newNote != note) {
            note = newNote
            getSystemService(NotificationManager::class.java)
                .notify(Notifications.ID_GUARD, Notifications.guard(this, note))
        }
    }

    private companion object {
        const val HOLDER = "guard-service"
    }
}

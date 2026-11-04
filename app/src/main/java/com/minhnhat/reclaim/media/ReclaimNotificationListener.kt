package com.minhnhat.reclaim.media

import android.service.notification.NotificationListenerService
import com.minhnhat.reclaim.graph

/**
 * Không đọc nội dung thông báo. Dịch vụ này tồn tại chỉ vì Android yêu cầu quyền "Truy cập thông báo"
 * để một app được phép liệt kê và điều khiển các MediaSession đang hoạt động.
 */
class ReclaimNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        graph.serviceState.setListenerConnected(true)
        graph.mediaGuard.acquire(HOLDER)
        graph.mediaGuard.refreshSessions()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        graph.serviceState.setListenerConnected(false)
        graph.mediaGuard.onSessionAccessLost()
        graph.mediaGuard.release(HOLDER)
    }

    private companion object {
        const val HOLDER = "notification-listener"
    }
}

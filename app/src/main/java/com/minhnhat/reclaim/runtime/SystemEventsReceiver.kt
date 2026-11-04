package com.minhnhat.reclaim.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minhnhat.reclaim.graph

/**
 * Điểm vào khi process có thể đã chết: khởi động máy, cập nhật app, đổi giờ/múi giờ, và báo thức lịch.
 * Luôn đánh giá lại chính sách đa phương tiện và đặt lại báo thức kế tiếp.
 */
class SystemEventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.graph
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                GuardController.sync(context, graph.configStore.current)
        }
        graph.mediaGuard.evaluate()
    }

    companion object {
        const val ACTION_TICK = "com.minhnhat.reclaim.action.SCHEDULE_TICK"
    }
}

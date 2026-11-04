package com.minhnhat.reclaim.blocking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import com.minhnhat.reclaim.AppGraph
import com.minhnhat.reclaim.domain.PolicyEvaluator
import com.minhnhat.reclaim.platform.Permissions
import java.time.LocalDateTime

/**
 * Chế độ dự phòng khi Trợ năng bị tắt: hỏi UsageStats vài lần mỗi giây (chỉ khi màn hình bật)
 * để biết app foreground, rồi hiện cùng overlay (loại TYPE_APPLICATION_OVERLAY).
 * Chậm hơn đường chính khoảng 100-300 ms.
 */
class UsageStatsBlocker(private val context: Context, private val graph: AppGraph) {

    private val main = Handler(Looper.getMainLooper())
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var overlay: OverlayController? = null

    @Volatile
    private var running = false
    private var lastQueryEnd = 0L
    private var currentPackage: String? = null
    private var lastLock = false

    /** Bắt đầu chạy. Trả về false nếu thiếu quyền (Usage access hoặc Hiển thị trên ứng dụng khác). */
    fun start(): Boolean {
        if (running) return true
        if (!Permissions.hasUsageAccess(context) || !Permissions.canDrawOverlays(context)) return false

        val controller = OverlayController(context, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        if (!controller.attach()) return false
        overlay = controller

        resetState()
        val newThread = HandlerThread("reclaim-usage").also { it.start() }
        thread = newThread
        val handler = Handler(newThread.looper)
        worker = handler
        running = true
        handler.post(poll)
        return true
    }

    fun stop() {
        if (!running && thread == null && overlay == null) return
        running = false
        worker?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        worker = null
        overlay?.release()
        overlay = null
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            var delay = POLL_MS
            try {
                if (!power.isInteractive) {
                    // Màn hình tắt: ẩn overlay, quên trạng thái để truy vấn lại từ đầu khi bật.
                    setLock(false)
                    resetState()
                    delay = IDLE_MS
                } else {
                    readForeground()
                    decide()
                }
            } catch (e: Exception) {
                // Bỏ qua lỗi tạm thời (mất quyền giữa chừng...), vòng sau thử lại.
            }
            worker?.postDelayed(this, delay)
        }
    }

    private fun resetState() {
        lastQueryEnd = 0L
        currentPackage = null
        lastLock = false
    }

    private fun readForeground() {
        val now = System.currentTimeMillis()
        val from = if (lastQueryEnd == 0L) now - INITIAL_LOOKBACK_MS else lastQueryEnd
        val events = usage.queryEvents(from, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) currentPackage = event.packageName
        }
        // Chừa chồng lấn nhỏ để không lỡ sự kiện ghi trễ; xử lý lặp lại không gây hại.
        lastQueryEnd = now - OVERLAP_MS
    }

    private fun decide() {
        val pkg = currentPackage ?: return
        val lock = pkg != context.packageName &&
            PolicyEvaluator.isAppLocked(graph.configStore.current, pkg, LocalDateTime.now())
        setLock(lock)
    }

    private fun setLock(lock: Boolean) {
        if (lock == lastLock) return
        lastLock = lock
        main.post { if (lock) overlay?.show() else overlay?.hide() }
    }

    private companion object {
        const val POLL_MS = 250L
        const val IDLE_MS = 2_000L
        // Tra ngược đủ dài để thấy app đã nằm ở foreground từ lâu (không có sự kiện RESUMED mới).
        const val INITIAL_LOOKBACK_MS = 24 * 60 * 60 * 1000L
        const val OVERLAP_MS = 500L
    }
}

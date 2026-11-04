package com.minhnhat.reclaim.blocking

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.minhnhat.reclaim.domain.PolicyEvaluator
import com.minhnhat.reclaim.graph
import com.minhnhat.reclaim.platform.Compat
import com.minhnhat.reclaim.runtime.GuardController
import java.time.LocalDateTime

/**
 * Đường chính của khóa ứng dụng. Mỗi sự kiện cửa sổ: xác định app foreground, hỏi PolicyEvaluator,
 * rồi hiện/ẩn overlay dựng sẵn. Ngoài ra chặn phím âm lượng trong giờ hạn chế đa phương tiện.
 */
class LockAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val reevaluate = Runnable { evaluate() }
    private val throttled = Runnable {
        throttlePending = false
        evaluate()
    }
    private val tracker = ForegroundTracker()
    private var overlay: OverlayController? = null
    private var contentEventsOn = false
    private var lastEvaluatedAt = 0L
    private var throttlePending = false
    private var connected = false
    private var appLabel = ""

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            evaluate()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        appLabel = applicationInfo.loadLabel(packageManager).toString()

        overlay = OverlayController(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY).also { it.attach() }

        // Màn hình bật lại / mở khóa / đổi giờ: app foreground có thể đã đổi trạng thái khóa mà không có sự kiện cửa sổ.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        Compat.registerReceiver(this, systemReceiver, filter)

        graph.mediaGuard.acquire(HOLDER)
        graph.serviceState.setAccessibilityConnected(true)
        GuardController.sync(this, graph.configStore.current)
        evaluate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        tracker.onEvent(event)
        // Sự kiện đổi nội dung (chỉ bật khi đang ở Settings/trình cài đặt) bị giới hạn tần suất:
        // tối đa một lần đánh giá mỗi CONTENT_THROTTLE_MS. Đây là throttle (không phải debounce): sự kiện
        // đến dồn dập vẫn được đánh giá đều đặn, không bị lùi mãi.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val elapsed = SystemClock.uptimeMillis() - lastEvaluatedAt
            if (elapsed < CONTENT_THROTTLE_MS) {
                if (!throttlePending) {
                    throttlePending = true
                    handler.postDelayed(throttled, CONTENT_THROTTLE_MS - elapsed)
                }
                return
            }
        }
        evaluate()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        val code = event?.keyCode ?: return false
        val isVolumeKey = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
        return isVolumeKey && graph.mediaGuard.shouldBlockVolumeKey()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (!connected) return
        connected = false
        throttlePending = false
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(systemReceiver)
        } catch (e: Exception) {
            // Chưa đăng ký hoặc đã gỡ
        }
        overlay?.release()
        overlay = null
        graph.mediaGuard.release(HOLDER)
        graph.serviceState.setAccessibilityConnected(false)
        // Cố bật chế độ dự phòng ngay; nếu hệ thống chặn thì GuardService (đang chạy sẵn) tự xử lý.
        GuardController.sync(this, graph.configStore.current)
    }

    private fun evaluate() {
        val pkg = tracker.resolve(this) ?: return
        handler.removeCallbacks(reevaluate)
        lastEvaluatedAt = SystemClock.uptimeMillis()

        if (pkg == packageName) {
            setContentEvents(false)
            overlay?.hide()
            return
        }

        val config = graph.configStore.current
        val now = LocalDateTime.now()
        var lock = PolicyEvaluator.isAppLocked(config, pkg, now)
        var wakeMillis = -1L
        if (pkg in config.lockedPackages) {
            wakeMillis = PolicyEvaluator.millisUntilAppChange(config, pkg, now) ?: -1L
        }

        val guarded = config.options.tamperGuard && pkg in TamperGuard.PACKAGES
        setContentEvents(guarded)
        if (guarded) {
            val nowMillis = System.currentTimeMillis()
            val tamper = graph.tamperGuard
            if (tamper.isPaused(nowMillis)) {
                wakeMillis = tamper.pausedUntil - nowMillis + 200
            } else {
                // Chỉ ở Settings/trình gỡ cài đặt mới cần soi nội dung màn hình.
                @Suppress("DEPRECATION")
                val root = rootInActiveWindow
                if (root != null) {
                    if (TamperGuard.mentions(root, appLabel, packageName)) lock = true
                    @Suppress("DEPRECATION")
                    root.recycle()
                }
            }
        }

        if (lock) overlay?.show() else overlay?.hide()
        if (wakeMillis > 0) handler.postDelayed(reevaluate, wakeMillis)
    }

    private fun setContentEvents(on: Boolean) {
        if (on == contentEventsOn) return
        contentEventsOn = on
        val info = serviceInfo ?: return
        info.eventTypes = BASE_EVENTS or (if (on) AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED else 0)
        serviceInfo = info
    }

    private companion object {
        const val HOLDER = "accessibility"
        const val CONTENT_THROTTLE_MS = 300L
        const val BASE_EVENTS =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }
}

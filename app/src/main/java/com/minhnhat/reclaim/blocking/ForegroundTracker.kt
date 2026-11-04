package com.minhnhat.reclaim.blocking

import android.accessibilityservice.AccessibilityService
import android.util.SparseArray
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Xác định app đang ở foreground mà không phải hỏi app về cây giao diện của nó:
 *  - nguồn sự thật là cửa sổ "active" do hệ thống cung cấp;
 *  - package của cửa sổ lấy từ map windowId → package, được nạp từ TYPE_WINDOW_STATE_CHANGED;
 *  - chỉ khi chưa có trong map mới hỏi rootInActiveWindow một lần.
 */
class ForegroundTracker {

    private val packageByWindow = SparseArray<String>()

    fun onEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        packageByWindow.put(event.windowId, pkg)
    }

    /**
     * Trả về package của app đang foreground, hoặc null nếu cửa sổ active không phải cửa sổ ứng dụng
     * (bảng thông báo, bàn phím, menu nguồn...) hoặc chưa xác định được: khi đó giữ nguyên trạng thái cũ.
     */
    @Suppress("DEPRECATION")
    fun resolve(service: AccessibilityService): String? {
        var activeId = -1
        var activeIsApp = false
        val liveIds = HashSet<Int>()
        // Một số ROM trả về null khi dịch vụ đang ngắt/kết nối lại: coi như chưa xác định, giữ nguyên trạng thái.
        val windows: List<AccessibilityWindowInfo> = service.windows ?: return null
        for (window in windows) {
            liveIds.add(window.id)
            if (window.isActive) {
                activeId = window.id
                activeIsApp = window.type == AccessibilityWindowInfo.TYPE_APPLICATION
            }
            window.recycle()
        }
        pruneStale(liveIds)
        if (activeId == -1 || !activeIsApp) return null

        var pkg = packageByWindow.get(activeId)
        if (pkg == null) {
            val root = service.rootInActiveWindow
            pkg = root?.packageName?.toString()
            root?.recycle()
            if (pkg != null) packageByWindow.put(activeId, pkg)
        }
        return pkg
    }

    /** windowId có thể bị hệ thống tái sử dụng, nên bỏ các mục của cửa sổ đã biến mất. */
    private fun pruneStale(liveIds: Set<Int>) {
        for (i in packageByWindow.size() - 1 downTo 0) {
            if (packageByWindow.keyAt(i) !in liveIds) packageByWindow.removeAt(i)
        }
    }
}

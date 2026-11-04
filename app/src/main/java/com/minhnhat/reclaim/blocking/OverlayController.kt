package com.minhnhat.reclaim.blocking

import android.content.Context
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager

/**
 * Cửa sổ overlay toàn màn hình được tạo SẴN một lần. "Hiện/ẩn" chỉ là đổi alpha và cờ xuyên chạm
 * bằng updateViewLayout, nhanh hơn nhiều so với thêm/gỡ cửa sổ lúc có sự kiện.
 *
 * - FLAG_NOT_FOCUSABLE: Back/Home/Recents và thanh hệ thống vẫn thao tác bình thường.
 * - Dùng chung cho đường chính (TYPE_ACCESSIBILITY_OVERLAY) và dự phòng (TYPE_APPLICATION_OVERLAY).
 *
 * Chỉ gọi từ main thread.
 */
class OverlayController(private val context: Context, private val windowType: Int) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    var isShown: Boolean = false
        private set

    /** Dựng và thêm cửa sổ ở trạng thái ẩn. Trả về false nếu hệ thống từ chối (thiếu quyền...). */
    fun attach(): Boolean {
        if (view != null) return true
        val newView = OverlayView.create(context)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.alpha = 0f
        lp.title = "ReclaimOverlay"
        return try {
            windowManager.addView(newView, lp)
            view = newView
            params = lp
            isShown = false
            true
        } catch (e: Exception) {
            false
        }
    }

    fun show() {
        if (!isShown) apply(true)
    }

    fun hide() {
        if (isShown) apply(false)
    }

    fun release() {
        val v = view
        view = null
        params = null
        isShown = false
        if (v != null) {
            try {
                windowManager.removeView(v)
            } catch (e: Exception) {
                // Cửa sổ đã bị hệ thống gỡ
            }
        }
    }

    private fun apply(visible: Boolean) {
        if (view == null && !attach()) return
        if (tryUpdate(visible)) return
        // Cửa sổ bị hệ thống gỡ mất (một số ROM): dựng lại rồi thử thêm một lần.
        release()
        if (attach()) tryUpdate(visible)
    }

    private fun tryUpdate(visible: Boolean): Boolean {
        val v = view ?: return false
        val lp = params ?: return false
        if (visible) {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            lp.alpha = 1f
        } else {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            lp.alpha = 0f
        }
        return try {
            windowManager.updateViewLayout(v, lp)
            isShown = visible
            true
        } catch (e: Exception) {
            false
        }
    }
}

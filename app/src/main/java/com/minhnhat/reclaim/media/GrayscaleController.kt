package com.minhnhat.reclaim.media

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import com.minhnhat.reclaim.platform.Permissions

/**
 * Bật/tắt thang độ xám toàn hệ thống bằng cách ghi cài đặt "Chỉnh màu" (daltonizer) ở chế độ đơn sắc.
 * Android không có API công khai nào khác cho app thường, nên cần quyền WRITE_SECURE_SETTINGS
 * (cấp một lần bằng ADB, xem [Permissions.adbGrantCommand]).
 *
 * Quy tắc an toàn:
 *  - Chỉ khôi phục những gì CHÍNH Reclaim đã đổi: giá trị chỉnh màu cũ được lưu lại (bền qua việc process chết)
 *    và đặt lại khi hết giờ hạn chế. Nếu người dùng vốn đã bật thang độ xám từ trước thì Reclaim không đụng vào.
 *  - [apply] idempotent và rẻ (chỉ đọc hai giá trị), nên gọi mỗi lần đánh giá chính sách đều được.
 *  - Thiếu quyền thì không làm gì (không crash); giao diện có banner nhắc cấp quyền.
 *
 * Gọi từ main thread.
 */
class GrayscaleController(context: Context) {

    private val app = context.applicationContext
    private val resolver: ContentResolver = app.contentResolver
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var observer: ContentObserver? = null

    /** [active] = true: ép thang độ xám; false: trả lại trạng thái trước khi Reclaim can thiệp (nếu có). */
    fun apply(active: Boolean) {
        try {
            if (active) enable() else disable()
        } catch (e: SecurityException) {
            // Quyền bị thu hồi giữa chừng
        }
    }

    /**
     * Theo dõi khi người dùng tự đổi cài đặt chỉnh màu (ví dụ tắt từ ô Cài đặt nhanh) để [callback]
     * kịp kéo lại trong giờ hạn chế. Lệnh ghi của chính Reclaim cũng kích hoạt callback nhưng vô hại
     * vì [apply] không ghi lại khi trạng thái đã đúng.
     */
    fun startObserving(handler: Handler, callback: () -> Unit) {
        if (observer != null) return
        val created = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                callback()
            }
        }
        observer = created
        resolver.registerContentObserver(Settings.Secure.getUriFor(KEY_ENABLED), false, created)
        resolver.registerContentObserver(Settings.Secure.getUriFor(KEY_MODE), false, created)
    }

    fun stopObserving() {
        observer?.let { resolver.unregisterContentObserver(it) }
        observer = null
    }

    // commit() (đồng bộ) có chủ đích: phải lưu giá trị cũ xuống đĩa TRƯỚC khi ghi cài đặt hệ thống,
    // nếu process chết giữa chừng thì lần sau vẫn khôi phục đúng.
    @SuppressLint("ApplySharedPref")
    private fun enable() {
        if (!Permissions.canWriteSecureSettings(app)) return
        val enabled = Settings.Secure.getInt(resolver, KEY_ENABLED, 0)
        val mode = Settings.Secure.getInt(resolver, KEY_MODE, MODE_DEFAULT)
        if (enabled == 1 && mode == MODE_MONOCHROMACY) return // đã đen trắng (của ta hoặc của người dùng)

        if (!prefs.getBoolean(KEY_APPLIED, false)) {
            prefs.edit()
                .putInt(KEY_PREV_ENABLED, enabled)
                .putInt(KEY_PREV_MODE, mode)
                .putBoolean(KEY_APPLIED, true)
                .commit()
        }
        // Đặt kiểu trước rồi mới bật, để không có khoảnh khắc hiện sai màu.
        Settings.Secure.putInt(resolver, KEY_MODE, MODE_MONOCHROMACY)
        Settings.Secure.putInt(resolver, KEY_ENABLED, 1)
    }

    @SuppressLint("ApplySharedPref")
    private fun disable() {
        if (!prefs.getBoolean(KEY_APPLIED, false)) return
        // Chưa có quyền để trả lại: giữ cờ "đã áp dụng" để lần có quyền sau còn khôi phục được.
        if (!Permissions.canWriteSecureSettings(app)) return

        val prevEnabled = prefs.getInt(KEY_PREV_ENABLED, 0)
        val prevMode = prefs.getInt(KEY_PREV_MODE, MODE_DEFAULT)
        prefs.edit().putBoolean(KEY_APPLIED, false).commit()
        if (prevEnabled == 0) {
            Settings.Secure.putInt(resolver, KEY_ENABLED, 0)
            Settings.Secure.putInt(resolver, KEY_MODE, prevMode)
        } else {
            Settings.Secure.putInt(resolver, KEY_MODE, prevMode)
            Settings.Secure.putInt(resolver, KEY_ENABLED, 1)
        }
    }

    private companion object {
        const val PREFS = "reclaim_display"
        const val KEY_APPLIED = "applied"
        const val KEY_PREV_ENABLED = "prev_enabled"
        const val KEY_PREV_MODE = "prev_mode"

        // Tên khóa trong Settings.Secure (ẩn khỏi SDK công khai nhưng ổn định từ Android 8).
        const val KEY_ENABLED = "accessibility_display_daltonizer_enabled"
        const val KEY_MODE = "accessibility_display_daltonizer"

        /** AccessibilityManager.DALTONIZER_SIMULATE_MONOCHROMACY: hiển thị đơn sắc = thang độ xám. */
        const val MODE_MONOCHROMACY = 0

        /** Giá trị mặc định của framework khi chưa có khóa (deuteranomaly). */
        const val MODE_DEFAULT = 12
    }
}

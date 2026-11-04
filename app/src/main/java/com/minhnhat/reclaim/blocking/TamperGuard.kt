package com.minhnhat.reclaim.blocking

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Chống tự vượt rào: khi người dùng vào màn hình Cài đặt/trình gỡ cài đặt có nhắc đến Reclaim
 * (tắt Trợ năng, gỡ app, buộc dừng...) thì che bằng overlay. Có thể tạm dừng trong RAM
 * (process chết thì bảo vệ tự bật lại).
 */
class TamperGuard {

    @Volatile
    var pausedUntil: Long = 0L
        private set

    fun isPaused(nowMillis: Long): Boolean = nowMillis < pausedUntil

    fun pauseFor(millis: Long) {
        pausedUntil = System.currentTimeMillis() + millis
    }

    companion object {
        /** Các app hệ thống mà ta soi nội dung. */
        val PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.securitycenter"
        )

        /** Màn hình đang hiện tên app hoặc package name của Reclaim (tùy ROM hiển thị kiểu nào). */
        @Suppress("DEPRECATION")
        fun mentions(root: AccessibilityNodeInfo, appLabel: String, packageName: String): Boolean {
            // Chuỗi rỗng có thể khớp mọi node trên màn hình: bỏ qua khi nhãn/package trống.
            if (appLabel.isNotBlank()) {
                val byLabel = root.findAccessibilityNodeInfosByText(appLabel)
                val hasLabel = byLabel.isNotEmpty()
                byLabel.forEach { it.recycle() }
                if (hasLabel) return true
            }
            if (packageName.isBlank()) return false

            val byPackage = root.findAccessibilityNodeInfosByText(packageName)
            val hasPackage = byPackage.isNotEmpty()
            byPackage.forEach { it.recycle() }
            return hasPackage
        }
    }
}

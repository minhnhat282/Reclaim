package com.minhnhat.reclaim.runtime

import android.content.Context
import android.content.Intent
import com.minhnhat.reclaim.domain.Config

/** Bật/tắt [GuardService] tùy theo việc có tính năng nào đang được cấu hình hay không. */
object GuardController {

    fun isNeeded(config: Config): Boolean = config.lockedPackages.isNotEmpty() || config.media.enabled

    /**
     * Gọi từ nơi được phép khởi chạy foreground service (Activity đang hiện, BOOT_COMPLETED...).
     * Gọi lại khi service đang chạy sẽ chỉ làm nó đánh giá lại trạng thái.
     */
    fun sync(context: Context, config: Config) {
        val intent = Intent(context, GuardService::class.java)
        try {
            if (isNeeded(config)) context.startForegroundService(intent) else context.stopService(intent)
        } catch (e: Exception) {
            // Bị chặn khởi chạy từ nền: lần mở app / khởi động máy kế tiếp sẽ thử lại.
        }
    }
}

package com.minhnhat.reclaim

import android.app.Application
import com.minhnhat.reclaim.blocking.TamperGuard
import com.minhnhat.reclaim.data.ConfigStore
import com.minhnhat.reclaim.data.InstalledApps
import com.minhnhat.reclaim.data.PinManager
import com.minhnhat.reclaim.data.VolumeMemory
import com.minhnhat.reclaim.media.GrayscaleController
import com.minhnhat.reclaim.media.MediaGuard
import com.minhnhat.reclaim.runtime.GuardController
import com.minhnhat.reclaim.runtime.Scheduler
import com.minhnhat.reclaim.runtime.ServiceState

/**
 * Bộ dựng phụ thuộc thủ công: mọi thành phần dùng chung được tạo một lần ở đây
 * (Activity, Service và Receiver cùng một process nên dùng chung được).
 */
class AppGraph(val app: Application) {
    val configStore = ConfigStore(app)
    val serviceState = ServiceState()
    val tamperGuard = TamperGuard()
    val pin = PinManager(app)
    val installedApps = InstalledApps(app)
    val scheduler = Scheduler(app)
    val grayscale = GrayscaleController(app)
    val mediaGuard = MediaGuard(app, configStore, VolumeMemory(app), scheduler, grayscale)

    init {
        // Mỗi lần cấu hình đổi: bật/tắt GuardService cho khớp và áp dụng lại chính sách media.
        configStore.addListener { config ->
            GuardController.sync(app, config)
            mediaGuard.onConfigChanged()
        }
    }
}

package com.minhnhat.reclaim.platform

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * MỌI nhánh theo phiên bản Android nằm ở đây (và ở [Permissions] cho phần quyền).
 * Khi nâng minSdk, chỉ cần xóa nhánh cũ trong file này, phần còn lại của app không phải sửa.
 */
object Compat {

    /**
     * Android 13+ bắt buộc chỉ rõ cờ export khi đăng ký receiver động. Bỏ nhánh khi minSdk >= 33.
     * Nhánh cũ (< 13) không có cờ nên lint báo nhầm; receiver ở đây chỉ nhận broadcast hệ thống.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerReceiver(
        context: Context,
        receiver: BroadcastReceiver,
        filter: IntentFilter,
        exported: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flag = if (exported) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED
            context.registerReceiver(receiver, filter, flag)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    /** Android 14+ yêu cầu khai báo loại foreground service khi gọi startForeground. Bỏ nhánh khi minSdk >= 34. */
    fun startForeground(service: Service, id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            service.startForeground(id, notification)
        }
    }

    /** Android 12+ có thể thu hồi quyền báo thức chính xác. Bỏ nhánh khi minSdk >= 31. */
    fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Đặt báo thức đúng giờ, tự hạ xuống báo thức gần đúng nếu không được phép. */
    fun setExactAlarm(alarmManager: AlarmManager, atMillis: Long, operation: PendingIntent) {
        try {
            if (canScheduleExactAlarms(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    /** Android 13+ có quyền thông báo lúc chạy. Bỏ nhánh khi minSdk >= 33. */
    val needsNotificationPermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Android 13+ chặn bật Trợ năng/Truy cập thông báo cho app cài ngoài cho đến khi cho phép "cài đặt bị hạn chế". */
    val hasRestrictedSettings: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Khóa nhận diện thiết bị đầu ra đang phát nhạc (loa / tai nghe có dây / Bluetooth...).
     * Từ Android 11, âm lượng STREAM_MUSIC được nhớ riêng theo từng thiết bị đầu ra.
     */
    fun activeOutputKey(audio: AudioManager): String {
        val device: AudioDeviceInfo? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                audio.getAudioDevicesForAttributes(MEDIA_ATTRIBUTES).firstOrNull()
            } else {
                pickOutputHeuristically(audio)
            }
        return device?.let { "${it.type}:${it.address}" } ?: "speaker"
    }

    private val MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
        .build()

    // Android 11-12 chưa có API công khai để hỏi thiết bị đang phát: chọn theo thứ tự ưu tiên thông dụng.
    // TYPE_BLE_* là hằng số API 31 nhưng được trình biên dịch chép thẳng giá trị vào mã nên an toàn trên API 30.
    @SuppressLint("InlinedApi")
    private val OUTPUT_PRIORITY = intArrayOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES
    )

    private fun pickOutputHeuristically(audio: AudioManager): AudioDeviceInfo? {
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (type in OUTPUT_PRIORITY) {
            outputs.firstOrNull { it.type == type }?.let { return it }
        }
        return null
    }
}

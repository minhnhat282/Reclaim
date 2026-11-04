package com.minhnhat.reclaim.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.minhnhat.reclaim.R
import com.minhnhat.reclaim.blocking.LockAccessibilityService
import com.minhnhat.reclaim.media.ReclaimNotificationListener
import com.minhnhat.reclaim.runtime.AdminReceiver

enum class Need { REQUIRED, RECOMMENDED }

/**
 * Một dòng trong danh sách quyền ở màn Cài đặt.
 * [runtimePermission] != null: xin quyền lúc chạy; ngược lại mở màn hình hệ thống bằng [intent].
 */
data class PermissionItem(
    val titleRes: Int,
    val descriptionRes: Int,
    val granted: Boolean,
    val need: Need,
    val intent: Intent? = null,
    val runtimePermission: String? = null,
    /** != null: quyền chỉ cấp được bằng ADB; hiện hộp thoại hướng dẫn kèm lệnh này. */
    val adbCommand: String? = null
)

/** Kiểm tra trạng thái và dựng Intent thiết lập cho từng quyền. */
object Permissions {

    fun isAccessibilityEnabled(context: Context): Boolean = isServiceListed(
        context,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ComponentName(context, LockAccessibilityService::class.java)
    )

    fun isNotificationListenerEnabled(context: Context): Boolean = isServiceListed(
        context,
        "enabled_notification_listeners",
        ComponentName(context, ReclaimNotificationListener::class.java)
    )

    // unsafeCheckOpNoThrow bị đánh dấu deprecated ở SDK mới nhưng vẫn hoạt động đúng; Usage access chưa có API thay thế công khai.
    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** WRITE_SECURE_SETTINGS: cần để bật thang độ xám. Chỉ cấp được bằng ADB. */
    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun adbGrantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"

    fun canPostNotifications(context: Context): Boolean =
        !Compat.needsNotificationPermission ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canScheduleExactAlarms(context: Context): Boolean =
        Compat.canScheduleExactAlarms(context.getSystemService(AlarmManager::class.java))

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun isDeviceAdminActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)
            .isAdminActive(ComponentName(context, AdminReceiver::class.java))

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    /** Danh sách quyền hiển thị ở màn Cài đặt (quyền nào không áp dụng cho máy này thì bỏ ra). */
    // Chỉ thêm dòng báo thức chính xác khi hệ thống (API 31+) có thể thu hồi quyền; hằng số được chép thẳng giá trị.
    @SuppressLint("InlinedApi")
    fun items(context: Context): List<PermissionItem> {
        val list = ArrayList<PermissionItem>()

        list.add(
            PermissionItem(
                R.string.perm_accessibility_title, R.string.perm_accessibility_desc,
                isAccessibilityEnabled(context), Need.REQUIRED,
                intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        )
        list.add(
            PermissionItem(
                R.string.perm_listener_title, R.string.perm_listener_desc,
                isNotificationListenerEnabled(context), Need.REQUIRED,
                intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
        )
        if (Compat.needsNotificationPermission) {
            list.add(
                PermissionItem(
                    R.string.perm_post_title, R.string.perm_post_desc,
                    canPostNotifications(context), Need.RECOMMENDED,
                    runtimePermission = Manifest.permission.POST_NOTIFICATIONS
                )
            )
        }
        list.add(
            PermissionItem(
                R.string.perm_usage_title, R.string.perm_usage_desc,
                hasUsageAccess(context), Need.RECOMMENDED,
                intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
        )
        list.add(
            PermissionItem(
                R.string.perm_overlay_title, R.string.perm_overlay_desc,
                canDrawOverlays(context), Need.RECOMMENDED,
                intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))
            )
        )
        list.add(
            PermissionItem(
                R.string.perm_battery_title, R.string.perm_battery_desc,
                isIgnoringBatteryOptimizations(context), Need.RECOMMENDED,
                intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
            )
        )
        if (!canScheduleExactAlarms(context)) {
            list.add(
                PermissionItem(
                    R.string.perm_alarm_title, R.string.perm_alarm_desc,
                    false, Need.RECOMMENDED,
                    intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))
                )
            )
        }
        list.add(
            PermissionItem(
                R.string.perm_secure_title, R.string.perm_secure_desc,
                canWriteSecureSettings(context), Need.RECOMMENDED,
                adbCommand = adbGrantCommand(context)
            )
        )
        list.add(
            PermissionItem(
                R.string.perm_admin_title, R.string.perm_admin_desc,
                isDeviceAdminActive(context), Need.RECOMMENDED,
                intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(
                        DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        ComponentName(context, AdminReceiver::class.java)
                    )
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        context.getString(R.string.admin_explanation)
                    )
            )
        )
        return list
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    /** Chuỗi hệ thống liệt kê các dịch vụ đã bật: "pkg/pkg.Class:pkg2/.Class2". */
    private fun isServiceListed(context: Context, settingKey: String, target: ComponentName): Boolean {
        val raw = Settings.Secure.getString(context.contentResolver, settingKey) ?: return false
        return raw.split(':').any { ComponentName.unflattenFromString(it) == target }
    }
}

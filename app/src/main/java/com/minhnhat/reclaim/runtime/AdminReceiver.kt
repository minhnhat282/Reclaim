package com.minhnhat.reclaim.runtime

import android.app.admin.DeviceAdminReceiver

/** Chỉ dùng để Android không cho gỡ cài đặt khi quyền quản trị còn bật. */
class AdminReceiver : DeviceAdminReceiver()

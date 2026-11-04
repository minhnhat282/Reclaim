package com.minhnhat.reclaim.data

import android.content.Context

/**
 * Nhớ mức âm lượng gốc theo từng thiết bị đầu ra trước khi bị kéo về 0, để (tùy chọn) khôi phục
 * khi hết giờ hạn chế. Chỉ ghi lần đầu cho mỗi thiết bị trong một đợt hạn chế.
 */
class VolumeMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reclaim_volume", Context.MODE_PRIVATE)

    fun rememberIfAbsent(deviceKey: String, volume: Int) {
        if (!prefs.contains(deviceKey)) prefs.edit().putInt(deviceKey, volume).apply()
    }

    fun take(deviceKey: String): Int? {
        if (!prefs.contains(deviceKey)) return null
        val value = prefs.getInt(deviceKey, 0)
        prefs.edit().remove(deviceKey).apply()
        return value
    }

    fun hasAny(): Boolean = prefs.all.isNotEmpty()

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}

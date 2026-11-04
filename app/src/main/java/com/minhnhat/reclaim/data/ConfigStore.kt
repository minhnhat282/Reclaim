package com.minhnhat.reclaim.data

import android.content.Context
import com.minhnhat.reclaim.domain.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Giữ cấu hình trong RAM (đọc không tốn gì, an toàn ở hot path) và ghi xuống đĩa không đồng bộ.
 */
class ConfigStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val listeners = CopyOnWriteArrayList<(Config) -> Unit>()

    @Volatile
    var current: Config = ConfigJson.decode(prefs.getString(KEY, null))
        private set

    fun update(transform: (Config) -> Config) {
        var changed: Config? = null
        synchronized(this) {
            val next = transform(current)
            if (next != current) {
                current = next
                prefs.edit().putString(KEY, ConfigJson.encode(next)).apply()
                changed = next
            }
        }
        changed?.let { config -> listeners.forEach { listener -> listener(config) } }
    }

    fun addListener(listener: (Config) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Config) -> Unit) {
        listeners.remove(listener)
    }

    private companion object {
        const val PREFS = "reclaim_config"
        const val KEY = "config"
    }
}

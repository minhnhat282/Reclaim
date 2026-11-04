package com.minhnhat.reclaim.runtime

import java.util.concurrent.CopyOnWriteArrayList

/** Trạng thái kết nối của các dịch vụ hệ thống (Trợ năng, Truy cập thông báo) trong process này. */
class ServiceState {
    @Volatile
    var accessibilityConnected: Boolean = false
        private set

    @Volatile
    var listenerConnected: Boolean = false
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun setAccessibilityConnected(value: Boolean) {
        accessibilityConnected = value
        notifyChanged()
    }

    fun setListenerConnected(value: Boolean) {
        listenerConnected = value
        notifyChanged()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.forEach { it() }
    }
}

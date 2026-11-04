package com.minhnhat.reclaim.data

import android.content.Context
import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Mã PIN tùy chọn để mở Reclaim (chống tự tắt lịch lúc yếu lòng). Chỉ lưu băm có muối.
 */
class PinManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reclaim_security", Context.MODE_PRIVATE)

    @Volatile
    private var lastActive = -2 * GRACE_MS

    @Volatile
    private var failures = 0

    @Volatile
    private var lockedUntil = 0L

    val isSet: Boolean get() = prefs.contains(KEY_HASH)

    /** Số ms còn bị khóa nhập PIN do sai quá nhiều lần (0 nếu được phép thử). */
    fun lockoutRemainingMs(): Long = (lockedUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    /**
     * Thử PIN ở cổng mở app: sai [MAX_FREE_FAILURES] lần thì khóa tạm, mỗi lần sai tiếp khóa lâu gấp đôi
     * (tối đa [MAX_LOCKOUT_MS]). Trong thời gian khóa luôn trả về false mà không kiểm tra.
     */
    fun attempt(pin: String): Boolean {
        if (lockoutRemainingMs() > 0) return false
        if (verify(pin)) {
            failures = 0
            return true
        }
        failures++
        if (failures >= MAX_FREE_FAILURES) {
            val step = (failures - MAX_FREE_FAILURES).coerceAtMost(5)
            lockedUntil = SystemClock.elapsedRealtime() + minOf(BASE_LOCKOUT_MS shl step, MAX_LOCKOUT_MS)
        }
        return false
    }

    fun set(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        prefs.edit().putString(KEY_SALT, salt).putString(KEY_HASH, hash(salt, pin)).apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        return MessageDigest.isEqual(expected.toByteArray(), hash(salt, pin).toByteArray())
    }

    fun clear() {
        prefs.edit().remove(KEY_SALT).remove(KEY_HASH).apply()
    }

    /** Ghi nhận lần cuối người dùng còn ở trong app (gọi khi rời app và khi vừa mở khóa). */
    fun touch() {
        lastActive = SystemClock.elapsedRealtime()
    }

    /** Cần hỏi PIN nếu đã đặt PIN và đã rời app quá [GRACE_MS]. */
    fun needsGate(): Boolean = isSet && SystemClock.elapsedRealtime() - lastActive > GRACE_MS

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val GRACE_MS = 30_000L
        const val MAX_FREE_FAILURES = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 10 * 60_000L
    }
}

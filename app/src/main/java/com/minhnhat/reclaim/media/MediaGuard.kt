package com.minhnhat.reclaim.media

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.minhnhat.reclaim.data.ConfigStore
import com.minhnhat.reclaim.data.VolumeMemory
import com.minhnhat.reclaim.domain.PolicyEvaluator
import com.minhnhat.reclaim.platform.Compat
import com.minhnhat.reclaim.runtime.Scheduler
import java.time.LocalDateTime

/**
 * Hạn chế đa phương tiện. Trong giờ hạn chế:
 *  1. Dừng mọi MediaSession đang phát (video, nhạc, trình duyệt) - cần quyền Truy cập thông báo.
 *  2. Kéo âm lượng STREAM_MUSIC về 0 trên thiết bị đầu ra đang dùng (loa, tai nghe...), và kéo lại
 *     mỗi khi thiết bị đổi hoặc người dùng tăng âm.
 *  3. (Tùy chọn) chuyển màn hình sang thang độ xám trong đúng khung giờ hạn chế, xem [GrayscaleController].
 *
 * Không dùng audio focus: giữ focus có thể làm hỏng cuộc gọi VoIP (Zalo, Messenger...).
 * Âm lượng là lớp chặn cuối cho app không có MediaSession.
 *
 * Dùng theo kiểu "giữ chỗ": các dịch vụ (Trợ năng, Truy cập thông báo, GuardService) gọi [acquire];
 * listener chỉ được đăng ký khi có ít nhất một dịch vụ đang sống. [evaluate] dùng được cả khi
 * không có dịch vụ nào (ví dụ từ báo thức khi process vừa được dựng lại). Gọi từ main thread.
 */
class MediaGuard(
    private val app: Application,
    private val configStore: ConfigStore,
    private val volumeMemory: VolumeMemory,
    private val scheduler: Scheduler,
    private val grayscale: GrayscaleController
) {
    private class Registered(val controller: MediaController, val callback: MediaController.Callback)

    private val handler = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(AudioManager::class.java)
    private val sessionManager = app.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(app, ReclaimNotificationListener::class.java)

    private val holders = HashSet<String>()
    private var running = false
    private var sessionListenerRegistered = false
    private val controllers = HashMap<MediaSession.Token, Registered>()

    // ---------------------------------------------------------------- vòng đời

    fun acquire(holder: String) {
        holders.add(holder)
        if (!running) start()
    }

    fun release(holder: String) {
        holders.remove(holder)
        if (holders.isEmpty() && running) stop()
    }

    private fun start() {
        running = true
        audio.registerAudioPlaybackCallback(playbackCallback, handler)
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        Compat.registerReceiver(app, volumeReceiver, IntentFilter(ACTION_VOLUME_CHANGED))
        // Người dùng tự tắt thang độ xám trong giờ hạn chế thì bật lại.
        grayscale.startObserving(handler) { applyGrayscale() }
        refreshSessions()
        evaluate()
    }

    private fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        audio.unregisterAudioPlaybackCallback(playbackCallback)
        audio.unregisterAudioDeviceCallback(deviceCallback)
        grayscale.stopObserving()
        try {
            app.unregisterReceiver(volumeReceiver)
        } catch (e: Exception) {
            // Chưa đăng ký
        }
        unregisterSessions()
    }

    /** Gỡ listener và các callback theo dõi phiên phát; lần [refreshSessions] sau sẽ đăng ký lại từ đầu. */
    private fun unregisterSessions() {
        if (sessionListenerRegistered) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (e: Exception) {
                // Mất quyền giữa chừng
            }
            sessionListenerRegistered = false
        }
        controllers.values.forEach {
            try {
                it.controller.unregisterCallback(it.callback)
            } catch (e: Exception) {
                // Phiên đã đóng
            }
        }
        controllers.clear()
    }

    /** Gọi khi Truy cập thông báo bị tắt: bỏ trạng thái "đã đăng ký" để bật lại quyền là tự nối lại. */
    fun onSessionAccessLost() {
        unregisterSessions()
    }

    // ---------------------------------------------------------------- đánh giá chính sách

    fun onConfigChanged() {
        evaluate()
        if (running) refreshSessions()
    }

    /**
     * Áp dụng chính sách theo giờ hiện tại: trong giờ hạn chế thì dừng trình phát và kéo âm lượng về 0;
     * vừa hết giờ hạn chế thì (tùy chọn) khôi phục âm lượng. Luôn đặt lại báo thức và hẹn giờ kế tiếp.
     */
    fun evaluate() {
        val config = configStore.current
        val now = LocalDateTime.now()
        if (PolicyEvaluator.isMediaRestricted(config, now)) {
            enforce()
        } else if (volumeMemory.hasAny()) {
            releaseRestriction()
        }
        grayscale.apply(PolicyEvaluator.isGrayscaleActive(config, now))
        scheduler.scheduleMedia(config, now)

        handler.removeCallbacks(tick)
        if (running) {
            PolicyEvaluator.millisUntilMediaChange(config, now)?.let { handler.postDelayed(tick, it) }
        }
    }

    private val tick = Runnable { evaluate() }

    private fun isRestricted(): Boolean =
        PolicyEvaluator.isMediaRestricted(configStore.current, LocalDateTime.now())

    /** Cho Trợ năng quyết định có nuốt phím âm lượng không. Không chặn khi đang có cuộc gọi. */
    fun shouldBlockVolumeKey(): Boolean {
        val config = configStore.current
        if (!config.media.enabled || !config.options.blockVolumeKeys) return false
        if (audio.mode != AudioManager.MODE_NORMAL) return false
        return PolicyEvaluator.isMediaRestricted(config, LocalDateTime.now())
    }

    private fun enforce() {
        clampWithRetry()
        pauseAllPlaying()
    }

    private fun applyGrayscale() {
        grayscale.apply(PolicyEvaluator.isGrayscaleActive(configStore.current, LocalDateTime.now()))
    }

    /**
     * Hết giờ hạn chế. Android chỉ cho đặt âm lượng của thiết bị đang phát, nên khi bật "khôi phục":
     * trả ngay mức của thiết bị hiện tại, còn các thiết bị khác (tai nghe chưa cắm...) giữ lại trong
     * [VolumeMemory] và trả khi chúng được kết nối ([deviceCallback]). Khi tắt "khôi phục" thì xóa hết.
     */
    private fun releaseRestriction() {
        if (configStore.current.options.restoreVolume) {
            restoreRememberedVolume()
        } else {
            volumeMemory.clearAll()
        }
    }

    private fun restoreRememberedVolume() {
        volumeMemory.take(Compat.activeOutputKey(audio))?.let { saved ->
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
            } catch (e: SecurityException) {
                // Bỏ qua
            }
        }
    }

    /** Thiết bị đổi ngoài giờ hạn chế: nếu thiết bị mới đang có mức âm lượng chờ khôi phục thì trả lại. */
    private fun restoreAfterDeviceChange() {
        if (!configStore.current.options.restoreVolume || !volumeMemory.hasAny()) return
        // Chờ hệ thống cập nhật thiết bị đầu ra đang dùng.
        handler.postDelayed({
            if (!isRestricted()) restoreRememberedVolume()
        }, DEVICE_SETTLE_MS)
    }

    // ---------------------------------------------------------------- âm lượng

    private fun clampVolume() {
        try {
            val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (current > min) {
                // Chỉ ghi mức gốc lần đầu của thiết bị này trong đợt hạn chế (tránh ghi đè bằng mức do người dùng bấm tăng).
                volumeMemory.rememberIfAbsent(Compat.activeOutputKey(audio), current)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, min, 0)
            }
        } catch (e: SecurityException) {
            // Bỏ qua
        }
    }

    private val retryClamp = Runnable { if (isRestricted()) clampVolume() }

    /** Bluetooth "absolute volume" đôi khi bỏ qua lần đặt đầu tiên: kiểm tra và kéo lại sau 0,3 s và 1,2 s. */
    private fun clampWithRetry() {
        clampVolume()
        handler.removeCallbacks(retryClamp)
        handler.postDelayed(retryClamp, 300)
        handler.postDelayed(retryClamp, 1200)
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
            if (isRestricted()) clampVolume()
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (isRestricted()) clampWithRetry() else restoreAfterDeviceChange()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (isRestricted()) clampWithRetry() else restoreAfterDeviceChange()
        }
    }

    // ---------------------------------------------------------------- dừng trình phát

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (configs.isNullOrEmpty() || !isRestricted()) return
            clampVolume()
            pauseAllPlaying()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        syncControllers(list ?: emptyList())
    }

    /** Đăng ký theo dõi và đồng bộ danh sách phiên phát. Chỉ chạy được khi đã cấp Truy cập thông báo. */
    fun refreshSessions() {
        if (!sessionListenerRegistered) {
            try {
                sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
                sessionListenerRegistered = true
            } catch (e: SecurityException) {
                sessionListenerRegistered = false
                return
            }
        }
        syncControllers(activeSessions())
    }

    private fun activeSessions(): List<MediaController> = try {
        sessionManager.getActiveSessions(listenerComponent)
    } catch (e: SecurityException) {
        // Mất quyền: lần refreshSessions sau (khi cấp lại) phải đăng ký lại listener.
        sessionListenerRegistered = false
        emptyList()
    }

    private fun syncControllers(list: List<MediaController>) {
        val seen = HashSet<MediaSession.Token>()
        for (controller in list) {
            val token = controller.sessionToken
            seen.add(token)
            if (token !in controllers) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        if (isActivePlayback(state) && isRestricted()) pause(controller)
                    }

                    override fun onSessionDestroyed() {
                        controllers.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
                    }
                }
                controller.registerCallback(callback, handler)
                controllers[token] = Registered(controller, callback)
            }
            if (isRestricted() && isActivePlayback(controller.playbackState)) pause(controller)
        }
        for (token in controllers.keys.toList()) {
            if (token !in seen) {
                controllers.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
            }
        }
    }

    private fun pauseAllPlaying() {
        for (controller in activeSessions()) {
            if (isActivePlayback(controller.playbackState)) pause(controller)
        }
    }

    private fun isActivePlayback(state: PlaybackState?): Boolean =
        state != null &&
            (state.state == PlaybackState.STATE_PLAYING || state.state == PlaybackState.STATE_BUFFERING)

    private fun pause(controller: MediaController) {
        // Không đụng vào cuộc gọi (âm thanh cuộc gọi dùng chế độ khác NORMAL).
        if (audio.mode != AudioManager.MODE_NORMAL) return
        try {
            controller.transportControls.pause()
        } catch (e: Exception) {
            return
        }
        handler.postDelayed({
            if (isActivePlayback(controller.playbackState)) {
                try {
                    controller.transportControls.stop()
                } catch (e: Exception) {
                    // Phiên đã đóng
                }
            }
        }, STOP_DELAY_MS)
    }

    private companion object {
        // Action không chính thức nhưng được dùng rộng rãi để biết âm lượng đổi.
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val STOP_DELAY_MS = 500L
        const val DEVICE_SETTLE_MS = 400L
    }
}

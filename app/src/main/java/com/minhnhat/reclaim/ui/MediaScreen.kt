package com.minhnhat.reclaim.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.minhnhat.reclaim.AppGraph
import com.minhnhat.reclaim.R
import com.minhnhat.reclaim.domain.PolicyEvaluator
import com.minhnhat.reclaim.domain.formatMinutes
import com.minhnhat.reclaim.platform.Permissions
import java.time.LocalDateTime

/**
 * Tab "Hạn chế đa phương tiện": công tắc tổng, trạng thái hiện tại và lịch mở khóa theo tuần.
 * Lịch được lưu ngay mỗi khi chỉnh.
 */
class MediaScreen(private val activity: Activity, private val graph: AppGraph) : Screen {

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 20_000)
        }
    }

    private lateinit var enableSwitch: Switch
    private lateinit var grayscaleSwitch: Switch
    private lateinit var grayscaleRow: View
    private lateinit var secureBanner: View
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var listenerBanner: View
    private lateinit var editorCard: View
    private lateinit var editor: ScheduleEditor

    override val view: View by lazy { build() }

    private fun build(): View {
        val config = graph.configStore.current
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(activity.screenHeader(activity.getString(R.string.media_title), activity.getString(R.string.media_subtitle)))

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16), 0, activity.dp(16), activity.dp(24))
        }

        // Công tắc tổng
        val master = activity.card()
        val masterRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val masterTexts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        masterTexts.addView(activity.label(activity.getString(R.string.media_enable), 17f, bold = true))
        masterTexts.addView(
            activity.label(activity.getString(R.string.media_enable_desc), 13.5f, R.color.text_secondary),
            activity.linearParams(MATCH, WRAP, top = 2)
        )
        masterRow.addView(masterTexts, activity.linearParams(0, WRAP, 1f, right = 12))
        enableSwitch = activity.greenSwitch().apply {
            isChecked = config.media.enabled
            setOnCheckedChangeListener { _, checked ->
                graph.configStore.update { it.copy(media = it.media.copy(enabled = checked)) }
                updateStatus()
            }
        }
        masterRow.addView(enableSwitch)
        master.addView(masterRow)

        // Tùy chọn con ngay dưới công tắc tổng: thang độ xám trong giờ hạn chế (dùng chung lịch bên dưới)
        master.addView(
            View(activity).apply { setBackgroundColor(activity.getColor(R.color.outline)) },
            activity.linearParams(MATCH, activity.dp(1), top = 14, bottom = 14)
        )
        val grayRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val grayTexts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        grayTexts.addView(activity.label(activity.getString(R.string.media_grayscale), 17f, bold = true))
        grayTexts.addView(
            activity.label(activity.getString(R.string.media_grayscale_desc), 13.5f, R.color.text_secondary),
            activity.linearParams(MATCH, WRAP, top = 2)
        )
        grayRow.addView(grayTexts, activity.linearParams(0, WRAP, 1f, right = 12))
        grayscaleSwitch = activity.greenSwitch().apply {
            isChecked = config.media.grayscale
            setOnCheckedChangeListener { _, checked ->
                graph.configStore.update { it.copy(media = it.media.copy(grayscale = checked)) }
                updateStatus()
            }
        }
        grayRow.addView(grayscaleSwitch)
        grayscaleRow = grayRow
        master.addView(grayscaleRow)
        body.addView(master)

        // Trạng thái hiện tại
        val status = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = activity.roundedBackground(R.color.green_container, 16)
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
        }
        statusTitle = activity.label("", 16f, R.color.green_primary, bold = true)
        statusDetail = activity.label("", 13.5f, R.color.text_primary)
        status.addView(statusTitle)
        status.addView(statusDetail, activity.linearParams(MATCH, WRAP, top = 2))
        body.addView(status, activity.linearParams(MATCH, WRAP, top = 12))

        listenerBanner = activity.warningBanner(
            activity.getString(R.string.banner_listener_off),
            activity.getString(R.string.banner_action_setup)
        ) { openSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        body.addView(listenerBanner, activity.linearParams(MATCH, WRAP, top = 12))

        secureBanner = activity.warningBanner(
            activity.getString(R.string.banner_secure_off),
            activity.getString(R.string.banner_action_setup)
        ) { Dialogs.adbGuide(activity, Permissions.adbGrantCommand(activity)) }
        body.addView(secureBanner, activity.linearParams(MATCH, WRAP, top = 12))

        // Lịch mở khóa
        val schedule = activity.card()
        schedule.addView(activity.label(activity.getString(R.string.media_schedule_title), 17f, bold = true))
        schedule.addView(
            activity.label(activity.getString(R.string.media_schedule_desc), 13.5f, R.color.text_secondary),
            activity.linearParams(MATCH, WRAP, top = 2, bottom = 8)
        )
        editor = ScheduleEditor(activity, config.media.schedule) { newSchedule ->
            graph.configStore.update { it.copy(media = it.media.copy(schedule = newSchedule)) }
            updateStatus()
        }
        schedule.addView(editor.view)
        editorCard = schedule
        body.addView(schedule, activity.linearParams(MATCH, WRAP, top = 12))

        content.addView(body)
        return ScrollView(activity).apply {
            isFillViewport = true
            addView(content)
        }
    }

    override fun onShow() {
        view
        enableSwitch.isChecked = graph.configStore.current.media.enabled
        grayscaleSwitch.isChecked = graph.configStore.current.media.grayscale
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onHide() {
        handler.removeCallbacks(refresh)
    }

    private fun updateStatus() {
        val config = graph.configStore.current
        val now = LocalDateTime.now()
        editorCard.alpha = if (config.media.enabled) 1f else 0.55f
        grayscaleRow.alpha = if (config.media.enabled) 1f else 0.55f
        listenerBanner.visibility =
            if (config.media.enabled && !Permissions.isNotificationListenerEnabled(activity)) View.VISIBLE else View.GONE
        val canGray = Permissions.canWriteSecureSettings(activity)
        secureBanner.visibility =
            if (config.media.enabled && config.media.grayscale && !canGray) View.VISIBLE else View.GONE

        if (!config.media.enabled) {
            statusTitle.text = activity.getString(R.string.media_status_off)
            statusDetail.visibility = View.GONE
            return
        }
        statusDetail.visibility = View.VISIBLE
        val restricted = PolicyEvaluator.isMediaRestricted(config, now)
        statusTitle.text = activity.getString(if (restricted) R.string.media_status_restricted else R.string.media_status_allowed)

        val next = config.media.schedule.nextStateChangeAfter(now)
        // next == null nghĩa là trạng thái không bao giờ đổi: hạn chế liên tục HOẶC mở khóa liên tục.
        val base = when {
            next == null ->
                activity.getString(if (restricted) R.string.media_always_restricted else R.string.media_never_restricted)
            restricted -> activity.getString(R.string.media_until_unlock, describe(next, now))
            else -> activity.getString(R.string.media_until_restrict, describe(next, now))
        }
        val grayNow = canGray && PolicyEvaluator.isGrayscaleActive(config, now)
        statusDetail.text = if (grayNow) base + "\n" + activity.getString(R.string.media_gray_active) else base
    }

    /** Một số ROM không có màn hình cài đặt này: mở Thông tin ứng dụng thay vì làm app crash. */
    private fun openSafely(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(Permissions.appDetailsIntent(activity))
        }
    }

    /** "18:00" nếu trong hôm nay, ngược lại "Thứ Ba 07:30". */
    private fun describe(time: LocalDateTime, now: LocalDateTime): String {
        val clock = formatMinutes(time.hour * 60 + time.minute)
        if (time.toLocalDate() == now.toLocalDate()) return clock
        val dayName = activity.resources.getStringArray(R.array.day_names)[time.dayOfWeek.ordinal]
        return "$dayName $clock"
    }
}

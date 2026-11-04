package com.minhnhat.reclaim.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.minhnhat.reclaim.AppGraph
import com.minhnhat.reclaim.R
import com.minhnhat.reclaim.domain.Options
import com.minhnhat.reclaim.platform.Compat
import com.minhnhat.reclaim.platform.Need
import com.minhnhat.reclaim.platform.PermissionItem
import com.minhnhat.reclaim.platform.Permissions

/**
 * Tab "Cài đặt": trạng thái các quyền (kèm nút mở đúng màn hình hệ thống), tùy chọn, mã PIN, giới thiệu.
 */
class SettingsScreen(private val activity: Activity, private val graph: AppGraph) : Screen {

    private lateinit var permissionList: LinearLayout
    private lateinit var restrictedHint: View
    private lateinit var blockKeysSwitch: Switch
    private lateinit var restoreSwitch: Switch
    private lateinit var tamperSwitch: Switch
    private lateinit var tamperPause: View
    private lateinit var pinDescription: TextView
    private lateinit var pinButton: TextView

    override val view: View by lazy { build() }

    private fun build(): View {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(activity.screenHeader(activity.getString(R.string.settings_title), activity.getString(R.string.settings_subtitle)))

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16), 0, activity.dp(16), activity.dp(24))
        }

        // ---- Quyền
        body.addView(activity.sectionLabel(activity.getString(R.string.settings_permissions)))
        restrictedHint = activity.warningBanner(
            activity.getString(R.string.restricted_hint),
            activity.getString(R.string.open_app_info)
        ) { activity.startActivity(Permissions.appDetailsIntent(activity)) }
        body.addView(restrictedHint, activity.linearParams(MATCH, WRAP, bottom = 12))
        permissionList = activity.card(8)
        body.addView(permissionList)

        // ---- Tùy chọn
        body.addView(activity.sectionLabel(activity.getString(R.string.settings_options)))
        val options = activity.card(8)
        val defaults = graph.configStore.current.options

        blockKeysSwitch = activity.greenSwitch().apply {
            isChecked = defaults.blockVolumeKeys
            setOnCheckedChangeListener { _, on -> updateOptions { it.copy(blockVolumeKeys = on) } }
        }
        options.addView(switchRow(R.string.opt_block_keys, R.string.opt_block_keys_desc, blockKeysSwitch))
        options.addView(divider())

        restoreSwitch = activity.greenSwitch().apply {
            isChecked = defaults.restoreVolume
            setOnCheckedChangeListener { _, on -> updateOptions { it.copy(restoreVolume = on) } }
        }
        options.addView(switchRow(R.string.opt_restore_volume, R.string.opt_restore_desc, restoreSwitch))
        options.addView(divider())

        tamperSwitch = activity.greenSwitch().apply {
            isChecked = defaults.tamperGuard
            setOnCheckedChangeListener { _, on ->
                updateOptions { it.copy(tamperGuard = on) }
                tamperPause.visibility = if (on) View.VISIBLE else View.GONE
            }
        }
        options.addView(switchRow(R.string.opt_tamper, R.string.opt_tamper_desc, tamperSwitch))
        tamperPause = activity.textButton(activity.getString(R.string.opt_tamper_pause)) {
            graph.tamperGuard.pauseFor(PAUSE_MS)
            Toast.makeText(activity, R.string.toast_tamper_paused, Toast.LENGTH_SHORT).show()
        }
        options.addView(tamperPause, activity.linearParams(WRAP, WRAP, left = 4).apply { gravity = Gravity.START })
        body.addView(options)

        // ---- Bảo mật
        body.addView(activity.sectionLabel(activity.getString(R.string.settings_security)))
        val security = activity.card(12)
        val pinRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pinTexts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        pinTexts.addView(activity.label(activity.getString(R.string.pin_title), 16f, bold = true))
        pinDescription = activity.label("", 13f, R.color.text_secondary)
        pinTexts.addView(pinDescription, activity.linearParams(MATCH, WRAP, top = 2))
        pinRow.addView(pinTexts, activity.linearParams(0, WRAP, 1f, right = 12))
        pinButton = smallButton("") { onPinClicked() }
        pinRow.addView(pinButton)
        security.addView(pinRow)
        body.addView(security)

        // ---- Giới thiệu
        body.addView(activity.sectionLabel(activity.getString(R.string.settings_about)))
        val about = activity.card(16)
        about.addView(activity.label(activity.getString(R.string.app_name), 16f, bold = true))
        about.addView(
            activity.label(activity.getString(R.string.about_version, versionName()), 13f, R.color.text_secondary),
            activity.linearParams(MATCH, WRAP, top = 2)
        )
        body.addView(about)

        content.addView(body)
        return ScrollView(activity).apply {
            isFillViewport = true
            addView(content)
        }
    }

    override fun onShow() {
        view
        renderPermissions()
        val options = graph.configStore.current.options
        blockKeysSwitch.isChecked = options.blockVolumeKeys
        restoreSwitch.isChecked = options.restoreVolume
        tamperSwitch.isChecked = options.tamperGuard
        tamperPause.visibility = if (options.tamperGuard) View.VISIBLE else View.GONE
        renderPin()
    }

    // ---------------------------------------------------------------- quyền

    private fun renderPermissions() {
        val items = Permissions.items(activity)
        permissionList.removeAllViews()
        items.forEachIndexed { index, item ->
            if (index > 0) permissionList.addView(divider())
            permissionList.addView(permissionRow(item))
        }
        val restrictedLikely = Compat.hasRestrictedSettings &&
            (!Permissions.isAccessibilityEnabled(activity) || !Permissions.isNotificationListenerEnabled(activity))
        restrictedHint.visibility = if (restrictedLikely) View.VISIBLE else View.GONE
    }

    private fun permissionRow(item: PermissionItem): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(8), activity.dp(12), activity.dp(8), activity.dp(12))
        }
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val titleLine = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleLine.addView(activity.label(activity.getString(item.titleRes), 16f, bold = true))
        val tagText = if (item.need == Need.REQUIRED) R.string.need_required else R.string.need_recommended
        val tagColor = if (item.need == Need.REQUIRED) R.color.warning else R.color.text_secondary
        titleLine.addView(activity.label(activity.getString(tagText), 11f, tagColor, bold = true), activity.linearParams(WRAP, WRAP, left = 8))
        texts.addView(titleLine)
        texts.addView(activity.label(activity.getString(item.descriptionRes), 13f, R.color.text_secondary), activity.linearParams(MATCH, WRAP, top = 2))
        row.addView(texts, activity.linearParams(0, WRAP, 1f, right = 12))

        if (item.granted) {
            row.addView(activity.icon(R.drawable.ic_check_circle, R.color.green_primary, 26))
        } else {
            row.addView(smallButton(activity.getString(R.string.action_enable)) { launch(item) })
        }
        return row
    }

    /** Mở màn hình hệ thống tương ứng. Tạm dừng bảo vệ chống tắt ngắn để không tự che màn hình cấp quyền. */
    private fun launch(item: PermissionItem) {
        // Quyền chỉ cấp được bằng ADB: hiện hướng dẫn kèm lệnh, không có màn hình hệ thống để mở.
        val command = item.adbCommand
        if (command != null) {
            Dialogs.adbGuide(activity, command)
            return
        }
        graph.tamperGuard.pauseFor(GRANT_PAUSE_MS)
        val runtime = item.runtimePermission
        if (runtime != null) {
            requestRuntime(runtime)
            return
        }
        val intent = item.intent ?: return
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(Permissions.appDetailsIntent(activity))
        }
    }

    /**
     * Android ngừng hiện hộp thoại xin quyền sau khi bị từ chối hai lần (nút "Bật" sẽ không còn tác dụng).
     * Khi đó chuyển sang màn Thông tin ứng dụng để người dùng cấp thủ công.
     */
    private fun requestRuntime(permission: String) {
        val prefs = activity.getSharedPreferences("reclaim_ui", Context.MODE_PRIVATE)
        val key = "asked_$permission"
        if (prefs.getBoolean(key, false) && !activity.shouldShowRequestPermissionRationale(permission)) {
            activity.startActivity(Permissions.appDetailsIntent(activity))
            return
        }
        prefs.edit().putBoolean(key, true).apply()
        activity.requestPermissions(arrayOf(permission), REQUEST_CODE)
    }

    // ---------------------------------------------------------------- PIN

    private fun renderPin() {
        val isSet = graph.pin.isSet
        pinDescription.text = activity.getString(if (isSet) R.string.pin_desc_on else R.string.pin_desc_off)
        pinButton.text = activity.getString(if (isSet) R.string.pin_manage else R.string.pin_set)
    }

    private fun onPinClicked() {
        if (!graph.pin.isSet) {
            setPin()
            return
        }
        val labels = listOf(activity.getString(R.string.pin_change), activity.getString(R.string.pin_remove))
        Dialogs.pick(activity, activity.getString(R.string.pin_title), labels, -1) { index ->
            if (index == 0) changePin() else removePin()
        }
    }

    private fun setPin() {
        Dialogs.pinForm(
            activity, activity.getString(R.string.pin_set),
            listOf(activity.getString(R.string.pin_new), activity.getString(R.string.pin_confirm)),
            activity.getString(R.string.action_save)
        ) { values ->
            validateNewPin(values[0], values[1]) ?: run {
                graph.pin.set(values[0])
                graph.pin.touch()
                renderPin()
                null
            }
        }
    }

    private fun changePin() {
        Dialogs.pinForm(
            activity, activity.getString(R.string.pin_change),
            listOf(
                activity.getString(R.string.pin_current),
                activity.getString(R.string.pin_new),
                activity.getString(R.string.pin_confirm)
            ),
            activity.getString(R.string.action_save)
        ) { values ->
            if (!graph.pin.verify(values[0])) {
                activity.getString(R.string.pin_err_wrong)
            } else {
                validateNewPin(values[1], values[2]) ?: run {
                    graph.pin.set(values[1])
                    renderPin()
                    null
                }
            }
        }
    }

    private fun removePin() {
        Dialogs.pinForm(
            activity, activity.getString(R.string.pin_remove),
            listOf(activity.getString(R.string.pin_current)),
            activity.getString(R.string.pin_remove)
        ) { values ->
            if (!graph.pin.verify(values[0])) {
                activity.getString(R.string.pin_err_wrong)
            } else {
                graph.pin.clear()
                renderPin()
                null
            }
        }
    }

    /** Trả về thông báo lỗi, hoặc null nếu hợp lệ. */
    private fun validateNewPin(pin: String, confirm: String): String? = when {
        pin.length !in 4..8 || !pin.all { it.isDigit() } -> activity.getString(R.string.pin_err_length)
        pin != confirm -> activity.getString(R.string.pin_err_mismatch)
        else -> null
    }

    // ---------------------------------------------------------------- tiện ích dựng View

    private fun updateOptions(transform: (Options) -> Options) {
        graph.configStore.update { it.copy(options = transform(it.options)) }
    }

    private fun switchRow(titleRes: Int, descRes: Int, toggle: Switch): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(8), activity.dp(12), activity.dp(8), activity.dp(12))
        }
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(activity.label(activity.getString(titleRes), 16f, bold = true))
        texts.addView(activity.label(activity.getString(descRes), 13f, R.color.text_secondary), activity.linearParams(MATCH, WRAP, top = 2))
        row.addView(texts, activity.linearParams(0, WRAP, 1f, right = 12))
        row.addView(toggle)
        return row
    }

    private fun smallButton(text: String, onClick: () -> Unit): TextView =
        activity.filledButton(text, onClick).apply {
            textSize = 14f
            minHeight = activity.dp(34)
            setPadding(activity.dp(16), activity.dp(6), activity.dp(16), activity.dp(6))
        }

    private fun divider(): View = View(activity).apply {
        setBackgroundColor(activity.getColor(R.color.outline))
        layoutParams = LinearLayout.LayoutParams(MATCH, activity.dp(1))
    }

    @Suppress("DEPRECATION")
    private fun versionName(): String =
        try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

    private companion object {
        const val REQUEST_CODE = 100
        const val PAUSE_MS = 5 * 60_000L
        const val GRANT_PAUSE_MS = 90_000L
    }
}

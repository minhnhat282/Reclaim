package com.minhnhat.reclaim.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import com.minhnhat.reclaim.AppGraph
import com.minhnhat.reclaim.R
import com.minhnhat.reclaim.data.InstalledApps
import com.minhnhat.reclaim.platform.Permissions

/**
 * Tab "Hạn chế ứng dụng": danh sách app, mỗi dòng có công tắc khóa và biểu tượng hẹn giờ mở khóa.
 */
class AppsScreen(
    private val activity: Activity,
    private val graph: AppGraph,
    private val openSettingsTab: () -> Unit
) : Screen {

    private val iconLoader = IconLoader(activity.packageManager, activity.dp(44))
    private val adapter = AppAdapter()
    private var allApps: List<InstalledApps.Entry> = emptyList()
    private var query = ""
    private var loaded = false
    private var lastLoadAt = 0L

    private lateinit var banner: View
    private lateinit var stateText: TextView

    override val view: View by lazy { build() }

    private fun build(): View {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(activity.screenHeader(activity.getString(R.string.apps_title), activity.getString(R.string.apps_subtitle)))

        banner = activity.warningBanner(
            activity.getString(R.string.banner_a11y_off),
            activity.getString(R.string.banner_action_setup)
        ) { openSettingsTab() }
        root.addView(banner, activity.linearParams(MATCH, WRAP, left = 16, right = 16, bottom = 8))

        root.addView(searchBox(), activity.linearParams(MATCH, WRAP, left = 16, right = 16, bottom = 4))

        stateText = activity.label("", 14f, R.color.text_secondary).apply {
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(32), 0, 0)
        }
        root.addView(stateText, LinearLayout.LayoutParams(MATCH, WRAP))

        val list = ListView(activity).apply {
            divider = null
            dividerHeight = 0
            selector = ColorDrawable(Color.TRANSPARENT)
            setBackgroundColor(Color.TRANSPARENT)
            clipToPadding = false
            setPadding(0, activity.dp(4), 0, activity.dp(12))
            adapter = this@AppsScreen.adapter
        }
        root.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return root
    }

    private fun searchBox(): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = activity.roundedBackground(R.color.surface, 14, R.color.outline)
            setPadding(activity.dp(14), 0, activity.dp(14), 0)
        }
        box.addView(activity.icon(R.drawable.ic_search, R.color.text_secondary, 20))
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.apps_search_hint)
            setHintTextColor(activity.getColor(R.color.text_secondary))
            setTextColor(activity.getColor(R.color.text_primary))
            textSize = 16f
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(activity.dp(10), activity.dp(12), 0, activity.dp(12))
            onTextChanged {
                query = it.trim()
                applyFilter()
            }
        }
        box.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))
        return box
    }

    override fun onShow() {
        view // đảm bảo đã dựng
        banner.visibility = if (Permissions.isAccessibilityEnabled(activity)) View.GONE else View.VISIBLE
        if (!loaded) {
            stateText.text = activity.getString(R.string.apps_loading)
            stateText.visibility = View.VISIBLE
        }
        // Quét danh sách app khá nặng: chỉ nạp lại khi chưa có hoặc đã cũ, còn lại chỉ sắp xếp lại theo trạng thái khóa.
        if (loaded) applyFilter()
        if (!loaded || SystemClock.elapsedRealtime() - lastLoadAt > RELOAD_MS) {
            graph.installedApps.loadAsync { list ->
                if (activity.isDestroyed) return@loadAsync
                allApps = list
                loaded = true
                lastLoadAt = SystemClock.elapsedRealtime()
                applyFilter()
            }
        }
    }

    /** Lọc theo ô tìm kiếm; app đang khóa lên đầu rồi theo tên. */
    private fun applyFilter() {
        val locked = graph.configStore.current.lockedPackages
        val filtered = allApps
            .filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) }
            .sortedWith(compareByDescending<InstalledApps.Entry> { it.packageName in locked }.thenBy { it.label.lowercase() })
        adapter.items = filtered
        adapter.notifyDataSetChanged()
        if (loaded && filtered.isEmpty()) {
            stateText.text = activity.getString(R.string.apps_empty)
            stateText.visibility = View.VISIBLE
        } else {
            stateText.visibility = View.GONE
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        var items: List<InstalledApps.Entry> = emptyList()

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView?.tag as? Row) ?: Row().also { it.root.tag = it }
            row.bind(items[position])
            return row.root
        }
    }

    private inner class Row {
        private val icon = ImageView(activity)
        private val name = activity.label("", 16f)
        private val status = activity.label("", 13f, R.color.text_secondary)
        private val timer = ImageView(activity)
        private val toggle: Switch = activity.greenSwitch()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(20), activity.dp(8), activity.dp(12), activity.dp(8))
        }

        init {
            root.addView(icon, LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)))

            val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(name)
            texts.addView(status)
            root.addView(texts, activity.linearParams(0, WRAP, 1f, left = 14, right = 8))

            timer.setImageResource(R.drawable.ic_timer)
            timer.contentDescription = activity.getString(R.string.app_timer_desc)
            timer.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            timer.background = RippleDrawable(ColorStateList.valueOf(activity.getColor(R.color.ripple)), null, null)
            root.addView(timer, LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)))

            root.addView(toggle, activity.linearParams(WRAP, WRAP, left = 4))
        }

        fun bind(entry: InstalledApps.Entry) {
            val pkg = entry.packageName
            name.text = entry.label
            iconLoader.load(pkg, icon)

            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = pkg in graph.configStore.current.lockedPackages
            toggle.setOnCheckedChangeListener { _, checked ->
                graph.configStore.update { it.withLocked(pkg, checked) }
                bindStatus(pkg)
            }
            timer.setOnClickListener { openSchedule(entry) }
            bindStatus(pkg)
        }

        private fun bindStatus(pkg: String) {
            val config = graph.configStore.current
            val locked = pkg in config.lockedPackages
            val hasSchedule = config.hasSchedule(pkg)
            timer.setColorFilter(activity.getColor(if (hasSchedule) R.color.green_primary else R.color.text_secondary))
            when {
                !locked -> status.visibility = View.GONE
                hasSchedule -> {
                    status.text = activity.getString(R.string.app_status_scheduled, config.scheduleOf(pkg).enabledDayCount)
                    status.visibility = View.VISIBLE
                }
                else -> {
                    status.text = activity.getString(R.string.app_status_always)
                    status.visibility = View.VISIBLE
                }
            }
        }
    }

    private companion object {
        const val RELOAD_MS = 60_000L
    }

    private fun openSchedule(entry: InstalledApps.Entry) {
        val pkg = entry.packageName
        ScheduleSheet.show(activity, entry.label, graph.configStore.current.scheduleOf(pkg)) { schedule ->
            graph.configStore.update { it.withSchedule(pkg, schedule) }
            adapter.notifyDataSetChanged()
        }
    }
}

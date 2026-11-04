package com.minhnhat.reclaim.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.minhnhat.reclaim.AppGraph
import com.minhnhat.reclaim.R
import com.minhnhat.reclaim.ReclaimApp
import com.minhnhat.reclaim.runtime.GuardController

/** Màn hình chính: ba tab (Hạn chế ứng dụng, Hạn chế đa phương tiện, Cài đặt) và cổng PIN tùy chọn. */
class MainActivity : Activity() {

    private val graph: AppGraph by lazy { (applicationContext as ReclaimApp).graph }

    private lateinit var content: FrameLayout
    private lateinit var nav: BottomNav
    private lateinit var rootStack: FrameLayout
    private lateinit var screens: List<Screen>
    private var current = -1
    private var gate: PinGateView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Vẽ tràn dưới thanh hệ thống rồi tự đệm theo inset: hoạt động đúng với mọi targetSdk.
        @Suppress("DEPRECATION") // bản thay thế nằm trong androidx (WindowCompat); app không dùng thư viện ngoài
        window.setDecorFitsSystemWindows(false)

        screens = listOf(
            AppsScreen(this, graph) { select(2) },
            MediaScreen(this, graph),
            SettingsScreen(this, graph)
        )
        nav = BottomNav(this) { select(it) }.also {
            it.add(R.drawable.ic_nav_apps, R.string.nav_apps)
            it.add(R.drawable.ic_nav_media, R.string.nav_media)
            it.add(R.drawable.ic_nav_settings, R.string.nav_settings)
        }

        content = FrameLayout(this)
        val divider = View(this).apply { setBackgroundColor(getColor(R.color.outline)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(divider, LinearLayout.LayoutParams(MATCH, dp(1)))
            addView(nav.view, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        rootStack = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        rootStack.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            column.setPadding(bars.left, bars.top, bars.right, 0)
            nav.view.setPadding(0, 0, 0, bars.bottom)
            WindowInsets.CONSUMED
        }
        setContentView(rootStack)

        select(savedInstanceState?.getInt(KEY_TAB) ?: 0)
    }

    override fun onStart() {
        super.onStart()
        if (graph.pin.needsGate()) showGate()
    }

    override fun onResume() {
        super.onResume()
        screens[current].onShow()
        // Đảm bảo GuardService chạy (hoặc dừng) đúng với cấu hình hiện tại và đánh giá lại chế độ dự phòng.
        GuardController.sync(this, graph.configStore.current)
    }

    override fun onPause() {
        screens[current].onHide()
        super.onPause()
    }

    override fun onStop() {
        // Chỉ tính là "còn ở trong app" khi nội dung thực sự đã mở khóa, không phải khi đang đứng ở cổng PIN.
        // Nếu không, rời app mà chưa nhập PIN vẫn làm đồng hồ ân hạn chạy và lần mở lại sẽ lọt qua cổng.
        if (gate == null) graph.pin.touch()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, current)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        screens[current].onShow()
    }

    private fun select(index: Int) {
        if (index == current) return
        if (current >= 0) screens[current].onHide()
        current = index
        content.removeAllViews()
        content.addView(screens[index].view, FrameLayout.LayoutParams(MATCH, MATCH))
        nav.select(index)
        screens[index].onShow()
    }

    private fun showGate() {
        if (gate != null) return
        val view = PinGateView(this, graph.pin) { hideGate() }
        gate = view
        rootStack.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        view.post { view.focusInput() }
    }

    private fun hideGate() {
        gate?.let { rootStack.removeView(it) }
        gate = null
    }

    private companion object {
        const val KEY_TAB = "tab"
    }
}

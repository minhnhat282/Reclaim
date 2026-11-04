package com.minhnhat.reclaim.blocking

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.minhnhat.reclaim.R

/** Màn hình khóa tối giản: mầm cây và một dòng chữ. Dựng bằng View thuần để khởi tạo nhanh nhất. */
object OverlayView {

    fun create(context: Context): View {
        val iconSize = (160 * context.resources.displayMetrics.density).toInt()
        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(context.getColor(R.color.overlay_bg))
        root.isClickable = true

        val icon = ImageView(context)
        icon.setImageResource(R.drawable.ic_sprout)
        root.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))

        val label = TextView(context)
        label.setText(R.string.overlay_locked)
        label.textSize = 20f
        label.gravity = Gravity.CENTER
        label.setTextColor(context.getColor(R.color.overlay_text))
        label.setPadding(0, iconSize / 10, 0, 0)
        root.addView(label)
        return root
    }
}

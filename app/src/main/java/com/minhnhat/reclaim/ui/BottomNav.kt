package com.minhnhat.reclaim.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.minhnhat.reclaim.R

/** Thanh điều hướng dưới cùng với 3 mục. Mục đang chọn có viên thuốc màu xanh lá nhạt sau biểu tượng. */
class BottomNav(private val context: Context, private val onSelect: (Int) -> Unit) {

    private class Item(val pill: FrameLayout, val icon: ImageView, val label: TextView)

    val view = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(context.getColor(R.color.surface))
    }
    private val items = ArrayList<Item>()

    fun add(iconRes: Int, labelRes: Int) {
        val index = items.size
        val icon = ImageView(context).apply { setImageResource(iconRes) }
        val pill = FrameLayout(context).apply {
            addView(icon, FrameLayout.LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER))
        }
        val label = context.label(context.getString(labelRes), 12f, R.color.text_secondary)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, context.dp(10), 0, context.dp(10))
            setOnClickListener { onSelect(index) }
            addView(pill, LinearLayout.LayoutParams(context.dp(56), context.dp(32)))
            addView(label, context.linearParams(WRAP, WRAP, top = 4))
        }
        view.addView(column, LinearLayout.LayoutParams(0, WRAP, 1f))
        items.add(Item(pill, icon, label))
    }

    fun select(selectedIndex: Int) {
        items.forEachIndexed { index, item ->
            val on = index == selectedIndex
            item.pill.background = if (on) context.roundedBackground(R.color.green_container, 16) else null
            item.icon.setColorFilter(context.getColor(if (on) R.color.green_primary else R.color.text_secondary))
            item.label.setTextColor(context.getColor(if (on) R.color.green_primary else R.color.text_secondary))
            item.label.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }
}

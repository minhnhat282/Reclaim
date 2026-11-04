package com.minhnhat.reclaim.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/** Nạp icon app ở luồng nền, có cache bitmap; an toàn với việc tái sử dụng dòng của ListView. */
class IconLoader(private val packageManager: PackageManager, private val sizePx: Int) {

    private val cache = LruCache<String, Bitmap>(160)
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(packageName: String, target: ImageView) {
        target.tag = packageName
        val cached = cache.get(packageName)
        if (cached != null) {
            target.setImageBitmap(cached)
            return
        }
        target.setImageDrawable(null)
        executor.execute {
            val bitmap = try {
                render(packageManager.getApplicationIcon(packageName))
            } catch (e: Exception) {
                null
            }
            if (bitmap != null) {
                cache.put(packageName, bitmap)
                main.post { if (target.tag == packageName) target.setImageBitmap(bitmap) }
            }
        }
    }

    private fun render(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}

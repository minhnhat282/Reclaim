package com.minhnhat.reclaim.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Danh sách app có icon launcher để người dùng chọn khóa (trừ Reclaim và app màn hình chính mặc định). */
class InstalledApps(private val context: Context) {

    data class Entry(val packageName: String, val label: String)

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun loadAsync(callback: (List<Entry>) -> Unit) {
        executor.execute {
            val list = try {
                query()
            } catch (e: Exception) {
                emptyList()
            }
            main.post { callback(list) }
        }
    }

    @Suppress("DEPRECATION")
    private fun query(): List<Entry> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        // Chỉ loại app màn hình chính MẶC ĐỊNH (khóa nó sẽ che luôn màn hình chính). Không loại mọi app có
        // category HOME: nhiều ROM khai báo màn hình dự phòng của app Cài đặt (FallbackHome) là HOME,
        // làm Cài đặt bị loại nhầm khỏi danh sách.
        val defaultHome = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

        val seen = HashSet<String>()
        val result = ArrayList<Entry>()
        for (info in pm.queryIntentActivities(launcherIntent, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || pkg == defaultHome || !seen.add(pkg)) continue
            result.add(Entry(pkg, info.loadLabel(pm).toString()))
        }
        result.sortBy { it.label.lowercase() }
        return result
    }
}

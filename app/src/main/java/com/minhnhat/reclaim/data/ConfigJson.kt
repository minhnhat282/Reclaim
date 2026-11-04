package com.minhnhat.reclaim.data

import com.minhnhat.reclaim.domain.Config
import com.minhnhat.reclaim.domain.DayWindow
import com.minhnhat.reclaim.domain.MediaRule
import com.minhnhat.reclaim.domain.Options
import com.minhnhat.reclaim.domain.WeeklySchedule
import org.json.JSONArray
import org.json.JSONObject

/**
 * Chuyển [Config] <-> JSON. Dữ liệu hỏng được bỏ qua TỪNG MỤC (một lịch hỏng không làm mất
 * cả danh sách app đã khóa) và không bao giờ làm app crash lúc khởi động.
 */
object ConfigJson {

    fun encode(config: Config): String {
        val root = JSONObject()
        root.put("version", 1)

        val locked = JSONArray()
        config.lockedPackages.sorted().forEach { locked.put(it) }
        root.put("locked", locked)

        val schedules = JSONObject()
        config.appSchedules.forEach { (pkg, schedule) -> schedules.put(pkg, encodeSchedule(schedule)) }
        root.put("schedules", schedules)

        val media = JSONObject()
        media.put("enabled", config.media.enabled)
        media.put("schedule", encodeSchedule(config.media.schedule))
        media.put("grayscale", config.media.grayscale)
        root.put("media", media)

        val options = JSONObject()
        options.put("blockVolumeKeys", config.options.blockVolumeKeys)
        options.put("restoreVolume", config.options.restoreVolume)
        options.put("tamperGuard", config.options.tamperGuard)
        root.put("options", options)

        return root.toString()
    }

    fun decode(json: String?): Config {
        if (json.isNullOrBlank()) return Config()
        return try {
            parse(JSONObject(json))
        } catch (e: Exception) {
            // Chỉ còn xảy ra khi chính chuỗi JSON không đọc được.
            Config()
        }
    }

    private fun parse(root: JSONObject): Config {
        val locked = HashSet<String>()
        root.optJSONArray("locked")?.let { array ->
            for (i in 0 until array.length()) {
                val pkg = array.optString(i, "")
                if (pkg.isNotEmpty()) locked.add(pkg)
            }
        }

        val schedules = HashMap<String, WeeklySchedule>()
        root.optJSONObject("schedules")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                decodeSchedule(obj.optJSONArray(pkg))?.let { schedules[pkg] = it }
            }
        }

        val mediaObj = root.optJSONObject("media")
        val media = MediaRule(
            enabled = mediaObj?.optBoolean("enabled", false) ?: false,
            schedule = decodeSchedule(mediaObj?.optJSONArray("schedule")) ?: WeeklySchedule.DEFAULT,
            grayscale = mediaObj?.optBoolean("grayscale", false) ?: false
        )

        val optionsObj = root.optJSONObject("options")
        val defaults = Options()
        val options = Options(
            blockVolumeKeys = optionsObj?.optBoolean("blockVolumeKeys", defaults.blockVolumeKeys)
                ?: defaults.blockVolumeKeys,
            restoreVolume = optionsObj?.optBoolean("restoreVolume", defaults.restoreVolume)
                ?: defaults.restoreVolume,
            tamperGuard = optionsObj?.optBoolean("tamperGuard", defaults.tamperGuard)
                ?: defaults.tamperGuard
        )
        return Config(locked, schedules, media, options)
    }

    /** Mỗi ngày là mảng [bật(0/1), phútBắtĐầu, phútKếtThúc]. */
    private fun encodeSchedule(schedule: WeeklySchedule): JSONArray {
        val array = JSONArray()
        schedule.days.forEach { day ->
            array.put(JSONArray().put(if (day.enabled) 1 else 0).put(day.startMin).put(day.endMin))
        }
        return array
    }

    /** Lịch hỏng (sai độ dài, sai kiểu...) trả về null để mục đó dùng mặc định thay vì phá cả cấu hình. */
    private fun decodeSchedule(array: JSONArray?): WeeklySchedule? {
        if (array == null || array.length() != 7) return null
        return try {
            val days = (0 until 7).map { i ->
                val d = array.getJSONArray(i)
                DayWindow.sanitized(d.getInt(0) == 1, d.getInt(1), d.getInt(2))
            }
            WeeklySchedule(days)
        } catch (e: Exception) {
            null
        }
    }
}

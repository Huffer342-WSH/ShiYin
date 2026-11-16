package com.Huffer342.shiyin

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

object AppStore {
    val lock = Any()
    private fun prefs(context: Context) = context.getSharedPreferences("shiyin", Context.MODE_PRIVATE)

    fun schedules(context: Context): List<Schedule> =
        ScheduleCodec.decode(prefs(context).getString("schedules", "[]")!!)

    fun save(context: Context, schedules: List<Schedule>) {
        check(prefs(context).edit().putString("schedules", ScheduleCodec.encode(schedules)).commit()) {
            "任务保存失败，请检查存储空间"
        }
    }

    fun playlists(context: Context): List<MusicPlaylist> =
        LibraryCodec.readPlaylists(prefs(context).getString("playlists_v1", "[]")!!)

    fun savePlaylists(context: Context, values: List<MusicPlaylist>) {
        check(prefs(context).edit().putString("playlists_v1", LibraryCodec.playlists(values)).commit()) { "播放列表保存失败" }
    }

    fun folders(context: Context): List<WatchedFolder> =
        LibraryCodec.readFolders(prefs(context).getString("folders_v1", "[]")!!)

    fun saveFolders(context: Context, values: List<WatchedFolder>) {
        check(prefs(context).edit().putString("folders_v1", LibraryCodec.folders(values)).commit()) { "目录保存失败" }
    }

    fun notice(context: Context, message: String) = synchronized(lock) {
        prefs(context).edit().putString("notice", message).apply()
    }

    fun notice(context: Context): String = prefs(context).getString("notice", "")!!

    fun calendars(context: Context): Map<Int, WorkCalendar> =
        prefs(context).all.filterKeys { it.startsWith("calendar_") }.map { (key, value) ->
            val year = key.removePrefix("calendar_").toInt()
            year to ScheduleCodec.calendar(value as String, year)
        }.toMap()

    // shortcut: only mainland China's annual adjustment calendar is supported; add regions with verified make-up workday data.
    fun updateCalendar(context: Context, year: Int) {
        require(year in 2000..2100)
        val connection = URL("https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/$year.json")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            check(connection.responseCode == 200) { "${year} 年日历暂不可用（HTTP ${connection.responseCode}）" }
            val bytes = connection.inputStream.use { it.readBytesLimited(256 * 1024) }
            val text = bytes.toString(Charsets.UTF_8)
            ScheduleCodec.calendar(text, year)
            synchronized(lock) {
                check(prefs(context).edit().putString("calendar_$year", text).commit()) { "日历保存失败" }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val size = read(buffer)
            if (size == -1) break
            require(output.size() + size <= limit) { "日历响应过大" }
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }
}

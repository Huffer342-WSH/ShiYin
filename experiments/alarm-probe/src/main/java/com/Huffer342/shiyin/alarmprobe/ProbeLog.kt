package com.Huffer342.shiyin.alarmprobe

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.File

internal fun record(context: Context, event: String, detail: String = "") {
    val line = JSONObject().put("wall", System.currentTimeMillis())
        .put("elapsed", SystemClock.elapsedRealtime()).put("event", event)
        .put("target", context.applicationInfo.targetSdkVersion)
        .put("run", context.getSharedPreferences("probe", 0).getString("run", ""))
        .put("detail", detail).toString()
    Log.i("ShiYinProbe", line)
    try { File(context.filesDir, "events.jsonl").appendText(line + "\n") }
    catch (error: Exception) { Log.e("ShiYinProbe", "record failed", error) }
}

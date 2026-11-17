package com.Huffer342.shiyin

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow

data class RingingAlarm(val token: String, val title: String, val deadline: Long)
fun isCurrentRingingToken(token: String?, alarm: RingingAlarm?): Boolean =
    token != null && alarm != null && token == alarm.token

object AlarmPresentation {
    val current = MutableStateFlow<RingingAlarm?>(null)
    private const val ID = 1003
    fun show(context: Context, title: String, deadline: Long) {
        val alarm = RingingAlarm(java.util.UUID.randomUUID().toString(), title, deadline)
        current.value = alarm
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("ringing", "全屏闹钟", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        val open = PendingIntent.getActivity(context, 0, Intent(context, AlarmActivity::class.java)
            .setData(Uri.parse("shiyin://ring/${alarm.token}")).putExtra("token", alarm.token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(context, 0, Intent(context, PlaybackService::class.java)
            .setAction(STOP).setData(Uri.parse("shiyin://stop/${alarm.token}")).putExtra("token", alarm.token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, "ringing").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText("闹钟响铃中").setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true).addAction(0, "停止", stop)
        if (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) builder.setFullScreenIntent(open, true)
        try { manager.notify(ID, builder.build()) } catch (_: SecurityException) { }
    }
    fun clear(context: Context) {
        current.value = null
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
    const val STOP = "com.Huffer342.shiyin.STOP_RINGING"
}

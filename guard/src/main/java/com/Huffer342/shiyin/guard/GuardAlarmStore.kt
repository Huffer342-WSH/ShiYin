package com.Huffer342.shiyin.guard

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Log
import org.json.JSONObject

internal fun encodeAlarmMap(alarms: Map<String, Long>): String {
    val json = JSONObject()
    alarms.forEach { (id, expectedAt) ->
        if (id.isNotBlank() && expectedAt > 0L) json.put(id, expectedAt)
    }
    return json.toString()
}

internal fun decodeAlarmMap(raw: String): Map<String, Long> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(raw)
        val result = linkedMapOf<String, Long>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val expectedAt = json.optLong(id, 0L)
            if (id.isNotBlank() && expectedAt > 0L) result[id] = expectedAt
        }
        result
    }.getOrDefault(emptyMap())
}

internal fun isValidFireWindow(expectedAt: Long, storedExpectedAt: Long?, now: Long): Boolean =
    expectedAt > 0L && storedExpectedAt == expectedAt && now >= expectedAt && now - expectedAt <= 120_000L

internal object GuardAlarmStore {
    const val TAG = "ShiYinGuard"
    const val MAIN_PACKAGE = "com.Huffer342.shiyin"
    const val ACTION_FIRE_ALARM = "com.Huffer342.shiyin.guard.FIRE_ALARM"
    const val ACTION_COMPANION_WAKE = "com.Huffer342.shiyin.COMPANION_ALARM"
    private const val PREFS = "shiyin_guard"
    private const val KEY_ALARMS = "alarms_v1"
    private const val UNINSTALL_CHANNEL = "guard_uninstall"
    private const val UNINSTALL_NOTIFICATION_ID = 2001

    val lock = Any()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun notificationsEnabled(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun isMainAppInstalled(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(MAIN_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(MAIN_PACKAGE, 0)
        }
        true
    }.getOrDefault(false)

    fun load(context: Context): Map<String, Long> =
        decodeAlarmMap(prefs(context).getString(KEY_ALARMS, "{}").orEmpty())

    private fun save(context: Context, alarms: Map<String, Long>) {
        prefs(context).edit().putString(KEY_ALARMS, encodeAlarmMap(alarms)).commit()
    }

    private fun fireOperation(context: Context, id: String, expectedAt: Long): PendingIntent {
        val intent = Intent(context, GuardFireReceiver::class.java)
            .setAction(ACTION_FIRE_ALARM)
            .setData(Uri.parse("shiyin://guard-alarm/$id"))
            .putExtra("id", id)
            .putExtra("expectedAt", expectedAt)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun applyAlarmOperation(context: Context, id: String, expectedAt: Long, now: Long) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java)
        val operation = fireOperation(app, id, expectedAt)
        alarms.cancel(operation)
        if (expectedAt > now && canScheduleExactAlarms(app) && isMainAppInstalled(app)) {
            val showIntent = Intent().setClassName(MAIN_PACKAGE, "$MAIN_PACKAGE.MainActivity")
            val show = PendingIntent.getActivity(
                app, 0, showIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(expectedAt, show), operation)
            Log.i(TAG, "setAlarmClock id=$id expectedAt=$expectedAt pid=${Process.myPid()}")
        } else {
            Log.i(TAG, "canceled id=$id expectedAt=$expectedAt canSchedule=${canScheduleExactAlarms(app)}")
        }
    }

    fun syncOne(context: Context, id: String, expectedAt: Long) = synchronized(lock) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val current = load(app).toMutableMap()
        if (expectedAt > now) current[id] = expectedAt else current.remove(id)
        save(app, current)
        applyAlarmOperation(app, id, expectedAt, now)
    }

    fun syncAll(context: Context, desired: Map<String, Long>) = synchronized(lock) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val previous = load(app)
        previous.keys.filter { it !in desired }.forEach { removedId ->
            applyAlarmOperation(app, removedId, 0L, now)
        }
        val cleaned = desired.filter { (id, at) -> id.isNotBlank() && at > now }
        save(app, cleaned)
        cleaned.forEach { (id, expectedAt) ->
            applyAlarmOperation(app, id, expectedAt, now)
        }
    }

    fun rescheduleAll(context: Context) = synchronized(lock) {
        val app = context.applicationContext
        if (!isMainAppInstalled(app)) {
            cancelAll(app)
            return
        }
        val now = System.currentTimeMillis()
        val current = load(app)
        val active = current.filterValues { it > now - 120_000L }
        if (active.size != current.size) save(app, active)
        active.forEach { (id, expectedAt) ->
            applyAlarmOperation(app, id, expectedAt, now)
        }
    }

    fun consumeIfDue(context: Context, id: String, expectedAt: Long, now: Long): Boolean = synchronized(lock) {
        val app = context.applicationContext
        val current = load(app)
        val stored = current[id]
        val valid = isValidFireWindow(expectedAt, stored, now)
        if (stored != null && (valid || now > stored + 120_000L)) {
            save(app, current - id)
        }
        valid
    }

    fun cancelAll(context: Context) = synchronized(lock) {
        val app = context.applicationContext
        val current = load(app)
        val now = System.currentTimeMillis()
        current.keys.forEach { id -> applyAlarmOperation(app, id, 0L, now) }
        save(app, emptyMap())
    }

    fun showUninstallPrompt(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(UNINSTALL_CHANNEL, "主应用卸载提醒", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(
            app, 1, uninstallIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(app, UNINSTALL_CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(app)
        }
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("时音主应用已卸载")
            .setContentText("点击即可卸载「时音闹钟守护」组件")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        runCatching { manager.notify(UNINSTALL_NOTIFICATION_ID, builder.build()) }
    }

    fun clearUninstallPrompt(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)
            .cancel(UNINSTALL_NOTIFICATION_ID)
    }
}

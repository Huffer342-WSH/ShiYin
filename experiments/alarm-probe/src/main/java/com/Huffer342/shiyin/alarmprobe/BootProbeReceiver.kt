package com.Huffer342.shiyin.alarmprobe

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.util.Log

class CompanionSyncProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context?.applicationContext ?: return null
        Log.i("ShiYinCompanion", "CompanionSyncProvider call method=$method id=$arg pid=${Process.myPid()}")
        if (method == "sync_alarm" && arg != null) {
            val expectedAt = extras?.getLong("expectedAt", 0L) ?: 0L
            BootProbeReceiver.handleSyncAlarm(ctx, arg, expectedAt)
        } else if (method == "log" && arg != null) {
            record(ctx, arg, extras?.getString("detail").orEmpty())
        }
        return Bundle.EMPTY
    }
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}

class BootProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive action=${intent.action} pid=${Process.myPid()}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> record(context, "boot_received")
            ACTION_SYNC_ALARM -> {
                val id = intent.getStringExtra("id") ?: return
                val expectedAt = intent.getLongExtra("expectedAt", 0L)
                handleSyncAlarm(context, id, expectedAt)
            }
            ACTION_FIRE_ALARM -> {
                val id = intent.getStringExtra("id") ?: return
                val expectedAt = intent.getLongExtra("expectedAt", 0L)
                Log.i(TAG, "companion firing alarm id=$id expectedAt=$expectedAt pid=${Process.myPid()}")
                record(context, "companion_fired", "id=$id expectedAt=$expectedAt")
                val broadcastSent = runCatching {
                    val wakeBroadcast = Intent(ACTION_COMPANION_WAKE)
                        .setClassName("com.Huffer342.shiyin", "com.Huffer342.shiyin.ScheduleReceiver")
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
                        .putExtra("id", id)
                        .putExtra("expectedAt", expectedAt)
                    context.sendBroadcast(wakeBroadcast)
                    Log.i(TAG, "companion sent wake broadcast to com.Huffer342.shiyin")
                    record(context, "wake_broadcast_sent")
                }.onFailure {
                    Log.w(TAG, "companion sendBroadcast failed: ${it.message}", it)
                    record(context, "wake_broadcast_fail", it.toString())
                }.isSuccess
                if (!broadcastSent) {
                    runCatching {
                        val serviceIntent = Intent("com.Huffer342.shiyin.ALARM")
                            .setClassName("com.Huffer342.shiyin", "com.Huffer342.shiyin.PlaybackService")
                            .setData(Uri.parse("shiyin://alarm/$id"))
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            .putExtra("id", id)
                            .putExtra("expectedAt", expectedAt)
                        context.startForegroundService(serviceIntent)
                        Log.i(TAG, "companion startForegroundService fallback succeeded")
                        record(context, "wake_fgs_ok")
                    }.onFailure {
                        Log.w(TAG, "companion startForegroundService fallback failed: ${it.message}", it)
                        record(context, "wake_fgs_fail", it.toString())
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ShiYinCompanion"
        const val ACTION_SYNC_ALARM = "com.Huffer342.shiyin.alarmprobe.SYNC_ALARM"
        const val ACTION_FIRE_ALARM = "com.Huffer342.shiyin.alarmprobe.FIRE_ALARM"
        const val ACTION_COMPANION_WAKE = "com.Huffer342.shiyin.COMPANION_ALARM"

        fun handleSyncAlarm(context: Context, id: String, expectedAt: Long) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val fireIntent = Intent(context, BootProbeReceiver::class.java)
                .setAction(ACTION_FIRE_ALARM)
                .setData(Uri.parse("shiyin://companion-alarm/$id"))
                .putExtra("id", id)
                .putExtra("expectedAt", expectedAt)
            val operation = PendingIntent.getBroadcast(
                context,
                0,
                fireIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarms.cancel(operation)
            if (expectedAt > System.currentTimeMillis() && alarms.canScheduleExactAlarms()) {
                val show = PendingIntent.getActivity(
                    context,
                    0,
                    Intent().setClassName("com.Huffer342.shiyin", "com.Huffer342.shiyin.MainActivity"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(expectedAt, show), operation)
                Log.i(TAG, "companion setAlarmClock id=$id expectedAt=$expectedAt pid=${Process.myPid()}")
                record(context, "companion_scheduled", "id=$id expectedAt=$expectedAt")
            } else {
                Log.i(TAG, "companion canceled id=$id expectedAt=$expectedAt canSchedule=${alarms.canScheduleExactAlarms()}")
                record(context, "companion_canceled", "id=$id expectedAt=$expectedAt")
            }
        }
    }
}

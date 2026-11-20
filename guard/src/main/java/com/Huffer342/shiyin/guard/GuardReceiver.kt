package com.Huffer342.shiyin.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Log

class GuardFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GuardAlarmStore.ACTION_FIRE_ALARM) return
        val id = intent.getStringExtra("id") ?: return
        val expectedAt = intent.getLongExtra("expectedAt", 0L)
        if (!GuardAlarmStore.isMainAppInstalled(context)) {
            GuardAlarmStore.cancelAll(context)
            return
        }
        if (!GuardAlarmStore.consumeIfDue(context, id, expectedAt, System.currentTimeMillis())) {
            Log.w(GuardAlarmStore.TAG, "Ignored stale/unmatched fire id=$id expectedAt=$expectedAt")
            return
        }
        Log.i(GuardAlarmStore.TAG, "Firing companion wake for id=$id expectedAt=$expectedAt pid=${Process.myPid()}")
        val broadcastSent = runCatching {
            val wakeBroadcast = Intent(GuardAlarmStore.ACTION_COMPANION_WAKE)
                .setClassName(GuardAlarmStore.MAIN_PACKAGE, "${GuardAlarmStore.MAIN_PACKAGE}.CompanionAlarmReceiver")
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra("id", id)
                .putExtra("expectedAt", expectedAt)
            context.sendBroadcast(wakeBroadcast)
        }.onFailure {
            Log.w(GuardAlarmStore.TAG, "sendBroadcast failed: ${it.message}", it)
        }.isSuccess
        if (!broadcastSent) {
            runCatching {
                val serviceIntent = Intent("${GuardAlarmStore.MAIN_PACKAGE}.ALARM")
                    .setClassName(GuardAlarmStore.MAIN_PACKAGE, "${GuardAlarmStore.MAIN_PACKAGE}.PlaybackService")
                    .setData(Uri.parse("shiyin://alarm/$id"))
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    .putExtra("id", id)
                    .putExtra("expectedAt", expectedAt)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent)
                else context.startService(serviceIntent)
            }.onFailure {
                Log.w(GuardAlarmStore.TAG, "startForegroundService fallback failed: ${it.message}", it)
            }
        }
    }
}

class GuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(GuardAlarmStore.TAG, "GuardReceiver action=$action pid=${Process.myPid()}")
        when (action) {
            Intent.ACTION_PACKAGE_FULLY_REMOVED, Intent.ACTION_PACKAGE_REMOVED -> {
                val pkg = intent.data?.schemeSpecificPart
                val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (pkg == GuardAlarmStore.MAIN_PACKAGE && !replacing) {
                    GuardAlarmStore.cancelAll(context)
                    GuardAlarmStore.showUninstallPrompt(context)
                }
            }
            Intent.ACTION_PACKAGE_ADDED -> {
                if (intent.data?.schemeSpecificPart == GuardAlarmStore.MAIN_PACKAGE) {
                    GuardAlarmStore.clearUninstallPrompt(context)
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                GuardAlarmStore.rescheduleAll(context)
            }
        }
    }
}

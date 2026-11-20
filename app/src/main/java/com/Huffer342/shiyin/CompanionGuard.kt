package com.Huffer342.shiyin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

data class CompanionGuardStatus(
    val installed: Boolean = false,
    val signatureMatches: Boolean = false,
    val canScheduleExactAlarms: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val activeCount: Int = 0,
    val hasBundledApk: Boolean = false
) {
    val ready: Boolean get() = installed && signatureMatches && canScheduleExactAlarms
}

internal fun encodeGuardAlarmSyncJson(schedules: List<Schedule>, now: Long): String {
    val json = JSONObject()
    schedules.forEach { schedule ->
        if (schedule.enabled && schedule.nextAt > now) {
            json.put(schedule.id, schedule.nextAt)
        }
    }
    return json.toString()
}

object CompanionGuard {
    private const val TAG = "ShiYinGuardClient"
    const val GUARD_PACKAGE = "com.Huffer342.shiyin.guard"
    private const val GUARD_SETUP_ACTION = "com.Huffer342.shiyin.guard.SETUP"
    private const val GUARD_ACTIVITY_CLASS = "com.Huffer342.shiyin.guard.GuardActivity"
    private const val GUARD_AUTHORITY = "com.Huffer342.shiyin.guard.sync"
    const val ACTION_COMPANION_ALARM = "com.Huffer342.shiyin.COMPANION_ALARM"
    private const val GUARD_ASSET_NAME = "shiyin-guard.apk"

    fun isInstalled(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(GUARD_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(GUARD_PACKAGE, 0)
        }
        true
    }.getOrDefault(false)

    fun signatureMatches(context: Context): Boolean {
        if (!isInstalled(context)) return false
        @Suppress("DEPRECATION")
        return context.packageManager.checkSignatures(context.packageName, GUARD_PACKAGE) == PackageManager.SIGNATURE_MATCH
    }

    fun hasBundledApk(context: Context): Boolean = runCatching {
        context.assets.open(GUARD_ASSET_NAME).close()
        true
    }.getOrDefault(false)

    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun queryStatus(context: Context): CompanionGuardStatus {
        val installed = isInstalled(context)
        val sigMatch = installed && signatureMatches(context)
        val hasAsset = hasBundledApk(context)
        if (!installed || !sigMatch) {
            return CompanionGuardStatus(
                installed = installed,
                signatureMatches = sigMatch,
                hasBundledApk = hasAsset
            )
        }
        val result = runCatching {
            context.contentResolver.call(Uri.parse("content://$GUARD_AUTHORITY"), "status", null, null)
        }.onFailure {
            Log.w(TAG, "queryStatus call failed: ${it.message}")
        }.getOrNull()
        return CompanionGuardStatus(
            installed = true,
            signatureMatches = true,
            canScheduleExactAlarms = result?.getBoolean("canScheduleExactAlarms", false) ?: false,
            notificationsEnabled = result?.getBoolean("notificationsEnabled", false) ?: false,
            activeCount = result?.getInt("activeCount", 0) ?: 0,
            hasBundledApk = hasAsset
        )
    }

    fun syncOne(context: Context, id: String, expectedAt: Long) {
        if (!isInstalled(context) || !signatureMatches(context)) return
        runCatching {
            val extras = Bundle().apply { putLong("expectedAt", expectedAt) }
            context.contentResolver.call(
                Uri.parse("content://$GUARD_AUTHORITY"),
                "sync_alarm",
                id,
                extras
            )
        }.onFailure {
            Log.w(TAG, "syncOne failed id=$id: ${it.message}")
        }
    }

    fun syncAll(context: Context, schedules: List<Schedule>) {
        if (!isInstalled(context) || !signatureMatches(context)) return
        val json = encodeGuardAlarmSyncJson(schedules, System.currentTimeMillis())
        runCatching {
            val extras = Bundle().apply { putString("alarms_json", json) }
            context.contentResolver.call(
                Uri.parse("content://$GUARD_AUTHORITY"),
                "sync_all",
                null,
                extras
            )
        }.onFailure {
            Log.w(TAG, "syncAll failed: ${it.message}")
        }
    }

    fun prepareInstallerIntent(context: Context): Intent {
        val dir = File(context.cacheDir, "guard").apply { mkdirs() }
        val target = File(dir, GUARD_ASSET_NAME)
        context.assets.open(GUARD_ASSET_NAME).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun unknownSourcesIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    } else null

    fun setupIntent(): Intent = Intent(GUARD_SETUP_ACTION)
        .setPackage(GUARD_PACKAGE)
        .setClassName(GUARD_PACKAGE, GUARD_ACTIVITY_CLASS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$GUARD_PACKAGE"))

    fun uninstallIntent(): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$GUARD_PACKAGE"))
}

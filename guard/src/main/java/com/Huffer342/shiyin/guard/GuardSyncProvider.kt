package com.Huffer342.shiyin.guard

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log

class GuardSyncProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context?.applicationContext ?: return Bundle.EMPTY
        verifyMainAppCaller(ctx)
        when (method) {
            "sync_alarm" -> if (!arg.isNullOrBlank()) {
                val expectedAt = extras?.getLong("expectedAt", 0L) ?: 0L
                GuardAlarmStore.syncOne(ctx, arg, expectedAt)
            }
            "sync_all" -> {
                val desired = decodeAlarmMap(extras?.getString("alarms_json").orEmpty())
                GuardAlarmStore.syncAll(ctx, desired)
            }
            "status" -> Unit
            else -> Log.w(GuardAlarmStore.TAG, "Unknown method=$method pid=${Process.myPid()}")
        }
        return statusBundle(ctx)
    }

    private fun verifyMainAppCaller(ctx: Context) {
        val caller = callingPackage
        val uidPackages = ctx.packageManager.getPackagesForUid(Binder.getCallingUid())?.toSet().orEmpty()
        val packageValid = if (caller != null) {
            caller == GuardAlarmStore.MAIN_PACKAGE
        } else {
            uidPackages.size == 1 && GuardAlarmStore.MAIN_PACKAGE in uidPackages
        }
        if (!packageValid) {
            throw SecurityException("Caller $caller is not ${GuardAlarmStore.MAIN_PACKAGE}")
        }
        @Suppress("DEPRECATION")
        val sigMatch = ctx.packageManager.checkSignatures(ctx.packageName, GuardAlarmStore.MAIN_PACKAGE)
        if (sigMatch != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("Caller signature mismatch: $sigMatch")
        }
    }

    private fun statusBundle(ctx: Context): Bundle {
        val pkgInfo = if (Build.VERSION.SDK_INT >= 33) {
            ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= 28) pkgInfo.longVersionCode.toInt()
        else @Suppress("DEPRECATION") pkgInfo.versionCode
        return Bundle().apply {
            putBoolean("canScheduleExactAlarms", GuardAlarmStore.canScheduleExactAlarms(ctx))
            putBoolean("notificationsEnabled", GuardAlarmStore.notificationsEnabled(ctx))
            putInt("activeCount", GuardAlarmStore.load(ctx).size)
            putInt("versionCode", versionCode)
        }
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}

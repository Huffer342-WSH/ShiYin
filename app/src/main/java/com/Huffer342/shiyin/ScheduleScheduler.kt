package com.Huffer342.shiyin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

object ScheduleScheduler {
    private fun manager(context: Context) = context.getSystemService(AlarmManager::class.java)

    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || manager(context).canScheduleExactAlarms()

    private fun operation(context: Context, schedule: Schedule): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_ALARM)
            .setData(Uri.parse("shiyin://alarm/${schedule.id}"))
            .putExtra("id", schedule.id).putExtra("expectedAt", schedule.nextAt)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, 0, intent, flags)
        else PendingIntent.getService(context, 0, intent, flags)
    }

    private fun scheduleOne(context: Context, schedule: Schedule): Schedule {
        manager(context).cancel(operation(context, schedule))
        if (!schedule.enabled) return schedule.copy(nextAt = 0, status = "已关闭")
        if (!canSchedule(context)) return schedule.copy(nextAt = 0, status = "等待闹钟和提醒授权")
        return try {
            val next = nextOccurrence(schedule, ZonedDateTime.now(), AppStore.calendars(context))
            if (next == null) schedule.copy(enabled = false, nextAt = 0, status = "时间已过，未补播")
            else {
                val scheduled = schedule.copy(nextAt = next.toInstant().toEpochMilli(), status = "")
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                manager(context).setAlarmClock(
                    AlarmManager.AlarmClockInfo(scheduled.nextAt, show), operation(context, scheduled)
                )
                scheduled
            }
        } catch (e: Exception) {
            schedule.copy(nextAt = 0, status = e.message ?: "闹钟注册失败")
        }
    }

    fun save(context: Context, schedule: Schedule) = synchronized(AppStore.lock) {
        val current = AppStore.schedules(context)
        val updated = current.filterNot { it.id == schedule.id } + schedule.copy(nextAt = 0)
        // Persist the user's change before changing the system alarm.
        AppStore.save(context, updated)
        val registered = scheduleOne(context, schedule)
        AppStore.save(context, updated.map { if (it.id == schedule.id) registered else it })
    }

    fun delete(context: Context, id: String) = synchronized(AppStore.lock) {
        val current = AppStore.schedules(context)
        AppStore.save(context, current.filterNot { it.id == id })
        current.find { it.id == id }?.let { manager(context).cancel(operation(context, it)) }
    }

    fun reschedule(context: Context, force: Boolean = false) = synchronized(AppStore.lock) {
        val now = System.currentTimeMillis()
        AppStore.save(context, AppStore.schedules(context).map {
            // Leave an alarm being delivered alone when the UI resumes at its trigger time.
            if (!force && canSchedule(context) && it.enabled &&
                it.nextAt in (now - 120_000)..(now + 2_000)) it
            else scheduleOne(context, it)
        })
    }

    fun consume(context: Context, id: String, expectedAt: Long): Schedule? = synchronized(AppStore.lock) {
        val current = AppStore.schedules(context)
        val target = current.find { it.id == id } ?: return@synchronized null
        if (!target.enabled || target.nextAt != expectedAt || expectedAt <= 0) return@synchronized null
        val valid = shouldFire(target, expectedAt, System.currentTimeMillis())
        val next = if (target.rule == RepeatRule.ONCE) {
            target.copy(enabled = false, nextAt = 0, status = if (valid) "已触发" else "已错过，未补播")
        } else scheduleOne(context, target)
        AppStore.save(context, current.map { if (it.id == id) next else it })
        if (!valid) AppStore.notice(context, "任务「${target.title}」已错过，未补播")
        target.takeIf { valid }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleScheduler.reschedule(context, force = true)
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
                    AlarmStandbyService.restoreAfterBoot(context)
            } catch (e: Exception) {
                AppStore.notice(context, e.message ?: "恢复定时任务失败，请打开时音检查")
            } finally {
                pending.finish()
            }
        }
    }
}

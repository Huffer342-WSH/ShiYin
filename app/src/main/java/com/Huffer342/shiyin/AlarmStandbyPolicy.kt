package com.Huffer342.shiyin

fun shouldRestoreStandby(schedules: List<Schedule>, now: Long): Boolean =
    schedules.any { it.enabled && it.nextAt > now }

/** Keep the delivery grace window so a due alarm is not mistaken for an obsolete one. */
fun standbyNextAt(schedules: List<Schedule>, now: Long): Long? =
    schedules.filter { it.enabled && it.nextAt > 0 && it.nextAt >= now - 120_000 }
        .minOfOrNull { it.nextAt }

fun needsAlarmStandby(schedules: List<Schedule>, now: Long, playbackStarting: Boolean): Boolean =
    playbackStarting || standbyNextAt(schedules, now) != null

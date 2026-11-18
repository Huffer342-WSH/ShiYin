package com.Huffer342.shiyin.alarmprobe

internal fun validWaitingRun(token: String?, current: String?, due: Long, now: Long): Boolean =
    !token.isNullOrBlank() && token == current && due > now && due - now <= 240_000


internal fun validRun(token: String?, current: String?, due: Long, now: Long): Boolean =
    !token.isNullOrBlank() && token == current && due > 0 && now >= due && now - due <= 30_000

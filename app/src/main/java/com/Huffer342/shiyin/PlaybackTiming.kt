package com.Huffer342.shiyin

object PlaybackTiming {
    fun fadeGain(elapsedMillis: Long, fadeSeconds: Int): Float {
        require(fadeSeconds >= 0)
        return if (fadeSeconds == 0) 1f
        else (elapsedMillis.toFloat() / (fadeSeconds * 1000L)).coerceIn(0f, 1f)
    }

    fun expired(deadline: Long, now: Long): Boolean = deadline > 0 && now >= deadline

    fun sleepDeadline(now: Long, minutes: Int): Long {
        require(minutes in 0..1440)
        return if (minutes == 0) 0 else now + minutes * 60_000L
    }
}

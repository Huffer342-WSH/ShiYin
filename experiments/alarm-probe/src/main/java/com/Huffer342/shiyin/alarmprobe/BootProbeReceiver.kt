package com.Huffer342.shiyin.alarmprobe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Only observes system startup; it must not conceal alarm cancellation by rearming it.
class BootProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) record(context, "boot_received")
    }
}

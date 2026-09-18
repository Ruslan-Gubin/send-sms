package ru.pumpsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON")) return
        val prefs = context.getSharedPreferences("pump", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("isRunning", false)) return
        val phone = prefs.getString("senderPhone", "") ?: ""
        if (phone.isBlank()) return
        PumpService.start(context, phone)
    }
}

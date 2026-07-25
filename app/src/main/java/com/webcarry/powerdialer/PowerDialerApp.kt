package com.webcarry.powerdialer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PowerDialerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SYNC_CHANNEL_ID,
                "Power Dialer sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps your phone connected to the WebCarry Power Dialer dashboard."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val SYNC_CHANNEL_ID = "wcab_power_dialer_sync"
    }
}

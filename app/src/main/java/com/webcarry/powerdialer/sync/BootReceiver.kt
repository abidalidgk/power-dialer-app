package com.webcarry.powerdialer.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.webcarry.powerdialer.prefs.SecurePrefs

/**
 * Without this, a phone restart would silently disconnect the dashboard
 * pairing until someone remembers to reopen the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = SecurePrefs(context)
        if (!prefs.isPaired) return

        val serviceIntent = Intent(context, CallSmsSyncService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

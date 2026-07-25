package com.webcarry.powerdialer.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Logs incoming SMS to the dashboard. The message stays on the phone as
 * normal (this app is not the default SMS app and never intercepts or
 * blocks anything) — it just also reports the sender/body to the paired
 * WebCarry dashboard so staff can see it alongside the call activity.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }

        val serviceIntent = Intent(context, CallSmsSyncService::class.java).apply {
            action = CallSmsSyncService.ACTION_LOG_SMS
            putExtra(CallSmsSyncService.EXTRA_PHONE, sender)
            putExtra(CallSmsSyncService.EXTRA_SMS_BODY, body)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
    }
}

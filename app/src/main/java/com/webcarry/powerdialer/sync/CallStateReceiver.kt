package com.webcarry.powerdialer.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tracks RINGING → OFFHOOK → IDLE transitions to work out, for every call,
 * whether it was answered, missed, rejected, or not answered — and its
 * duration — then hands that off to CallSmsSyncService to report to the
 * paired WebCarry dashboard.
 *
 * This does NOT rely on being the phone's default dialer; it only listens
 * to the standard PHONE_STATE / NEW_OUTGOING_CALL broadcasts, same as any
 * call-recording or call-blocking app on the Play Store.
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                if (!number.isNullOrBlank()) expectedOutgoingPhone = number
            }
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                handleStateChange(context, state, incomingNumber)
            }
        }
    }

    private fun handleStateChange(context: Context, state: String?, incomingNumber: String?) {
        val now = System.currentTimeMillis()

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                wasRinging = true
                callAnswered = false
                if (!incomingNumber.isNullOrBlank()) currentNumber = incomingNumber
                isIncoming = true
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (offHookStartedAt == 0L) offHookStartedAt = now
                callAnswered = true
                if (!wasRinging) {
                    // We went straight to OFFHOOK without ringing on this device
                    // → this is an outgoing call we just dialed.
                    isIncoming = false
                    if (currentNumber.isNullOrBlank()) currentNumber = expectedOutgoingPhone
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (!currentNumber.isNullOrBlank()) {
                    val duration = if (offHookStartedAt > 0) ((now - offHookStartedAt) / 1000).toInt() else 0
                    val direction = if (isIncoming == true) "incoming" else "outgoing"
                    val status = when {
                        isIncoming == true && !callAnswered -> "missed"
                        isIncoming == false && !callAnswered -> "no_answer"
                        else -> "answered"
                    }
                    reportCall(context, currentNumber!!, direction, status, duration, offHookStartedAt.takeIf { it > 0 } ?: now)
                }
                // reset for the next call
                wasRinging = false
                callAnswered = false
                offHookStartedAt = 0L
                currentNumber = null
                isIncoming = null
                expectedOutgoingPhone = null
            }
        }
    }

    private fun reportCall(
        context: Context,
        phone: String,
        direction: String,
        status: String,
        durationSeconds: Int,
        startedAtMs: Long
    ) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val intent = Intent(context, CallSmsSyncService::class.java).apply {
            action = CallSmsSyncService.ACTION_LOG_CALL
            putExtra(CallSmsSyncService.EXTRA_PHONE, phone)
            putExtra(CallSmsSyncService.EXTRA_DIRECTION, direction)
            putExtra(CallSmsSyncService.EXTRA_CALL_STATUS, status)
            putExtra(CallSmsSyncService.EXTRA_DURATION, durationSeconds)
            putExtra(CallSmsSyncService.EXTRA_STARTED_AT, fmt.format(Date(startedAtMs)))
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        // Process-lifetime state; safe because the sync foreground service keeps
        // this app process alive for as long as a phone stays paired.
        @Volatile var expectedOutgoingPhone: String? = null

        private var wasRinging = false
        private var callAnswered = false
        private var offHookStartedAt = 0L
        private var currentNumber: String? = null
        private var isIncoming: Boolean? = null
    }
}

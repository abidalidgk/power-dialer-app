package com.webcarry.powerdialer.sync

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.webcarry.powerdialer.MainActivity
import com.webcarry.powerdialer.PowerDialerApp
import com.webcarry.powerdialer.R
import com.webcarry.powerdialer.api.AckRequest
import com.webcarry.powerdialer.api.ApiClient
import com.webcarry.powerdialer.api.CallLogRequest
import com.webcarry.powerdialer.api.HeartbeatRequest
import com.webcarry.powerdialer.api.SmsLogRequest
import com.webcarry.powerdialer.prefs.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs continuously once a phone is paired. It:
 *  1) sends a periodic heartbeat so the dashboard can show "last seen",
 *  2) polls the website for pending call/SMS requests queued from the
 *     dashboard and carries them out through this phone's own dialer/SMS,
 *  3) receives call/SMS activity reported by CallStateReceiver / SmsReceiver
 *     and forwards it to the website so it shows up in the Call Log.
 */
class CallSmsSyncService : Service() {

    private var job = Job()
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: SecurePrefs

    override fun onCreate() {
        super.onCreate()
        prefs = SecurePrefs(this)
        job = Job()
        scope = CoroutineScope(Dispatchers.IO + job)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_LOG_CALL -> {
                handleLogCall(intent)
                return START_STICKY
            }
            ACTION_LOG_SMS -> {
                handleLogSms(intent)
                return START_STICKY
            }
        }

        startSyncLoopIfNeeded()
        return START_STICKY
    }

    private var loopStarted = false
    private fun startSyncLoopIfNeeded() {
        if (loopStarted) return
        loopStarted = true
        scope.launch {
            while (true) {
                try {
                    syncOnce()
                } catch (_: Exception) {
                    // Network hiccup — just retry on the next tick.
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun syncOnce() {
        if (!prefs.isPaired) return
        val api = ApiClient.build(prefs) ?: return

        // 1) Heartbeat
        try {
            api.heartbeat(HeartbeatRequest(phone_number = prefs.myPhoneNumber))
        } catch (_: Exception) { }

        // 2) Pull pending call/SMS requests queued from the dashboard
        val response = api.getQueue()
        val items = response.body()?.items ?: return

        for (item in items) {
            var success = true
            try {
                when (item.action_type) {
                    "call" -> placeCall(item.phone)
                    "sms" -> sendSms(item.phone, item.message ?: "")
                    else -> success = false
                }
            } catch (e: Exception) {
                success = false
            }
            try {
                api.ackQueue(item.id, AckRequest(status = if (success) "done" else "failed"))
            } catch (_: Exception) { }
        }
    }

    private fun placeCall(phone: String) {
        CallStateReceiver.expectedOutgoingPhone = phone
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun sendSms(phone: String, message: String) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(phone, null, parts, null, null)

        // Log immediately (fire-and-forget); PROCESS_OUTGOING confirmation isn't
        // available for SMS the way it is for calls, so we log optimistically.
        scope.launch {
            val api = ApiClient.build(prefs) ?: return@launch
            try {
                api.postSmsLog(
                    SmsLogRequest(
                        phone = phone,
                        direction = "outgoing",
                        sms_status = "sent",
                        body = message,
                        sent_at = nowFormatted()
                    )
                )
            } catch (_: Exception) { }
        }
    }

    private fun handleLogCall(intent: Intent) {
        val phone = intent.getStringExtra(EXTRA_PHONE) ?: return
        val direction = intent.getStringExtra(EXTRA_DIRECTION) ?: "outgoing"
        val status = intent.getStringExtra(EXTRA_CALL_STATUS) ?: "unknown"
        val duration = intent.getIntExtra(EXTRA_DURATION, 0)
        val startedAt = intent.getStringExtra(EXTRA_STARTED_AT) ?: nowFormatted()

        scope.launch {
            val api = ApiClient.build(prefs) ?: return@launch
            try {
                api.postCallLog(
                    CallLogRequest(
                        phone = phone,
                        direction = direction,
                        call_status = status,
                        duration_seconds = duration,
                        started_at = startedAt
                    )
                )
            } catch (_: Exception) { }
        }
    }

    private fun handleLogSms(intent: Intent) {
        val phone = intent.getStringExtra(EXTRA_PHONE) ?: return
        val body = intent.getStringExtra(EXTRA_SMS_BODY) ?: ""
        scope.launch {
            val api = ApiClient.build(prefs) ?: return@launch
            try {
                api.postSmsLog(
                    SmsLogRequest(
                        phone = phone,
                        direction = "incoming",
                        sms_status = "received",
                        body = body,
                        sent_at = nowFormatted()
                    )
                )
            } catch (_: Exception) { }
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, PowerDialerApp.SYNC_CHANNEL_ID)
            .setContentTitle("Power Dialer connected")
            .setContentText("Calls and SMS from your dashboard will use this phone.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 4821
        private const val POLL_INTERVAL_MS = 10_000L

        const val ACTION_LOG_CALL = "com.webcarry.powerdialer.action.LOG_CALL"
        const val ACTION_LOG_SMS = "com.webcarry.powerdialer.action.LOG_SMS"

        const val EXTRA_PHONE = "phone"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_CALL_STATUS = "call_status"
        const val EXTRA_DURATION = "duration_seconds"
        const val EXTRA_STARTED_AT = "started_at"
        const val EXTRA_SMS_BODY = "sms_body"

        fun nowFormatted(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            return fmt.format(Date())
        }
    }
}

package com.webcarry.powerdialer.api

import com.google.gson.annotations.SerializedName

data class PairClaimRequest(
    val code: String,
    val device_model: String,
    val device_name: String,
    val app_version: String,
    val phone_number: String?
)

data class PairClaimResponse(
    val device_token: String,
    val staff_name: String?,
    val site_name: String?
)

data class HeartbeatRequest(
    val phone_number: String?
)

data class QueueItem(
    val id: Long,
    val staff_id: Long,
    val device_id: Long,
    val contact_id: Long?,
    val action_type: String, // "call" or "sms"
    val phone: String,
    val message: String?
)

data class QueueResponse(
    val items: List<QueueItem>
)

data class AckRequest(
    val status: String // "done" or "failed"
)

data class CallLogRequest(
    val phone: String,
    val direction: String, // "outgoing" or "incoming"
    val call_status: String, // answered/missed/no_answer/busy/rejected/failed/unknown
    val duration_seconds: Int,
    val started_at: String // "yyyy-MM-dd HH:mm:ss" in site's local time (UTC also fine, server just stores it)
)

data class SmsLogRequest(
    val phone: String,
    val direction: String, // "outgoing" or "incoming"
    val sms_status: String, // "sent", "received", "failed"
    val body: String,
    val sent_at: String
)

data class SimpleOkResponse(
    val ok: Boolean? = null,
    @SerializedName("matched_contact_id") val matchedContactId: Long? = null
)

data class ApiErrorBody(
    val code: String?,
    val message: String?
)

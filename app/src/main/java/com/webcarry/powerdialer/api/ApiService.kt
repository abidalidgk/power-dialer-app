package com.webcarry.powerdialer.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Mirrors the WordPress plugin's REST routes registered under
 * wcab-power-dialer/v1 (see includes/power-dialer.php in the plugin).
 */
interface ApiService {

    @POST("pair/claim")
    suspend fun pairClaim(@Body body: PairClaimRequest): Response<PairClaimResponse>

    @POST("heartbeat")
    suspend fun heartbeat(@Body body: HeartbeatRequest): Response<SimpleOkResponse>

    @GET("queue")
    suspend fun getQueue(): Response<QueueResponse>

    @POST("queue/{id}/ack")
    suspend fun ackQueue(@Path("id") id: Long, @Body body: AckRequest): Response<SimpleOkResponse>

    @POST("call-log")
    suspend fun postCallLog(@Body body: CallLogRequest): Response<SimpleOkResponse>

    @POST("sms-log")
    suspend fun postSmsLog(@Body body: SmsLogRequest): Response<SimpleOkResponse>

    @POST("unpair")
    suspend fun unpair(): Response<SimpleOkResponse>
}

package com.webcarry.powerdialer.api

import com.webcarry.powerdialer.prefs.SecurePrefs
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    /**
     * Builds a fresh Retrofit instance pointed at the paired site's
     * wp-json/wcab-power-dialer/v1/ base, with the device token attached
     * as a Bearer header on every request. Every call site should use this
     * (rather than caching one instance) since the paired site can change
     * if the user re-pairs to a different WebCarry installation.
     */
    fun build(prefs: SecurePrefs): ApiService? {
        val site = prefs.siteUrl?.trimEnd('/') ?: return null
        val token = prefs.deviceToken

        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("$site/wp-json/wcab-power-dialer/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }

    /** Used only for the one-time pairing call, before a device token exists. */
    fun buildForPairing(siteUrl: String): ApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("${siteUrl.trimEnd('/')}/wp-json/wcab-power-dialer/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}

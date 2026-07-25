package com.webcarry.powerdialer.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the pairing result (site URL + device token) encrypted on-device.
 * The device token is the ONLY thing that authorizes this phone to talk to
 * the website's Power Dialer REST API — never store the site's own admin
 * credentials here.
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "wcab_power_dialer_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var siteUrl: String?
        get() = prefs.getString(KEY_SITE_URL, null)
        set(value) = prefs.edit().putString(KEY_SITE_URL, value).apply()

    var deviceToken: String?
        get() = prefs.getString(KEY_DEVICE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    var staffName: String?
        get() = prefs.getString(KEY_STAFF_NAME, null)
        set(value) = prefs.edit().putString(KEY_STAFF_NAME, value).apply()

    var siteName: String?
        get() = prefs.getString(KEY_SITE_NAME, null)
        set(value) = prefs.edit().putString(KEY_SITE_NAME, value).apply()

    /** User-entered SIM phone number, sent with heartbeats since Android cannot
     * reliably read this itself on modern OS versions/carriers. */
    var myPhoneNumber: String?
        get() = prefs.getString(KEY_MY_NUMBER, null)
        set(value) = prefs.edit().putString(KEY_MY_NUMBER, value).apply()

    /** The android.provider.CallLog._ID of the last call we already reported
     * to the server, so the sync service never uploads the same call twice. */
    var lastSyncedCallLogId: Long
        get() = prefs.getLong(KEY_LAST_CALL_LOG_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_LAST_CALL_LOG_ID, value).apply()

    val isPaired: Boolean
        get() = !siteUrl.isNullOrBlank() && !deviceToken.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SITE_URL = "site_url"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_STAFF_NAME = "staff_name"
        private const val KEY_SITE_NAME = "site_name"
        private const val KEY_MY_NUMBER = "my_phone_number"
        private const val KEY_LAST_CALL_LOG_ID = "last_synced_call_log_id"
    }
}

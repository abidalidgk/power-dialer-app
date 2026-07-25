package com.webcarry.powerdialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.webcarry.powerdialer.api.ApiClient
import com.webcarry.powerdialer.api.PairClaimRequest
import com.webcarry.powerdialer.databinding.ActivityMainBinding
import com.webcarry.powerdialer.prefs.SecurePrefs
import com.webcarry.powerdialer.sync.CallSmsSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Payload encoded in the QR code shown on the website dashboard. */
private data class PairingPayload(val app: String?, val site: String?, val code: String?)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SecurePrefs

    private val requiredPermissions = buildList {
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { result ->
            refreshUi()
            if (result.values.all { it }) {
                startSyncServiceIfPaired()
            }
        }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScannedQr(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = SecurePrefs(this)

        binding.btnScan.setOnClickListener {
            if (!hasAllPermissions()) {
                permissionLauncher.launch(requiredPermissions)
                return@setOnClickListener
            }
            val options = ScanOptions()
                .setPrompt("Scan the QR code from your WebCarry Power Dialer dashboard")
                .setBeepEnabled(true)
                .setOrientationLocked(true)
            scanLauncher.launch(options)
        }

        binding.btnGrantPermissions.setOnClickListener {
            permissionLauncher.launch(requiredPermissions)
        }

        binding.btnBatteryOptimization.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATIONS_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
            }
        }

        binding.btnSaveNumber.setOnClickListener {
            val number = binding.inputMyNumber.text?.toString()?.trim().orEmpty()
            prefs.myPhoneNumber = number.ifBlank { null }
            startSyncServiceIfPaired()
        }

        binding.btnUnpair.setOnClickListener { unpair() }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun handleScannedQr(raw: String) {
        val payload = try {
            Gson().fromJson(raw, PairingPayload::class.java)
        } catch (e: Exception) {
            null
        }
        if (payload?.site.isNullOrBlank() || payload?.code.isNullOrBlank()) {
            binding.textStatus.text = "That QR code doesn't look like a WebCarry Power Dialer code."
            return
        }

        lifecycleScope.launch {
            binding.textStatus.text = "Pairing…"
            try {
                val api = ApiClient.buildForPairing(payload!!.site!!)
                val response = withContext(Dispatchers.IO) {
                    api.pairClaim(
                        PairClaimRequest(
                            code = payload.code!!,
                            device_model = "${Build.MANUFACTURER} ${Build.MODEL}",
                            device_name = Build.MODEL ?: "Android device",
                            app_version = BuildConfigVersion.NAME,
                            phone_number = prefs.myPhoneNumber
                        )
                    )
                }
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    prefs.siteUrl = payload.site
                    prefs.deviceToken = body.device_token
                    prefs.staffName = body.staff_name
                    prefs.siteName = body.site_name
                    binding.textStatus.text = "Connected to ${body.site_name ?: payload.site} as ${body.staff_name ?: "staff"}."
                    startSyncServiceIfPaired()
                } else {
                    binding.textStatus.text = "Pairing failed (${response.code()}). Generate a new QR code on the dashboard and try again."
                }
            } catch (e: Exception) {
                binding.textStatus.text = "Could not reach the site: ${e.message}"
            }
            refreshUi()
        }
    }

    private fun unpair() {
        lifecycleScope.launch {
            val api = ApiClient.build(prefs)
            try {
                if (api != null) withContext(Dispatchers.IO) { api.unpair() }
            } catch (_: Exception) {
                // Even if this fails (e.g. offline), still clear locally so the
                // user isn't stuck. The device token can be revoked from the
                // dashboard side too.
            }
            stopService(Intent(this@MainActivity, CallSmsSyncService::class.java))
            prefs.clear()
            refreshUi()
        }
    }

    private fun startSyncServiceIfPaired() {
        if (prefs.isPaired && hasAllPermissions()) {
            val intent = Intent(this, CallSmsSyncService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun refreshUi() {
        val paired = prefs.isPaired
        binding.groupPaired.visibility = if (paired) android.view.View.VISIBLE else android.view.View.GONE
        binding.groupUnpaired.visibility = if (paired) android.view.View.GONE else android.view.View.VISIBLE
        binding.groupPermissions.visibility = if (hasAllPermissions()) android.view.View.GONE else android.view.View.VISIBLE

        if (paired) {
            binding.textConnectedTo.text = "Connected to: ${prefs.siteName ?: prefs.siteUrl}"
            binding.textConnectedAs.text = "Signed in as: ${prefs.staffName ?: "—"}"
            binding.inputMyNumber.setText(prefs.myPhoneNumber ?: "")
        }
    }
}

/** Small shim so we don't depend on generated BuildConfig fields directly in this sample. */
private object BuildConfigVersion {
    const val NAME = "1.0.0"
}

package tech.syncvr.mdm_agent.device_management.services

import android.annotation.TargetApi
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.device_management.services.WifiConfigLogic.Companion.addQuotesIfNeeded
import javax.inject.Inject

@AndroidEntryPoint
class WifiConfigService : Service() {

    @Inject
    lateinit var wifiHandler: WifiConfigLogic

    companion object {
        private const val TAG = "WifiConfigService"
        private const val WIFI_SSID_KEY = "ssid"
        private const val WIFI_PASSWORD_KEY = "password"
        private const val WIFI_TYPE_KEY = "type"

        // intended for testing/manual purposes
        private const val WIFI_NO_BASE64 = "nobase64"
        private const val ACTION_ADD_WIFI = "tech.syncvr.intent.ADD_WIFI_NETWORK"
        private const val ACTION_REMOVE_WIFI = "tech.syncvr.intent.REMOVE_WIFI_NETWORK"
        private const val ACTION_START_HOTSPOT = "tech.syncvr.intent.START_HOTSPOT"
        private const val ACTION_STOP_HOTSPOT = "tech.syncvr.intent.STOP_HOTSPOT"
    }

    private var hotspotAutoShutdownJob: Job? = null
    private lateinit var devicePolicyManager: DevicePolicyManager
    private val AUTO_SHUTDOWN_HOTSPOT_DELAY_MINUTES = 60
    var saveReservation: WifiManager.LocalOnlyHotspotReservation? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        devicePolicyManager = getSystemService(DevicePolicyManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        if (!devicePolicyManager.isDeviceOwnerApp(packageName)) {
            Log.w(TAG, "Receiving intent and not device owner yet!")
        }
        when (intent?.action) {
            ACTION_ADD_WIFI -> {
                processActionAddWifi(intent)
            }
            ACTION_REMOVE_WIFI -> {
                processActionRemoveWifi(intent)
            }
            ACTION_START_HOTSPOT -> {
                processActionStartHotspot()
            }
            ACTION_STOP_HOTSPOT -> {
                processActionStopHotspot()
            }
            else -> {
                Log.d(TAG, "Received unknown action ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun processActionStopHotspot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(
                TAG,
                "Hotspot can't be used on this device. Needs api lvl 30+ (Q). Current is ${Build.VERSION.SDK_INT}"
            )
            return
        }
        stopHotspot()
    }

    private fun processActionStartHotspot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(
                TAG,
                "Hotspot can't be used on this device. Needs api lvl 30+ (Q). Current is ${Build.VERSION.SDK_INT}"
            )
            return
        }
        startHotspot()
    }

    @TargetApi(30)
    private fun startHotspot() {
        MDMAgentApplication.coroutineScope.launch {
            wifiHandler.startHotspotFlow().collect { evt ->
                when (evt.type) {
                    EvtType.FAILED -> {
                        Log.d(TAG, "Hotspot failed with reason ${evt.failed}")
                    }
                    EvtType.STOPPED -> {
                        Log.d(TAG, "Hotspot stopped")
                    }
                    EvtType.STARTED -> {
                        saveReservation = evt.reservation
                        Log.d(
                            TAG,
                            "Hotspot started ${evt.reservation?.softApConfiguration?.ssid} \"" +
                                    "-- ${evt.reservation?.softApConfiguration?.passphrase}"
                        )
                    }
                }
            }
        }
        hotspotAutoShutdownJob = MDMAgentApplication.coroutineScope.launch {
            delay(1000 * 60 * AUTO_SHUTDOWN_HOTSPOT_DELAY_MINUTES.toLong())
            stopHotspot()
        }
    }

    @Suppress("DEPRECATION")
    private fun processActionRemoveWifi(intent: Intent): Boolean {
        var ssid = intent.extras?.getString(WifiConfigService.WIFI_SSID_KEY)
        val nobase64 = intent.extras?.getBoolean(WifiConfigService.WIFI_NO_BASE64, false)
        if (ssid.isNullOrEmpty()) {
            // Actually ... wifi without SSID are a thing. We'll see when we get there
            Log.w(TAG, "Unable to remove Wifi without ssid")
            return false
        }
        ssid = when (nobase64) {
            true -> addQuotesIfNeeded(ssid)
            else -> addQuotesIfNeeded(decodeB64(ssid))
        }
        Log.d(TAG, "About to remove network $ssid")
        return wifiHandler.removeWifi(ssid)
    }

    private fun decodeB64(input: String?): String {
        if (input.isNullOrBlank()) {
            return ""
        }
        return String(Base64.decode(input, Base64.NO_PADDING or Base64.URL_SAFE))
    }


    private fun processActionAddWifi(intent: Intent): Boolean {
        val ssid = intent.extras?.getString(WIFI_SSID_KEY)
        val pwd = intent.extras?.getString(WIFI_PASSWORD_KEY)
        // type unused for now but will come back into play for 802.11x
        val type = intent.extras?.getString(WIFI_TYPE_KEY)
        val nobase64 = intent.extras?.getBoolean(WIFI_NO_BASE64, false)
        if (ssid.isNullOrEmpty()) {
            Log.w(TAG, "Unable to add Wifi invalid config")
            return false
        }
        return when (nobase64) {
            true -> wifiHandler.setupWifiCredentials(ssid, pwd)
            else -> wifiHandler.setupWifiCredentials(decodeB64(ssid), decodeB64(pwd))
        }
    }


    data class HotspotEvent(
        val type: EvtType,
        val reservation: WifiManager.LocalOnlyHotspotReservation? = null,
        val failed: Int = 0
    )

    enum class EvtType {
        STARTED,
        FAILED,
        STOPPED
    }


    @TargetApi(30)
    private fun stopHotspot() {
        Log.d(TAG, "Time to Shutdown hotspot")
        saveReservation?.let { res ->
            Log.d(TAG, "Shutting down hotspot")
            res.close()
        }
        hotspotAutoShutdownJob?.let {
            it.cancel()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
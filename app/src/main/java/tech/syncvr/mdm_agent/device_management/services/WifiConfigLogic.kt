package tech.syncvr.mdm_agent.device_management.services

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import tech.syncvr.mdm_agent.device_management.configuration.models.WifiPoint
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiConfigLogic @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analyticsLogger: AnalyticsLogger
) {
    val wifiManager = context.getSystemService(WifiManager::class.java)

    val TAG = this.javaClass.simpleName

    companion object {
        fun addQuotesIfNeeded(ssid: String): String {
            if (ssid.isNullOrEmpty())
                return ssid
            var hasDoubleQuotes = Regex("^\".+\"$").containsMatchIn(ssid)
            return when (hasDoubleQuotes) {
                true -> ssid
                else -> "\"$ssid\""
            }
        }
    }


    @Suppress("DEPRECATION")
    fun setupWifiCredentials(ssid: String, pwd: String?): Boolean {
        analyticsLogger.log(
            AnalyticsLogger.Companion.LogEventType.MDM_EVENT, hashMapOf(
                "action" to "set_wifi_network",
                "ssid" to ssid
            )
        )
        val wifiConfiguration = WifiConfiguration().also { wifiConfiguration ->
            wifiConfiguration.SSID = addQuotesIfNeeded(ssid)
            if (pwd?.isNotBlank() == true) {
                wifiConfiguration.preSharedKey = addQuotesIfNeeded(pwd)
            } else {
                wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
        }

        wifiManager.isWifiEnabled = true
        val networkId = wifiManager.addNetwork(wifiConfiguration)
        return wifiManager.enableNetwork(networkId, true)
    }

    fun removeWifi(ssid: String): Boolean {
        val ssid = addQuotesIfNeeded(ssid)
        return wifiManager.configuredNetworks.find { it.SSID.equals(ssid) }?.let { toRemove ->
            Log.d(TAG, "Removing network $ssid")
            wifiManager.removeNetwork(toRemove.networkId)
        } ?: false
    }

    @SuppressLint("MissingPermission")
    fun startHotspotFlow() = callbackFlow<WifiConfigService.HotspotEvent> {
        wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
            @SuppressLint("MissingPermission")
            override fun onFailed(reason: Int) {
                super.onFailed(reason)
                trySendBlocking(
                    WifiConfigService.HotspotEvent(
                        WifiConfigService.EvtType.FAILED,
                        failed = reason
                    )
                )
                close()
            }

            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                super.onStarted(reservation)
                trySendBlocking(
                    WifiConfigService.HotspotEvent(
                        WifiConfigService.EvtType.STARTED,
                        reservation
                    )
                )
            }

            override fun onStopped() {
                super.onStopped()
                trySendBlocking(WifiConfigService.HotspotEvent(WifiConfigService.EvtType.STOPPED))
                close()
            }
        }, Handler(Looper.getMainLooper()))
        awaitClose { }
    }

    @SuppressLint("MissingPermission")
    fun addDefaultWifiIfNotPresent() {
        val configuredNetworks: List<WifiConfiguration> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                wifiManager.callerConfiguredNetworks // for the future, so we no longer have to filter on creatorName
            } else {
                wifiManager.configuredNetworks // this is what we currently get from our devices
            }
        val defaultSSID = "\"SyncVRDefault\""
        val defaultPassword = "\"1234567890\""
        if (configuredNetworks.none {
                it.SSID == defaultSSID
            }) {
            setupWifiCredentials(ssid = defaultSSID, pwd = defaultPassword)
        }
    }

    @SuppressLint("MissingPermission")
    fun updateWifiCredentials(oldWifis: List<WifiPoint>, newWifis: List<WifiPoint>): Boolean {
        val toBeRemoved = oldWifis.filterNot { it in newWifis }
        val toBeAdded = newWifis.filterNot { it in oldWifis }
        val removedWifisSuccesfully =
            toBeRemoved.fold(true) { sum: Boolean, element: WifiPoint ->
                sum && removeWifi(element.ssid)
            }
        val addedWifisSuccessfully = addWifis(toBeAdded)
        return (toBeAdded.isNotEmpty() || toBeRemoved.isNotEmpty()) && addedWifisSuccessfully && removedWifisSuccesfully
    }

    fun addWifis(toBeAdded: List<WifiPoint>): Boolean {
        val addedWifisSuccessfully = toBeAdded.fold(true) { sum: Boolean, element: WifiPoint ->
            sum && setupWifiCredentials(element.ssid, pwd = element.password)
        }
        return addedWifisSuccessfully
    }

}
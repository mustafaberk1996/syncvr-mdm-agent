package tech.syncvr.mdm_agent.provisioning

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.device_management.configuration.IConfigurationRemoteSource
import tech.syncvr.mdm_agent.device_management.device_status.DeviceStatusFactory
import tech.syncvr.mdm_agent.device_management.device_status.IDeviceStatusRemoteSource
import tech.syncvr.mdm_agent.device_management.services.WifiConfigLogic
import tech.syncvr.mdm_agent.firebase.IAuthenticationService
import tech.syncvr.mdm_agent.firebase.IDeviceApiService
import tech.syncvr.mdm_agent.repositories.AppPackageRepository
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

@Singleton
class ProvisioningServerLogic @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val deviceStatusRemoteSource: IDeviceStatusRemoteSource,
    private val appPackageRepository: AppPackageRepository,
    private val deviceStatusFactory: DeviceStatusFactory,
    private val authenticationService: IAuthenticationService,
    private val wifiConfigLogic: WifiConfigLogic,
    private val remoteSource: IConfigurationRemoteSource,
    private val deviceApiService: IDeviceApiService,
) {
    companion object {
        const val TAG = "ProvisioningServerLogic"
        const val JSON_MIMETYPE = "application/json"
    }

    private val devicePolicyManager: DevicePolicyManager =
        appContext.getSystemService(DevicePolicyManager::class.java)
    private val application = appContext as MDMAgentApplication
    private val connectivityManager: ConnectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)

    private val provisioningModeSwitchedTrigger = MutableStateFlow(false)
    val provisioningModeSwitched = provisioningModeSwitchedTrigger.asStateFlow()

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    @Serializable
    data class Pong(val ping: String = "pong")

    @Serializable
    data class PostDeviceStatus(val postDeviceStatusResult: Boolean)

    @Serializable
    data class MDMState(
        val wifiConnected: Boolean,
        val connectivity: Boolean,
        val firebaseSignedIn: Boolean,
        val isDeviceOwner: Boolean
    )

    @Serializable
    data class DownloadableApk(val packageName: String, val url: String)

    @Serializable
    data class DeviceConfiguration(
        val apks: List<DownloadableApk> = emptyList(),
        val autoStartPackageName: String? = null
    )

    fun pongResponse(): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            JSON_MIMETYPE,
            json.encodeToString(Pong())
        )
    }

    fun postDeviceStatusResponse(): NanoHTTPD.Response {
        val statusPostResult = postDeviceStatus()
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, JSON_MIMETYPE,
            json.encodeToString(PostDeviceStatus(statusPostResult))
        )
    }

    fun mdmStateResponse(): NanoHTTPD.Response {
        val connected = isWifiConnected()
        val hasConnectivity = testConnectivity()
        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(application.packageName)
        val signedIn = authenticationService.isSignedIn()
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, JSON_MIMETYPE,
            json.encodeToString(
                MDMState(
                    wifiConnected = connected,
                    connectivity = hasConnectivity,
                    firebaseSignedIn = signedIn,
                    isDeviceOwner = isDeviceOwner
                )
            )
        )
    }

    fun deviceConfigurationReponse(): NanoHTTPD.Response {
        if (!authenticationService.isSignedIn()) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                JSON_MIMETYPE,
                json.encodeToString(DeviceConfiguration())
            )
        }
        val (configuration, defaultApps) = runBlocking {
            Pair(
                remoteSource.getConfiguration(),
                deviceApiService.getPlatformApps())
        }
        val configuredManagedApps =
            configuration?.managed ?: emptyList()
        val configuredPlatformApps =
            defaultApps ?: emptyList()
        val configAndPlatformApps =
            configuredManagedApps + configuredPlatformApps
        val apkList =
            configAndPlatformApps.map {
                DownloadableApk(
                    packageName = it.appPackageName,
                    url = it.releaseDownloadURL
                )

            }

        Log.d(TAG, "apkList $apkList")
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            JSON_MIMETYPE,
            json.encodeToString(
                DeviceConfiguration(
                    apkList,
                    configuration?.autoStart?.ifBlank { null }
                )
            )
        )
    }

    private fun postDeviceStatus(): Boolean {
        return authenticationService.isSignedIn() &&
                runBlocking {
                    // trigger configuration fetch
                    val configuration = remoteSource.getConfiguration()
                    val platformApps =
                        deviceApiService.getPlatformApps()
                            ?: emptyList()
                    val configuredManagedApps =
                        configuration?.managed ?: emptyList()
                    // compute status -> apps from conf against installed apps
                    val managedApps =
                        appPackageRepository.getConfigurationStatus(
                            configuredManagedApps
                        )
                    val status = deviceStatusFactory.currentDeviceStatus(
                        managedApps,
                        platformApps
                    )
                    Log.d(TAG, "device status $status")
                    val resultPostDeviceStatus =
                        deviceStatusRemoteSource.postDeviceStatus(status)
                    Log.d(TAG, "Device status res $resultPostDeviceStatus")
                    resultPostDeviceStatus
                }
    }

    private fun testConnectivity(): Boolean {
        return try {
            val connection: HttpsURLConnection =
                URL("https://www.google.com/generate_204").openConnection() as HttpsURLConnection
            connection.connectTimeout = 3000
            connection.connect()
            connection.inputStream.close()
            connection.responseCode == 204
        } catch (e: IOException) {
            return false
        }
    }

    private fun isWifiConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        return caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun setProvisioning(value: Boolean) {
        provisioningModeSwitchedTrigger.value = value
        appPackageRepository.cancelAllDownloads() // at beginning and end of provisioning, so we're clean
    }

    @Serializable
    data class SetConfigWifisResult(val success: Boolean, val wifis: List<String>)

    fun setWifis(): NanoHTTPD.Response {
        val wifis = runBlocking {
            remoteSource.getConfiguration()?.wifis
        } ?: emptyList()
        val succes = wifiConfigLogic.addWifis(wifis)
        val setConfigWifisResult = SetConfigWifisResult(succes, wifis.map { it.ssid })
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            JSON_MIMETYPE,
            json.encodeToString(
                setConfigWifisResult
            )
        )
    }
}

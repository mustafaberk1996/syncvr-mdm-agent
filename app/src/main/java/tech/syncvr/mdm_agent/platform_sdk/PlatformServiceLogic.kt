package tech.syncvr.mdm_agent.platform_sdk

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.RemoteCallbackList
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tech.syncvr.mdm_agent.device_identity.DeviceIdentityRepository
import tech.syncvr.mdm_agent.device_management.configuration.ConfigurationRepository
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.device_management.configuration.models.WifiType
import tech.syncvr.mdm_agent.device_management.services.WifiConfigLogic
import tech.syncvr.mdm_agent.firebase.IAuthenticationService
import tech.syncvr.mdm_agent.repositories.AppPackageRepository
import tech.syncvr.mdm_agent.repositories.DeviceInfoRepository
import tech.syncvr.platform_sdk.ISyncVRPlatformCallback
import tech.syncvr.platform_sdk.parcelables.App
import tech.syncvr.platform_sdk.parcelables.AppState
import tech.syncvr.platform_sdk.parcelables.Configuration
import tech.syncvr.platform_sdk.parcelables.WifiPoint
import javax.inject.Inject
import javax.inject.Singleton
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration as PlatformConfiguration
import tech.syncvr.mdm_agent.device_management.configuration.models.WifiPoint as PlatformWifiPoint

@Singleton
class PlatformServiceLogic @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val firebaseAuthHandler: IAuthenticationService,
    private val platformServiceAccessControl: PlatformServiceAccessControl,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val wifiConfigLogic: WifiConfigLogic,
    private val configurationRepository: ConfigurationRepository,
    private val appStatusRepository: AppPackageRepository
) {
    companion object {
        val TAG: String = this::class.java.declaringClass.simpleName
        const val DELAY_CONFIG_STATUS_UPDATE = 3000L
    }

    private val exHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "An error occurred", throwable)
    }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exHandler)

    private val devicePolicyManager: DevicePolicyManager =
        appContext.getSystemService(DevicePolicyManager::class.java)

    private var remoteCallbackList: RemoteCallbackList<ISyncVRPlatformCallback>? = null
    private var configurationJob: Job? = null
    fun getSerialNo(): String? {
        platformServiceAccessControl.checkCallingPackagePermission(appContext)
        if (!devicePolicyManager.isDeviceOwnerApp(appContext.packageName)) {
            Log.w(TAG, "Can't get serialNo, not device owner yet")
            return null
        }
        return deviceIdentityRepository.getDeviceId()
    }

    fun getCustomer(): String? {
        platformServiceAccessControl.checkCallingPackagePermission(appContext)
        return deviceInfoRepository.deviceInfoStateFlow.value.customerName
    }

    fun getDepartment(): String? {
        platformServiceAccessControl.checkCallingPackagePermission(appContext)
        return deviceInfoRepository.deviceInfoStateFlow.value.departmentName
    }

    fun getSpectatingToken(): String? {
        platformServiceAccessControl.checkCallingPackagePermission(appContext)
        platformServiceAccessControl.checkCallingPackageSigningCert(appContext)
        return runBlocking(exHandler + Dispatchers.IO) {
            firebaseAuthHandler.getIdToken(false)
        }
    }

    fun addOpenWifiConfiguration(wifiSsid: String): Boolean {
        platformServiceAccessControl.checkCallingPackagePermission(appContext)
        return wifiConfigLogic.addWifis(listOf(PlatformWifiPoint(wifiSsid, null, WifiType.OPEN)))
    }

    fun addWifiConfiguration(wifiSsid: String, wifiPassword: String): Boolean {
        platformServiceAccessControl.checkCallingPackagePermission(appContext)
        return wifiConfigLogic.addWifis(
            listOf(
                PlatformWifiPoint(
                    wifiSsid,
                    wifiPassword,
                    WifiType.WPA2
                )
            )
        )
    }

    fun getConfiguration(): Configuration? {
        platformServiceAccessControl.checkCallingPackagePermission(appContext)
        val configuration = configurationRepository.configuration.value
        val appListWithStatus = configuration?.managed?.let {
            appStatusRepository.getConfigurationStatus(
                it
            )
        }
        return appListWithStatus?.let { appListWithStatusSafe ->
            Configuration(
                configuration.autoStart,
                appListWithStatusSafe.mapToSdkApp(),
                configuration.wifis.mapToSdkWifiPoint()
            )
        }
    }

    private fun convertAppStatusToAppState(
        status: ManagedAppPackage.Status,
        progress: Long
    ): AppState {
        return when (status) {
            ManagedAppPackage.Status.NOT_INSTALLED -> AppState.NotInstalled
            ManagedAppPackage.Status.DOWNLOADING -> AppState.Downloading(progress)
            ManagedAppPackage.Status.DOWNLOADED -> AppState.Installing
            ManagedAppPackage.Status.INSTALLING -> AppState.Installing
            ManagedAppPackage.Status.NEED_PERMISSIONS -> AppState.Installing
            ManagedAppPackage.Status.INSTALLED -> AppState.Installed
        }
    }

    private fun List<ManagedAppPackage>.mapToSdkApp(): List<App> {
        return map {
            App(
                it.appPackageName,
                it.appVersionCode,
                convertAppStatusToAppState(it.status, it.progress),
                it.installedAppVersionCode
            )
        }
    }

    private fun List<PlatformWifiPoint>.mapToSdkWifiPoint(): List<WifiPoint> {
        return map {
            WifiPoint(it.ssid, it.wifiType.name)
        }
    }

    private fun setupConfigurationFlowForCallback() {
        if (configurationJob == null) {
            configurationJob = coroutineScope.launch {
                // when the config repo receives a config, fetch the app status (installing/downloading/...)
                configurationRepository.configuration.mapNotNull {
                    if (it?.managed == null) null else PlatformConfiguration(
                        appStatusRepository.getConfigurationStatus(it.managed),
                        emptyList(),
                        it.autoStart,
                        it.wifis
                    )
                }.flatMapLatest { platformConfiguration ->
                    // flatMapLatest allows to inject additional Configuration for each configuration received
                    // it wraps the PlatformConfiguration (platform agent business logic) into an "sdk configuration"
                    // additional Configuration injection happens when not all managed apps are installed
                    // ie. we're in the process of downloading/installing apps from configuration
                    // When that happens, the flow waits a little while, fetch the new app state and re-emits the configuration
                    // That provides sdk users with an "almost realtime" state of the Configuration processing by the platform agent
                    // We need this since the configuration repository publishes configuration with app state that are not
                    // reflecting the actual appstates.
                    // The "latest" in flatMapLatest means: if the upstream sends a new configuration, stop the inner flow
                    // which is still working on the previous config so that it's immediately restarted with the new config
                    flow {
                        var updatedAppList = platformConfiguration.managed
                        do {
                            emit(
                                Configuration(
                                    platformConfiguration.autoStart,
                                    updatedAppList.mapToSdkApp(),
                                    platformConfiguration.wifis.mapToSdkWifiPoint()
                                )
                            )
                            delay(DELAY_CONFIG_STATUS_UPDATE)
                            updatedAppList =
                                appStatusRepository.getConfigurationStatus(platformConfiguration.managed)
                        }
                        while (updatedAppList.any { it.status != ManagedAppPackage.Status.INSTALLED })
                        // Don't forget to emit the state when the condition to stop checking for update is reached
                        emit(
                            Configuration(
                                platformConfiguration.autoStart,
                                updatedAppList.mapToSdkApp(),
                                platformConfiguration.wifis.mapToSdkWifiPoint()
                            )
                        )
                    }
                }.collect { sdkConfiguration ->
                    for (i in 0..(remoteCallbackList?.beginBroadcast()?.minus(1) ?: 0)) {
                        remoteCallbackList?.getBroadcastItem(i)
                            ?.onConfigurationChanged(sdkConfiguration)
                    }
                    remoteCallbackList?.finishBroadcast()
                }
            }
        }
    }

    fun registerCallback(callback: ISyncVRPlatformCallback?) {
        remoteCallbackList?.register(callback)
        setupConfigurationFlowForCallback()
    }

    fun removeCallback(callback: ISyncVRPlatformCallback) {
        remoteCallbackList?.unregister(callback)
        if (remoteCallbackList?.registeredCallbackCount == 0) {
            configurationJob?.cancel()
            configurationJob = null
        }
    }

    fun onDestroy() {
        configurationJob?.cancel()
        configurationJob = null
        remoteCallbackList?.kill()
    }

    fun onCreate() {
        remoteCallbackList = RemoteCallbackList()
    }
}

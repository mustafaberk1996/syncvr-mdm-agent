package tech.syncvr.mdm_agent.device_management


import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import android.os.HandlerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.device_management.configuration.ConfigurationRepository
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.device_management.default_apps.PlatformAppsRepository
import tech.syncvr.mdm_agent.device_management.device_status.DeviceStatusRepository
import tech.syncvr.mdm_agent.device_management.services.AppInstallService
import tech.syncvr.mdm_agent.device_management.services.LaunchSyncVRAppService
import tech.syncvr.mdm_agent.device_management.services.RebootService
import tech.syncvr.mdm_agent.device_management.services.WifiConfigLogic
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.provisioning.ProvisioningServerLogic
import tech.syncvr.mdm_agent.repositories.AppPackageRepository
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager
import tech.syncvr.mdm_agent.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MDMWorkManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val configurationRepository: ConfigurationRepository,
    private val platformAppsRepository: PlatformAppsRepository,
    private val wifiConfigLogic: WifiConfigLogic,
    private val appInstallService: AppInstallService,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val appPackageRepository: AppPackageRepository,
    private val launchSyncVRAppService: LaunchSyncVRAppService,
    private val autoStartManager: AutoStartManager,
    private val rebootService: RebootService,
    private val logger: Logger,
    private val provisioningServerLogic: ProvisioningServerLogic,
    private val analyticsLogger: AnalyticsLogger
) {


    companion object {
        private const val TAG = "MDMWorkManager"
    }

    private val handlerThread = HandlerThread("MDMWorker")

    init {
        handlerThread.start()
        MDMAgentApplication.coroutineScope.launch {
            var previousValue: Configuration? = null
            combine(
                configurationRepository.configuration,
                provisioningServerLogic.provisioningModeSwitched
            ) { newConfiguration, isProvisioning -> if (!isProvisioning) newConfiguration else null }.filterNotNull()
                .collect { newConfiguration ->
                    previousValue?.let { prevValue ->
                        if (prevValue != newConfiguration) {
                            appsCleanup(prevValue.managed, newConfiguration.managed)
                            // TODO: convert this wifi-stuff into checking if the wifi-configs exist and not rely on a diff between current and previous config (less reliable)
                            if (wifiConfigLogic.updateWifiCredentials(
                                    prevValue.wifis,
                                    newConfiguration.wifis
                                )
                            ) {
                                scheduleStatusUpdate()
                            }
                        }
                    }
                    logger.d(
                        TAG,
                        "new config received from configurationRepository.configuration"
                    )
                    scheduleWorkConfigurationChanged(newConfiguration)
                    previousValue = newConfiguration
                }
        }
        MDMAgentApplication.coroutineScope.launch {
            var previousValue: List<ManagedAppPackage>? = null
            combine(
                platformAppsRepository.platformApps,
                provisioningServerLogic.provisioningModeSwitched
            ) { defaultApps, isProvisioning -> if (!isProvisioning) defaultApps else null }.filterNotNull()
                .collect { newDefaultApps ->
                    previousValue?.let { prevValue ->
                        if (prevValue != newDefaultApps) {
                            appsCleanup(prevValue, newDefaultApps)
                        }
                        scheduleWorkDefaultAppsAvailable(newDefaultApps)
                    }
                    previousValue = newDefaultApps
                }
        }
    }

    private fun appsCleanup(
        oldConfiguration: List<ManagedAppPackage>,
        newConfiguration: List<ManagedAppPackage>
    ) {
        val appsToUninstall = Configuration.appsToUninstall(oldConfiguration, newConfiguration)
        when {
            appsToUninstall.isNotEmpty() ->
                scheduleAppCleanup(
                    appsToUninstall
                )
        }
    }

    private val mdmWorker by lazy {
        MDMWorkHandler(
            handlerThread.looper,
            context,
            autoStartManager,
            launchSyncVRAppService,
            appPackageRepository,
            appInstallService,
            deviceStatusRepository,
            rebootService,
            logger,
            configurationRepository,
            analyticsLogger
        )
    }

    fun scheduleWorkConfigurationChanged(configuration: Configuration) {
        val message =
            mdmWorker.obtainMessage(MDMWorkHandler.WORK_CONFIGURATION_RECEIVED, configuration)
        mdmWorker.sendMessage(message)
    }

    fun scheduleAppCleanup(pkgNamesToClean: List<String>) {
        val message = mdmWorker.obtainMessage(MDMWorkHandler.APPS_CLEANUP, pkgNamesToClean)
        mdmWorker.sendMessage(message)
    }

    fun scheduleWorkDefaultAppsAvailable(platformApps: List<ManagedAppPackage>) {
        val message =
            mdmWorker.obtainMessage(MDMWorkHandler.DEFAULT_APPS_AVAILABLE, platformApps)
        mdmWorker.sendMessage(message)
    }

    fun scheduleWorkDownloadCompleted(downloadId: Long) {
        val message = mdmWorker.obtainMessage(MDMWorkHandler.WORK_DOWNLOAD_COMPLETE)
        message.data = Bundle().also {
            it.putLong(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
        }
        mdmWorker.sendMessage(message)
    }

    fun scheduleWorkAppInstalled(downloadId: Long) {
        val message = mdmWorker.obtainMessage(MDMWorkHandler.WORK_APP_INSTALLED)
        message.data = Bundle().also {
            it.putLong(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
        }
        mdmWorker.sendMessage(message)
    }

    fun scheduleStatusUpdate() {
        val message = mdmWorker.obtainMessage(MDMWorkHandler.SYNC_STATUS)
        mdmWorker.sendMessage(message)
    }

    fun scheduleReboot() {
        val message = mdmWorker.obtainMessage(MDMWorkHandler.SCHEDULE_REBOOT)
        mdmWorker.sendMessage(message)
    }
}
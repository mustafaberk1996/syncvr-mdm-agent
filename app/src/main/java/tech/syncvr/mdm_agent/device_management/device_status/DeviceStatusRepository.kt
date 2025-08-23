package tech.syncvr.mdm_agent.device_management.device_status

import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication.Companion.coroutineScope
import tech.syncvr.mdm_agent.device_management.configuration.ConfigurationRepository
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.device_management.default_apps.PlatformAppsRepository
import tech.syncvr.mdm_agent.repositories.AppPackageRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStatusRepository @Inject constructor(
    private val deviceStatusRemoteSource: IDeviceStatusRemoteSource,
    private val deviceStatusFactory: DeviceStatusFactory,
    private val configurationRepository: ConfigurationRepository,
    private val platformAppsRepository: PlatformAppsRepository,
    private val appPackageRepository: AppPackageRepository
) {

    companion object {
        private const val TAG = "DeviceStatusRepository"
    }

    fun syncStatus(
        currentConfiguration: Configuration?,
        defaultAppPackages: List<ManagedAppPackage>?
    ) {
        val managedAppStatus =
            currentConfiguration?.let {
                appPackageRepository.getConfigurationStatus(currentConfiguration.managed)
            } ?: emptyList()

        val defaultAppStatus =
            defaultAppPackages?.let {
                appPackageRepository.getConfigurationStatus(it)
            } ?: emptyList()

        syncDeviceStatus(
            deviceStatusFactory.currentDeviceStatus(
                managedAppStatus,
                defaultAppStatus,
            )
        )
    }

    private fun syncDeviceStatus(deviceStatus: DeviceStatus) {
        coroutineScope.launch {
            deviceStatusRemoteSource.postDeviceStatus(deviceStatus)
        }
    }
}

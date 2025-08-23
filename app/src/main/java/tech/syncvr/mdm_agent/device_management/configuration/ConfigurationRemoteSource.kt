package tech.syncvr.mdm_agent.device_management.configuration

import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration
import tech.syncvr.mdm_agent.firebase.IDeviceApiService
import javax.inject.Inject

class ConfigurationRemoteSource @Inject constructor(
    private val deviceApiService: IDeviceApiService
) : IConfigurationRemoteSource {

    companion object {
        private const val TAG = "ConfigurationRemoteSource"
    }

    override suspend fun getConfiguration(): Configuration? {
        return deviceApiService.getConfiguration()
    }
}
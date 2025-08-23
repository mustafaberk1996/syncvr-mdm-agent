package tech.syncvr.mdm_agent.device_management.device_status

import tech.syncvr.mdm_agent.firebase.IDeviceApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStatusRemoteSource @Inject constructor(private val deviceApiService: IDeviceApiService) :
    IDeviceStatusRemoteSource {

    override suspend fun postDeviceStatus(deviceStatus: DeviceStatus): Boolean {
        return deviceApiService.postDeviceStatus(deviceStatus)
    }

}
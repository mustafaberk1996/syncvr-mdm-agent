package tech.syncvr.mdm_agent.device_management.device_status

interface IDeviceStatusRemoteSource {

    suspend fun postDeviceStatus(deviceStatus: DeviceStatus): Boolean
}
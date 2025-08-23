package tech.syncvr.mdm_agent.firebase

import okhttp3.RequestBody
import tech.syncvr.mdm_agent.app_usage.AppUsageStats
import tech.syncvr.mdm_agent.device_management.bluetooth_name.DeviceInfo
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.device_management.device_status.DeviceStatus
import tech.syncvr.mdm_agent.device_management.firmware_upgrade.FirmwareInfo
import tech.syncvr.mdm_agent.logging.AnalyticsEntriesDto
import tech.syncvr.mdm_agent.logging.AnalyticsEntryDto

interface IDeviceApiService {

    suspend fun getConfiguration(): Configuration?
    suspend fun getPlatformApps(): List<ManagedAppPackage>?
    suspend fun postDeviceStatus(deviceStatus: DeviceStatus): Boolean
    suspend fun getFirmwareInfo(): FirmwareInfo?
    suspend fun getDeviceInfo(): DeviceInfo?
    suspend fun postAppUsage(appUsage: Map<String, List<AppUsageStats>>): Boolean
    suspend fun postLogs(requestBody: RequestBody): Boolean
}
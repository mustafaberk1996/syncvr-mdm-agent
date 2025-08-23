package tech.syncvr.mdm_agent.localcache

import tech.syncvr.mdm_agent.app_usage.app_sessions.AppUsageSessionCalculator
import tech.syncvr.mdm_agent.device_management.bluetooth_name.DeviceInfo
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage

interface ILocalCacheSource {

    fun storePlatformApps(defaultApps: List<ManagedAppPackage>)
    fun storeConfiguration(configuration: Configuration)
    fun getPlatformApps(): List<ManagedAppPackage>?
    fun getConfiguration(): Configuration?
    fun getPlayAreaConfig(): String?
    fun setPlayAreaConfig(mode: String)
    fun setDeviceInfo(deviceInfo: DeviceInfo)
    fun getDeviceInfo(): DeviceInfo
    fun getActiveAppSession(): AppUsageSessionCalculator.ActiveAppUsageSession?
    fun setActiveAppSession(session: AppUsageSessionCalculator.ActiveAppUsageSession)
    fun clearActiveAppSession()
    fun getLastSystemEventQueryTime(): Long
    fun setLastSystemEventQueryTime(lastQueryTime: Long)
    fun getVisibleApps(): List<AppUsageSessionCalculator.VisibleApp>
    fun setVisibleApps(visibleApps: List<AppUsageSessionCalculator.VisibleApp>)
    fun getLastUsageStatsQueryTime(): Long
    fun setLastUsageStatsQueryTime(lastQueryTime: Long)
}
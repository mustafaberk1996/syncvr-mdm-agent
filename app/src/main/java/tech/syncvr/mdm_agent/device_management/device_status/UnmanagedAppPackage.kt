package tech.syncvr.mdm_agent.device_management.device_status

import kotlinx.serialization.Serializable

@Serializable
data class UnmanagedAppPackage(
    val appPackageName: String = "",
    var installedAppVersionCode: Long = 0
)

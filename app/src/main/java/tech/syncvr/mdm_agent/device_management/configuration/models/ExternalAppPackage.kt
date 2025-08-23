package tech.syncvr.mdm_agent.device_management.configuration.models

import kotlinx.serialization.Serializable

@Serializable
data class ExternalAppPackage(
    val appPackageName: String = "",
)

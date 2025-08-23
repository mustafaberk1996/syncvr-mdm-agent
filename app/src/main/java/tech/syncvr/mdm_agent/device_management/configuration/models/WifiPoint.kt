package tech.syncvr.mdm_agent.device_management.configuration.models

import kotlinx.serialization.Serializable

@Serializable
data class WifiPoint(
    val ssid: String,
    val password: String? = null,
    val wifiType: WifiType
)

enum class WifiType {
    WPA2,
    OPEN
}
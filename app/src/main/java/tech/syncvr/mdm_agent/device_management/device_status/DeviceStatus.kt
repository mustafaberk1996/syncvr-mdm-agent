package tech.syncvr.mdm_agent.device_management.device_status

import kotlinx.serialization.Serializable
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.device_management.configuration.models.WifiPoint
import tech.syncvr.mdm_agent.utils.DateSerializer
import java.util.*

@Serializable
data class DeviceStatus(
    val managedApps: List<ManagedAppPackage>,
    val platformApps: List<ManagedAppPackage>,
    val unmanagedApps: List<UnmanagedAppPackage>,
    val currentStatus: String,
    val osVersion: String,
    val firmwareVersion: String,
    val battery: Int,
    val chargingState: Boolean,
    val wifiSSID: String,
    val wifiCapabilities: Map<String, Boolean>,
    val autoStart: String,
)
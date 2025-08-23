package tech.syncvr.mdm_agent.device_management.bluetooth_name

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    var humanReadableName: String = "",
    var customerName: String = "",
    var departmentName: String = ""
)

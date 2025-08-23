package tech.syncvr.mdm_agent.device_management.firmware_upgrade

import kotlinx.serialization.Serializable

@Serializable
data class FirmwareInfo(val firmwareVersion: String?)

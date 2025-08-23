package tech.syncvr.mdm_agent.device_management.system_settings

interface ISystemSettingsService {
    fun setDefaultSystemSettings()

    fun setAcceptFirmwareUpdates()
    fun setDisableFirmwareUpdates()
}
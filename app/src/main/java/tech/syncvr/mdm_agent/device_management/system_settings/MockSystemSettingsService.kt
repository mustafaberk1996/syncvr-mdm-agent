package tech.syncvr.mdm_agent.device_management.system_settings

import android.util.Log

class MockSystemSettingsService: ISystemSettingsService {

    companion object {
        private const val TAG = "MockSystemSettingsService"
    }

    override fun setDefaultSystemSettings() {
        Log.d(TAG, "Setting Default System Settings!")
    }

    override fun setAcceptFirmwareUpdates() {
        Log.d(TAG, "setAcceptFirmwareUpdates")
    }

    override fun setDisableFirmwareUpdates() {
        Log.d(TAG, "setDisableFirmwareUpdates")
    }
}
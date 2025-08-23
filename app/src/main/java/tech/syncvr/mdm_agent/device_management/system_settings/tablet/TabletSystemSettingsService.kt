package tech.syncvr.mdm_agent.device_management.system_settings.tablet

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import tech.syncvr.mdm_agent.device_management.system_settings.ISystemSettingsService
import tech.syncvr.mdm_agent.receivers.DeviceOwnerReceiver

class TabletSystemSettingsService(private val context: Context) : ISystemSettingsService {

    companion object {
        private const val TAG = "TabletSystemSettingsService"
        private const val SCREEN_TIMEOUT_MILLIS = 300 * 1000L
    }

    override fun setDefaultSystemSettings() {
        Log.d(TAG, "Setting Default System Settings")
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val deviceOwnerComponent = DeviceOwnerReceiver().getWho(context)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // api 28 is the first to support this
            dpm.setSystemSetting(
                deviceOwnerComponent, Settings.System.SCREEN_OFF_TIMEOUT,
                SCREEN_TIMEOUT_MILLIS.toString()
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // api 30 is the first to support this
            dpm.setLocationEnabled(deviceOwnerComponent, true)
        }
    }

    override fun setAcceptFirmwareUpdates() {
        Log.d(TAG, "setAcceptFirmwareUpdates")
    }

    override fun setDisableFirmwareUpdates() {
        Log.d(TAG, "setDisableFirmwareUpdates")
    }
}
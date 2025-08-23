package tech.syncvr.mdm_agent.device_management.system_settings.pico

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import tech.syncvr.mdm_agent.device_management.services.ShellCommandService
import tech.syncvr.mdm_agent.device_management.system_settings.ISystemSettingsService
import tech.syncvr.mdm_agent.logging.AnalyticsLogger

open class PicoSystemSettingsService(
    private val context: Context,
    private val analyticsLogger: AnalyticsLogger
) : ISystemSettingsService {
    companion object {
        private const val TAG = "PicoSystemSettingsService"
        private const val RADIX_PACKAGE = "com.viso.mdm"
    }

    override fun setDefaultSystemSettings() {
        Log.d(TAG, "Set Default System Settings")
        val shellCommandService = ShellCommandService()

        val sleepRes = shellCommandService.runCommand(
            arrayOf(
                "setprop",
                "persist.psensor.sleep.delay",
                "300"
            )
        )

        val screenRes = shellCommandService.runCommand(
            arrayOf(
                "setprop",
                "persist.psensor.screenoff.delay",
                "300"
            )
        )

        if (!(sleepRes.success && screenRes.success)) {
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.SYSTEM_SETTINGS_EVENT,
                "Failed to set Sleep and Screen Timeout values!"
            )
        }

        // TODO: This does not really belong here, but since it's temporary and specific to Pico G2 4K and Neo 2,
        // TODO: it seems ridiculous to properly factor it somewhere.
        checkRemoveRadix()
    }

    override fun setAcceptFirmwareUpdates() {
        Log.d(TAG, "setAcceptFirmwareUpdates")
    }

    override fun setDisableFirmwareUpdates() {
        Log.d(TAG, "setDisableFirmwareUpdates")
    }



    private fun checkRemoveRadix() {
        if (isRadixInstalled()) {
            analyticsLogger.logMsg(
                AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                "Radix is installed, will try to remove it!"
            )

            val intent = Intent("tech.syncvr.intent.REMOVE_RADIX")
            val resolveInfoList =
                context.packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)
            if (resolveInfoList.size == 0) {
                analyticsLogger.logErrorMsg(
                    AnalyticsLogger.Companion.LogEventType.SYSTEM_SETTINGS_EVENT,
                    "Radix is installed, but not GrantDeviceOwner! Can't remove Radix!"
                )
            }

            resolveInfoList.forEach {
                intent.component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                context.sendBroadcast(intent)
            }
        }
    }

    private fun isRadixInstalled(): Boolean {
        return context.packageManager.getInstalledPackages(0).map {
            it.packageName
        }.contains(RADIX_PACKAGE)
    }
}
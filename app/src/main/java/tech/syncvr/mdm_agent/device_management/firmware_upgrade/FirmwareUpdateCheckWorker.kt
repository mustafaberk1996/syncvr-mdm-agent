package tech.syncvr.mdm_agent.device_management.firmware_upgrade

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.runBlocking
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.device_management.system_settings.ISystemSettingsService
import tech.syncvr.mdm_agent.firebase.IDeviceApiService
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.repositories.firmware.FirmwareRepository

@HiltWorker
class FirmwareUpdateCheckWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted workerParams: WorkerParameters,
    private val firmwareRepository: FirmwareRepository,
    private val deviceApiService: IDeviceApiService,
    private val systemSettingsService: ISystemSettingsService,
    private val analyticsLogger: AnalyticsLogger
) :
    Worker(ctx, workerParams) {

    val context: MDMAgentApplication by lazy { ctx as MDMAgentApplication }

    override fun doWork(): Result = runBlocking {
        val currentFirmware = firmwareRepository.getFirmwareVersion()
        val desiredFirmware = deviceApiService.getFirmwareInfo()?.firmwareVersion?.let {
            Version.parse(it)
        }

        if (desiredFirmware != null) {
            if (currentFirmware < desiredFirmware) {
                analyticsLogger.logMsg(
                    AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                    "Current Firmware: $currentFirmware. Desired Firmware: $desiredFirmware. Firmware update ON"
                )
                systemSettingsService.setAcceptFirmwareUpdates()
            } else {
                analyticsLogger.logMsg(
                    AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                    "Current Firmware: $currentFirmware. Desired Firmware: $desiredFirmware. Firmware update OFF"
                )
                systemSettingsService.setDisableFirmwareUpdates()
            }
        } else {
            analyticsLogger.logMsg(
                AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                "Current Firmware: $currentFirmware. No Desired Firmware NO-OP."
            )
        }

        return@runBlocking Result.success()
    }

}
package tech.syncvr.mdm_agent.device_management.bluetooth_name

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking
import tech.syncvr.mdm_agent.firebase.IDeviceApiService
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.repositories.DeviceInfoRepository


@HiltWorker
class FetchDeviceInfoWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val deviceApiService: IDeviceApiService,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val analyticsLogger: AnalyticsLogger
) : Worker(context, workerParameters) {

    companion object {
        private const val TAG = "FetchDeviceInfoWorker"
    }

    override fun doWork(): Result = runBlocking {
        val deviceInfo = fetchDeviceInfo()
        if (deviceInfo != null) {
            analyticsLogger.logMsg(TAG, "Successfully synced device info.")
            if (deviceInfo.departmentName.isNotBlank()) {
                deviceInfoRepository.onDeviceInfoFetched(deviceInfo)
            }
        }
        return@runBlocking Result.success()
    }

    private suspend fun fetchDeviceInfo(): DeviceInfo? {
        return deviceApiService.getDeviceInfo()
    }

}
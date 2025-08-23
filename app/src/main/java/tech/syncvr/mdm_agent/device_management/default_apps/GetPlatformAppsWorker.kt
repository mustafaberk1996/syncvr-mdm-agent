package tech.syncvr.mdm_agent.device_management.default_apps

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class GetPlatformAppsWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val platformAppsRepository: PlatformAppsRepository
) : Worker(
    context, workerParameters
) {

    companion object {
        private const val TAG = "GetDefaultAppsWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "start GetDefaultAppsWorker")
        platformAppsRepository.refreshPlatformApps()

        return Result.success()
    }
}
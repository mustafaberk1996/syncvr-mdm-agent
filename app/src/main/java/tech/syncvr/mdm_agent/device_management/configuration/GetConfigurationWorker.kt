package tech.syncvr.mdm_agent.device_management.configuration

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication

@HiltWorker
class GetConfigurationWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val configurationRepository: ConfigurationRepository
) : Worker(
    context, workerParameters
) {

    companion object {
        private const val TAG = "GetConfigurationWorker"
    }

    override fun doWork(): Result {
        MDMAgentApplication.coroutineScope.launch {
            configurationRepository.refreshConfigurationTrigger.send(Unit)
        }
        return Result.success()
    }

}
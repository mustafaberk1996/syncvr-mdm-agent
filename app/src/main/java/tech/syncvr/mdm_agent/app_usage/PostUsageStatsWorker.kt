package tech.syncvr.mdm_agent.app_usage

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking

@HiltWorker
class PostUsageStatsWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val usageStatsRepository: UsageStatsRepository,
) : Worker(context, workerParameters) {

    companion object {
        private const val TAG = "PostUsageStatsWorker"
    }

    override fun doWork(): Result = runBlocking {
        // Tell the usageStatsRepository to gather and store usage data.
        // That needs to happen anyhow, we can try to upload later.
        if (usageStatsRepository.syncRemoteUsageStats()) {
            return@runBlocking Result.success()
        } else {
            return@runBlocking Result.failure()
        }
    }

}
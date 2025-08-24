package tech.syncvr.mdm_agent.policy

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.syncvr.mdm_agent.utils.Logger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolicyBroadcastScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "PolicyBroadcastScheduler"
        private const val POLICY_BROADCAST_INTERVAL_MINUTES = 15L // Check policy every 15 minutes
    }

    private val workManager = WorkManager.getInstance(context)

    fun schedulePolicyBroadcast() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<PolicyBroadcastWorker>(
                POLICY_BROADCAST_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            ).build()

            workManager.enqueueUniquePeriodicWork(
                PolicyBroadcastWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            logger.i(TAG, "Policy broadcast work scheduled successfully")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to schedule policy broadcast work: ${e.message}")
        }
    }

    fun triggerImmediateBroadcast() {
        try {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<PolicyBroadcastWorker>()
                .build()

            workManager.enqueue(workRequest)
            logger.i(TAG, "Immediate policy broadcast triggered")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to trigger immediate policy broadcast: ${e.message}")
        }
    }

    fun cancelPolicyBroadcast() {
        workManager.cancelUniqueWork(PolicyBroadcastWorker.WORK_NAME)
        logger.i(TAG, "Policy broadcast work cancelled")
    }
}
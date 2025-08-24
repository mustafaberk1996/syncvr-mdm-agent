package tech.syncvr.mdm_agent.policy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.syncvr.mdm_agent.utils.Logger

@HiltWorker
class PolicyBroadcastWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val policyRepository: PolicyRepository,
    private val logger: Logger
) : Worker(context, workerParameters) {

    companion object {
        private const val TAG = "PolicyBroadcastWorker"
        const val ACTION_POLICY_UPDATE = "tech.syncvr.mdm_agent.POLICY_UPDATE"
        const val EXTRA_POLICY_JSON = "policy_json"
        const val WORK_NAME = "policy_broadcast_work"
    }

    override fun doWork(): Result {
        return try {
            logger.d(TAG, "Starting policy broadcast work")

            // Check READ_EXTERNAL_STORAGE permission
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                logger.i(TAG, "READ_EXTERNAL_STORAGE permission not granted. Cannot read policy file. Using cached policy.")
                // Don't refresh from file, just use cached policy
            } else {
                // Refresh policy from file only if we have permission
                policyRepository.refreshPolicy()
            }

            // Get current policy (either refreshed or cached)
            logger.d(TAG, "Current policy: ${policyRepository.currentPolicy.value}")
            val currentPolicy = policyRepository.currentPolicy.value
            
            // Serialize policy to JSON
            val policyJson = Json.encodeToString(currentPolicy)

            // Create broadcast intent
            val intent = Intent(ACTION_POLICY_UPDATE).apply {
                putExtra(EXTRA_POLICY_JSON, policyJson)
                // Add flag to allow broadcast to reach other apps
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            
            // Send broadcast
            context.sendBroadcast(intent)
            
            logger.i(TAG, "Policy broadcast sent successfully${if (!hasPermission) " (using cached policy)" else ""}")
            Result.success()
            
        } catch (e: Exception) {
            logger.e(TAG, "Failed to broadcast policy: ${e.message}")
            Result.failure()
        }
    }
}
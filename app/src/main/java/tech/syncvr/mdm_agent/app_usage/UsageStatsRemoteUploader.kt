package tech.syncvr.mdm_agent.app_usage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.syncvr.mdm_agent.firebase.IDeviceApiService
import tech.syncvr.mdm_agent.storage.UsageStatsEntity
import javax.inject.Inject


class UsageStatsRemoteUploader @Inject constructor(
    private val deviceApiService: IDeviceApiService,
) {

    private fun UsageStatsEntity.toEntry() =
        AppUsageStats(startTime = startTime, endTime = endTime, usageTime = usageTime)

    suspend fun uploadUsageStats(usageStats: List<UsageStatsEntity>): Boolean {
        return withContext(Dispatchers.IO) {
            val statListsToSync: Map<String, List<AppUsageStats>> =
                usageStats.groupBy(
                    { it.packageName },
                    { it.toEntry() }
                )


            return@withContext deviceApiService.postAppUsage(statListsToSync)
        }
    }

}
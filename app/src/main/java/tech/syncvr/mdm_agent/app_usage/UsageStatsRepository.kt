package tech.syncvr.mdm_agent.app_usage

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.localcache.ILocalCacheSource
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.storage.AppDatabase
import tech.syncvr.mdm_agent.storage.UsageStatsEntity
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsRepository @Inject constructor(
    private val usageStatsManager: UsageStatsManager,
    private val appDatabase: AppDatabase,
    private val analyticsLogger: AnalyticsLogger,
    private val usageStatsRemoteUploader: UsageStatsRemoteUploader,
    private val localCacheSource: ILocalCacheSource
) {

    companion object {
        private const val TAG = "UsageStatsRepository"
        private const val MILLISECONDS_PER_DAY = 1000L * 60L * 60L * 24L
    }

    private val usageStatsRepositoryMutex = Mutex()

    private suspend fun syncLocalUsageStats() {
        val now = System.currentTimeMillis()

        val startTimeFrom = now - (30 * MILLISECONDS_PER_DAY)
        val usageStats = queryUsageStatsFromAndroid(startTimeFrom)
        // Android may return a bucket with a startTime that is before the startTimeFrom we pass it.
        // Need to ensure we also query that far in the past from our database.
        val usageStatsEntities = queryUsageStatsFromDatabase(
            startTimeFrom.minus(
                MILLISECONDS_PER_DAY
            )
        )

//        Log.i(TAG, "IN DATABASE:")
//        usageStatsEntities.forEach {
//            Log.i(TAG, it.toLogString())
//        }

        val (insertList, updateList) = calculateInsertUpdateStats(usageStats, usageStatsEntities)
        analyticsLogger.log(
            TAG,
            hashMapOf("LocalSyncUsageStatsResults" to "${insertList.count()} records inserted - ${updateList.count()} records updated")
        )

        appDatabase.withTransaction {
            appDatabase.usageStatsDao().insert(insertList)
            appDatabase.usageStatsDao().updateUsageStats(updateList)
        }

        setLastLocalSyncTime(now)
    }

    private fun queryUsageStatsFromAndroid(startTimeFrom: Long): List<UsageStats> {
        val now = System.currentTimeMillis()
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTimeFrom,
            now
        )

        val filteredUsageStats = usageStatsList.filter {
            it.totalTimeInForeground > 0
        }

        return filteredUsageStats
    }

    private suspend fun queryUsageStatsFromDatabase(startTimeFrom: Long): List<UsageStatsEntity> {
        return appDatabase.usageStatsDao().getFromStartTime(startTimeFrom)
    }

    private fun calculateInsertUpdateStats(
        usageStats: List<UsageStats>,
        usageStatsEntities: List<UsageStatsEntity>
    ): Pair<List<UsageStatsEntity>, List<UsageStatsEntity>> {

        val entitiesMap = usageStatsEntities.groupBy { it.packageName }
        val statsMap = usageStats.groupBy { it.packageName }

        val insertList = mutableListOf<UsageStatsEntity>()
        val updateList = mutableListOf<UsageStatsEntity>()
        val noopList = mutableListOf<UsageStats>()

        statsMap.forEach { statsMapEntry ->
            val entities = entitiesMap[statsMapEntry.key]
            // if there's nothing for this app in the DB, we must insert everything.
            if (entities == null) {
                statsMapEntry.value.forEach {
                    insertList.add(
                        UsageStatsEntity(
                            packageName = it.packageName,
                            startTime = it.firstTimeStamp,
                            endTime = it.lastTimeStamp,
                            usageTime = it.totalTimeInForeground,
                            uploadRequired = true
                        )
                    )
                }
            } else {
                // else, we go case by case
                statsMapEntry.value.forEach { stats ->
                    if (entityExistsDontUpdate(stats, entities)) {
                        noopList.add(stats)
                    } else {
                        val entityId = entityExistsUpdate(stats, entities)
                        if (entityId != null) {
                            updateList.add(
                                UsageStatsEntity(
                                    entityId,
                                    stats.packageName,
                                    stats.firstTimeStamp,
                                    stats.lastTimeStamp,
                                    stats.totalTimeInForeground,
                                    true
                                )
                            )
                        } else {
                            insertList.add(
                                UsageStatsEntity(
                                    packageName = stats.packageName,
                                    startTime = stats.firstTimeStamp,
                                    endTime = stats.lastTimeStamp,
                                    usageTime = stats.totalTimeInForeground,
                                    uploadRequired = true
                                )
                            )
                        }
                    }
                }
            }
        }

//        Log.i(TAG, "NOOP LIST:")
//        noopList.forEach {
//            Log.i(TAG, "${it.packageName} - ${it.firstTimeStamp} - ${it.lastTimeStamp} - ${it.lastTimeUsed} - ${it.totalTimeInForeground}")
//        }
//
//        Log.i(TAG, "INSERT LIST:")
//        insertList.forEach {
//            Log.i(TAG, it.toLogString())
//        }
//        Log.i(TAG, "UPDATE LIST:")
//        updateList.forEach {
//            Log.i(TAG, it.toLogString())
//        }

        return Pair(insertList.toList(), updateList.toList())
    }

    private fun entityExistsDontUpdate(
        stats: UsageStats,
        entities: List<UsageStatsEntity>
    ): Boolean {
        return entities.firstOrNull {
            it.isEqual(stats) || //it's completely equal
                    (it.isEqualStartTime(stats) && !it.uploadRequired) || // start time is equal, but it's been uploaded already
                    (it.isEqualOverlap(stats) && !it.uploadRequired) // there's an overlapping bucket, but it's been uploaded already
        } != null
    }

    private fun entityExistsUpdate(stats: UsageStats, entities: List<UsageStatsEntity>): Long? {
        entities.forEach {
            if (it.isEqualStartTime(stats) && it.uploadRequired) {
                return it.itemId
            } else if (it.isEqualOverlap(stats) && it.uploadRequired) {
                return it.itemId
            }
        }

        return null
    }

    fun startPeriodicQueryUsageStats() {
        MDMAgentApplication.coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                usageStatsRepositoryMutex.withLock {
//                    Log.i(TAG, "======== ${LocalDateTime.now()} ========")
                    syncLocalUsageStats()
                }
                delay(60000L)
            }
        }
    }

    suspend fun syncRemoteUsageStats(): Boolean {
        usageStatsRepositoryMutex.withLock {
            // If the last time we synced locally was less than 24 hours after the startTime of a bucket,
            // there's a chance the bucket might still change in a future localSync. So even if the startTime
            // itself is more than 24 hours ago, it could still change. For that reason, we only upload data
            // whose startTime is more than 24 hours before the last local sync time, because then we are
            // sure it wont change anymore.
            val lastLocalSyncTime = getLastLocalSyncTime()
            val uploadList = appDatabase.usageStatsDao()
                .getUploadRequired(getLastLocalSyncTime() - MILLISECONDS_PER_DAY)

            analyticsLogger.log(
                TAG,
                hashMapOf("UploadUsageStatsResults" to "${uploadList.count()} records to upload")
            )

//            Log.i(TAG, "UPLOAD LIST (last local sync: ${Date(lastLocalSyncTime)} ):")
//            uploadList.forEach {
//                Log.i(TAG, it.toLogString())
//            }

            return usageStatsRemoteUploader.uploadUsageStats(uploadList).also { success ->
                if (success) {
                    val updateList = uploadList.map {
                        UsageStatsEntity(
                            it.itemId,
                            it.packageName,
                            it.startTime,
                            it.endTime,
                            it.usageTime,
                            false
                        )
                    }
                    appDatabase.usageStatsDao().updateUsageStats(updateList)
                    analyticsLogger.log(
                        TAG,
                        hashMapOf("UploadUsageStatsResults" to "${uploadList.count()} records uploaded")
                    )
                }
            }
        }
    }

    private fun UsageStats.toEntity() =
        UsageStatsEntity(
            packageName = packageName,
            startTime = firstTimeStamp,
            endTime = lastTimeStamp,
            usageTime = totalTimeInForeground,
            uploadRequired = true
        )

    private fun getLastLocalSyncTime(): Long {
        return localCacheSource.getLastUsageStatsQueryTime()
    }

    private fun setLastLocalSyncTime(localSyncTime: Long) {
        localCacheSource.setLastUsageStatsQueryTime(localSyncTime)
    }

}

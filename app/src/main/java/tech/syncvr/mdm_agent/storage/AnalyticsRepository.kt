package tech.syncvr.mdm_agent.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import tech.syncvr.mdm_agent.model.AnalyticsEntry
import tech.syncvr.mdm_agent.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    val logger: Logger
) {

    companion object {
        private val TAG = "AnalyticsRepository"

        // use gson and not kotlin-serialization, because gson allows to serialize HashMap<String,Any> (very lenient)
        // it is not easy to deserialize, but we don't need that.
        // date format is the date format as expected by the backend
        val gson: Gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create()
    }

    suspend fun insertAnalyticsEntry(analyticsEntry: AnalyticsEntry): Long {
        logger.d(TAG, "analyticsEntry.toEntity() = ${analyticsEntry.toEntity()}")
        return appDatabase.analyticsDao()
            .insert(analyticsEntry.toEntity())
    }

    suspend fun getOldest100Entries(): List<AnalyticsEntity> {
        return appDatabase.analyticsDao().getOldest100Entries()
    }

    suspend fun deleteEntriesUpTo(cutoffTimestamp: Long) {
        appDatabase.analyticsDao().deleteEntriesUpTo(cutoffTimestamp)
    }

    private fun AnalyticsEntry.toEntity() =
        AnalyticsEntity(timeStamp = timeStamp, analyticsHashmap = gson.toJson(hashMap))
}
package tech.syncvr.mdm_agent.logging

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tech.syncvr.mdm_agent.firebase.IDeviceApiService
import tech.syncvr.mdm_agent.storage.AnalyticsEntity
import tech.syncvr.mdm_agent.storage.AnalyticsRepository

@HiltWorker
class UploadLogEntriesWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val deviceApiService: IDeviceApiService,
    private val analyticsRepository: AnalyticsRepository,
) :
    Worker(context, workerParameters) {

    companion object {
        const val TAG = "UploadLogEntriesWorker"
        private val dbMutex = Mutex()
    }

    override fun doWork(): Result = runBlocking {

        if (Firebase.auth.currentUser == null) {
            return@runBlocking Result.failure()
        }

        dbMutex.withLock {
            var entries = analyticsRepository.getOldest100Entries()
            while (entries.isNotEmpty()) {
                // Upload the batch of entries
                val success = uploadEntries(entries)
                if (success) {
                    // Delete the uploaded entries
                    val lastEntry = entries.last()
                    analyticsRepository.deleteEntriesUpTo(lastEntry.timeStamp.time)

                    // Get the next batch of entries
                    entries = analyticsRepository.getOldest100Entries()
                } else {
                    return@runBlocking Result.failure()
                }
            }
        }

        return@runBlocking Result.success()

    }

    private suspend fun uploadEntries(entries: List<AnalyticsEntity>): Boolean {
        val logEntriesList = entries.map { it.analyticsHashmap }
        val logEntriesJSONArray = JSONArray(logEntriesList.map { JSONObject(it) })
        val logEntriesJSONObject = JSONObject().also {
            it.put("logEntries", logEntriesJSONArray)
        }
        val logEntriesString = logEntriesJSONObject.toString()
        val requestBody = logEntriesString.toRequestBody("application/json".toMediaType())
        Log.d(TAG, "logEntries: $logEntriesString")
        return deviceApiService.postLogs(requestBody)
    }

}
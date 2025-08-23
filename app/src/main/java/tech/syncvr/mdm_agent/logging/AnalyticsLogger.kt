package tech.syncvr.mdm_agent.logging

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import tech.syncvr.logging_connector_v2.LogLevel
import tech.syncvr.mdm_agent.BuildConfig
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.device_identity.DeviceIdentityRepository
import tech.syncvr.mdm_agent.model.AnalyticsEntry
import tech.syncvr.mdm_agent.repositories.DeviceInfoRepository
import tech.syncvr.mdm_agent.storage.AnalyticsRepository
import tech.syncvr.mdm_agent.utils.Logger
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsLogger @Inject constructor(
    @ApplicationContext val context: Context,
    private val deviceInfoRepository: DeviceInfoRepository,
    val deviceIdentityRepository: DeviceIdentityRepository,
    private val logger: Logger,
    private val analyticsRepository: AnalyticsRepository,
) {

    companion object {

        private const val TAG = "LoggingManager"

        enum class LogEventType {
            MDM_EVENT,
            APP_INTERFACE_EVENT,
            FIRMWARE_UPDATE_EVENT,
            DOWNLOAD_EVENT,
            INSTALL_EVENT,
            PERMISSION_EVENT,
            SERVER_ERROR_EVENT,
            HTTP_ERROR_EVENT,
            CONNECTIVITY_EVENT,
            APP_START_EVENT,
            AUTHENTICATION_EVENT,
            SYSTEM_SETTINGS_EVENT
        }

        enum class PayloadKeys {
            ERROR_MSG, LOG_MSG
        }
    }

    fun log(
        appName: String,
        eventType: String,
        severity: LogLevel,
        payload: HashMap<String, Any>,
    ) {
        val deviceId = try {
            deviceIdentityRepository.getDeviceId()
        } catch (throwable: Throwable) { // not device owner yet
            logger.e(
                TAG,
                "analytics logging probably before device ownership: ${throwable.message}"
            )
            return
        }

        val customerName = deviceInfoRepository.deviceInfoStateFlow.value.customerName
        val departmentName =
            deviceInfoRepository.deviceInfoStateFlow.value.departmentName

        val timeStamp = Date()
        val logEntry: HashMap<String, Any> = hashMapOf<String, Any>(
            "logName" to "device.analytics.events",
            "severity" to mapToGoogleCloudLogLevel(severity),
            "timestamp" to timeStamp,
            "labels" to hashMapOf(
                "appName" to appName,
                "deviceId" to deviceId,
                "customerName" to customerName,
                "departmentName" to departmentName,
                "mdmVersion" to BuildConfig.VERSION_CODE,
                "taVersion" to context.getRemoteAppVersionCode("tech.syncvr.treatment_agent"),
                "thVersion"  to context.getRemoteAppVersionCode("tech.syncvr.treatment_hub"),
            ),
            "jsonPayload" to HashMap(payload).also {
                it["eventType"] = eventType
            }
        )

        MDMAgentApplication.coroutineScope.launch {
            analyticsRepository.insertAnalyticsEntry(AnalyticsEntry(timeStamp, logEntry))

            Log.d(TAG, GsonBuilder().create().toJson(logEntry))

            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val uploadLogEntryWorkRequest: OneTimeWorkRequest = OneTimeWorkRequest.Builder(
                UploadLogEntriesWorker::class.java
            ).setConstraints(constraints).build()

            try {
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(
                        UploadLogEntriesWorker.TAG,
                        ExistingWorkPolicy.KEEP,
                        uploadLogEntryWorkRequest
                    )
            } catch (throwable: Throwable) {
                logger.d(
                    TAG,
                    "on analytics-events fired from Hilt-provisioning itself the application is not injected properly yet, so kipping: ${throwable.message}"
                )
            }
        }
    }


    fun log(appName: String, eventType: String, payload: HashMap<String, Any>) {
        log(appName, eventType, LogLevel.INFO, payload)
    }

    fun log(appName: String, eventType: LogEventType, payload: HashMap<String, Any>) {
        log(appName, eventType.name, LogLevel.INFO, payload)
    }

    fun log(eventType: String, payload: HashMap<String, Any>) {
        log(context.packageName, eventType, payload)
    }

    fun log(eventType: LogEventType, payload: HashMap<String, Any>) {
        log(context.packageName, eventType, payload)
    }

    fun logMsg(eventType: String, msg: String) {
        log(eventType, hashMapOf(PayloadKeys.LOG_MSG.name to msg))
    }

    fun logMsg(eventType: LogEventType, msg: String) {
        log(eventType, hashMapOf(PayloadKeys.LOG_MSG.name to msg))
    }

    fun logError(eventType: String, payload: HashMap<String, Any>) {
        log(context.packageName, eventType, LogLevel.ERROR, payload)
    }

    fun logError(eventType: LogEventType, payload: HashMap<String, Any>) {
        log(context.packageName, eventType.name, LogLevel.ERROR, payload)
    }

    fun logErrorMsg(eventType: String, errorMsg: String) {
        logError(eventType, hashMapOf(PayloadKeys.ERROR_MSG.name to errorMsg))
    }

    fun logErrorMsg(eventType: LogEventType, errorMsg: String) {
        logError(eventType, hashMapOf(PayloadKeys.ERROR_MSG.name to errorMsg))
    }

    private fun mapToGoogleCloudLogLevel(level: LogLevel): String = level.name


    fun Context.getRemoteAppVersionCode(packageName: String): Int {
        return try {
            packageManager
                .getPackageInfo(packageName, 0)
                .versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            // app not installed
            -1
        }
    }
}

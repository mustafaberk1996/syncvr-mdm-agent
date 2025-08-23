package tech.syncvr.mdm_agent.firebase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import tech.syncvr.mdm_agent.BuildConfig
import tech.syncvr.mdm_agent.app_usage.AppUsageStats
import tech.syncvr.mdm_agent.device_identity.DeviceIdentityRepository
import tech.syncvr.mdm_agent.device_management.bluetooth_name.DeviceInfo
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.device_management.device_status.DeviceStatus
import tech.syncvr.mdm_agent.device_management.firmware_upgrade.FirmwareInfo
import tech.syncvr.mdm_agent.firebase.retrofit.HttpStatusCodes
import tech.syncvr.mdm_agent.firebase.retrofit.RetrofitDeviceApiService
import tech.syncvr.mdm_agent.firebase.retrofit.RetrofitServiceBuilder
import tech.syncvr.mdm_agent.logging.AnalyticsEntriesDto
import tech.syncvr.mdm_agent.logging.AnalyticsEntryDto
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceApiService @Inject constructor(
    private val authenticationService: IAuthenticationService,
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val analyticsLogger: AnalyticsLogger
) : IDeviceApiService {

    companion object {
        private const val TAG = "DeviceApiService"
    }

    private val deviceDotNetApiService: RetrofitDeviceApiService =
        RetrofitServiceBuilder.buildDeviceApiService(
            authenticationService, BuildConfig.SYNCVR_DEVICE_API_BASE_URL
        )

    override suspend fun getConfiguration(): Configuration? {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                val res =
                    deviceDotNetApiService.getConfigurationV2(deviceIdentityRepository.getDeviceId())
                if (res.isSuccessful) {
                    res.body()?.response
                } else {
                    with("HTTP error getting Configuration: ${res.code()}") {
                        analyticsLogger.logErrorMsg(
                            AnalyticsLogger.Companion.LogEventType.HTTP_ERROR_EVENT, this
                        )
                        Log.e(TAG, this)
                    }
                    null
                }
            } catch (e: IOException) {
                with("Exception getting Configuration: ${e.message}") {
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.CONNECTIVITY_EVENT, this
                    )
                    Log.e(TAG, this)
                }
                null
            }
        }
    }

    override suspend fun getPlatformApps(): List<ManagedAppPackage>? {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                val res =
                    deviceDotNetApiService.getPlatformAppsV2(deviceIdentityRepository.getDeviceId())
                if (res.isSuccessful) {
                    res.body()?.response
                } else {
                    with("HTTP error getting Platform Apps: ${res.code()} - ${res.body()?.errors?.firstOrNull()}") {
                        analyticsLogger.logErrorMsg(
                            AnalyticsLogger.Companion.LogEventType.HTTP_ERROR_EVENT, this
                        )
                        Log.e(TAG, this)
                    }
                    null
                }
            } catch (e: IOException) {
                with("Exception getting Platform Apps: ${e.message}") {
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.CONNECTIVITY_EVENT, this
                    )
                    Log.e(TAG, this)
                }
                null
            }
        }
    }

    override suspend fun postDeviceStatus(deviceStatus: DeviceStatus): Boolean {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                val res = deviceDotNetApiService.postDeviceStatusV2(
                    deviceIdentityRepository.getDeviceId(), deviceStatus
                )
                if (res.isSuccessful) {
                    true
                } else {
                    with("HTTP error posting Device Status: ${res.code()} - ${res.body()?.errors?.firstOrNull()}") {
                        analyticsLogger.logErrorMsg(
                            AnalyticsLogger.Companion.LogEventType.HTTP_ERROR_EVENT, this
                        )
                        Log.e(TAG, this)
                    }
                    false
                }
            } catch (e: Exception) {
                with("Exception posting Device Status: ${e.message}") {
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.CONNECTIVITY_EVENT, this
                    )
                    Log.e(TAG, this)
                }
                false
            }
        }
    }

    override suspend fun getFirmwareInfo(): FirmwareInfo? {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                val res =
                    deviceDotNetApiService.getFirmwareInfoV2(deviceIdentityRepository.getDeviceId())
                if (res.isSuccessful) {
                    res.body()?.response
                } else {
                    with("HTTP error getting Firmware Info: ${res.code()} - ${res.body()?.errors?.firstOrNull()}") {
                        analyticsLogger.logErrorMsg(
                            AnalyticsLogger.Companion.LogEventType.HTTP_ERROR_EVENT, this
                        )
                        Log.e(TAG, this)
                    }
                    null
                }
            } catch (e: IOException) {
                with("Exception getting Firmware Info: ${e.message}") {
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.CONNECTIVITY_EVENT, this
                    )
                    Log.e(TAG, this)
                }
                null
            }
        }
    }


    override suspend fun getDeviceInfo(): DeviceInfo? {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                val res =
                    deviceDotNetApiService.getDeviceInfoV2(deviceIdentityRepository.getDeviceId())
                if (res.isSuccessful) {
                    res.body()?.response
                } else {
                    with("HTTP error getting Device Info: ${res.code()} - ${res.body()?.errors?.firstOrNull()}") {
                        analyticsLogger.logErrorMsg(
                            AnalyticsLogger.Companion.LogEventType.HTTP_ERROR_EVENT, this
                        )
                        Log.e(TAG, this)
                    }
                    null
                }
            } catch (e: IOException) {
                with("Exception getting Device Info: ${e.message}") {
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.CONNECTIVITY_EVENT, this
                    )
                    Log.e(TAG, this)
                }
                null
            }
        }
    }

    override suspend fun postAppUsage(appUsage: Map<String, List<AppUsageStats>>): Boolean {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                val res = deviceDotNetApiService.postAppUsageV2(
                    deviceIdentityRepository.getDeviceId(), appUsage
                )
                if (res.isSuccessful) {
                    true
                } else {
                    with("HTTP error posting App Usage: ${res.code()} - ${res.body()?.errors?.firstOrNull()}") {
                        analyticsLogger.logErrorMsg(
                            AnalyticsLogger.Companion.LogEventType.HTTP_ERROR_EVENT, this
                        )
                        Log.e(TAG, this)
                    }
                    false
                }
            } catch (e: Exception) {
                with("Exception posting App Usage: ${e.message}") {
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.CONNECTIVITY_EVENT, this
                    )
                    Log.e(TAG, this)
                }
                false
            }
        }
    }

    override suspend fun postLogs(requestBody: RequestBody): Boolean {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                val res = deviceDotNetApiService.postLogsV2(
                    deviceIdentityRepository.getDeviceId(), requestBody
                )
                if (res.isSuccessful) {
                    true
                } else {
                    with("HTTP error posting Analytics Logs: ${res.code()} - ${res.body()?.errors?.firstOrNull()}") {
                        analyticsLogger.logErrorMsg(
                            AnalyticsLogger.Companion.LogEventType.HTTP_ERROR_EVENT, this
                        )
                        Log.e(TAG, this)
                    }
                    false
                }
            } catch (e: Exception) {
                with("Exception posting Analytics Logs: ${e.message}") {
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.CONNECTIVITY_EVENT, this
                    )
                    Log.e(TAG, this)
                }
                false
            }
        }
    }
}

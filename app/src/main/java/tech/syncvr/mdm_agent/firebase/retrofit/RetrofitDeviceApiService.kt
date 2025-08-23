package tech.syncvr.mdm_agent.firebase.retrofit

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import tech.syncvr.mdm_agent.app_usage.AppUsageStats
import tech.syncvr.mdm_agent.device_management.bluetooth_name.DeviceInfo
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.device_management.device_status.DeviceStatus
import tech.syncvr.mdm_agent.device_management.firmware_upgrade.FirmwareInfo
import tech.syncvr.mdm_agent.logging.AnalyticsEntriesDto
import tech.syncvr.mdm_agent.logging.AnalyticsEntryDto

interface RetrofitDeviceApiService {

    @GET("api/v2/configuration/{SerialNo}")
    suspend fun getConfigurationV2(
        @Path("SerialNo") serialNo: String?
    ): Response<DeviceApiV2Response<Configuration>>

    @POST("api/v2/device-status/{SerialNo}")
    suspend fun postDeviceStatusV2(
        @Path("SerialNo") serialNo: String?,
        @Body deviceStatus: DeviceStatus
    ): Response<DeviceApiV2Response<Unit>>

    @GET("api/v2/platform-apps-latest/{SerialNo}")
    suspend fun getPlatformAppsV2(
        @Path("SerialNo") serialNo: String?
    ): Response<DeviceApiV2Response<List<ManagedAppPackage>>>

    @GET("api/v2/device-info/{SerialNo}")
    suspend fun getDeviceInfoV2(
        @Path("SerialNo") serialNo: String?
    ): Response<DeviceApiV2Response<DeviceInfo>>

    @GET("api/v2/device-firmware/{SerialNo}")
    suspend fun getFirmwareInfoV2(
        @Path("SerialNo") serialNo: String?
    ): Response<DeviceApiV2Response<FirmwareInfo>>

    @POST("api/v2/app-usage/{SerialNo}")
    @JvmSuppressWildcards // needed or Retrofit will choke on the type of appUsage: https://github.com/square/retrofit/issues/3275
    suspend fun postAppUsageV2(
        @Path("SerialNo") serialNo: String?,
        @Body appUsage: Map<String, List<AppUsageStats>>
    ): Response<DeviceApiV2Response<Unit>>

    @POST("api/v2/log/{SerialNo}")
    suspend fun postLogsV2(
        @Path("SerialNo") serialNo: String?,
        // here we provide a complete RequestBody which is already json serialized, because
        // kotlinx-serialization has real trouble serializing type Map<String, Any>
        @Body requestBody: RequestBody
    ): Response<DeviceApiV2Response<Unit>>


    @GET("device_api/v1/firmware_info")
    suspend fun getFirmwareInfo(
        @Query("device_type") deviceType: String?,
        @Query("current") current: String?
    ): Response<FirmwareInfo>

}

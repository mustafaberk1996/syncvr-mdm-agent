package tech.syncvr.mdm_agent.firebase.retrofit

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface RetrofitAuthenticationApiService {

    @Serializable
    data class LoginPostData(val appKey: String)

    @Serializable
    data class CustomTokenResponse(val customToken: String)

    @POST("api/v2/device-login/{SerialNo}")
    suspend fun getCustomToken(
        @Path("SerialNo") serialNo: String,
        @Body postData: LoginPostData
    ): Response<DeviceApiV2Response<CustomTokenResponse>>
}
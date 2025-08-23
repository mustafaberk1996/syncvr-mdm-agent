package tech.syncvr.mdm_agent.firebase.retrofit

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import tech.syncvr.mdm_agent.firebase.IAuthenticationService

private val json: Json = Json {
    ignoreUnknownKeys = true;
    encodeDefaults = true;
    // this should help in serializing Map<String, Any>, as long as Any is a primitive type.
    serializersModule = SerializersModule {
        contextual(Any::class, PolymorphicSerializer(Any::class))
    }
}

object RetrofitServiceBuilder {

    fun buildDeviceApiService(
        authenticationService: IAuthenticationService,
        baseUrl: String
    ): RetrofitDeviceApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(json.asConverterFactory(MediaTypes.APPLICATION_JSON))
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .addInterceptor(authorizedInterceptor(authenticationService))
                    .build()
            ).build()
            .create(RetrofitDeviceApiService::class.java)
    }

    fun buildAuthenticationApiService(baseUrl: String): RetrofitAuthenticationApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(json.asConverterFactory(MediaTypes.APPLICATION_JSON))
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .build()
            ).build()
            .create(RetrofitAuthenticationApiService::class.java)
    }

    private val loggingInterceptor: HttpLoggingInterceptor =
        HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)

    private fun authorizedInterceptor(authenticationService: IAuthenticationService): Interceptor {
        return Interceptor { chain ->
            val idToken = runBlocking {
                authenticationService.loginAndGetIdToken()
            } ?: return@Interceptor chain.createNotSignedInResponse()
            val newRequest: Request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $idToken")
                .build()
            chain.proceed(newRequest)
        }
    }

    private fun Interceptor.Chain.createNotSignedInResponse(): Response {
        return Response.Builder()
            .request(request())
            .protocol(Protocol.HTTP_1_1)
            .code(HttpStatusCodes.UNAUTHORIZED)
            .message("Not signed in")
            .body("Not yet signed in".toResponseBody(MediaTypes.TEXT_PLAIN))
            .build()
    }

}
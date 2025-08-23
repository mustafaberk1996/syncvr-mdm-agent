package tech.syncvr.mdm_agent.firebase

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import tech.syncvr.mdm_agent.BuildConfig
import tech.syncvr.mdm_agent.device_identity.DeviceIdentityRepository
import tech.syncvr.mdm_agent.firebase.retrofit.RetrofitAuthenticationApiService
import tech.syncvr.mdm_agent.firebase.retrofit.RetrofitServiceBuilder
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FirebaseAuthHandler(
    private val appKey: String,
    deviceIdentityRepository: DeviceIdentityRepository,
    private val analyticsLogger: AnalyticsLogger
) :
    IAuthenticationService {

    val deviceId by lazy { deviceIdentityRepository.getDeviceId() }

    companion object {
        private const val TAG: String = "FirebaseAuthHandler"
    }

    private val signInMutex = Mutex()
    private val api: RetrofitAuthenticationApiService =
        RetrofitServiceBuilder.buildAuthenticationApiService(BuildConfig.SYNCVR_DEVICE_API_BASE_URL)

    override suspend fun loginAndGetIdToken(): String? {
        signInMutex.withLock {
            if (!isSignedIn()) {
                if (!signIn()) {
                    return null
                }
            }
        }

        return getIdToken(false)
    }

    override fun isSignedIn(): Boolean {
        return Firebase.auth.currentUser != null
    }

    override suspend fun signInRoutine() {
        while (!signIn()) {
            Log.d(TAG, "Firebase Login not successful, try again in 10s")
            delay(10000)
        }

        Log.d(TAG, "Firebase Login successful!")
    }

    override suspend fun getIdToken(refresh: Boolean): String? {
        return withContext(Dispatchers.IO) {
            try {
                return@withContext Firebase.auth.currentUser?.getIdToken(refresh)?.await()?.token
            } catch (e: FirebaseException) {
                Log.e(TAG, "Exception getting id Token: ${e.message}")
                analyticsLogger.logErrorMsg(
                    AnalyticsLogger.Companion.LogEventType.AUTHENTICATION_EVENT,
                    "Exception getting id Token: ${e.message}"
                )
                return@withContext null
            }
        }
    }

    private suspend fun signIn(): Boolean {
        return withContext(Dispatchers.IO) {
            val customToken = getCustomToken() ?: return@withContext false
            return@withContext signInWithCustomToken(customToken)
        }
    }

    private suspend fun getCustomToken(): String? {
        val postData = RetrofitAuthenticationApiService.LoginPostData(appKey)
        return try {
            val res = api.getCustomToken(deviceId, postData)
            if (res.isSuccessful) {
                res.body()?.response?.customToken
            } else {
                with("Failed getting custom token: ${res.code()} - ${res.body()?.errors?.firstOrNull()}")
                {
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.HTTP_ERROR_EVENT, this
                    )
                    Log.e(TAG, this)
                }
                null
            }

        } catch (e: IOException) {
            with("Exception getting Custom Token: ${e.message}") {
                analyticsLogger.logErrorMsg(
                    AnalyticsLogger.Companion.LogEventType.CONNECTIVITY_EVENT, this
                )
                Log.e(TAG, this)
            }
            null
        }
    }

    private suspend fun signInWithCustomToken(customToken: String): Boolean =
        suspendCoroutine { continuation ->
            Firebase.auth.signInWithCustomToken(customToken)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener {
                    Log.d(TAG, it.message!!)
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.AUTHENTICATION_EVENT,
                        "Failure signing in with CustomToken: ${it.message}"
                    )
                    continuation.resume(false)
                }
        }

}
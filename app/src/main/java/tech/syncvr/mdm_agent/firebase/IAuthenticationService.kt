package tech.syncvr.mdm_agent.firebase

interface IAuthenticationService {

    fun isSignedIn(): Boolean
    suspend fun getIdToken(refresh: Boolean): String?
    suspend fun signInRoutine()
    suspend fun loginAndGetIdToken(): String?
}
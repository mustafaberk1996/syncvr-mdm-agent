package tech.syncvr.platform_sdk

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import tech.syncvr.platform_sdk.parcelables.Configuration

open class SyncVRPlatformManager(context: Context) {
    companion object {
        val TAG: String = this::class.java.declaringClass.simpleName
        const val PLATFORM_AGENT_PACKAGE = "tech.syncvr.mdm_agent"
        const val PLATFORM_AGENT_SERVICE_BIND_ACTION = "tech.syncvr.intent.BIND_PLATFORM_SERVICE"
        const val PLATFORM_AGENT_SERVICE =
            "$PLATFORM_AGENT_PACKAGE.platform_sdk.SyncVRPlatformService"
        const val SERVICE_NOT_AVAILABLE_MSG =
            "SyncVRPlatformService doesn't seem to be available on this device"
    }

    private val applicationContext = context.applicationContext

    private var syncvrPlatformService: ISyncVRPlatform? = null
    private val exceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "An error occured", throwable)
        }
    internal val coroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private lateinit var isBoundStateFlow: StateFlow<Boolean>
    private lateinit var boundFlowCreatorJob: Job
    private var systemCanBind = true
    private val configurationMutableFlow = MutableStateFlow<Configuration?>(null)
    private val synvrPlatformServiceCallback = object : ISyncVRPlatformCallback.Stub() {
        override fun onConfigurationChanged(config: Configuration?) {
            config?.run {
                configurationMutableFlow.value = this
            }
        }
    }
    val configurationFlow = configurationMutableFlow.asStateFlow()

    init {
        bindToPlatformService()
    }

    private fun bindToPlatformService() {
        boundFlowCreatorJob = coroutineScope.launch {
            createIsBoundFlow()
        }
    }

    private suspend fun createIsBoundFlow() {
        // try to background bind asap
        isBoundStateFlow = flowFromServiceBinding().stateIn(coroutineScope)
    }

    private fun flowFromServiceBinding(): Flow<Boolean> = callbackFlow {
        systemCanBind = applicationContext.bindService(
            Intent(PLATFORM_AGENT_SERVICE_BIND_ACTION).apply {
                setClassName(PLATFORM_AGENT_PACKAGE, PLATFORM_AGENT_SERVICE)
            },
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    Log.d(TAG, "onServiceConnected $name")
                    syncvrPlatformService = ISyncVRPlatform.Stub.asInterface(service)
                    try {
                        syncvrPlatformService?.registerCallback(synvrPlatformServiceCallback)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Unable to register callback upon connection $name", e)
                        // onServiceDisconnected will get triggered
                    }
                    trySendBlocking(true).onFailure {
                        Log.e(TAG, "Unable to send connection event from coroutine", it)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    Log.d(TAG, "onServiceDisconnected $name")
                    // unregister callback
                    syncvrPlatformService = null
                    trySendBlocking(false).onFailure {
                        Log.e(TAG, "Unable to send disconnection event from coroutine", it)
                    }
                    // not too sure this is smart but why not. At least "it cleans itself up"
                    cancel("onServiceDisconnected")
                }
            },
            BIND_AUTO_CREATE
        )
        if (!systemCanBind) {
            Log.w(TAG, SERVICE_NOT_AVAILABLE_MSG)
            cancel(SERVICE_NOT_AVAILABLE_MSG)
        } else {
            Log.d(TAG, "System is binding us to SyncVRPlatformService")
        }
        awaitClose { }
    }

    private suspend fun ensureBound(): Boolean {
        boundFlowCreatorJob.join()
        if (!systemCanBind) {
            throw IllegalStateException(SERVICE_NOT_AVAILABLE_MSG)
        }
        if (!isBoundStateFlow.value) {
            bindToPlatformService()
        }
        // check if we the system found target service
        if (!systemCanBind) {
            return false
        }
        // return state from ServiceConnection callback
        return isBoundStateFlow.value
    }

    suspend fun getSerialNumber(): String? {
        if (!ensureBound()) {
            return null
        }
        return try {
            syncvrPlatformService?.serialNo
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get serial number ", e)
            null
        }
    }

    suspend fun getDepartment(): String? {
        if (!ensureBound()) {
            return null
        }
        return try {
            syncvrPlatformService?.department
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get department", e)
            null
        }
    }

    suspend fun getCustomer(): String? {
        if (!ensureBound()) {
            return null
        }
        return try {
            syncvrPlatformService?.customer
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get customer", e)
            null
        }
    }

    suspend fun getAuthorizationToken(): String? {
        if (!ensureBound()) {
            return null
        }
        return try {
            syncvrPlatformService?.authorizationToken
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get authorizationToken", e)
            null
        }
    }

    suspend fun addWifiConfiguration(wifiSsid: String, wifiPassword: String): Boolean {
        if (!ensureBound()) {
            return false
        }
        return try {
            syncvrPlatformService?.addWifiConfiguration(wifiSsid, wifiPassword) == true
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get spectatingToken", e)
            false
        }
    }

    suspend fun addOpenWifiConfiguration(wifiSsid: String): Boolean {
        if (!ensureBound()) {
            return false
        }
        return try {
            syncvrPlatformService?.addOpenWifiConfiguration(wifiSsid) == true
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get spectatingToken", e)
            false
        }
    }

    suspend fun getConfiguration(): Configuration? {
        if (!ensureBound()) {
            return null
        }
        return try {
            syncvrPlatformService?.configuration
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get authorizationToken", e)
            null
        }
    }
}
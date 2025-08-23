package tech.syncvr.mdm_agent.device_management.default_apps

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.BuildConfig
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.firebase.IDeviceApiService
import tech.syncvr.mdm_agent.localcache.ILocalCacheSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformAppsRepository @Inject constructor(
    private val deviceApiService: IDeviceApiService,
    private val localCacheSource: ILocalCacheSource
) {

    companion object {
        private const val TAG = "DefaultAppsRepository"
        private const val UPDATE_SLEEP_TIME = 60 * 1000L
        fun getSelfApp(managedApps: List<ManagedAppPackage>) =
            managedApps.firstOrNull { app -> app.appPackageName == BuildConfig.APPLICATION_ID }
    }

    private val _platformApps = MutableSharedFlow<List<ManagedAppPackage>?>(replay = 1)
    val platformApps = _platformApps.asSharedFlow()

    fun refreshPlatformApps() {
        MDMAgentApplication.coroutineScope.launch {
            refreshPlatformAppsRoutine()
        }
    }

    private suspend fun loadInitialPlatformApps() {
        localCacheSource.getPlatformApps().let {
            if (it != null) {
                Log.d(TAG, "Starting from a cached DefaultApps: $it")
                _platformApps.emit(it)
            } else {
                Log.d(TAG, "No cached DefaultApps available!")
            }
        }
    }

    private suspend fun refreshPlatformAppsRoutine() {
        deviceApiService.getPlatformApps()?.let { platformApps ->
            Log.d(TAG, "fetched default apps: ${platformApps.map { it.appPackageName }}")
            setPlatformApps(platformApps)
        }
    }

    private suspend fun setPlatformApps(
        newPlatformApps: List<ManagedAppPackage>
    ) {
        _platformApps.emit(newPlatformApps)
        localCacheSource.storePlatformApps(newPlatformApps)
    }

    fun startPeriodicGetPlatformApps() {
        MDMAgentApplication.coroutineScope.launch {
            loadInitialPlatformApps()
            while (true) {
                // be safe...
                try {
                    refreshPlatformAppsRoutine()
                } catch (throwable: Throwable) {
                    Log.e(TAG, throwable.message, throwable)
                    if (BuildConfig.DEBUG) { // crash so it gets noticed during development
                        throw throwable
                    }
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                }
                delay(UPDATE_SLEEP_TIME)
            }
        }
    }
}
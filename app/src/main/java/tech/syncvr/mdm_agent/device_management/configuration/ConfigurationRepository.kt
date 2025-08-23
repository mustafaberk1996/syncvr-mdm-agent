package tech.syncvr.mdm_agent.device_management.configuration

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.BuildConfig
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.localcache.ILocalCacheSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigurationRepository @Inject constructor(
    private val localCacheSource: ILocalCacheSource,
    private val remoteSource: IConfigurationRemoteSource
) {

    companion object {
        private const val TAG = "ConfigurationRepository"
        private const val UPDATE_SLEEP_TIME = 60 * 1000L
    }

    val appInstalledTrigger = MutableSharedFlow<Unit>()

    val refreshConfigurationTrigger = Channel<Unit>()

    val configuration = refreshConfigurationTrigger.consumeAsFlow().map {
        try {
            remoteSource.getConfiguration()?.also {
                localCacheSource.storeConfiguration(it)
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, throwable.message, throwable)
            if (throwable is CancellationException) {
                throw throwable
            }
            if (BuildConfig.DEBUG) { // crash so it gets noticed during development
                throw throwable
            }
            null
        }
    }.filterNotNull().onStart {
        localCacheSource.getConfiguration()?.let {
            emit(it)
        }
    }.stateIn(
        scope = MDMAgentApplication.coroutineScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = null
    )

    fun startPeriodicGetConfiguration() {
        MDMAgentApplication.coroutineScope.launch {
            while (true) {
                refreshConfigurationTrigger.send(Unit)
                delay(UPDATE_SLEEP_TIME)
            }
        }
    }

    fun onAppInstalled() {
        MDMAgentApplication.coroutineScope.launch {
            appInstalledTrigger.emit(Unit)
        }
    }
}

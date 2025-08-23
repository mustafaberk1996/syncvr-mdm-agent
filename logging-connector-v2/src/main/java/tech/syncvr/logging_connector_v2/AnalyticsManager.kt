package tech.syncvr.logging_connector_v2

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.syncvr.mdm_agent.mdm_common.Constants
import java.lang.IllegalStateException

/**
 * This class is used by other applications to send analytics data to the service,
 * which will, in turn, utilize the MDM's functionality to send analytics data.
 */
class AnalyticsManager(private val applicationContext: Context) {

    private val channel =
        Channel<Bundle>(capacity = Channel.UNLIMITED, onBufferOverflow = BufferOverflow.SUSPEND)
    private var serviceMessenger: Messenger? = null
    private var packageName: String? = null
    private var needBinding = MutableStateFlow(true)
    private var bundleConsumer: Job? = null

    private val exceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "An error occurred", throwable)
        }

    private val coroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            serviceMessenger = Messenger(iBinder)
            bundleConsumer = sendQueuesData()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bundleConsumer?.cancel()
            serviceMessenger = null
            needBinding.update { true }
            coroutineScope.launch {
                flow<Unit> {
                    bindToTheService()
                    if (needBinding.value.not())
                        throw IllegalStateException("Binding not started after initialize")
                }.retry { _ ->
                    delay(RETRY_DELAY)
                    true
                }.collect()
            }
        }
    }

    fun sendAnalyticsEvent(
        eventType: String,
        eventName: String,
        eventValue: String? = null,
        logLevel: LogLevel = LogLevel.INFO
    ) {
        bindToTheService()
        val msgData = Bundle()
        msgData.putInt(Constants.ANALYTICS_LOG_LEVEL_KEY, logLevel.ordinal)
        msgData.putCharSequence(Constants.PACKAGE_NAME_KEY, packageName)
        msgData.putCharSequence(Constants.ANALYTICS_EVENT_TYPE_KEY, eventType)
        msgData.putCharSequence(
            Constants.ANALYTICS_PAYLOAD_KEY,
            Json.encodeToString(AnalyticsEventData(eventName, eventValue))
        )
        coroutineScope.launch {
            addToTheQueue(msgData)
        }
    }

    private suspend fun addToTheQueue(msgData: Bundle) {
        channel.send(msgData)
    }

    private fun bindToTheService() {
        if (needBinding.value.not()) return
        packageName = applicationContext.packageName
        val intent = Intent()
        intent.setClassName(
            BIND_INTENT_PACKAGE_NAME,
            BIND_INTENT_CLASS_NAME
        )

        needBinding.update {
            applicationContext.bindService(
                intent,
                mConnection,
                Context.BIND_AUTO_CREATE
            ).not()
        }
        if (needBinding.value.not()) {
            Log.w(
                TAG, "isBound = false, either missing permission to bind, or couldn't find service!"
            )
        }
    }

    private fun sendQueuesData(): Job {
        return coroutineScope.launch {
            val msg = Message.obtain()
            for (bundle in channel) {
                msg.data = bundle
                try {
                    serviceMessenger?.send(msg)
                    serviceMessenger ?: run {
                        channel.send(bundle)
                        Log.w(TAG, "serviceMessenger is null! saving bundle back in the channel")
                    }
                    Log.d(
                        TAG,
                        "sendAnalytics function called with eventType: ${
                            msg.data.getCharSequence(
                                Constants.ANALYTICS_EVENT_TYPE_KEY
                            )
                        }"
                    )
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error sending message to service", e)
                    channel.send(bundle)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AnalyticsProxy"
        private const val BIND_INTENT_PACKAGE_NAME = "tech.syncvr.mdm_agent"
        private const val BIND_INTENT_CLASS_NAME =
            "tech.syncvr.mdm_agent.logging.LoggingAppInterface"
        private const val RETRY_DELAY = 5_000L

    }
}

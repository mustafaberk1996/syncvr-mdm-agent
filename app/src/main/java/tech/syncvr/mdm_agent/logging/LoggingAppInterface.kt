package tech.syncvr.mdm_agent.logging

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import dagger.hilt.android.AndroidEntryPoint
import tech.syncvr.logging_connector_v2.LogLevel
import tech.syncvr.mdm_agent.logging.AnalyticsLogger.Companion.LogEventType
import tech.syncvr.mdm_agent.mdm_common.Constants
import javax.inject.Inject

@AndroidEntryPoint
class LoggingAppInterface : Service() {

    @Inject
    lateinit var analyticsLogger: AnalyticsLogger

    class LoggingAppInterfaceHandler(looper: Looper, val analyticsLogger: AnalyticsLogger) :
        Handler(looper) {

        companion object {
            private const val TAG = "LoggingAppInterfaceHandler"
        }

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            val appPackageName: String =
                msg.data.getCharSequence(Constants.PACKAGE_NAME_KEY, "").toString()
            val eventType: String =
                msg.data.getCharSequence(Constants.ANALYTICS_EVENT_TYPE_KEY, "").toString()
            val payload: String =
                msg.data.getCharSequence(Constants.ANALYTICS_PAYLOAD_KEY, "").toString()
            val logLevelInt =
                msg.data.getInt(Constants.ANALYTICS_LOG_LEVEL_KEY, -1)
            val logLevel = if (logLevelInt == -1) LogLevel.INFO else LogLevel.values()[logLevelInt]
            analyticsLogger.log(appPackageName, eventType, logLevel, hashMapOf("msg" to payload))
        }
    }

    private val loggingAppInterfaceHandler by lazy { // lazy to make sure hilt has injected analyticsLogger
        LoggingAppInterfaceHandler(Looper.myLooper()!!, analyticsLogger)
    }

    override fun onBind(intent: Intent?): IBinder? {
        val messenger = Messenger(loggingAppInterfaceHandler)
        analyticsLogger.log(
            LogEventType.APP_INTERFACE_EVENT,
            hashMapOf("msg" to "onBind to LoggingAppInterface")
        )
        return messenger.binder
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        analyticsLogger.log(
            LogEventType.APP_INTERFACE_EVENT,
            hashMapOf("msg" to "onRebind to LoggingAppInterface")
        )
    }

    override fun onUnbind(intent: Intent?): Boolean {
        analyticsLogger.log(
            LogEventType.APP_INTERFACE_EVENT,
            hashMapOf("msg" to "onUnbind to LoggingAppInterface")
        )
        return super.onUnbind(intent)
    }

}
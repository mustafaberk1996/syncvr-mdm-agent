package tech.syncvr.mdm_agent.device_identity

import android.app.Service
import android.content.Intent
import android.os.*
import dagger.hilt.android.AndroidEntryPoint
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.mdm_common.Constants.DEVICE_ID
import javax.inject.Inject

@AndroidEntryPoint
class DeviceIdentityAppInterface : Service() {

    @Inject
    lateinit var analyticsLogger: AnalyticsLogger

    class DeviceIdentityAppInterfaceHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            val replyToMessenger = msg.replyTo ?: return
            val replyMessage = Message.obtain().also {
                it.data.putString(DEVICE_ID, DeviceIdentityRepository().getDeviceId())
            }

            replyToMessenger.send(replyMessage)
        }
    }

    private val deviceIdentityAppInterfaceHandler =
        DeviceIdentityAppInterfaceHandler(Looper.myLooper()!!)

    override fun onBind(intent: Intent?): IBinder? {
        val messenger = Messenger(deviceIdentityAppInterfaceHandler)
        analyticsLogger.log(
            AnalyticsLogger.Companion.LogEventType.APP_INTERFACE_EVENT,
            hashMapOf("msg" to "onBind to DeviceIdentityAppInterface")
        )
        return messenger.binder
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        analyticsLogger.log(
            AnalyticsLogger.Companion.LogEventType.APP_INTERFACE_EVENT,
            hashMapOf("msg" to "onRebind to DeviceIdentityAppInterface")
        )
    }

    override fun onUnbind(intent: Intent?): Boolean {
        analyticsLogger.log(
            AnalyticsLogger.Companion.LogEventType.APP_INTERFACE_EVENT,
            hashMapOf("msg" to "onUnbind to DeviceIdentityAppInterface")
        )
        return super.onUnbind(intent)
    }

}
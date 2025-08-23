package tech.syncvr.mdm_agent.repositories.play_area

import android.app.Service
import android.content.Intent
import android.os.*
import dagger.hilt.android.AndroidEntryPoint
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.mdm_common.Constants
import javax.inject.Inject

@AndroidEntryPoint
class PlayAreaConfigAppInterface : Service() {

    @Inject
    lateinit var analyticsLogger: AnalyticsLogger

    companion object {
        private const val TAG = "PlayAreaConfigAppInterface"
    }

    @Inject
    lateinit var playAreaRepository: IPlayAreaRepository

    class PlayAreaConfigAppInterfaceHandler(
        looper: Looper,
        private val playAreaRepository: IPlayAreaRepository,
        private val analyticsLogger: AnalyticsLogger
    ) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            val replyToMessenger = msg.replyTo ?: return
            val setting: String =
                msg.data.getCharSequence(Constants.AUTO_PLAY_AREA_KEY, "").toString()

            if (isValidSetting(setting)) {
                analyticsLogger.log(
                    AnalyticsLogger.Companion.LogEventType.APP_INTERFACE_EVENT,
                    hashMapOf("msg" to "Received PlayArea setting: $setting in $TAG")
                )
                when (setting) {
                    Constants.AUTO_PLAY_AREA_NONE -> playAreaRepository.clearPlayAreaConfiguration()
                    Constants.AUTO_PLAY_AREA_STANDING -> playAreaRepository.setPlayAreaStanding()
                    Constants.AUTO_PLAY_AREA_SITTING -> playAreaRepository.setPlayAreaSitting()
                }
            }

            val reply = Message.obtain().also {
                it.data.putString(
                    Constants.AUTO_PLAY_AREA_KEY,
                    playAreaRepository.getPlayAreaSetting()
                )
            }
            replyToMessenger.send(reply)
        }

        private fun isValidSetting(setting: String): Boolean {
            return arrayOf(
                Constants.AUTO_PLAY_AREA_NONE,
                Constants.AUTO_PLAY_AREA_SITTING,
                Constants.AUTO_PLAY_AREA_STANDING
            ).contains(setting)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        val playAreaConfigAppInterfaceHandler =
            PlayAreaConfigAppInterfaceHandler(
                Looper.myLooper()!!,
                playAreaRepository,
                analyticsLogger
            )
        val messenger = Messenger(playAreaConfigAppInterfaceHandler)
        analyticsLogger.log(
            AnalyticsLogger.Companion.LogEventType.APP_INTERFACE_EVENT,
            hashMapOf("msg" to "onBind to $TAG")
        )

        return messenger.binder
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        analyticsLogger.log(
            AnalyticsLogger.Companion.LogEventType.APP_INTERFACE_EVENT,
            hashMapOf("msg" to "onRebind to $TAG")
        )
    }

    override fun onUnbind(intent: Intent?): Boolean {
        analyticsLogger.log(
            AnalyticsLogger.Companion.LogEventType.APP_INTERFACE_EVENT,
            hashMapOf("msg" to "onUnbind to $TAG")
        )
        return super.onUnbind(intent)
    }
}
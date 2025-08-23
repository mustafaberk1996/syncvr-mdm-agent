package tech.syncvr.mdm_agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import tech.syncvr.mdm_agent.repositories.play_area.IPlayAreaRepository
import javax.inject.Inject

@AndroidEntryPoint
class PlayAreaConfigReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PlayAreaConfigReceiver"
    }

    @Inject
    lateinit var playAreaRepository: IPlayAreaRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "tech.syncvr.intent.AUTO_CONFIG_PLAY_AREA_SITTING" -> {
                playAreaRepository.setPlayAreaSitting()
            }
            "tech.syncvr.intent.AUTO_CONFIG_PLAY_AREA_STANDING" -> {
                playAreaRepository.setPlayAreaStanding()
            }
            "tech.syncvr.intent.AUTO_CONFIG_PLAY_AREA_OFF" -> {
                playAreaRepository.clearPlayAreaConfiguration()
            }
        }
    }
}
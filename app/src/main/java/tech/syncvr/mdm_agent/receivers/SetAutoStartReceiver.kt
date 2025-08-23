package tech.syncvr.mdm_agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager
import javax.inject.Inject

@AndroidEntryPoint
class SetAutoStartReceiver() : BroadcastReceiver() {

    companion object {
        private const val TAG = "SetAutoStartReceiver"
        private const val PACKAGE_NAME_KEY = "autoStartPackageName"
    }

    @Inject
    lateinit var autoStartManager: AutoStartManager

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            "tech.syncvr.intent.GET_AUTO_START" -> {
                autoStartManager.getAutoStartPackage()
            }
            "tech.syncvr.intent.SET_AUTO_START" -> {
                val packageName = intent.extras?.getString(PACKAGE_NAME_KEY) ?: return
                autoStartManager.setAutoStart(packageName, listOf())
            }
            "tech.syncvr.intent.CLEAR_AUTO_START" -> {
                autoStartManager.clearAutoStartPackage()
            }
            "tech.syncvr.intent.AUTO_START_ON" -> {
                Log.d(TAG, "RECEIVED AUTO_START_ON!")
                autoStartManager.setAutoStart(
                    "tech.syncvr.mdm_agent",
                    listOf("tech.syncvr.native_spectator")
                )
            }
            "tech.syncvr.intent.AUTO_START_OFF" -> {
                Log.d(TAG, "RECEIVED AUTO_START_OFF!")
                autoStartManager.clearAutoStartPackage()
            }
        }
    }

}
package tech.syncvr.mdm_agent.device_management.configuration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration

class AdbConfigurationRemoteSource(private val context: Context) : IConfigurationRemoteSource {

    companion object {
        private const val TAG = "AdbConfigurationRemoteSource"
        private const val SET_CONFIGURATION_INTENT = "tech.syncvr.intent.SET_CONFIGURATION"
        private const val QUICK_SET_AUTO_START_INTENT = "tech.syncvr.intent.QUICK_SET_AUTO_START"
        private const val QUICK_CLEAR_AUTO_START_INTENT =
            "tech.syncvr.intent.QUICK_CLEAR_AUTO_START"
        private const val PACKAGE_NAME_KEY = "autoStartPackageName"

    }

    private var configuration: Configuration? = null

    private val adbConfigurationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "adbConfigurationReceiver did receive an Intent: ${intent.action}")
            when (intent.action) {
                SET_CONFIGURATION_INTENT -> {
                    Log.d(TAG, "SET_CONFIGURATION_INTENT not yet implemented!")
                }
                QUICK_SET_AUTO_START_INTENT -> {
                    val packageName = intent.extras?.getString(PACKAGE_NAME_KEY)
                    if (packageName == null) {
                        Log.d(TAG, "Missing Package Name to set in Auto Start!")
                        return
                    } else {
                        configuration = configuration?.copy(autoStart = packageName)
                            ?: Configuration(listOf(), listOf(), packageName)
                    }
                }
                QUICK_CLEAR_AUTO_START_INTENT -> {
                    configuration = configuration?.copy(autoStart = null)
                        ?: Configuration(listOf(), listOf(), null)
                }
            }
        }
    }

    init {
        Log.d(TAG, "Using AdbConfigurationRemoteSource!")

        IntentFilter().also {
            it.addAction(SET_CONFIGURATION_INTENT)
            it.addAction(QUICK_SET_AUTO_START_INTENT)
            it.addAction(QUICK_CLEAR_AUTO_START_INTENT)
            context.registerReceiver(adbConfigurationReceiver, it)
        }
    }

    override suspend fun getConfiguration(): Configuration? {
        Log.d(TAG, "Current ADB configuration autostart = ${configuration?.autoStart}")
        return configuration
    }
}

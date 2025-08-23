package tech.syncvr.mdm_agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MY_PACKAGE_REPLACED
import android.util.Log
import tech.syncvr.mdm_agent.BuildConfig

class PackageReplacedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PackageReplacedReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_MY_PACKAGE_REPLACED) {
            //TODO: on tablet if auto-start is one, relaunch the gallery activity!
            Log.d(TAG, "App was updated! New version: ${BuildConfig.VERSION_CODE}")
        }
    }
}
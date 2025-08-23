package tech.syncvr.mdm_agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StartOnceReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG: String = "StartOnceReceiver"
    }

    override fun onReceive(context: Context, p1: Intent?) {
        if (p1?.action == "tech.syncvr.intent.START_ONCE") {
            Log.d(TAG, "tech.syncvr.intent.START_ONCE")
        } else {
            return
        }
    }
}

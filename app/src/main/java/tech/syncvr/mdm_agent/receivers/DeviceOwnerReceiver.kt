package tech.syncvr.mdm_agent.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DeviceOwnerReceiver : DeviceAdminReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("DeviceOwnerReceiver", "Receiving intent $intent")
    }
}
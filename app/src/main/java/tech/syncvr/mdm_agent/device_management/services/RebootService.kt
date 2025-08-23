package tech.syncvr.mdm_agent.device_management.services

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.syncvr.mdm_agent.receivers.DeviceOwnerReceiver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RebootService @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val TAG = "RebootService"
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun tryReboot(): Boolean {
        if (!powerManager.isInteractive) {
            Log.d(TAG, "Device is not interactive, do reboot right now!")
            dpm.reboot(ComponentName(context, DeviceOwnerReceiver::class.java))
            return true
        }

        Log.d(TAG, "Device is interactive, not rebooting now!")
        return false
    }
}
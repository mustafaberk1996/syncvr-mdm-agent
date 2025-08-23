package tech.syncvr.mdm_agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import tech.syncvr.mdm_agent.repositories.auto_start.oculus.helpers.OculusSystemUtils
import kotlin.reflect.full.functions

// activate Oculus actions by issuing:
// adb shell am broadcast -a tech.syncvr.intent.OCULUS_ACTION_CALLED -n tech.syncvr.mdm_agent/tech.syncvr.mdm_agent.receivers.OculusActionReceiver --es function_name \"launchGuardianSetup\"
// adb shell am broadcast -a tech.syncvr.intent.OCULUS_ACTION_CALLED -n tech.syncvr.mdm_agent/tech.syncvr.mdm_agent.receivers.OculusActionReceiver --es function_name \"launchCastingDialog\"
// adb shell am broadcast -a tech.syncvr.intent.OCULUS_ACTION_CALLED -n tech.syncvr.mdm_agent/tech.syncvr.mdm_agent.receivers.OculusActionReceiver --es function_name \"launchWifiSettings\"
// adb shell am broadcast -a tech.syncvr.intent.OCULUS_ACTION_CALLED -n tech.syncvr.mdm_agent/tech.syncvr.mdm_agent.receivers.OculusActionReceiver --es function_name \"launchBluetoothSettings\"
// adb shell am broadcast -a tech.syncvr.intent.OCULUS_ACTION_CALLED -n tech.syncvr.mdm_agent/tech.syncvr.mdm_agent.receivers.OculusActionReceiver --es function_name \"launchDialogNone\"

//TODO: Only use this BroadcastReceiver on Oculus Devices.
class OculusActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "OculusActionReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "Received broadcast!")
        if (intent == null || intent.extras == null) {
            return
        }
        val extras: Bundle = intent.extras!!
        val functionName = extras.getString("function_name")
        Log.d(TAG, "Received broadcast: functionName: $functionName")

        val functionToCall =
            OculusSystemUtils::class.functions.find { it.name == functionName }
        try {
            functionToCall?.call(OculusSystemUtils::class.objectInstance, context)
        } catch (th: Throwable) {
            Log.d(TAG, "Couldn't find method to call!")
        }
    }
}

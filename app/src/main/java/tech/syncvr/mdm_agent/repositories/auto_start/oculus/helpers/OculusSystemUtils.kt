package tech.syncvr.mdm_agent.repositories.auto_start.oculus.helpers

import android.content.Context
import android.net.Uri
import tech.syncvr.mdm_agent.repositories.auto_start.oculus.models.AppWithClass

object OculusSystemUtils {

    private fun launchWithUri(context: Context, uri: Uri) {
        AppWithClass("com.oculus.vrshell", "com.oculus.vrshell.MainActivity").also {
            it.launchWithUri(context, uri)
        }
    }

    fun closeUniversalMenuPanel(context: Context) {
        launchWithUri(context, Uri.parse("systemux://aui-tablet-none"))
    }

    fun launchOculusBrowser(context: Context) {
        launchWithUri(context, Uri.parse("systemux://browser"))
    }

    fun launchCastingDialog(context: Context) {
        launchWithUri(context, Uri.parse("systemux://dialog/local-stream-start-from-device"))
    }

    fun launchGuardianSetup(context: Context) {
        launchWithUri(context, Uri.parse("systemux://guardian/adjust-setup"))
    }

    fun launchWifiSettings(context: Context) {
        launchWithUri(context, Uri.parse("systemux://wifi"))
    }

    fun launchBluetoothSettings(context: Context) {
        launchWithUri(context, Uri.parse("systemux://bluetooth"))
    }

    fun launchDialogNone(context: Context) {
        launchWithUri(context, Uri.parse("systemux://dialog/none"))
    }
}
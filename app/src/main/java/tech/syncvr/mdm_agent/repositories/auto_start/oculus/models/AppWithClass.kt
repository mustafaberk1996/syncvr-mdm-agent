package tech.syncvr.mdm_agent.repositories.auto_start.oculus.models

import android.app.usage.UsageEvents
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

data class AppWithClass(val packageName: String = "", val className: String = "") {

    constructor(event: UsageEvents.Event) : this() {
        event.packageName
        event.className
    }

    fun equalsPackage(app: AppWithClass): Boolean {
        return this.packageName == app.packageName
    }

    fun toComponent(): ComponentName {
        return ComponentName(packageName, className)
    }

    val isOculusSystemApp: Boolean
        get() = packageName in listOf(
            ANDROID_SYSTEM_UI_PACKAGE,
            OCULUS_GUARDIAN_PACKAGE,
            OCULUS_CLEAR_ACTIVITY_PACKAGE,
            OCULUS_SYSTEMUX_PACKAGE,
            OCULUS_VRSHELL_PACKAGE,
            OCULUS_VRSHELLENV_PACKAGE
        )

    fun launch(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.also {
            Log.d(
                TAG,
                "About to launch: ${launchIntent.`package`} - ${launchIntent.component?.packageName} - ${launchIntent.component?.className}"
            )
            // only do this for pure android home app
            it.addCategory(Intent.CATEGORY_LAUNCHER)
            it.action = Intent.ACTION_MAIN
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(launchIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Cant launch App: $packageName: ${e.message}", e)
        }
    }

    fun launchWithUri(context: Context, uri: Uri) {
        val intent = Intent("android.intent.action.VIEW").also {
            it.setClassName(packageName, className)
            it.data = uri
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Cant launch App: $packageName: ${e.message}", e)
        }
    }


    companion object {
        private const val TAG = "AppWithClass"

        const val OCULUS_CLEAR_ACTIVITY_PACKAGE = "com.oculus.os.clearactivity"
        const val OCULUS_GUARDIAN_PACKAGE = "com.oculus.guardian"
        const val OCULUS_GUARDIAN_CLASS = "com.oculus.vrguardianservice.PTODActivity"
        const val OCULUS_SYSTEMUX_PACKAGE = "com.oculus.systemux"
        const val OCULUS_VRSHELLENV_CLASS = "com.oculus.shellenv.ShellEnvActivity"
        const val OCULUS_VRSHELLENV_PACKAGE = "com.oculus.shellenv"
        const val OCULUS_VRSHELL_CLASS = "com.oculus.vrshell.MainActivity"
        const val OCULUS_VRSHELL_PACKAGE = "com.oculus.vrshell"

        const val ANDROID_SYSTEM_UI_PACKAGE = "com.android.systemui"

        //TODO: removed check for OculusRuntimeVersion. Is that ok?
        val OculusLauncher = AppWithClass(OCULUS_VRSHELL_PACKAGE, OCULUS_VRSHELL_CLASS)
        val OculusGuardian = AppWithClass(OCULUS_GUARDIAN_PACKAGE, OCULUS_GUARDIAN_CLASS)
    }
}
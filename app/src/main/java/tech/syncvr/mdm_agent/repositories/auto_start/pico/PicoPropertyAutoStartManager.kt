package tech.syncvr.mdm_agent.repositories.auto_start.pico

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import tech.syncvr.mdm_agent.device_management.services.ShellCommandService
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager
import java.nio.file.Paths

class PicoPropertyAutoStartManager(
    context: Context, private val analyticsLogger: AnalyticsLogger
) : AutoStartManager(context) {

    companion object {
        private const val TAG = "PicoPropertyAutoStartRepository"
    }

    init {
        val storagePath = Environment.getExternalStorageDirectory().absolutePath
        val configFile = Paths.get(storagePath, PicoConfigAutoStartManager.CONFIG_FILE).toFile()
        if (configFile.exists()) {
            configFile.delete().also {
                Log.d(TAG, "Deleted config.txt file: $it")
            }
        }
    }

    override fun getAutoStartPackage(): String? {
        val result = ShellCommandService().runCommand(
            arrayOf(
                "getprop",
                "persist.pxr.force.home"
            )
        )

        // if this fails for whatever reason, no auto-start
        if (!result.success) {
            return null
        }

        // empty string means no auto-start setting
        if (result.stdOut == "\"\"") {
            return null
        }

        val splits = result.stdOut.split(",")

        // correct auto-start setting should have two parts delimited by ','
        if (splits.size != 2) {
            Log.d(
                TAG,
                "AutoStart setting is not set or not formatted correctly \"${result.stdOut}\""
            )
            return null
        }
        val autoStartPackageName = splits[0]
        val autoStartActivity = splits[1]

        // if the configured activity does not match the Main Activity of this package, auto-start is not configured
        val mainActivity = getMainActivity(autoStartPackageName)
        if (autoStartActivity != mainActivity) {
            Log.e(
                TAG,
                "Wrong Activity configured for autostart app: $autoStartActivity vs $mainActivity"
            )
            return null
        }

        // if we get here, this package is properly auto-start configured
        return autoStartPackageName
    }

    override fun setAutoStart(
        autoStartPackageName: String,
        allowedPackages: List<String>
    ): Boolean {
        val currentAutoStart = getAutoStartPackage()
        return if (currentAutoStart != autoStartPackageName) {
            analyticsLogger.logMsg(
                "SetAutoStart",
                "Current autostart: $currentAutoStart, new autostart: $autoStartPackageName, update = TRUE"
            )
            val result = ShellCommandService().runCommandPrintResult(
                arrayOf(
                    "setprop",
                    "persist.pxr.force.home",
                    "$autoStartPackageName,${getMainActivity(autoStartPackageName)}"
                )
            )
            result.success
        } else {
            false
        }
    }

    override fun clearAutoStartPackage(): Boolean {
        val currentAutoStart = getAutoStartPackage()
        return if (currentAutoStart != null) {
            analyticsLogger.logMsg(
                "SetAutoStart",
                "Current autostart: $currentAutoStart, clear = true"
            )
            val result = ShellCommandService().runCommand(
                arrayOf(
                    "setprop",
                    "persist.pxr.force.home",
                    "\"\""
                )
            )
            result.success
        } else {
            false
        }
    }

    override fun requiresReboot(): Boolean {
        return false
    }

    private fun getMainActivity(packageName: String): String {
        val intent = Intent(Intent.ACTION_MAIN).also {
            it.setPackage(packageName)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            ?: return "com.unity3d.player.UnityPlayerNativeActivityPico"

        return resolveInfo.activityInfo.name
    }
}
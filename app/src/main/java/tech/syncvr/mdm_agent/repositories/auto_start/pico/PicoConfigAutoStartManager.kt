package tech.syncvr.mdm_agent.repositories.auto_start.pico

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.pvr.tobservice.ToBServiceHelper
import com.pvr.tobservice.enums.PBS_SwitchEnum
import com.pvr.tobservice.enums.PBS_SystemFunctionSwitchEnum
import tech.syncvr.mdm_agent.BuildConfig
import tech.syncvr.mdm_agent.MDMAgentModule
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager
import tech.syncvr.mdm_agent.utils.Logger
import java.io.File
import java.nio.file.Paths

/**
 * Unfortunately this class contains some magic. Pico seems to have adopted a format that looks
 * like YAML, but isn't quite. We can only hope that they don't change this format at some point.
 * For now, just read and write the file exactly as it is specified by their documentation, as that
 * seems to work.
 *
 * Format:
 * open_guide:1
 * ------
 * home_pkg:tech.syncvr.picogallery
 * ------
 *
 * Additionally, actually making this config file take effect requires a device reboot.
 */
class PicoConfigAutoStartManager(
    context: Context,
    private val logger: Logger,
    private val analyticsLogger: AnalyticsLogger
) : AutoStartManager(context) {

    companion object {
        private const val TAG = "ConfigAutoStartRepository"
        const val CONFIG_FILE = "config.txt"
        private const val OPEN_GUIDE_PICO_KEY = "open_guide"
        private const val AUTO_START_PICO_KEY = "home_pkg"
        private const val CONFIG_FILE_SEPARATOR = "------"
    }

    private var rebootRequired = false

    override fun getAutoStartPackage(): String? {
        val configLines = readConfigFile()
        if (configLines.size >= 3) {
            val splits = configLines[2].split(':')
            if (splits.size == 2 && splits[0] == AUTO_START_PICO_KEY) {
                return if (splits[1].isBlank()) {
                    null
                } else {
                    splits[1]
                }
            }
        }
        return null
    }

    override fun setAutoStart(
        autoStartPackageName: String, allowedPackages: List<String>
    ): Boolean {
        val currentAutoStart = getAutoStartPackage()
        return (currentAutoStart != autoStartPackageName).also {
            analyticsLogger.logMsg(
                "SetAutoStart",
                "Current autostart: $currentAutoStart, new autostart: $autoStartPackageName, update = $it"
            )
            writeConfigFile(true, autoStartPackageName)
            rebootRequired = true
            when (Build.MODEL) {
                MDMAgentModule.MODEL_PICO_4_ULTRA_A, MDMAgentModule.MODEL_PICO_4_ULTRA_B, MDMAgentModule.MODEL_PICO_4_ULTRA_C, MDMAgentModule.MODEL_PICO_4_ULTRA_D, MDMAgentModule.MODEL_PICO_4_A, MDMAgentModule.MODEL_PICO_4_B, MDMAgentModule.MODEL_PICO_4_C, MDMAgentModule.MODEL_PICO_G3_A, MDMAgentModule.MODEL_PICO_G3_B, MDMAgentModule.MODEL_PICO_G3_C -> {
                    showPico4NavigationMenu(false)
                }
            }
        }
    }

    override fun clearAutoStartPackage(): Boolean {

        val currentAutoStart = getAutoStartPackage()
        return (currentAutoStart != null).also {
            // TODO: the .also happens anyway, but the 'it' is only used in the logmessage. It should also determine whether we write to the config file. Now that just happens every time, as well as setting the reboot required to true. I don't understand why that does not lead to boot-loops yet.
            analyticsLogger.logMsg(
                "SetAutoStart", "Current autostart: $currentAutoStart, clear = $it"
            )
            writeConfigFile(true, "")
            rebootRequired = true
            when (Build.MODEL) {
                MDMAgentModule.MODEL_PICO_4_ULTRA_A, MDMAgentModule.MODEL_PICO_4_ULTRA_B, MDMAgentModule.MODEL_PICO_4_ULTRA_C, MDMAgentModule.MODEL_PICO_4_ULTRA_D, MDMAgentModule.MODEL_PICO_4_A, MDMAgentModule.MODEL_PICO_4_B, MDMAgentModule.MODEL_PICO_4_C, MDMAgentModule.MODEL_PICO_G3_A, MDMAgentModule.MODEL_PICO_G3_B, MDMAgentModule.MODEL_PICO_G3_C -> {
                    showPico4NavigationMenu(true)
                }
            }
        }
    }

    private fun writeConfigFile(openGuide: Boolean, autoStartOption: String) = runCatching {
        val file = getConfigFile()
        file.printWriter().use { out ->
            out.println("$OPEN_GUIDE_PICO_KEY:${if (openGuide) 1 else 0}")
            out.println(CONFIG_FILE_SEPARATOR)
            out.println("$AUTO_START_PICO_KEY:$autoStartOption")
            out.println(CONFIG_FILE_SEPARATOR)
        }
    }.onFailure { ex ->
        analyticsLogger.logErrorMsg(
            "AutoStart",
            "Couldn't write config (likely missing MANAGE_EXTERNAL_STORAGE): ${ex.message}"
        )
    }

    private fun showPico4NavigationMenu(value: Boolean): Boolean {
        val shouldShowPico4Navigation = if (value) PBS_SwitchEnum.S_ON else PBS_SwitchEnum.S_OFF
        try {
            logger.d(TAG, "before showPicoNavigationMenu($value)")
            return ToBServiceHelper.getInstance().serviceBinder?.run {
                // the following is taken from the pui-code of the serviceBinder, to guarantee the binder is still ok. The function checks is and restarts it if not, but the returned binder may still be corrupt then
                if (!asBinder().isBinderAlive || !asBinder().pingBinder()) return@run false
                pbsSwitchSystemFunction(
                    PBS_SystemFunctionSwitchEnum.SFS_NAVGATION_SWITCH, shouldShowPico4Navigation, 0
                )
                logger.d(TAG, "after showPicoNavigationMenu($value)")
                return@run true
            } ?: return false
        } catch (throwable: Throwable) {
            if (BuildConfig.DEBUG) {
                throw throwable
            } else {
                logger.e(
                    TAG, "error adding or removing Pico's navigation menu", throwable
                )
            }
            return false
        }
    }

    override fun requiresReboot(): Boolean {
        return rebootRequired
    }

    private fun readConfigFile(): List<String> = runCatching {
        getConfigFile().takeIf { it.exists() && it.isFile }
            ?.readLines()
            ?: emptyList()
    }.onFailure { ex ->
        analyticsLogger.logErrorMsg(
            "AutoStart",
            "Couldn't read config (likely missing MANAGE_EXTERNAL_STORAGE): ${ex.message}"
        )
    }.getOrDefault(emptyList())


    private fun getConfigFile(): File {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val dir = File(root, "pre_resource/feature")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, CONFIG_FILE)
    }
}

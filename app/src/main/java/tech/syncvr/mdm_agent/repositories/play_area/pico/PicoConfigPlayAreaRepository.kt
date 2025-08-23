package tech.syncvr.mdm_agent.repositories.play_area.pico

import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.localcache.ILocalCacheSource
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.mdm_common.Constants
import tech.syncvr.mdm_agent.mdm_common.Constants.AUTO_PLAY_AREA_NONE
import tech.syncvr.mdm_agent.repositories.play_area.IPlayAreaRepository
import java.io.File
import java.nio.file.Paths
import javax.inject.Inject

class PicoConfigPlayAreaRepository @Inject constructor(
    private val localCacheSource: ILocalCacheSource,
    private val analyticsLogger: AnalyticsLogger
) : IPlayAreaRepository {

    companion object {
        private const val TAG = "ConfigPlayAreaRepository"
    }

    override fun onAppCreated() {
        when (getPlayAreaSetting()) {
            "" -> {
                // no explicit setting for play area yet, default to standing
                Log.i(TAG, "no explicit setting for play area yet, default to standing")
                setPlayAreaStanding()
            }

            Constants.AUTO_PLAY_AREA_SITTING -> {
                // to ensure that we overwrite a potential obsolete config file with a current one
                Log.i(TAG, "play area sitting")
                setPlayAreaSitting()
            }

            Constants.AUTO_PLAY_AREA_STANDING -> {
                // to ensure that we overwrite a potential obsolete config file with a current one
                Log.i(TAG, "play area standing")
                setPlayAreaStanding()
            }

            AUTO_PLAY_AREA_NONE -> {
                Log.i(TAG, "play area none")
                clearPlayAreaConfiguration()
            }
        }

        clearOldPlayAreaConfiguration()
    }

    override fun getPlayAreaSetting(): String {
        return localCacheSource.getPlayAreaConfig() ?: ""
    }

    override fun setPlayAreaStanding() {

        val playAreaConfigFolder = getPlayAreaConfigFolder()
        if (!playAreaConfigFolder.exists()) {
            if (!playAreaConfigFolder.mkdirs()) {
                Log.e(TAG, "NOT ABLE TO CREATE ${playAreaConfigFolder.absolutePath}")
                return
            }
        }

        val configFile = getPlayAreaConfigFile()

        analyticsLogger.log("SetPlayArea", hashMapOf())

        configFile.printWriter().use { f ->
            f.println("AutoDelete 0")
            f.println("ForceTOC 0")
            f.println("ForceNOUI 1")
            f.println("ForceReset6Dof 0")
            f.println("ShowCloseTrackingBtn 0")
            f.println("height 1.80")
            f.println("#circle_r 1")
            f.println("#circle_pcount 36")
        }

        localCacheSource.setPlayAreaConfig(Constants.AUTO_PLAY_AREA_STANDING)
    }

    override fun setPlayAreaSitting() {
        setPlayAreaStanding()
    }

    override fun clearPlayAreaConfiguration() {
        val configFile = getPlayAreaConfigFile()
        if (configFile.exists()) {
            configFile.delete().let { success ->
                if (!success) {
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                        "Failed to delete Play Area Configuration File!"
                    )
                }
            }
        }

        localCacheSource.setPlayAreaConfig(Constants.AUTO_PLAY_AREA_NONE)
    }

    private fun getPlayAreaConfigFolder(): File {
        val storagePath = Environment.getExternalStorageDirectory().absolutePath
        return Paths.get(storagePath, "Android", "data", "com.pvr.seethrough.setting", "files")
            .toFile()
    }

    private fun getPlayAreaConfigFile(): File {
        return Paths.get(getPlayAreaConfigFolder().path, "Config1.txt").toFile()
    }

    private fun clearOldPlayAreaConfiguration() {
        CoroutineScope(Dispatchers.IO).launch {
            val storagePath = Environment.getExternalStorageDirectory().absolutePath
            val oldConfigFolder = Paths.get(storagePath, "SeethroughSetting", "Config").toFile()
            oldConfigFolder.listFiles()?.let {
                Log.d(TAG, "Found ${it.size} obsolete config files!")
            }
            oldConfigFolder.listFiles()?.forEach { file ->
                if (file.name.contains("Config")) {
                    file.delete().let { success ->
                        if (success) {
                            Log.d(TAG, "Deleted old Play Area Configuration File!")
                        } else {
                            analyticsLogger.logErrorMsg(
                                AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                                "Failed to delete old Play Area Configuration File!"
                            )
                        }
                    }
                }
            }
        }

    }
}
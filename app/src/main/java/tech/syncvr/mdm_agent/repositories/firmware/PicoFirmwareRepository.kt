package tech.syncvr.mdm_agent.repositories.firmware

import android.content.Context
import android.os.Build
import android.util.Log
import com.pvr.tobservice.ToBServiceHelper
import com.pvr.tobservice.enums.PBS_SystemInfoEnum
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_A
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_B
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_C
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_ULTRA_A
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_ULTRA_B
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_ULTRA_C
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_4_ULTRA_D
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_G3_A
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_G3_B
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_G3_C
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_NEO_3
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import javax.inject.Inject

class PicoFirmwareRepository @Inject constructor(
    context: Context,
    private val analyticsLogger: AnalyticsLogger
) : FirmwareRepository() {

    companion object {
        private const val TAG = "PicoFirmwareRepository"
    }

    private var isBound = false

    init {
        ToBServiceHelper.getInstance().bindTobService(context) { bound ->
            isBound = bound
            if (bound) {
                Log.i(TAG, "Bound to TobService")
            } else {
                Log.e(TAG, "Not bound to TobService!")
            }
        }
    }


    override fun getFirmwareVersion(): Version {
        val versionString = when (Build.MODEL) {
            MODEL_PICO_NEO_3, MODEL_PICO_4_ULTRA_A, MODEL_PICO_4_ULTRA_B, MODEL_PICO_4_ULTRA_C, MODEL_PICO_4_ULTRA_D, MODEL_PICO_4_A, MODEL_PICO_4_B, MODEL_PICO_4_C, MODEL_PICO_G3_A, MODEL_PICO_G3_B, MODEL_PICO_G3_C -> {
                if (isBound) {
                    ToBServiceHelper.getInstance().serviceBinder.pbsStateGetDeviceInfo(
                        PBS_SystemInfoEnum.PUI_VERSION, 0
                    )
                } else {
                    Build.DISPLAY
                }
            }

            else -> {
                Build.DISPLAY
            }
        }

        return try {
            Version.parse(versionString, strict = false)
        } catch (e: VersionFormatException) {
            analyticsLogger.logMsg(
                AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                "Failed to parse version string: $versionString. Will try to strip excess element."
            )
            return try {
                Version.parse(stripExcessElement(versionString), strict = false)
            } catch (e: VersionFormatException) {
                analyticsLogger.logErrorMsg(
                    AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                    "Failed to parse version string: $versionString."
                )
                Version(0, 0, 0)
            }
        }
    }


    private fun stripExcessElement(versionString: String): String {
        return versionString.split('.').take(3).joinToString(".")
    }
}

package tech.syncvr.mdm_agent.device_management.system_settings.pico

import android.content.Context
import android.util.Log
import com.pvr.tobservice.ToBServiceHelper
import com.pvr.tobservice.enums.PBS_HomeEventEnum
import com.pvr.tobservice.enums.PBS_HomeFunctionEnum
import com.pvr.tobservice.enums.PBS_ScreenOffDelayTimeEnum
import com.pvr.tobservice.enums.PBS_SleepDelayTimeEnum
import com.pvr.tobservice.enums.PBS_SwitchEnum
import com.pvr.tobservice.enums.PBS_SystemFunctionSwitchEnum
import com.pvr.tobservice.interfaces.IBoolCallback
import com.pvr.tobservice.interfaces.IIntCallback
import tech.syncvr.mdm_agent.device_management.system_settings.ISystemSettingsService
import tech.syncvr.mdm_agent.logging.AnalyticsLogger

open class ToBServiceSystemSettingsService(
    private val context: Context,
    private val logger: AnalyticsLogger,
    private val is3Dof: Boolean
) : ISystemSettingsService {

    companion object {
        private const val TAG = "ToBServiceSystemSettingsService"
    }

    //TODO: not sure if binding to the ToBService twice will screw things up.
    init {
        ToBServiceHelper.getInstance().bindTobService(context) { bound ->
            if (bound) {
                Log.i(TAG, "Bound to TobService")
            } else {
                Log.e(TAG, "Not bound to TobService!")
            }
        }
    }

    override fun setDefaultSystemSettings() {
        ToBServiceHelper.getInstance().bindTobService(context) { status ->
            if (status) {
                Log.i(TAG, "Bound to ToBService, now set Home key!")

                setHomeKeys()
                setSleepDelay() // this needs to come before ScreenOffDelay, because ScreenOffDelay can't be smaller than SleepDelay
                setScreenOffDelay()
                setWifiOnInSleepMode()
                // if the exit app dialog is off, and auto-start is off, pressing HOME button can lead to very weird behavior in the PICO Home Environment.
                setEnableExitAppDialog()
                // we can safely kill background apps. it seems the auto-start app is kept alive in the background
                setEnableKillBackgroundVrApp()

                if (is3Dof) {
                    setGlobalCalibrationOn()
                } else {
                    setMotionTrackingOn()
                }
            } else {
                logger.logErrorMsg(
                    AnalyticsLogger.Companion.LogEventType.SYSTEM_SETTINGS_EVENT,
                    "$TAG - Failed to bind to ToBService!"
                )
            }
        }
    }

    override fun setAcceptFirmwareUpdates() {
        ToBServiceHelper.getInstance().serviceBinder.pbsSwitchSystemFunction(
            PBS_SystemFunctionSwitchEnum.SFS_SYSTEM_UPDATE_OTA,
            PBS_SwitchEnum.S_ON,
            0
        )

        ToBServiceHelper.getInstance().serviceBinder.pbsSwitchSystemFunction(
            PBS_SystemFunctionSwitchEnum.SFS_SYSTEM_UPDATE,
            PBS_SwitchEnum.S_ON,
            0
        )
    }

    override fun setDisableFirmwareUpdates() {
        ToBServiceHelper.getInstance().serviceBinder.pbsSwitchSystemFunction(
            PBS_SystemFunctionSwitchEnum.SFS_SYSTEM_UPDATE_OTA,
            PBS_SwitchEnum.S_OFF,
            0
        )

        ToBServiceHelper.getInstance().serviceBinder.pbsSwitchSystemFunction(
            PBS_SystemFunctionSwitchEnum.SFS_SYSTEM_UPDATE,
            PBS_SwitchEnum.S_OFF,
            0
        )
    }

    private fun setEnableExitAppDialog() {
        ToBServiceHelper.getInstance().serviceBinder.pbsSwitchSystemFunction(
            PBS_SystemFunctionSwitchEnum.SFS_BASIC_SETTING_SHOW_APP_QUIT_CONFIRM_DIALOG,
            PBS_SwitchEnum.S_ON,
            0
        )
    }

    private fun setEnableKillBackgroundVrApp() {
        ToBServiceHelper.getInstance().serviceBinder.pbsSwitchSystemFunction(
            PBS_SystemFunctionSwitchEnum.SFS_BASIC_SETTING_KILL_BACKGROUND_VR_APP,
            PBS_SwitchEnum.S_ON,
            0
        )
    }

    private fun setGlobalCalibrationOn() {
        ToBServiceHelper.getInstance().serviceBinder.pbsSwitchSystemFunction(
            PBS_SystemFunctionSwitchEnum.SFS_GLOBAL_CALIBRATION,
            PBS_SwitchEnum.S_ON,
            0
        )
    }

    private fun setMotionTrackingOn() {
        ToBServiceHelper.getInstance().serviceBinder.pbsSwitchSystemFunction(
            PBS_SystemFunctionSwitchEnum.SFS_SIX_DOF_SWITCH,
            PBS_SwitchEnum.S_ON,
            0
        )
    }

    private fun setWifiOnInSleepMode() {
        ToBServiceHelper.getInstance().serviceBinder.pbsSwitchSystemFunction(
            PBS_SystemFunctionSwitchEnum.SFS_POWER_CTRL_WIFI_ENABLE,
            PBS_SwitchEnum.S_ON,
            0
        )
    }

    private fun setSleepDelay() {
        ToBServiceHelper.getInstance().serviceBinder.pbsPropertySetSleepDelay(
            PBS_SleepDelayTimeEnum.THREE_HUNDRED,
        )
    }

    private fun setScreenOffDelay() {
        ToBServiceHelper.getInstance().serviceBinder.pbsPropertySetScreenOffDelay(
            PBS_ScreenOffDelayTimeEnum.THREE_HUNDRED,
            object : IIntCallback.Stub() {
                override fun callback(result: Int) {
                    if (result != 0) {
                        logger.logErrorMsg(
                            AnalyticsLogger.Companion.LogEventType.SYSTEM_SETTINGS_EVENT,
                            "$TAG - Failed to set Screen Off Delay: $result"
                        )
                    }
                }
            }
        )
    }

    private fun setHomeKeys() {
        ToBServiceHelper.getInstance().serviceBinder.pbsPropertySetHomeKey(
            PBS_HomeEventEnum.SINGLE_CLICK,
            PBS_HomeFunctionEnum.VALUE_HOME_GO_TO_HOME,
            object : IBoolCallback.Stub() {
                override fun callBack(result: Boolean) {
                    Log.d(
                        TAG,
                        "callBack: set the function of single click the home button $result"
                    );
                }
            }
        )

        ToBServiceHelper.getInstance().serviceBinder.pbsPropertySetHomeKey(
            PBS_HomeEventEnum.DOUBLE_CLICK,
            PBS_HomeFunctionEnum.VALUE_HOME_QUICK_SETTING,
            object : IBoolCallback.Stub() {
                override fun callBack(result: Boolean) {
                    Log.d(
                        TAG,
                        "callBack: set the function of double click the home button $result"
                    );
                }
            }
        )
    }
}
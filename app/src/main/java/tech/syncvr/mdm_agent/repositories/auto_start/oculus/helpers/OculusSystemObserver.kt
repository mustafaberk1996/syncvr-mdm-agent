package tech.syncvr.mdm_agent.repositories.auto_start.oculus.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class OculusSystemObserver(
    private val context: Context,
    private val listener: OculusSystemEventsListener
) {

    companion object {
        private const val TAG = "OculusSystemObserver"
        private const val ANDROID_LOG_TIME_FORMAT = "MM-dd HH:mm:ss.SSS"
        private val logCatDate = SimpleDateFormat(ANDROID_LOG_TIME_FORMAT)
    }

    interface OculusSystemEventsListener {
        fun onOculusExitedDialog()
        fun onOculusGuardianEnd()
        fun onOculusGuardianStart()
        fun onOculusHomeButtonPressed()
        fun onOculusUniversalMenuPanelOpened(str: String?)
    }

    private var isObserving = true

    private var lastReadLogCatTime = Date()
    private var ignoreLog = false
    private val ignoreLogDebounce = ExecuteBuffer(350)
    private val guardianStartDebounce = ExecuteCooldown(1000)
    private val homeButtonDebounce = ExecuteCooldown(1000)
    private val oculusSystemUxPanelPattern = Pattern.compile(".*systemux://((\\w|-|/)*).*")

    private val userPresenceDetectionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if ("android.intent.action.SCREEN_ON" == action) {
                resumeParseLogCatCoroutine()
            } else if ("android.intent.action.SCREEN_OFF" == action) {
                pauseParseLogCatCoroutine()
            }
        }
    }

    fun start() {
        startParseLogCatCoroutine()
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        intentFilter.addAction(Intent.ACTION_SCREEN_ON)
        context.registerReceiver(userPresenceDetectionReceiver, intentFilter)
    }

    private fun startParseLogCatCoroutine() {
        Log.d(TAG, "startParseLogCatCoroutine: Start")
        MDMAgentApplication.coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                delay(100L)
                if (isObserving) {
                    // parseLogCat reads the entire current logcat buffer, parses it, and then returns
                    parseLogCat()
                }
            }
        }
    }

    private fun pauseParseLogCatCoroutine() {
        isObserving = false
    }

    private fun resumeParseLogCatCoroutine() {
        isObserving = true
    }

    private fun parseLogCat() {
        try {
            val args = listOf(
                "logcat",
                "-bmain",
                "-vtime",
                "-t" + logCatDate.format(lastReadLogCatTime)
            )

            val bufferedReader = BufferedReader(
                InputStreamReader(
                    ProcessBuilder(*arrayOfNulls(0)).command(args).start().inputStream
                )
            )

            while (true) {
                val readLine = bufferedReader.readLine()
                if (readLine != null) {
                    if (isDetectGuardianEnd(readLine)) {
                        oculusGuardianEndDetected()
                    } else if (isDetectGuardianStart(readLine)) {
                        oculusGuardianStartDetected()
                    }
                    if (!ignoreLog) {
                        if (shouldIgnoreLog(readLine)) {
                            ignoreLogBriefly()
                        } else if (isDetectHomeButton(readLine)) {
                            oculusHomeButtonPressDetected()
                        } else if (isDetectExitedDialog(readLine)) {
                            oculusExitedDialogDetected()
                            ignoreLogBriefly()
                        } else if (isDetectSystemUxExploreMenu(readLine)) {
                            oculusUniversalMenuPanelOpened("explore")
                        } else if (isDetectSystemUx(readLine)) {
                            oculusUniversalMenuPanelOpened(getOculusPanelNameFromLine(readLine))
                        }
                    }
                } else {
                    lastReadLogCatTime = Date()
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Logcat: ", e)
        }
    }

    private fun shouldIgnoreLog(str: String): Boolean {
        return str.contains("DialogResult next for dialog systemux://dialog/local-stream-start-from-device")
                || str.contains("System UX, targetComponent = systemux://guardian/adjust-setup")
                || str.contains("systemux://guardian/update_state")
    }

    private fun isDetectHomeButton(str: String): Boolean {
        return str.contains("dialog/exit") || str.contains("Telling app about short home press")
    }

    private fun isDetectExitedDialog(str: String): Boolean {
        return str.contains("DialogResult cancel for dialog shell_dialog_guardian_setup")
                || str.contains("DialogResult confirm for dialog shell_dialog_guardian_setup")
                || str.contains("DialogResult stationaryComplete for dialog shell_dialog_guardian_setup")
                || str.contains("DialogResult setupComplete for dialog shell_dialog_guardian_setup")
                || str.contains("DialogResult cancel for dialog guardian_configurable")
                || str.contains("DialogResult confirm for dialog guardian_configurable")
                || str.contains("DialogResult stationaryComplete for dialog guardian_configurable")
                || str.contains("DialogResult completeDrawing for dialog guardian_configurable")
                || str.contains("DialogResult close for dialog systemux://dialog/local-stream-start-from-device")
                || str.contains("Received system dialog result: {\"dialogId\":\"local_stream_wait_for_answer_dialog\",\"action\":\"cancel\"")
                || str.contains("[LiveVideo] LiveVideo::OnJsonDataReceived JSON data = {\"id\":1}")
                || str.contains("startCastingToTwilight")
    }

    private fun isDetectSystemUxExploreMenu(str: String): Boolean {
        return str.contains("(com.oculus.explore.PanelService) - false")
    }

    private fun isDetectSystemUx(str: String): Boolean {
        if (str.contains("ClickEventButtonId")
            && (str.contains("AUI_BAR_LIBRARY")
                    || str.contains("AUI_BAR_SHARING")
                    || str.contains("AUI_BAR_MESSENGER")
                    || str.contains("AUI_BAR_STORE")
                    || str.contains("AUI_BAR_EXPLORE")
                    || str.contains("AUI_SYSTEM_STATUS_PROFILE")
                    || str.contains("AUI_SYSTEM_STATUS_NOTIFICATIONS")
                    || str.contains("AUI_BAR_DESTINATION_UI")
                    || str.contains("AUI_SYSTEM_STATUS_QUICK_SETTINGS")
                    )
        ) {
            return true
        }
        if (!str.contains("System UX, targetComponent")) {
            return false
        }
        return str.contains("System UX, targetComponent = systemux://store")
                || str.contains("System UX, targetComponent = systemux://explore")
                || str.contains("System UX, targetComponent = systemux://aui-profile")
                || str.contains("System UX, targetComponent = systemux://notifications")
                || str.contains("System UX, targetComponent = systemux://quick_settings")
                || str.contains("System UX, targetComponent = systemux://aui-social-v2")
                || str.contains("System UX, targetComponent = systemux://library")
                || str.contains("System UX, targetComponent = systemux://sharing")
                || str.contains("System UX, targetComponent = systemux://settings")
                || str.contains("System UX, targetComponent = systemux://pause")
    }

    private fun isDetectGuardianEnd(str: String): Boolean {
        return str.contains("reason end guardian setup")
                || str.contains("DialogResult cancel for dialog shell_dialog_guardian_setup")
                || str.contains("DialogResult confirm for dialog shell_dialog_guardian_setup")
                || str.contains("DialogResult stationaryComplete for dialog shell_dialog_guardian_setup")
                || str.contains("DialogResult setupComplete for dialog shell_dialog_guardian_setup")
                || str.contains("stopStream camera")
                || str.contains("Passthrough state changed to hidden in render loop")
                || str.contains("passthroughPause")
                || str.contains("PanelAppLaunchInfo componentString:com.oculus.browser")
    }

    private fun isDetectGuardianStart(str: String): Boolean {
        return str.contains("Passthrough state changed to visible in render loop")
                || str.contains("VrGuardianService: onStartCommand")
                || str.contains("passthroughStart")
                || str.contains("OverlayGuardianFlow Activate")
    }

    private fun getOculusPanelNameFromLine(str: String): String? {
        return try {
            val matcher = oculusSystemUxPanelPattern.matcher(str)
            if (matcher.matches()) {
                matcher.group(1)
            } else null
        } catch (unused: Exception) {
            null
        }
    }

    private fun ignoreLogBriefly() {
        Log.d(TAG, "Ignoring briefly")
        ignoreLog = true
        ignoreLogDebounce.attempt {
            Log.d(TAG, "Stop ignoring briefly")
            ignoreLog = false
        }
    }

    private fun oculusHomeButtonPressDetected() {
        homeButtonDebounce.attempt {
            Log.d(TAG, "RUN - Home Button Press")
            listener.onOculusHomeButtonPressed()
        }
    }

    private fun oculusExitedDialogDetected() {
        homeButtonDebounce.attempt {
            Log.d(TAG, "RUN - Exited Dialog")
            listener.onOculusExitedDialog()
        }
    }

    private fun oculusGuardianStartDetected() {
        guardianStartDebounce.attempt {
            Log.d(TAG, "RUN - Oculus Guardian Start")
            listener.onOculusGuardianStart()
        }
    }

    private fun oculusGuardianEndDetected() {
        Log.d(TAG, "RUN - Oculus Guardian End")
        listener.onOculusGuardianEnd()
        guardianStartDebounce.restartCooldown()
    }

    private fun oculusUniversalMenuPanelOpened(str: String?) {
        Log.d(TAG, "RUN - oculusUniversalMenuOpened $str")
        listener.onOculusUniversalMenuPanelOpened(str)
    }
}
package tech.syncvr.mdm_agent.repositories.auto_start.oculus

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.receivers.DeviceOwnerReceiver
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager
import tech.syncvr.mdm_agent.repositories.auto_start.oculus.helpers.ForegroundAppObserver
import tech.syncvr.mdm_agent.repositories.auto_start.oculus.helpers.OculusSystemObserver
import tech.syncvr.mdm_agent.repositories.auto_start.oculus.helpers.OculusSystemUtils
import tech.syncvr.mdm_agent.repositories.auto_start.oculus.models.AppWithClass


class OculusAutoStartManager(context: Context, private val analyticsLogger: AnalyticsLogger) :
    AutoStartManager(context),
    OculusSystemObserver.OculusSystemEventsListener {

    companion object {
        private const val TAG = "OculusAutoStartRepository"
        private const val HOME_ENVIRONMENT_PACKAGE_NAME = "tech.syncvr.home_environment.oculus"
    }

    /**
     * There's no real way to ask the system what the current auto start package is.
     * We just store it here. On device boot, this value will be set by the ConfigurationRepository
     * based on a cached configuration.
     *
     * This is actually nice, since it will enable auto-start mode immediately on device boot.
     */
    private var autoStartPackage: String? = null
    private var oculusGuardianActive = false

    private val oculusSystemObserver: OculusSystemObserver = OculusSystemObserver(context, this)
    private val foregroundAppObserver: ForegroundAppObserver = ForegroundAppObserver(context)

    override fun start() {
        oculusSystemObserver.start()
        foregroundAppObserver.start()
    }

    override fun getAutoStartPackage(): String? {
        Log.d(TAG, "getAutoStartPackage, value is: $autoStartPackage")
        return autoStartPackage
    }

    override fun setAutoStart(
        autoStartPackageName: String,
        allowedPackages: List<String>
    ): Boolean {

        Log.d(
            TAG,
            "setAutoStart is called with value: $autoStartPackageName, it was $autoStartPackage"
        )
        if (!isAutoStartPackageInstalled(autoStartPackageName)) {
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                "This auto start package is not installed: $autoStartPackageName"
            )
            return false
        }
        if (autoStartPackageName.isBlank()) {
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                "Can't use an empty autoStartPackageName!"
            )
            return false
        }

        val isNewAutoStartPackage =
            autoStartPackage == null || autoStartPackage != autoStartPackageName
        autoStartPackage = autoStartPackageName

        return setOculusPackagesEnabled(false).also {
            if (isNewAutoStartPackage && it) {
                Log.d(TAG, "AutoStart package has changed, launch AutoStart!")
                forceLaunchApp()
            }
        }
    }

    private fun isAutoStartPackageInstalled(autoStartPackageName: String): Boolean {
        val installedPackages = context.packageManager.getInstalledPackages(0)
        return installedPackages.any { packageInfo ->
            //TODO: one day we will allow other packages to be the auto start package.
            //packageInfo.packageName == autoStartPackageName
            packageInfo.packageName == HOME_ENVIRONMENT_PACKAGE_NAME
        }
    }

    override fun clearAutoStartPackage(): Boolean {
        Log.d(TAG, "clearAutoStartPackage is called, value was: $autoStartPackage")

        autoStartPackage = null
        return setOculusPackagesEnabled()
    }

    override fun requiresReboot(): Boolean {
        return false
    }

    private fun setOculusPackagesEnabled(value: Boolean = true): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return dpm.setApplicationHidden(
            ComponentName(context, DeviceOwnerReceiver::class.java),
            "com.oculus.explore",
            !value
        ) && dpm.setApplicationHidden(
            ComponentName(context, DeviceOwnerReceiver::class.java),
            "com.oculus.store",
            !value
        )
    }

    private fun setOculusShellEnabled(value: Boolean = true): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return dpm.setApplicationHidden(
            ComponentName(context, DeviceOwnerReceiver::class.java),
            AppWithClass.OCULUS_VRSHELL_PACKAGE,
            !value
        ) &&
                dpm.setApplicationHidden(
                    ComponentName(context, DeviceOwnerReceiver::class.java),
                    AppWithClass.OCULUS_VRSHELLENV_PACKAGE,
                    !value
                )
    }

    override fun onOculusExitedDialog() {
        autoStartPackage?.let {
            onOculusGuardianEnd()
            if (!foregroundAppObserver.currentForegroundApp.isOculusSystemApp) {
                Log.d(TAG, "OculusKioskHandler: Not in Oculus Home when exited dialog. Ignoring...")
                return@let
            }
            val previousForegroundApp: AppWithClass = foregroundAppObserver.previousForegroundApp
            if (previousForegroundApp.packageName.isBlank() || previousForegroundApp.isOculusSystemApp) {
                Log.d(TAG, "OculusKioskHandler: Launching Kiosk App")
                forceLaunchApp()
                return@let
            }
            Log.d(
                TAG,
                "OculusKioskHandler: Launching previous foreground app: " + previousForegroundApp.packageName
            )
            previousForegroundApp.launch(context)
        }
    }

    override fun onOculusGuardianEnd() {
        Log.d(TAG, "Oculus Guardian End")
        oculusGuardianActive = false
    }

    override fun onOculusGuardianStart() {
        Log.d(TAG, "Oculus Guardian Start")
        oculusGuardianActive = true
    }

    override fun onOculusHomeButtonPressed() {
        autoStartPackage?.let {
            Log.d(TAG, "oculusHomeButtonPressed")
            forceLaunchApp()
        }
    }

    override fun onOculusUniversalMenuPanelOpened(str: String?) {
        autoStartPackage?.let {
            Log.d(TAG, "OculusKioskHandler: Force closing universal menu $str")
            if ("explore" == str) {
                forceLaunchApp()
            } else {
                onOculusGuardianEnd()
                forceLaunchApp()
            }
        }
    }

    //TODO: reintroduce an argument to this method once we allow other apps to go into auto-start
    private fun forceLaunchApp() {
        //TODO: understand this logic. Why these conditions?
        //TODO: for now we only allow Home Environment in auto start. One day we will allow others.
        Log.d(TAG, "forceLaunchApp is called for $HOME_ENVIRONMENT_PACKAGE_NAME")
        if (!oculusGuardianActive
            || !foregroundAppObserver.currentForegroundApp.equalsPackage(
                AppWithClass(HOME_ENVIRONMENT_PACKAGE_NAME)
            )
            && !foregroundAppObserver.currentForegroundApp.isOculusSystemApp
        ) {
            setOculusShellEnabled(false)
            AppWithClass(HOME_ENVIRONMENT_PACKAGE_NAME).launch(context)
            MDMAgentApplication.coroutineScope.launch(Dispatchers.Main) {
                delay(1000)
                setOculusShellEnabled()
            }
        } else {
            Log.d(TAG, "Launching home when guardian is open. Resetting System UX...")
            OculusSystemUtils.launchDialogNone(context)
        }
    }
}

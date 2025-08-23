package tech.syncvr.mdm_agent.device_management

import android.app.DownloadManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import tech.syncvr.mdm_agent.device_management.configuration.ConfigurationRepository
import tech.syncvr.mdm_agent.device_management.configuration.models.Configuration
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.device_management.default_apps.PlatformAppsRepository
import tech.syncvr.mdm_agent.device_management.device_status.DeviceStatusFactory
import tech.syncvr.mdm_agent.device_management.device_status.DeviceStatusRepository
import tech.syncvr.mdm_agent.device_management.services.AppInstallService
import tech.syncvr.mdm_agent.device_management.services.LaunchSyncVRAppService
import tech.syncvr.mdm_agent.device_management.services.RebootService
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.repositories.AppPackageRepository
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager
import tech.syncvr.mdm_agent.utils.Logger

private val appsToNeverPostponeInstalling = listOf("tech.syncvr.picogallery")

class MDMWorkHandler(
    looper: Looper,
    val context: Context,
    private val autoStartManager: AutoStartManager,
    private val launchSyncVRAppService: LaunchSyncVRAppService,
    private val appPackageRepository: AppPackageRepository,
    private val appInstallService: AppInstallService,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val rebootService: RebootService,
    private val logger: Logger,
    private val configurationRepository: ConfigurationRepository,
    private val analyticsLogger: AnalyticsLogger
) : Handler(looper) {

    companion object {
        private const val TAG = "MDMWorkHandler"

        const val WORK = 1
        const val WORK_DOWNLOAD_COMPLETE = 2
        const val WORK_CONFIGURATION_RECEIVED = 3
        const val WORK_APP_INSTALLED = 4
        const val SYNC_STATUS = 5
        const val DEFAULT_APPS_AVAILABLE = 6
        const val SCHEDULE_REBOOT = 7
        const val APPS_CLEANUP = 8

        private const val REBOOT_RETRY_PERIOD = 60 * 1000L
    }


    private var selfPackage: ManagedAppPackage? = null
    private var currentConfiguration: Configuration? = null
    private var defaultAppPackages: List<ManagedAppPackage>? = null

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)

        when (msg.what) {
            WORK -> {
                work()
            }

            WORK_APP_INSTALLED -> {
                onAppInstalled(msg.data.getLong(DownloadManager.EXTRA_DOWNLOAD_ID))
            }

            WORK_DOWNLOAD_COMPLETE -> {
                onDownloadComplete(msg.data.getLong(DownloadManager.EXTRA_DOWNLOAD_ID))
            }

            WORK_CONFIGURATION_RECEIVED -> {
                val configuration = msg.obj as Configuration
                onConfigurationReceived(configuration)
            }

            SYNC_STATUS -> {
                selfPackage?.let {
                    val defaultPackagesIncludingMDM = mutableListOf(it)
                    defaultPackagesIncludingMDM.addAll(defaultAppPackages?.toList() ?: emptyList())
                    deviceStatusRepository.syncStatus(
                        currentConfiguration,
                        defaultPackagesIncludingMDM
                    )
                }

            }

            DEFAULT_APPS_AVAILABLE -> {
                val platformApps = msg.obj as List<ManagedAppPackage>
                onPlatformAppsAvailable(platformApps)
            }

            SCHEDULE_REBOOT -> {
                onRebootScheduled()
            }

            APPS_CLEANUP -> {
                onAppsCleanup(msg.obj as List<String>)
            }

            else -> {
                logger.d(TAG, "Received unknown Message!")
            }
        }
    }

    private fun work() {
        // First handle installing packages!
        //TODO: add checks on whether what we are trying to install is indeed what we intend to install
        //TODO: add a failure status if installing an application has failed and it is not something MDM can fix

        // first check the current status of the mdm agent itself, and do any work
        logger.d(TAG, "execute work function")
        if (workUpdateSelf()) {
            removeMessages(SYNC_STATUS)
            sendMessage(obtainMessage(SYNC_STATUS))
            return
        }

        if (workUpdateDefaultApps()) {
            removeMessages(SYNC_STATUS)
            sendMessage(obtainMessage(SYNC_STATUS))
            return
        }

        if (workUpdateManagedApps()) {
            removeMessages(SYNC_STATUS)
            sendMessage(obtainMessage(SYNC_STATUS))
            return
        }

        if (workSetAutoStart()) {
            removeMessages(SYNC_STATUS)
            sendMessage(obtainMessage(SYNC_STATUS))
            if (autoStartManager.requiresReboot()) {
                sendMessage(obtainMessage(SCHEDULE_REBOOT))
            }
        }
    }

    private fun workUpdateSelf(): Boolean {
        logger.d(TAG, "Do workUpdateSelf")

        return selfPackage?.let { selfPackage ->
            logger.d(
                TAG,
                "UpdateSelf lastest version of mdm on backend: ${selfPackage.appVersionCode}"
            )
            val appStatus = updateApps(listOf(selfPackage))

            !appStatus.map { app -> app.status == ManagedAppPackage.Status.INSTALLED }
                .fold(true) { b1, b2 -> b1 && b2 }
        } ?: false // false when selfpackage is not yet set
    }

    private fun workUpdateDefaultApps(): Boolean {
        logger.d(TAG, "Do workUpdateDefaultApps")

        return defaultAppPackages?.let { defaultAppPackages ->
            val appStatus = updateApps(defaultAppPackages)

            launchSyncVRAppService.launchSyncVRStartOnceApps()

            return !appStatus.map { app -> app.status == ManagedAppPackage.Status.INSTALLED }
                .fold(true) { b1, b2 -> b1 && b2 }
        } ?: false
    }

    private fun workUpdateManagedApps(): Boolean {
        logger.d(TAG, "Do workUpdateManagedApps")

        return currentConfiguration?.let { currentConfiguration ->
            val appStatus = updateApps(currentConfiguration.managed)
            !appStatus.map { app -> app.status == ManagedAppPackage.Status.INSTALLED }
                .fold(true) { b1, b2 -> b1 && b2 }
        } ?: false
    }

    private fun updateApps(apps: List<ManagedAppPackage>): List<ManagedAppPackage> {
        val appStatuses =
            appPackageRepository.getConfigurationStatus(apps) // this operation takes time
        logger.d(TAG, "updateApps called for: $appStatuses")
        for (app in appStatuses) {
            when (app.status) {
                ManagedAppPackage.Status.NOT_INSTALLED -> {
                    analyticsLogger.log(
                        AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                        hashMapOf(
                            "action" to "start_downloading",
                            "appPackageName" to app.appPackageName
                        )
                    )
                    appPackageRepository.downloadPackage(app)
                }

                ManagedAppPackage.Status.DOWNLOADING -> {
                    logger.d(
                        TAG,
                        "${app.appPackageName} is already downloading, progress: ${app.progress}"
                    )
                }

                ManagedAppPackage.Status.DOWNLOADED -> {
                    maybeInstallAppAfterDownload(app)
                }

                ManagedAppPackage.Status.INSTALLING -> {
                    // If it is installing, we do nothing but rejoice
                    logger.d(TAG, "App ${app.appPackageName} is installing!")
                }

                ManagedAppPackage.Status.NEED_PERMISSIONS -> {
                    analyticsLogger.log(
                        AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                        hashMapOf(
                            "action" to "set_permissions",
                            "appPackageName" to app.appPackageName
                        )
                    )
                    appPackageRepository.grantPermissions(app)
                }

                ManagedAppPackage.Status.INSTALLED -> {
                    logger.d(TAG, "App ${app.appPackageName} is already installed!")
                    appPackageRepository.grantPermissions(app)
                }
            }
        }

        return appStatuses
    }

    private fun maybeInstallAppAfterDownload(app: ManagedAppPackage) {
        val installInfo =
            appPackageRepository.getDownloadInstallInfo(app)
        installInfo?.run {
            // if an app is in the foreground it will be not in the foreground when the headset goes into standby. Then within a minute an update poll will take place and then the app WILL get updated
            val isForegroundApp by lazy {
                DeviceStatusFactory.getForegroundApp(context) == app.appPackageName
            }
            val appsNotToPostpone =
                appsToNeverPostponeInstalling + context.packageName
            val isNotPostPonableApp = appsNotToPostpone.contains(app.appPackageName)
            val shouldInstallImmediately = isNotPostPonableApp ||
                    !isForegroundApp
            logger.d(
                TAG,
                "2: new appPackage: ${app.appPackageName} is ${app.status}, isForegroundApp = " +
                        "$isForegroundApp, shouldInstallImmediately: $shouldInstallImmediately"
            )
            if (shouldInstallImmediately) {
                analyticsLogger.log(
                    AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
                    hashMapOf(
                        "action" to "start_installing",
                        "appPackageName" to app.appPackageName
                    )
                )
                appInstallService.installPackage(app, installInfo)
            }
        }
    }

    private fun workSetAutoStart(): Boolean {
        logger.d(TAG, "workSetAutoStart")

        return currentConfiguration?.let { currentConfiguration ->
            val autoStart = currentConfiguration.autoStart

            if (autoStart.isNullOrBlank()) {
                autoStartManager.clearAutoStartPackage()
            } else {
                autoStartManager.setAutoStart(
                    autoStart,
                    currentConfiguration.managed.map { app -> app.appPackageName })
            }
        } ?: false
    }

    private fun onConfigurationReceived(configuration: Configuration) {
        logger.d(TAG, "onConfigurationReceived")
        currentConfiguration = configuration
        removeMessages(WORK)
        sendMessage(obtainMessage(WORK))
    }

    private fun onPlatformAppsAvailable(managedApps: List<ManagedAppPackage>) {
        logger.d(TAG, "onPlatformAppsAvailable")
        val newSelfPackage =
            PlatformAppsRepository.getSelfApp(managedApps)
        newSelfPackage?.run {
            selfPackage = this
            removeMessages(WORK)
            sendMessage(obtainMessage(WORK))
        }

        val newPlatformAppPackages =
            managedApps.filterNot { app -> app.appPackageName == context.packageName }
        newPlatformAppPackages.run {
            defaultAppPackages = this
            removeMessages(WORK)
            sendMessage(obtainMessage(WORK))
        }
    }

    private fun onDownloadComplete(downloadId: Long) {
        logger.d(TAG, "Received onDownloadComplete for downloadId: $downloadId")
        removeMessages(WORK)
        sendMessage(obtainMessage(WORK))
    }

    private fun onAppInstalled(downloadId: Long) {
        appPackageRepository.removeDownload(downloadId)
        configurationRepository.onAppInstalled()
        removeMessages(WORK)
        sendMessage(obtainMessage(WORK))
    }

    private fun onRebootScheduled() {
        if (!rebootService.tryReboot()) {
            removeMessages(SCHEDULE_REBOOT)
            sendMessageDelayed(obtainMessage(SCHEDULE_REBOOT), REBOOT_RETRY_PERIOD)
        }
    }

    private fun onAppsCleanup(pkgNames: List<String>) {
        pkgNames.forEach {
            appInstallService.uninstallPackage(it)
        }
    }
}

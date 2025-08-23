package tech.syncvr.mdm_agent

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.PACKAGE_USAGE_STATS
import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.SuppressLint
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
import android.app.admin.DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import androidx.core.content.PermissionChecker
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import tech.syncvr.mdm_agent.MDMAgentModule.Companion.MODEL_PICO_G2_4K
import tech.syncvr.mdm_agent.app_usage.PostUsageStatsWorker
import tech.syncvr.mdm_agent.app_usage.app_sessions.UsageStatsEventsRepository
import tech.syncvr.mdm_agent.app_usage.UsageStatsRepository
import tech.syncvr.mdm_agent.device_identity.DeviceIdentityRepository
import tech.syncvr.mdm_agent.device_management.MDMWorkManager
import tech.syncvr.mdm_agent.device_management.bluetooth_name.DeviceInfo
import tech.syncvr.mdm_agent.device_management.bluetooth_name.FetchDeviceInfoWorker
import tech.syncvr.mdm_agent.device_management.configuration.ConfigurationRepository
import tech.syncvr.mdm_agent.device_management.configuration.GetConfigurationWorker
import tech.syncvr.mdm_agent.device_management.default_apps.PlatformAppsRepository
import tech.syncvr.mdm_agent.device_management.default_apps.GetPlatformAppsWorker
import tech.syncvr.mdm_agent.device_management.firmware_upgrade.FirmwareUpdateCheckWorker
import tech.syncvr.mdm_agent.device_management.services.AppInstallService
import tech.syncvr.mdm_agent.device_management.services.WifiConfigLogic
import tech.syncvr.mdm_agent.device_management.system_settings.ISystemSettingsService
import tech.syncvr.mdm_agent.disk_space.DiskSpaceUtil.getAvailableDiskSpace
import tech.syncvr.mdm_agent.firebase.IAuthenticationService
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.logging.UploadLogEntriesWorker
import tech.syncvr.mdm_agent.receivers.DeviceOwnerReceiver
import tech.syncvr.mdm_agent.receivers.PackageInstallerSessionStatusReceiver
import tech.syncvr.mdm_agent.repositories.DeviceInfoRepository
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager
import tech.syncvr.mdm_agent.repositories.play_area.IPlayAreaRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MDMAgentApplication : Application(), Configuration.Provider {

    companion object {
        val exHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
            Log.e(
                TAG,
                throwable.message,
                throwable
            )
        }
        private const val TAG = "MDMAgentApplication"
        private const val UPDATE_SLEEP_TIME = 1000 * 20L
        private const val POST_USAGE_STATS_WORK_TAG = "PostUsageStatsWorker"
        private const val GET_CONFIGURATION_WORK_TAG = "GetConfigurationWorker"
        private const val GET_DEFAULT_APPS_WORK_TAG = "GetDefaultAppsWorker"
        private const val CHECK_FIRMWARE_UPGRADE_WORK_TAG = "CheckFirmwareUpgradeWorker"
        private const val GET_DEV_INFO_BT_NAME_WORK_TAG = "GetDevInfoBtNameWorker"
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exHandler)
    }

    @Inject
    lateinit var playAreaRepository: IPlayAreaRepository
    lateinit var devicePolicyManager: DevicePolicyManager
    lateinit var bluetoothManager: BluetoothManager
    lateinit var deviceOwnerComponent: ComponentName

    @Inject
    lateinit var mdmWorkManager: MDMWorkManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var systemSettingsService: ISystemSettingsService

    @Inject
    lateinit var firebaseAuthHandler: IAuthenticationService

    @Inject
    lateinit var configurationRepository: ConfigurationRepository

    @Inject
    lateinit var platformAppsRepository: PlatformAppsRepository

    @Inject
    lateinit var usageStatsEventsRepository: UsageStatsEventsRepository

    @Inject
    lateinit var usageStatsRepository: UsageStatsRepository

    @Inject
    lateinit var wifiConfigLogic: WifiConfigLogic

    @Inject
    lateinit var deviceInfoRepository: DeviceInfoRepository

    @Inject
    lateinit var analyticsLogger: AnalyticsLogger

    @Inject
    lateinit var autoStartManager: AutoStartManager

    @Inject
    lateinit var appInstallService: AppInstallService

    // https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "start of oncreate of the application")
        Log.i(TAG, "Product model: \"${Build.MODEL}\"")
        devicePolicyManager = getSystemService(DevicePolicyManager::class.java)
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        deviceOwnerComponent = ComponentName(this, DeviceOwnerReceiver::class.java)

        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            runBlocking {
                mdmAgentAppSetup()
            }
        } else {
            coroutineScope.launch {
                mdmAgentAppSetup()
            }
        }
        removeLegacyApps()
    }

    private fun removeLegacyApps() {
        coroutineScope.launch {
            waitForDeviceOwnership()
            val appsToRemove = listOf("tech.syncvr.syncvr_agent", "tech.syncvr.syncvr_agent.neo_3")
            appsToRemove.filter { appInstallService.isAppInstalled(it) }.forEach {
                appInstallService.uninstallPackage(it)
            }
        }
    }

    // can't DI that one.
    // When DI'd, it triggers a crash on g2-4k during provisioning by the setuptool
    val deviceIdentityRepository: DeviceIdentityRepository by lazy {
        runBlocking {
            if (!waitForDeviceOwnership()) {
                throw IllegalStateException("Unable to get device ownership")
            }
        }
        DeviceIdentityRepository()
    }

    private suspend fun mdmAgentAppSetup() {
        val gotIt = waitForDeviceOwnership()
        if (!gotIt) {
            return
        }
        permissionsGrantsAndPolicy()
        // Initialize objects that need it.
        playAreaRepository.onAppCreated()
        registerPermanentReceivers()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork() // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }

        // Schedule periodic work based on version update or debug build
        if (isVersionUpdate() || BuildConfig.DEBUG) {
            schedulePeriodicWork(ExistingPeriodicWorkPolicy.REPLACE)
        } else {
            schedulePeriodicWork(ExistingPeriodicWorkPolicy.KEEP)
        }

        // do a network connectivity check
        checkInternetConnectivity()

        // start running things that do NOT require an internet connection
        coroutineScope.launch {
            systemSettingsService.setDefaultSystemSettings()
            usageStatsRepository.startPeriodicQueryUsageStats()
            usageStatsEventsRepository.startPeriodicQueryUsageEvents(Build.MODEL == MODEL_PICO_G2_4K)
            autoStartManager.start()
        }

        // start running things that do require an internet connection
        coroutineScope.launch {
            waitForSignIn()
            // and on top of that start a coroutine doing the same thing is quick-polling fashion (but is less safe because it may crash)
            configurationRepository.startPeriodicGetConfiguration()
            platformAppsRepository.startPeriodicGetPlatformApps()
            periodicStatusUpdateScheduler()
        }

        wifiConfigLogic.addDefaultWifiIfNotPresent()

        Log.d(TAG, "Device: ${Build.MANUFACTURER} - ${Build.MODEL}")

        // Running this mainly to start spectating agent on boot without accounting for connectivity
        coroutineScope.launch {
            launchSyncVRStartOnceApps()
        }
        // start the foreground service to keep the app running
        startForegroundService()
    }

    private fun launchSyncVRStartOnceApps() {
        val intent = Intent("tech.syncvr.intent.START_ONCE")
        val resolveInfoList =
            packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)

        resolveInfoList.forEach {
            intent.component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
            sendBroadcast(intent)
        }

    }

    private fun registerPermanentReceivers() {
        registerReceiver(
            PackageInstallerSessionStatusReceiver(),
            IntentFilter().also {
                it.addAction(PackageInstallerSessionStatusReceiver.INSTALLER_SESSION_STATUS_CHANGED)
                it.addAction(PackageInstallerSessionStatusReceiver.UNINSTALL_STATUS)
            }
        )

        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_SHUTDOWN) {
                        usageStatsEventsRepository.onShutdown()
                    }
                }
            },
            IntentFilter(Intent.ACTION_SHUTDOWN)
        )
    }

    private fun permissionsGrantsAndPolicy() {
        // Wanted behavior on all devices
        devicePolicyManager.setPermissionPolicy(deviceOwnerComponent, PERMISSION_POLICY_AUTO_GRANT)

        // Need that to authenticate
        val canGetSerial = devicePolicyManager.setPermissionGrantState(
            deviceOwnerComponent,
            packageName,
            READ_PHONE_STATE,
            PERMISSION_GRANT_STATE_GRANTED
        )
        Log.d(TAG, "Serial permission grant result $canGetSerial")

        if (PermissionChecker.checkSelfPermission(this, PACKAGE_USAGE_STATS)
            != PermissionChecker.PERMISSION_GRANTED
        ) {
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.PERMISSION_EVENT,
                "Don't have permission PACKAGE_USAGE_STATS."
            )
        }
    }

    private fun checkInternetConnectivity() {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.CONNECTIVITY_EVENT,
                "Connected to a Network without Internet Connection!"
            )
        } else {
            analyticsLogger.logMsg(
                AnalyticsLogger.Companion.LogEventType.APP_START_EVENT,
                "Connected to a Network with Internet Connection!"
            )
        }
    }

    private suspend fun waitForDeviceOwnership(): Boolean {
        repeat(10 * 20) {
            if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
                Log.i(TAG, "I'm DeviceOwner. LIMITED POWER !!")
                return true
            } else {
                Log.w(TAG, "I'm not device owner just yet, let me sleep on it")
                delay(100)
            }
        }
        Log.e(TAG, "I am not a Device Owner after multiple retries. Problem!")
        return false
    }

    private suspend fun waitForSignIn() {
        if (firebaseAuthHandler.isSignedIn()) {
            if (firebaseAuthHandler.getIdToken(true) != null) {
                Log.d(TAG, "Firebase authenticated with user: ${Firebase.auth.currentUser?.uid}")
                return
            }
        }

        Log.d(TAG, "No current Firebase user, start login procedure!")
        firebaseAuthHandler.signInRoutine()
        Log.d(
            TAG,
            "Signing into Firebase has succeeded: ${Firebase.auth.currentUser?.uid} start the app!"
        )
    }

    private fun periodicStatusUpdateScheduler() {
        coroutineScope.launch {
            while (true) {
                delay(UPDATE_SLEEP_TIME)
                mdmWorkManager.scheduleStatusUpdate()
            }
        }
    }

    private fun isVersionUpdate(): Boolean {
        val sharedPref = getSharedPreferences(
            this.getString(R.string.sharedprefs_filename),
            Context.MODE_PRIVATE
        )
        val previousVersion = sharedPref.getInt(this.getString(R.string.sharedprefs_app_version), 0)

        return if (previousVersion < BuildConfig.VERSION_CODE) {
            analyticsLogger.log(
                AnalyticsLogger.Companion.LogEventType.APP_START_EVENT,
                hashMapOf("version_update" to true, "version_code" to BuildConfig.VERSION_CODE)
            )
            writeVersion(sharedPref)
            true
        } else {
            analyticsLogger.log(
                AnalyticsLogger.Companion.LogEventType.APP_START_EVENT,
                hashMapOf("version_update" to false, "version_code" to BuildConfig.VERSION_CODE)
            )
            false
        }
    }

    private fun writeVersion(sharedPref: SharedPreferences) {
        // write this current version to shared prefs
        with(sharedPref.edit()) {
            putInt(getString(R.string.sharedprefs_app_version), BuildConfig.VERSION_CODE)
            commit()
        }
    }

    private fun startForegroundService() {
        // start the foreground Service
        val foregroundServiceIntent = Intent(this, MDMAgentForegroundService::class.java)
        this.startForegroundService(foregroundServiceIntent)
    }

    private fun schedulePeriodicWork(policy: ExistingPeriodicWorkPolicy) {
        schedulePeriodicPostUsageStats(policy)
        schedulePeriodicGetConfiguration(policy)
        schedulePeriodicGetDefaultApps(policy)
        schedulePeriodicSetBluetoothName(policy)
        schedulePeriodicFirmwareUpgradeCheck(policy)
    }

    private fun schedulePeriodicFirmwareUpgradeCheck(policy: ExistingPeriodicWorkPolicy) {

        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val workRequest = PeriodicWorkRequest.Builder(
            FirmwareUpdateCheckWorker::class.java,
            1,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CHECK_FIRMWARE_UPGRADE_WORK_TAG,
            policy,
            workRequest
        )
    }

    private fun schedulePeriodicPostUsageStats(policy: ExistingPeriodicWorkPolicy) {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val workRequest = PeriodicWorkRequest.Builder(
            PostUsageStatsWorker::class.java,
            15,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            POST_USAGE_STATS_WORK_TAG,
            policy,
            workRequest
        )
    }

    private fun schedulePeriodicGetConfiguration(policy: ExistingPeriodicWorkPolicy) {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val workRequest = PeriodicWorkRequest.Builder(
            GetConfigurationWorker::class.java,
            15,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            GET_CONFIGURATION_WORK_TAG,
            policy,
            workRequest
        )
    }

    private fun schedulePeriodicGetDefaultApps(policy: ExistingPeriodicWorkPolicy) {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val workRequest = PeriodicWorkRequest.Builder(
            GetPlatformAppsWorker::class.java,
            15,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            GET_DEFAULT_APPS_WORK_TAG,
            policy,
            workRequest
        )
        analyticsLogger.log(
            "DeviceAvailableDiskSpace",
            hashMapOf("name" to getAvailableDiskSpace())
        )
    }

    private fun schedulePeriodicSetBluetoothName(policy: ExistingPeriodicWorkPolicy) {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        coroutineScope.launch {
            deviceInfoRepository.deviceInfoStateFlow.collect {
                setBluetoothName(it)
            }
        }
        val syncDeviceInfoWorkRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
            FetchDeviceInfoWorker::class.java,
            15, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            GET_DEV_INFO_BT_NAME_WORK_TAG,
            policy,
            syncDeviceInfoWorkRequest
        )
    }

    @SuppressLint("MissingPermission")
    private fun setBluetoothName(deviceInfo: DeviceInfo) {
        val deviceName = deviceInfo.humanReadableName
        val prefix = "Pico"
        val bluetoothName =
            if (!deviceName.isNullOrBlank()) "$prefix - $deviceName"
            else "$prefix - ${Build.getSerial()}"

        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            val btConnectGrant = devicePolicyManager.setPermissionGrantState(
                deviceOwnerComponent,
                packageName,
                BLUETOOTH_CONNECT,
                PERMISSION_GRANT_STATE_GRANTED
            )
            Log.d(TAG, "BluetoothConnect permission grant result $btConnectGrant")
        }
        analyticsLogger.logMsg(
            AnalyticsLogger.Companion.LogEventType.MDM_EVENT,
            "Setting new Bluetooth name: $bluetoothName"
        )
        try {
            // This should require BLUETOOTH_CONNECT (or BLUETOOTH_ADMIN ?)
            // For some reason, it's not possible to grant BLUETOOTH_CONNECT on Pico4.
            // Added @SuppressLint so that Android Studio (and lint) stop stressing out about this
            bluetoothManager.adapter.name = bluetoothName
        } catch (e: Exception) {
            Log.w(TAG, "Exception occurred setting BT name", e)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

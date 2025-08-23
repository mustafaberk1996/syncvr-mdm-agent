package tech.syncvr.mdm_agent.device_management.device_status

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager
import tech.syncvr.mdm_agent.repositories.firmware.FirmwareRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStatusFactory @Inject constructor(
    @ApplicationContext val context: Context,
    private val autoStartManager: AutoStartManager,
    private val firmwareRepository: FirmwareRepository
) {

    companion object {
        private const val TAG = "DeviceStatusFactory"
        private const val DEVICE_STANDBY_VALUE = "standby"

        fun getForegroundApp(context: Context): String {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isInteractive) {
                return DEVICE_STANDBY_VALUE
            }

            val now = System.currentTimeMillis()
            val hourAgo = now - (60 * 60 * 1000)
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usageStatsManager.queryEvents(hourAgo, now)

            val usageEvent: UsageEvents.Event = UsageEvents.Event()
            var lastForegroundApp: String = ""


            while (events.getNextEvent(usageEvent)) {
                // Event.MOVE_TO_FOREGROUND (until SDK 29) and Event.ACTIVITY_RESUMED (from SDK 29) have value 1. This should work and save us from checking API levels
                if (usageEvent.eventType == 1) {
                    lastForegroundApp = usageEvent.packageName
                }
            }

            return lastForegroundApp
        }

    }

    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun currentDeviceStatus(
        managedApps: List<ManagedAppPackage>,
        defaultApps: List<ManagedAppPackage>,
    ): DeviceStatus {
        return DeviceStatus(
            managedApps,
            defaultApps,
            getUnmanagedApps(managedApps, defaultApps),
            getForegroundApp(),
            getOsVersion(),
            getFirmwareVersion(),
            getBatteryStatus(),
            getChargingState(),
            getWifiSSID(),
            getWifiCapabilities(),
            getAutoStart(),
        )
    }

    private fun getUnmanagedApps(
        managedApps: List<ManagedAppPackage>,
        defaultApps: List<ManagedAppPackage>
    ): List<UnmanagedAppPackage> {
        return context.packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            .filterNot {
                (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM
            }.filterNot {
                managedApps.map {
                    it.appPackageName
                }.contains(it.packageName)
            }.filterNot {
                defaultApps.map {
                    it.appPackageName
                }.contains(it.packageName)
            }.map {
                UnmanagedAppPackage(
                    appPackageName = it.packageName,
                    installedAppVersionCode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        it.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        it.versionCode.toLong()
                    }
                )
            }
    }

    private fun getBatteryStatus(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let {
            context.registerReceiver(null, it)
        }

        return batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale
        } ?: 0
    }

    private fun getChargingState(): Boolean {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let {
            context.registerReceiver(null, it)
        }

        return batteryStatus?.let {
            val status: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } ?: false
    }

    private fun getOsVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})"
    }

    private fun getFirmwareVersion(): String {
        return firmwareRepository.getFirmwareVersion().toString()
    }

    private fun getForegroundApp(): String {
        return getForegroundApp(context)
    }

    private fun getWifiSSID(): String {
        return wifiManager.connectionInfo.ssid
    }

    private fun getWifiCapabilities(): Map<String, Boolean> {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return if (networkCapabilities != null) {
            mapOf(
                "internet" to networkCapabilities.hasCapability(NET_CAPABILITY_INTERNET),
                "validated" to networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED),
                "captivePortal" to networkCapabilities.hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL)
            )
        } else {
            mapOf(
                "internet" to false,
                "validated" to false,
                "captivePortal" to false
            )
        }
    }

    private fun getAutoStart(): String = autoStartManager.getAutoStartPackage() ?: ""
}
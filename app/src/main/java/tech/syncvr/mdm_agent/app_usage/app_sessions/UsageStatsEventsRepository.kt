package tech.syncvr.mdm_agent.app_usage.app_sessions

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED
import android.app.usage.UsageEvents.Event.CONFIGURATION_CHANGE
import android.app.usage.UsageEvents.Event.DEVICE_SHUTDOWN
import android.app.usage.UsageEvents.Event.DEVICE_STARTUP
import android.app.usage.UsageEvents.Event.FOREGROUND_SERVICE_START
import android.app.usage.UsageEvents.Event.FOREGROUND_SERVICE_STOP
import android.app.usage.UsageEvents.Event.KEYGUARD_HIDDEN
import android.app.usage.UsageEvents.Event.KEYGUARD_SHOWN
import android.app.usage.UsageEvents.Event.NONE
import android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE
import android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE
import android.app.usage.UsageEvents.Event.SHORTCUT_INVOCATION
import android.app.usage.UsageEvents.Event.STANDBY_BUCKET_CHANGED
import android.app.usage.UsageEvents.Event.USER_INTERACTION
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.localcache.ILocalCacheSource
import tech.syncvr.mdm_agent.localcache.SharedPrefsLocalCacheSource
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.repositories.auto_start.AutoStartManager
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsEventsRepository @Inject constructor(
    private val usageStatsManager: UsageStatsManager,
    private val analyticsLogger: AnalyticsLogger,
    private val localCacheSource: ILocalCacheSource,
    private val autoStartManager: AutoStartManager,
    private val packageManager: PackageManager
) {

    companion object {
        const val TAG = "AppUsageSessions:EventsRepository"
        const val QUERY_EVENTS_INTERVAL = 5 * 1000L
    }

    private var bootEventTime: Long = 0L

    private val calculator = AppUsageSessionCalculator(localCacheSource, analyticsLogger)

    init {
        Log.i(TAG, "I have been created")
    }

    private fun queryUsageEvents() {
        Log.i(TAG, "Querying Usage Events!")
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(
            localCacheSource.getLastSystemEventQueryTime(),
            now
        )

        val event: UsageEvents.Event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            val eventType = eventTypeToString(event.eventType)
            Log.i(
                TAG,
                "$eventType at ${Date(event.timeStamp)} for app ${event.packageName} - ${event.className}."
            )
            // not interested in logging these to backend, these are thrown in heaps on device boot
            if (event.eventType != STANDBY_BUCKET_CHANGED) {
                analyticsLogger.log(
                    "UsageEvent", hashMapOf(
                        "usageEventType" to eventType,
                        "timestamp" to event.timeStamp,
                        "packageName" to event.packageName,
                        "className" to event.className
                    )
                )
            }

            calculator.handleEvent(event)
        }
        calculator.handleNoop(now)

        localCacheSource.setLastSystemEventQueryTime(now)
    }

    private fun handleAppStart(isPicoG24K: Boolean) {
        //NOTE: this calculation can be screwed if the system time changes, see
        // https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime()
        // For now I don't think this is problematic, it will not happen often.
        val deviceBootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()

        if (localCacheSource.getLastSystemEventQueryTime() > deviceBootTime) {
            Log.i(
                TAG, "App Restart. Querying from cached time: ${
                    Date(localCacheSource.getLastSystemEventQueryTime())
                }"
            )
            // in this case, the app restarted while the device was running
            // we may expect the events for what happened to be returned by Android OS
            // I guess we don't need to do anything? Even a cached session should be ended by the events we get from Android.
            // Only exception would be if the app didn't run for many days, and the event ending the app in the current cached session has already been discarded by Android. But this should be impossible, as we have numerous Workers that should make the app restart.
        } else {
            // if it's a fresh boot, we start querying from boot time. we're not interested in what happened before.
            Log.i(TAG, "Device Reboot. Querying from boot time: $deviceBootTime")
            localCacheSource.setLastSystemEventQueryTime(deviceBootTime)
            calculator.onDeviceBoot()

            // On Pico G24K, we seem not to receive an ACTIVITY_START event when the device is in auto start
            // Here this is simulated.
            if (isPicoG24K) {
                val autoStartPackage = autoStartManager.getAutoStartPackage()
                if (autoStartPackage != null) {
                    val className = getMainActivity(autoStartPackage)
                    calculator.startSessionAtDeviceBoot(autoStartPackage, className, deviceBootTime)
                }
            }
        }
    }

    fun startPeriodicQueryUsageEvents(isPicoG24K: Boolean) {
        MDMAgentApplication.coroutineScope.launch {
            handleAppStart(isPicoG24K)
            Log.i(TAG, "DEVICE_STARTUP at $bootEventTime by ACTION_BOOT_COMPLETED")
            while (true) {
                queryUsageEvents()
                //TODO: it seems we might miss events sometimes. See if we can validate the currently active app by using UsageStatsManager.queryUsageStats
                delay(QUERY_EVENTS_INTERVAL)
            }
        }
    }

    //TODO: we probably dont need this method anymore
    fun onBootCompleted() {
        bootEventTime = System.currentTimeMillis()
    }

    fun onShutdown() {
        Log.i(TAG, "DEVICE_SHUTDOWN at ${Date(System.currentTimeMillis())} by ACTION_SHUTDOWN")
        calculator.onDeviceShutdown()
    }

    private fun eventTypeToString(eventType: Int): String {
        return when (eventType) {
            1 -> "ACTIVITY_RESUMED"
            2 -> "ACTIVITY_PAUSED"
            ACTIVITY_STOPPED -> "ACTIVITY_STOPPED"
            CONFIGURATION_CHANGE -> "CONFIGURATION_CHANGE"
            DEVICE_SHUTDOWN -> "DEVICE_SHUTDOWN"
            DEVICE_STARTUP -> "DEVICE_STARTUP"
            FOREGROUND_SERVICE_START -> "FOREGROUND_SERVICE_START"
            FOREGROUND_SERVICE_STOP -> "FOREGROUND_SERVICE_STOP"
            KEYGUARD_HIDDEN -> "KEYGUARD_HIDDEN"
            KEYGUARD_SHOWN -> "KEYGUARD_SHOWN"
            NONE -> "NONE"
            SCREEN_INTERACTIVE -> "SCREEN_INTERACTIVE"
            SCREEN_NON_INTERACTIVE -> "SCREEN_NON_INTERACTIVE"
            SHORTCUT_INVOCATION -> "SHORTCUT_INVOCATION"
            STANDBY_BUCKET_CHANGED -> "STANDBY_BUCKET_CHANGED"
            USER_INTERACTION -> "USER_INTERACTION"
            else -> "UNKNOWN"
        }
    }

    private fun getMainActivity(packageName: String): String {
        val intent = Intent(Intent.ACTION_MAIN).also {
            it.setPackage(packageName)
        }
        val resolveInfo = packageManager.resolveActivity(intent, 0)
            ?: return "com.unity3d.player.UnityPlayerNativeActivityPico"

        return resolveInfo.activityInfo.name
    }

}
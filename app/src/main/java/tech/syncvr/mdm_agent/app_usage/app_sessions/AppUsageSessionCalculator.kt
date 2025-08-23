package tech.syncvr.mdm_agent.app_usage.app_sessions

import android.app.usage.UsageEvents
import android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED
import android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
import android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED
import android.util.Log
import kotlinx.serialization.Serializable
import tech.syncvr.mdm_agent.localcache.ILocalCacheSource
import tech.syncvr.mdm_agent.localcache.SharedPrefsLocalCacheSource
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import java.util.Date

class AppUsageSessionCalculator(
    private val localCacheSource: ILocalCacheSource,
    private val analyticsLogger: AnalyticsLogger
) {

    companion object {
        private const val TAG = "AppUsageSessions:Calculator"
        private const val MINIMUM_SESSION_TIME = 5 * 1000L // 5 seconds

        // DO NOT CHANGE THESE VALUES! BACKEND DEPENDS ON THEM!
        private const val APP_SESSION_EVENT_TYPE = "AppUsageSession"
        private const val APP_SESSION_APP_PACKAGE_NAME = "appPackageName"
        private const val APP_SESSION_START_TIME = "sessionStartTime"
        private const val APP_SESSION_END_TIME = "sessionEndTime"
        private const val APP_SESSION_DURATION = "sessionDuration"
    }

    @Serializable
    data class AppUsageSession(
        val appPackageName: String,
        val sessionStartTime: Long,
        val sessionEndTime: Long,
        val sessionDuration: Long
    ) {
        override fun toString(): String {
            return "[AppUsageSession] $appPackageName Start: ${Date(sessionStartTime)} End: ${
                Date(sessionEndTime)
            } Duration: $sessionDuration"
        }
    }

    @Serializable
    data class ActiveAppUsageSession(
        val appPackageName: String,
        val appClassName: String,
        val sessionStartTime: Long,
        val sessionDuration: Long
    ) {
        override fun toString(): String {
            return "[ActiveSession] $appPackageName-$appClassName Start: ${Date(sessionStartTime)} Duration: $sessionDuration"
        }
    }

    @Serializable
    data class VisibleApp(
        val appPackageName: String,
        val appClassName: String,
        val visibleStartTime: Long,
    ) {
        fun toLogString(): String {
            return "[AppVisible] $appPackageName-$appClassName since $visibleStartTime"
        }
    }


    fun onDeviceBoot() {
        // If the device boots, check if we have an active session still cached from before shutdown, and end it.
        getCachedActiveSession()?.let {
            endActiveSession(it, null)
        }
        // Also remove any cached visible apps, these are not relevant anymore.
        clearVisibleApps()
    }

    // This method is specifically for Pico G2 4K device boot with auto-start enabled. In that situation we don't
    // seem to receive an ACTIVITY_RESUMED event, so we force start a session.
    fun startSessionAtDeviceBoot(
        appPackageName: String,
        appClassName: String,
        sessionStartTime: Long
    ) {
        if (getCachedActiveSession() != null) {
            Log.e(TAG, "Pico G24K startSessionAtDeviceBoot, but already session active!")
        } else {
            startActiveSession(appPackageName, appClassName, sessionStartTime)
        }

    }

    fun handleEvent(event: UsageEvents.Event) {
        when (event.eventType) {
            ACTIVITY_RESUMED -> handleActivityResumedEvent(event)
            ACTIVITY_PAUSED, ACTIVITY_STOPPED -> handleActivityPausedEvent(event)
        }
    }

    // after all events during a sequence have been handled, this method must be called.
    fun handleNoop(timestamp: Long) {
        getCachedActiveSession()?.also {
            continueActiveSession(it, timestamp)
        }
    }

    fun onDeviceShutdown() {
        Log.i(TAG, "Handle Device Shutdown")
        getCachedActiveSession()?.also {
            endActiveSession(it, System.currentTimeMillis())
        }
    }

    private fun handleActivityResumedEvent(event: UsageEvents.Event) {
        // not too happy about this, but I think it's legit
        // on Pico G2 4K, on device boot, an ACTIVITY_RESUMED event is raised for com.android.settings - com.android.settings.FallbackHome
        // this app / activity is not actually visible at any point, and does not receive an ACTIVITY_PAUSED
        // event. This causes it to remain in the VisibleApp list and keeps coming back to fuck shit up.
        // Since it's not supposed to be launched ever, we might as well just ignore it, on all devices.
        if (event.packageName == "com.android.settings" && event.className == "com.android.settings.FallbackHome") {
            return
        }

        addVisibleApp(
            VisibleApp(
                event.packageName,
                event.className,
                event.timeStamp
            )
        )

        val activeSession = getCachedActiveSession()

        if (activeSession == null) {
            startActiveSession(event.packageName, event.className, event.timeStamp)
        } else {
            if (event.packageName != activeSession.appPackageName) {
                endActiveSession(activeSession, event.timeStamp)
                startActiveSession(event.packageName, event.className, event.timeStamp)
            } else {
                continueActiveSession(activeSession, event.timeStamp, event.className)
            }
        }
    }

    private fun handleActivityPausedEvent(event: UsageEvents.Event) {

        removeVisibleApp(event.packageName, event.className)

        val activeSession = getCachedActiveSession()

        if (activeSession != null && activeSession.appPackageName == event.packageName && activeSession.appClassName == event.className) {
            endActiveSession(activeSession, event.timeStamp)

            // if another app is still visible, we start a session for that app immediately.
            // this is to deal with overlays, that when closed do not trigger an ACTIVITY_RESUMED
            // event on the Activity visible behind it.
            getFirstVisibleApp()?.also {
                startActiveSession(
                    it.appPackageName,
                    it.appClassName,
                    event.timeStamp
                )
            }
        }
    }

    private fun startActiveSession(
        appPackageName: String,
        appClassName: String,
        sessionStartTime: Long
    ) {
        val newActiveSession = ActiveAppUsageSession(
            appPackageName,
            appClassName,
            sessionStartTime,
            0
        )

        Log.i(TAG, "Start Active Session: $newActiveSession")
        setCachedActiveSession(newActiveSession)
    }

    private fun continueActiveSession(
        activeSession: ActiveAppUsageSession,
        sessionCurrentTime: Long,
        appClassName: String = ""
    ) {
        val newActiveSession = ActiveAppUsageSession(
            activeSession.appPackageName,
            appClassName.ifEmpty { activeSession.appClassName },
            activeSession.sessionStartTime,
            sessionCurrentTime - activeSession.sessionStartTime
        )

        Log.i(TAG, "Continue Active Session: $newActiveSession")
        setCachedActiveSession(newActiveSession)
    }

    private fun endActiveSession(activeSession: ActiveAppUsageSession, sessionEndTime: Long?) {
        val sessionDuration = if (sessionEndTime != null) {
            sessionEndTime - activeSession.sessionStartTime
        } else {
            activeSession.sessionDuration
        }

        val endTime =
            sessionEndTime ?: (activeSession.sessionStartTime + activeSession.sessionDuration)

        Log.i(TAG, "End Active Session: $activeSession")

        // We might get a lot of artefacts on Pico 4 when it goes out of sleep mode, and tries
        // to restart tracking. Use a minimum session time to filter out this noise.
        if (sessionDuration >= MINIMUM_SESSION_TIME) {
            storeFinishedSession(
                AppUsageSession(
                    activeSession.appPackageName,
                    activeSession.sessionStartTime,
                    endTime,
                    sessionDuration
                )
            )
        }

        clearCachedActiveSession()
    }

    private fun getCachedActiveSession(): ActiveAppUsageSession? {
        return localCacheSource.getActiveAppSession()
    }

    private fun setCachedActiveSession(session: ActiveAppUsageSession) {
        localCacheSource.setActiveAppSession(session)
    }

    private fun clearCachedActiveSession() {
        localCacheSource.clearActiveAppSession()
    }


    // The list of activities that are currently visible. Generally, this should only contain the
    // activity of the current active session. However, when an overlay is displayed, it may contain
    // more than one activity.
    private fun addVisibleApp(visibleApp: VisibleApp) {
        val visibleApps = localCacheSource.getVisibleApps()
        localCacheSource.setVisibleApps(visibleApps + visibleApp)
    }

    private fun removeVisibleApp(appPackageName: String, appClassName: String) {
        val visibleApps = localCacheSource.getVisibleApps()
        localCacheSource.setVisibleApps(visibleApps.filterNot {
            it.appPackageName == appPackageName && it.appClassName == appClassName
        })
    }

    private fun clearVisibleApps() {
        localCacheSource.setVisibleApps(listOf())
    }

    private fun getFirstVisibleApp(): VisibleApp? {
        val visibleApps = localCacheSource.getVisibleApps()
        return if (visibleApps.isNotEmpty()) {
            visibleApps.last()
        } else {
            null
        }
    }

    private fun storeFinishedSession(session: AppUsageSession) {
        analyticsLogger.log(
            APP_SESSION_EVENT_TYPE, hashMapOf(
                APP_SESSION_APP_PACKAGE_NAME to session.appPackageName,
                APP_SESSION_START_TIME to session.sessionStartTime,
                APP_SESSION_END_TIME to session.sessionEndTime,
                APP_SESSION_DURATION to session.sessionDuration
            )
        )
        Log.i(TAG, "Store Finished Session: $session")
    }
}
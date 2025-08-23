package tech.syncvr.mdm_agent.repositories.auto_start.oculus.helpers

import android.app.usage.UsageEvents
import android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.repositories.auto_start.oculus.models.AppWithClass

class ForegroundAppObserver(private val context: Context) {
    companion object {
        private const val TAG = "ForegroundAppObserver"
        private const val FOREGROUND_APP_CHECK_TIMEOUT = 1000L // ms
    }

    var currentForegroundApp = AppWithClass(); private set
    var previousForegroundApp = AppWithClass(); private set

    fun start() {
        startForegroundAppCheckCoroutine()
    }

    private fun startForegroundAppCheckCoroutine() {
        //TODO: Coroutine launched on the Main thread. Might want to verify this is correct behavior
        MDMAgentApplication.coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                delay(FOREGROUND_APP_CHECK_TIMEOUT)
                doForegroundAppCheck()
            }
        }
    }

    private fun doForegroundAppCheck() {
        val detectedForegroundApp = getForegroundApp()
        if (detectedForegroundApp == currentForegroundApp) {
            previousForegroundApp = currentForegroundApp
            currentForegroundApp = detectedForegroundApp
        }
        //TODO: any hooks for objects that want callbacks when a new foreground app is detected
        //TODO: should be called here.
    }

    private fun getForegroundApp(): AppWithClass {
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        var app = currentForegroundApp


        //TODO: we query for any events of the past 10 seconds. Since we do this every second
        //TODO: we will generally parse every event 10x. Could probably reduce this to 2s.
        val currentTime = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(currentTime - 10000, currentTime)

        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            if (event.eventType == ACTIVITY_RESUMED) {
                app = AppWithClass(event)
            }
        }

        return app
    }
}

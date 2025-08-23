package tech.syncvr.mdm_agent.device_management.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaunchSyncVRAppService @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val TAG = "AutoLaunchAppRepository"
    }

    fun launchSyncVRStartOnceApps() {
        val intent = Intent("tech.syncvr.intent.START_ONCE")
        val resolveInfoList =
            context.packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)

        resolveInfoList.forEach {
            intent.component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
            context.sendBroadcast(intent)
        }

    }
}
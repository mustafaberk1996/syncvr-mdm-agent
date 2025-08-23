package tech.syncvr.mdm_agent.receivers

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.device_management.MDMWorkManager
import javax.inject.Inject

@AndroidEntryPoint
class DownloadCompleteReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DownloadCompleteReceiver"
    }

    @Inject
    lateinit var mdmWorkManager: MDMWorkManager

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

            Log.d(TAG, "Received DOWNLOAD_COMPLETE for id $downloadId")

            // This is ugly
            MDMAgentApplication.coroutineScope.launch {
                mdmWorkManager.scheduleWorkDownloadCompleted(downloadId)
            }
        }
    }
}
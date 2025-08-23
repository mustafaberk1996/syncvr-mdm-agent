package tech.syncvr.mdm_agent.receivers

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import tech.syncvr.mdm_agent.MDMAgentApplication
import tech.syncvr.mdm_agent.device_management.MDMWorkManager
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import javax.inject.Inject

@AndroidEntryPoint
class PackageInstallerSessionStatusReceiver : BroadcastReceiver() {

    @Inject
    lateinit var analyticsLogger: AnalyticsLogger

    companion object {
        private const val TAG = "PackageInstallerSessionStatusReceiver"
        const val INSTALLER_SESSION_STATUS_CHANGED =
            "tech.syncvr.intent.INSTALLER_SESSION_STATUS_CHANGED"
        const val UNINSTALL_STATUS =
            "tech.syncvr.intent.UNINSTALL_STATUS"
        const val UNINSTALL_REQUEST_CODE = 23491
        const val INSTALL_REQUEST_CODE = 23492
    }

    @Inject
    lateinit var mdmWorkManager: MDMWorkManager

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "Received broadcast!")
        if (intent == null || intent.extras == null) {
            return
        }
        val extras: Bundle = intent.extras!!
        when (intent.action) {
            INSTALLER_SESSION_STATUS_CHANGED -> {
                val sessionId = extras.getInt(PackageInstaller.EXTRA_SESSION_ID)
                val status = extras.getInt(PackageInstaller.EXTRA_STATUS)
                val message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val downloadId = extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID)

                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        analyticsLogger.logError(
                            AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                            hashMapOf("info" to "Pending user action!")
                        )
                        Log.d(
                            TAG,
                            "Install is pending user action! Shouldn't happen: $status - $message"
                        )
                        context!!.packageManager.packageInstaller.abandonSession(sessionId)
                    }

                    PackageInstaller.STATUS_SUCCESS -> {
                        mdmWorkManager.scheduleWorkAppInstalled(downloadId)
                        analyticsLogger.log(
                            AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                            hashMapOf("info" to (message ?: ""))
                        )
                        Log.d(TAG, "Installing succeeded: $status - $message")
                    }

                    PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_BLOCKED, PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_STORAGE -> {
                        analyticsLogger.logError(
                            AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                            hashMapOf("info" to (message ?: ""))
                        )
                        Log.e(TAG, "Installing failed: $status - $message")
                    }

                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        analyticsLogger.logError(
                            AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                            hashMapOf("info" to (message ?: ""))
                        )
                    }

                    else -> {
                        Log.d(TAG, "Unknown Status! $status")
                    }
                }
            }

            UNINSTALL_STATUS -> {
                val pkgName = extras.getString(PackageInstaller.EXTRA_PACKAGE_NAME, "")
                val statusMsg = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE, "")
                val status = extras.getInt(PackageInstaller.EXTRA_STATUS, -1)
                val payload: HashMap<String, Any> = hashMapOf(
                    "packageName" to pkgName,
                    "statusMsg" to statusMsg,
                    "status" to status
                )
                analyticsLogger.log(
                    "UninstallCleanup",
                    payload
                )
                Log.d(TAG, "Uninstalled $pkgName with \"$statusMsg\" and status $status")
            }
        }
    }
}
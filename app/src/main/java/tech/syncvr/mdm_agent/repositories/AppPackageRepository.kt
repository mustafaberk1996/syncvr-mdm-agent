package tech.syncvr.mdm_agent.repositories

import android.app.DownloadManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Process.myUid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.MDMAgentApplication.Companion.exHandler
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.device_management.services.AppInstallService
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.receivers.DeviceOwnerReceiver
import tech.syncvr.mdm_agent.utils.enqueueApk
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPackageRepository @Inject constructor(
    @ApplicationContext val context: Context,
    private val analyticsLogger: AnalyticsLogger
) {

    companion object {
        private const val TAG = "AppPackageRepository"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exHandler)

    fun getConfigurationStatus(configuredManagedApps: List<ManagedAppPackage>): List<ManagedAppPackage> {
        val installedPackages = getInstalledPackages()
        // make copy of configured apps list and check if they are already installed
        val apps = configuredManagedApps.map {
            val installedApp = it.copy()
            installedApp.installedAppVersionCode =
                getInstalledVersion(installedPackages, installedApp.appPackageName)
            if (installedApp.appVersionCode <= installedApp.installedAppVersionCode) {
                installedApp.status = ManagedAppPackage.Status.INSTALLED
            } else {
                installedApp.status = ManagedAppPackage.Status.NOT_INSTALLED
            }

            /**
             * TODO: no way yet to correctly check if all permission we can provide were indeed provided, and so
             * applications that were installed will always remain in NEED_PERMISSIONS mode. For now, just disable this
             * and silently try to give permissions to installed apps.
            if (p.status == ManagedAppPackage.Status.INSTALLED && getMissingPermissions(p).isNotEmpty()) {
            ManagedAppPackage.Status.NEED_PERMISSIONS
            }
             */

            installedApp
        }

        // for those apps not installed, check if they are downloading / downloaded / installing
        val cursor = getDownloadQueryCursor()
        for (app in apps) {
            // apps that are already installed don't need further investigation
            if (app.status == ManagedAppPackage.Status.INSTALLED || app.status == ManagedAppPackage.Status.NEED_PERMISSIONS) {
                continue
            }
            app.progress = getDownloadProgress(cursor, app.releaseDownloadURL)
            when {
                app.progress == 100L -> {
                    app.status = ManagedAppPackage.Status.DOWNLOADED
                }

                app.progress >= 0 -> {
                    app.status = ManagedAppPackage.Status.DOWNLOADING
                }

                else -> {
                    // app.progress -1, not downloading it!
                }
            }
        }

        // for those apps that are fully downloaded, check if they are installing
        val sessionInfoList = context.packageManager.packageInstaller.mySessions
        sessionInfoList.filterNot { it.isActive }.filter { it.originatingUid == myUid() }.forEach {
            context.packageManager.packageInstaller.abandonSession(it.sessionId)
        }
        for (app in apps) {
            // the only apps that might be installing are those that are already downloaded
            if (app.status == ManagedAppPackage.Status.DOWNLOADED) {
                if (isPackageInstalling(app, sessionInfoList)) {
                    app.status = ManagedAppPackage.Status.INSTALLING
                }
            }
        }

        cursor.close()
        return apps
    }

    fun cancelAllDownloads() {
        val query = DownloadManager.Query()
        query.setFilterByStatus(DownloadManager.STATUS_FAILED or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING)
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        coroutineScope.launch(Dispatchers.IO) {
            val cursor = downloadManager.query(query)
            Log.d(TAG, "cancelAllDownloads")
            cursor.use {
                while (cursor.moveToNext()) {
                    val long =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val title =
                        cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                    downloadManager.remove(long)
                    Log.d(TAG, "cancelAllDownloads, canceled download: $long $title")
                }
            }
        }
    }

    fun downloadPackage(managedAppPackage: ManagedAppPackage) {
        val cursor = getDownloadQueryCursor()
        cursor.use {
            while (cursor.moveToNext()) {
                val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
                if (uri == managedAppPackage.releaseDownloadURL) {
                    // we're already downloading this file!
                    Log.d(TAG, "Already downloading this file! ${managedAppPackage.appPackageName}")
                    return
                }
            }

            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            try {
                downloadManager.enqueueApk(
                    context = context,
                    url = managedAppPackage.releaseDownloadURL,
                    fileName = "${managedAppPackage.appPackageName}.apk"
                )
            } catch (e: Exception) {
                analyticsLogger.logErrorMsg(
                    AnalyticsLogger.Companion.LogEventType.DOWNLOAD_EVENT,
                    "can't download app: ${managedAppPackage.appPackageName} at url: ${managedAppPackage.releaseDownloadURL}"
                )
            }
        }
    }

    fun removeDownload(downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadId)
    }

    fun grantPermissions(managedAppPackage: ManagedAppPackage): Boolean {
        return getMissingPermissions(managedAppPackage).map {
            if (isPermissionGrantable(it)) {
                grantPermission(managedAppPackage, it)
            } else {
                true
            }
        }.fold(true) { b1, b2 -> b1 && b2 }
    }

    private fun getInstalledPackages(): List<ManagedAppPackage> {
        val installedPackages = context.packageManager.getInstalledPackages(0)
        return installedPackages.map { packageInfo ->
            ManagedAppPackage(
                packageInfo.packageName,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            )
        }
    }

    private fun getInstalledVersion(
        installedManagedApps: List<ManagedAppPackage>,
        appPackageName: String
    ): Long {
        for (installedApp in installedManagedApps) {
            if (installedApp.appPackageName == appPackageName) {
                return installedApp.appVersionCode
            }
        }

        return -1
    }

    private fun getDownloadQueryCursor(): Cursor {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadQuery = DownloadManager.Query()
        return downloadManager.query(downloadQuery)
    }

    private fun getDownloadProgress(cursor: Cursor, downloadURL: String): Long {
        cursor.moveToPosition(-1)

        while (cursor.moveToNext()) {
            val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
            if (uri == downloadURL) {
                return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> 100L
                    DownloadManager.STATUS_FAILED -> {

                        // log that this download failed and why.
                        val reason =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        analyticsLogger.logErrorMsg(
                            AnalyticsLogger.Companion.LogEventType.DOWNLOAD_EVENT,
                            "Download for $downloadURL has failed with reason: $reason - ${
                                reasonToString(
                                    reason
                                )
                            }"
                        )

                        // remove the download. the MDM will take care of starting it again.
                        val downloadManager =
                            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val downloadId =
                            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                        downloadManager.remove(downloadId)

                        // return -1, to indicate the download is not present
                        -1L
                    }

                    DownloadManager.STATUS_RUNNING -> {
                        return getDownloadProgressPercentage(cursor)
                    }

                    DownloadManager.STATUS_PAUSED -> {
                        val reason =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        analyticsLogger.logMsg(
                            AnalyticsLogger.Companion.LogEventType.DOWNLOAD_EVENT,
                            "Download for $downloadURL is paused with reason: $reason - ${
                                reasonToString(
                                    reason
                                )
                            }"
                        )
                        return getDownloadProgressPercentage(cursor)
                    }

                    DownloadManager.STATUS_PENDING -> {
                        analyticsLogger.logMsg(
                            AnalyticsLogger.Companion.LogEventType.DOWNLOAD_EVENT,
                            "Download for $downloadURL is pending."
                        )
                        0L
                    }

                    else -> 0L
                }
            }
        }

        return -1
    }

    private fun getDownloadProgressPercentage(cursor: Cursor): Long {
        val bytesDownloaded =
            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val totalBytes =
            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        return if (totalBytes > 0) {
            bytesDownloaded * 100 / totalBytes
        } else {
            0L
        }
    }

    fun getDownloadInstallInfo(managedAppPackage: ManagedAppPackage): AppInstallService.InstallInfo? {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadQuery = DownloadManager.Query()
        val cursor = downloadManager.query(downloadQuery)

        cursor.use {
            while (it.moveToNext()) {
                val uri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
                if (uri == managedAppPackage.releaseDownloadURL) {
                    return AppInstallService.InstallInfo(
                        it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)),
                        it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                        it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    )
                }
            }
            return null
        }
    }

    private fun isPackageInstalling(
        managedAppPackage: ManagedAppPackage,
        sessionInfoList: List<PackageInstaller.SessionInfo>
    ): Boolean {
        for (sessionInfo in sessionInfoList) {
            Log.d(TAG, "SessionInfo appPackageName: ${sessionInfo.appPackageName}")
            Log.d(TAG, "SessionInfo appLabel: ${sessionInfo.appLabel}")
            if (sessionInfo.appPackageName == managedAppPackage.appPackageName &&
                sessionInfo.originatingUid == myUid()
            ) {
                return sessionInfo.isActive
            }
        }

        return false
    }

    private fun getMissingPermissions(managedAppPackage: ManagedAppPackage): List<String> {
        val packageInfo = context.packageManager.getPackageInfo(
            managedAppPackage.appPackageName,
            PackageManager.GET_PERMISSIONS
        )
        val missingPermissions = ArrayList<String>()

        packageInfo.requestedPermissions?.let { // if the app requests no permissions this call return null
            for (i in packageInfo.requestedPermissions.indices) {
                if ((packageInfo.requestedPermissionsFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != PackageInfo.REQUESTED_PERMISSION_GRANTED) {
                    missingPermissions.add(packageInfo.requestedPermissions[i])
                }
            }
        }

        return missingPermissions
    }

    private fun grantPermission(managedAppPackage: ManagedAppPackage, permission: String): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.setPermissionGrantState(
            ComponentName(context, DeviceOwnerReceiver::class.java),
            managedAppPackage.appPackageName,
            permission,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )
    }

    //TODO: check if we can grant this permission... hunch is that we can NOT grant
    // protection level SIGNATURE and PRIVILEGED
    private fun isPermissionGrantable(permission: String): Boolean {
        return try {
            context.packageManager.getPermissionInfo(permission, PackageManager.GET_META_DATA)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Permission $permission can not be found on the system!")
            false
        }
    }

    private fun statusToString(status: Int): String {
        return when (status) {
            DownloadManager.STATUS_FAILED -> "FAILED"
            DownloadManager.STATUS_PAUSED -> "PAUSED"
            DownloadManager.STATUS_PENDING -> "PENDING"
            DownloadManager.STATUS_RUNNING -> "RUNNING"
            DownloadManager.STATUS_SUCCESSFUL -> "SUCCESS"
            else -> "UNKNOWN"
        }
    }

    private fun reasonToString(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "ERROR_CANNOT_RESUME"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "ERROR_CANNOT_RESUME"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "ERROR_FILE_ALREADY_EXISTS"
            DownloadManager.ERROR_FILE_ERROR -> "ERROR_FILE_ERROR"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "ERROR_HTTP_DATA_ERROR"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "ERROR_INSUFFICIENT_SPACE"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "ERROR_TOO_MANY_REDIRECTS"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "ERROR_UNHANDLED_HTTP_CODE"
            DownloadManager.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "PAUSED_QUEUED_FOR_WIFI"
            DownloadManager.PAUSED_UNKNOWN -> "PAUSED_UNKNOWN"
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "PAUSED_WAITING_FOR_NETWORK"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "PAUSED_WAITING_TO_RETRY"
            else -> "NO REASON"
        }
    }
}

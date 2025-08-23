package tech.syncvr.mdm_agent.device_management.services

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.syncvr.mdm_agent.device_management.configuration.models.ManagedAppPackage
import tech.syncvr.mdm_agent.logging.AnalyticsLogger
import tech.syncvr.mdm_agent.receivers.PackageInstallerSessionStatusReceiver
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInstallService @Inject constructor(
    @ApplicationContext private val context: Context,
    val analyticsLogger: AnalyticsLogger
) {

    companion object {
        private const val TAG = "AppInstallService"
    }

    data class InstallInfo(val uri: String, val size: Long, val downloadId: Long)


    fun uninstallPackage(packageName: String) {
        val intent = Intent(context, PackageInstallerSessionStatusReceiver::class.java).also {
            it.action = PackageInstallerSessionStatusReceiver.UNINSTALL_STATUS
        }

        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // maybe you can set these flags for all versions of Android, but I don't want to take the risk now. The mutability flag is mandatory for from Android 31
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PackageInstallerSessionStatusReceiver.UNINSTALL_REQUEST_CODE,
            intent,
            flags
        )
        val packageInstaller: PackageInstaller = context.packageManager.packageInstaller
        try {
            packageInstaller.uninstall(packageName, pendingIntent.intentSender)
        } catch (e: IllegalArgumentException) {
            val msg =
                "${e.javaClass.simpleName} installing ${packageName}: \"${e.message}\""
            Log.e(TAG, msg, e)
            analyticsLogger.logErrorMsg(AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT, msg)
        }
    }

    fun installPackage(managedAppPackage: ManagedAppPackage, installInfo: InstallInfo) {

        val session = openInstallSessionWithName(managedAppPackage.appPackageName) ?: return
        addApkToInstallSession(installInfo, session)

        // Create an install status receiver.
        val statusReceiver = installSessionStatusPendingIntentSender(installInfo.downloadId)

        // Commit the session (this will start the installation workflow).
        try {
            session.commit(statusReceiver)
        } catch (e: Exception) {
            val msg =
                "${e.javaClass.simpleName} installing ${managedAppPackage.appPackageName}: \"${e.message}\""
            Log.e(TAG, msg, e)
            when (e) {
                is IOException, is IllegalArgumentException, is RuntimeException -> {
                    session.abandon()
                    analyticsLogger.logErrorMsg(
                        AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                        msg
                    )
                }

                else -> throw e
            }
        }
    }

    private fun openInstallSessionWithName(appPackageName: String): PackageInstaller.Session? {
        val packageInstaller: PackageInstaller = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).also {
                it.setAppPackageName(appPackageName)
            }
        return try {
            val sessionId = packageInstaller.createSession(params)
            packageInstaller.openSession(sessionId)
        } catch (e: IOException) {
            Firebase.crashlytics.recordException(e)
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                "IOException trying to install $appPackageName: ${e.message}"
            )
            null
        } catch (e: SecurityException) {
            Firebase.crashlytics.recordException(e)
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                "SecurityException trying to install $appPackageName: ${e.message}"
            )
            null
        } catch (e: IllegalArgumentException) {
            Firebase.crashlytics.recordException(e)
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                "IllegalArgumentException trying to install $appPackageName: ${e.message}"
            )
            null
        } catch (e: IllegalStateException) {
            Firebase.crashlytics.recordException(e)
            analyticsLogger.logErrorMsg(
                AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                "IllegalStateException trying to install $appPackageName: ${e.message}"
            )
            null
        }
    }

    private fun installSessionStatusPendingIntentSender(downloadId: Long): IntentSender {
        val intent = Intent().also {
            it.action = PackageInstallerSessionStatusReceiver.INSTALLER_SESSION_STATUS_CHANGED
            it.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
        }

        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // maybe you can set these flags for all versions of Android, but I don't want to take the risk now. The mutability flag is mandatory for from Android 31
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                PackageInstallerSessionStatusReceiver.INSTALL_REQUEST_CODE,
                intent,
                flags
            )

        return pendingIntent.intentSender
    }

    private fun addApkToInstallSession(
        installInfo: InstallInfo,
        session: PackageInstaller.Session
    ) {
        // It's recommended to pass the file size to openWrite(). Otherwise installation may fail
        // if the disk is almost full.
        session.openWrite("package", 0, -1).use { outputStream ->
            val inputStream = try {
                context.contentResolver.openInputStream(Uri.parse(installInfo.uri))
            } catch (e: FileNotFoundException) {
                Firebase.crashlytics.recordException(e)
                analyticsLogger.logErrorMsg(
                    AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                    "FileNotFoundException trying to install ${installInfo.uri}: ${e.message}"
                )
                // this is the best place to catch and handle the FileNotFoundException, which also makes this
                // a logical place to delete the download so we can try again.
                // TODO: this can really benefit from Dependency Injection
                removeDownload(installInfo.downloadId)
                null
            }

            if (inputStream != null) {
                val buffer = ByteArray(16384)
                var n: Int
                while (inputStream.read(buffer).also { n = it } >= 0) {
                    outputStream.write(buffer, 0, n)
                }

                inputStream.close()
            } else {
                analyticsLogger.logErrorMsg(
                    AnalyticsLogger.Companion.LogEventType.INSTALL_EVENT,
                    "Couldn't open an InputStream! trying to install ${installInfo.uri}"
                )
            }
        }
    }

    private fun removeDownload(downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadId)
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}

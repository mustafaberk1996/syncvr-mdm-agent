package tech.syncvr.mdm_agent.utils

import android.app.DownloadManager
import android.app.DownloadManager.Request.NETWORK_MOBILE
import android.app.DownloadManager.Request.NETWORK_WIFI
import android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
import android.content.Context
import android.net.Uri
import android.os.Environment

fun DownloadManager.enqueueApk(
    context: Context,
    url: String,
    fileName: String
): Long {
    val req = DownloadManager.Request(Uri.parse(url)).apply {
        setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        setNotificationVisibility(VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setAllowedNetworkTypes(NETWORK_WIFI or NETWORK_MOBILE)
    }
    return this.enqueue(req)
}

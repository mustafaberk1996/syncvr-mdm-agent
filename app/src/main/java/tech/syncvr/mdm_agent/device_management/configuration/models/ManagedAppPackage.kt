package tech.syncvr.mdm_agent.device_management.configuration.models

import kotlinx.serialization.Serializable

@Serializable
data class ManagedAppPackage(
    val appPackageName: String = "",
    val appVersionCode: Long = 0,
    val releaseDownloadUrl: String = "",
) {
    val releaseDownloadURL: String
        get() = releaseDownloadUrl

    @Serializable
    enum class Status { NOT_INSTALLED, DOWNLOADING, DOWNLOADED, INSTALLING, NEED_PERMISSIONS, INSTALLED }

    var status: Status = Status.NOT_INSTALLED
    var installedAppVersionCode: Long = 0
    var progress: Long = -1

}

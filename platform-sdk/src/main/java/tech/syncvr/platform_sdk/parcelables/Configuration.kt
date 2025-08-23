package tech.syncvr.platform_sdk.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Configuration(val autoStartPackage:String?, val appList: List<App>, val configuredWifis: List<WifiPoint>) : Parcelable

@Parcelize
data class App(val packageName: String, val versionCode: Long, val state: AppState, val alreadyInstalledVersionCode: Long = 0) : Parcelable

@Parcelize
data class WifiPoint(val name: String, val type: String) : Parcelable

sealed class AppState : Parcelable {
    @Parcelize
    object Installed : AppState()

    @Parcelize
    object NotInstalled : AppState()

    @Parcelize
    data class Downloading(val progress: Long) : AppState()

    @Parcelize
    object Installing: AppState()
}
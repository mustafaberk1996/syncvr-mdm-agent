package tech.syncvr.mdm_agent.repositories.auto_start

import android.content.Context


abstract class AutoStartManager(protected val context: Context) {

    companion object {
        private const val TAG = "AutoStartRepository"
    }

    open fun start() {}

    abstract fun getAutoStartPackage(): String?
    abstract fun setAutoStart(autoStartPackageName: String, allowedPackages: List<String>): Boolean
    abstract fun clearAutoStartPackage(): Boolean
    abstract fun requiresReboot(): Boolean
}
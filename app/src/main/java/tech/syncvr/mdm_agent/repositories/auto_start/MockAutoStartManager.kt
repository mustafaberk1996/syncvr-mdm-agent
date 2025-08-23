package tech.syncvr.mdm_agent.repositories.auto_start

import android.content.Context

class MockAutoStartManager(context: Context) : AutoStartManager(context) {

    override fun getAutoStartPackage(): String? {
        return null
    }

    override fun setAutoStart(
        autoStartPackageName: String,
        allowedPackages: List<String>
    ): Boolean {
        return false
    }

    override fun clearAutoStartPackage(): Boolean {
        return false
    }

    override fun requiresReboot(): Boolean {
        return false
    }
}
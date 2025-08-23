package tech.syncvr.mdm_agent.device_management.configuration.models

import kotlinx.serialization.Serializable

@Serializable
data class Configuration(
    val managed: List<ManagedAppPackage> = emptyList(),
    val external: List<ExternalAppPackage> = emptyList(),
    val autoStart: String? = "",
    val wifis: List<WifiPoint> = emptyList()
) {
    companion object {
        fun appsToUninstall(old: Configuration, new: Configuration): List<String> {
            return appsToUninstall(old.managed, new.managed)
        }

        fun appsToUninstall(
            old: List<ManagedAppPackage>,
            new: List<ManagedAppPackage>
        ): List<String> {
            return appsToUninstall(old.map { it.appPackageName }, new.map { it.appPackageName })
        }

        // https://kotlinlang.org/docs/java-to-kotlin-interop.html#handling-signature-clashes-with-jvmname
        @JvmName("appsToUninstallBase")
        private fun appsToUninstall(old: List<String>, new: List<String>): List<String> {
            return old.filterNot { it in new }
        }
    }
}

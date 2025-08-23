package tech.syncvr.platform_sdk

import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tech.syncvr.platform_sdk.parcelables.Configuration


interface ConfigurationCollector {
    fun onConfigurationChanged(value: Configuration?)
}

class BlockingSyncVRPlatformManager(context: Context){
    private val syncVRPlatformManager = SyncVRPlatformManager(context)

    @SuppressWarnings("WeakerAccess")
    fun getSerialNumber(): String? {
        return runBlocking { syncVRPlatformManager.getSerialNumber() }
    }

    @SuppressWarnings("WeakerAccess")
    fun getDepartment(): String? {
        return runBlocking { syncVRPlatformManager.getDepartment() }
    }

    @SuppressWarnings("WeakerAccess")
    fun getCustomer(): String? {
        return runBlocking { syncVRPlatformManager.getCustomer() }
    }

    @SuppressWarnings("WeakerAccess")
    fun addWifiConfiguration(ssid: String, password: String): Boolean {
        return runBlocking { syncVRPlatformManager.addWifiConfiguration(ssid, password) }
    }

    @SuppressWarnings("WeakerAccess")
    fun addOpenWifiConfiguration(ssid: String): Boolean {
        return runBlocking { syncVRPlatformManager.addOpenWifiConfiguration(ssid) }
    }

    @SuppressWarnings("WeakerAccess")
    fun getConfiguration(): Configuration? {
        return runBlocking { syncVRPlatformManager.getConfiguration() }
    }

    fun registerConfigurationCallback(collector: ConfigurationCollector) {
        // how to not leak that job ?
        val job = syncVRPlatformManager.coroutineScope.launch {
            syncVRPlatformManager.configurationFlow.collect {
                collector.onConfigurationChanged(it)
            }
        }
    }
}
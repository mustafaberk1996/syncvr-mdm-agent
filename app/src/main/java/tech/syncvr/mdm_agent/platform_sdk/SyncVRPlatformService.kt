package tech.syncvr.mdm_agent.platform_sdk

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import dagger.hilt.android.AndroidEntryPoint
import tech.syncvr.platform_sdk.ISyncVRPlatform
import tech.syncvr.platform_sdk.ISyncVRPlatformCallback
import tech.syncvr.platform_sdk.parcelables.Configuration
import javax.inject.Inject

@AndroidEntryPoint
class SyncVRPlatformService : Service() {
    companion object {
        val TAG: String = this::class.java.declaringClass.simpleName
    }

    @Inject
    lateinit var platformServiceLogic: PlatformServiceLogic

    override fun onCreate() {
        super.onCreate()
        platformServiceLogic.onCreate()
    }

    private val binder = object : ISyncVRPlatform.Stub() {
        override fun getSerialNo(): String? {
            return platformServiceLogic.getSerialNo()
        }

        override fun getCustomer(): String? {
            return platformServiceLogic.getCustomer()
        }

        override fun getDepartment(): String? {
            return platformServiceLogic.getDepartment()
        }

        override fun getAuthorizationToken(): String? {
            return platformServiceLogic.getSpectatingToken()
        }

        override fun addOpenWifiConfiguration(wifiSsid: String): Boolean {
            return platformServiceLogic.addOpenWifiConfiguration(wifiSsid)
        }

        override fun registerCallback(callback: ISyncVRPlatformCallback?) {
            platformServiceLogic.registerCallback(callback)
        }

        override fun addWifiConfiguration(wifiSsid: String, wifiPassword: String) : Boolean{
            return platformServiceLogic.addWifiConfiguration(wifiSsid, wifiPassword)
        }

        override fun getConfiguration(): Configuration? {
            return platformServiceLogic.getConfiguration()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onDestroy() {
        platformServiceLogic.onDestroy()
    }
}
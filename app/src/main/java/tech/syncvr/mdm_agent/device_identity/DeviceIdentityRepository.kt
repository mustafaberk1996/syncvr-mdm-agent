package tech.syncvr.mdm_agent.device_identity

import android.annotation.SuppressLint
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DeviceIdentityRepository @Inject constructor() {

    @SuppressLint("MissingPermission")
    open fun getDeviceId(): String {
        return Build.getSerial()
    }
}
package tech.syncvr.platform_sdk;

// Declare any non-default types here with import statements
import tech.syncvr.platform_sdk.parcelables.Configuration;
import tech.syncvr.platform_sdk.parcelables.App;

// oneway interface means the PlatformService doesn't wait for reception on the client side
oneway interface ISyncVRPlatformCallback {
    void onConfigurationChanged(in Configuration config);
    //void onDeviceStatusSent(DeviceStatus status);
}
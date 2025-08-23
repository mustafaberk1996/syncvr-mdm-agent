package tech.syncvr.platform_sdk;
import tech.syncvr.platform_sdk.ISyncVRPlatformCallback;
import tech.syncvr.platform_sdk.parcelables.Configuration;
// WARNING: Be thoughtful changing this file
// renaming, reordering or deleting methods will break backward compatibility
// for any application already using Platform SDK
interface ISyncVRPlatform {
    String getSerialNo();
    String getCustomer();
    String getDepartment();
    String getAuthorizationToken();
    boolean addWifiConfiguration(String wifiSsid, String wifiPassword);
    boolean addOpenWifiConfiguration(String openWifiSsid);
    void registerCallback(ISyncVRPlatformCallback callback);
    Configuration getConfiguration();
}
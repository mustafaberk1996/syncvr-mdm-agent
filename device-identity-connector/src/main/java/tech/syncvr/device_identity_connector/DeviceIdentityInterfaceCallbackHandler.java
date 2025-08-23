package tech.syncvr.device_identity_connector;

public interface DeviceIdentityInterfaceCallbackHandler {
    void onServiceBound();

    void onServiceUnbound();

    void onDeviceIdReceived(String deviceId);
}

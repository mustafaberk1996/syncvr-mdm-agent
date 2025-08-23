package tech.syncvr.mdm_config_connector;

public interface PlayAreaConfigInterfaceCallbackHandler {
    void onServiceBound();

    void onServiceUnbound();

    void onPlayAreaSettingReceived(String setting);
}

package tech.syncvr.device_identity_connector;

import static tech.syncvr.mdm_agent.mdm_common.Constants.DEVICE_ID;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

public class DeviceIdentityInterfaceBinder extends Handler {

    private static final String TAG = "DeviceIdentityInterfaceBinder";
    private static final String bindIntentPackageName = "tech.syncvr.mdm_agent";
    private static final String bindIntentClassName = "tech.syncvr.mdm_agent.device_identity.DeviceIdentityAppInterface";

    private static DeviceIdentityInterfaceBinder instance;

    public static DeviceIdentityInterfaceBinder getInstance() {
        if (instance == null) {
            instance = new DeviceIdentityInterfaceBinder();
        }
        return instance;
    }

    private DeviceIdentityInterfaceCallbackHandler callbackHandler = null;
    private Messenger serviceMessenger = null;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            serviceMessenger = new Messenger(iBinder);
            if (callbackHandler != null) {
                callbackHandler.onServiceBound();
                requestDeviceId();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serviceMessenger = null;
            callbackHandler.onServiceUnbound();
        }
    };

    private void requestDeviceId() {
        if (serviceMessenger != null) {

            Message msg = Message.obtain();
            msg.replyTo = new Messenger(this);

            try {
                serviceMessenger.send(msg);
            } catch (RemoteException e) {
                Log.d(TAG, "Sending Request DeviceID message failed!");
                e.printStackTrace();
            }
        }
    }

    public void handleMessage(Message msg) {
        if (callbackHandler != null) {
            String deviceId = msg.getData().getString(DEVICE_ID, "");
            callbackHandler.onDeviceIdReceived(deviceId);
        }
    }

    private boolean bind(@NonNull DeviceIdentityInterfaceCallbackHandler handler, @NonNull Context c) {
        callbackHandler = handler;

        Intent intent = new Intent();
        intent.setClassName(bindIntentPackageName, bindIntentClassName);

        boolean isBound = false;
        try {
            isBound = c.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            Log.e(TAG, "App does not have permission to bind to service!");
        }

        if (!isBound) {
            Log.d(TAG, "isBound = false, either missing permission to bind, or couldn't find service!");
        }

        return isBound;
    }
}

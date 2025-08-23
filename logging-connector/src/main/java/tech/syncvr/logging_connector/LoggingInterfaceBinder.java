package tech.syncvr.logging_connector;

import static tech.syncvr.mdm_agent.mdm_common.Constants.ANALYTICS_EVENT_TYPE_KEY;
import static tech.syncvr.mdm_agent.mdm_common.Constants.ANALYTICS_PAYLOAD_KEY;
import static tech.syncvr.mdm_agent.mdm_common.Constants.PACKAGE_NAME_KEY;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

public class LoggingInterfaceBinder extends Handler {

    private static final String TAG = "LoggingInterfaceBinder";
    private static final String bindIntentPackageName = "tech.syncvr.mdm_agent";
    private static final String bindIntentClassName = "tech.syncvr.mdm_agent.logging.LoggingAppInterface";

    private static LoggingInterfaceBinder instance;

    public static LoggingInterfaceBinder getInstance() {
        if (instance == null) {
            instance = new LoggingInterfaceBinder();
        }

        return instance;
    }

    private LoggingInterfaceCallbackHandler callbackHandler = null;
    private Messenger serviceMessenger = null;
    private String packageName;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            serviceMessenger = new Messenger(iBinder);
            if (callbackHandler != null) {
                callbackHandler.onServiceBound();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serviceMessenger = null;
            callbackHandler.onServiceUnbound();
        }
    };

    private void sendAnalytics(String eventType, String jsonPayload) {
        if (serviceMessenger != null) {
            Bundle msgData = new Bundle();
            msgData.putCharSequence(PACKAGE_NAME_KEY, packageName);
            msgData.putCharSequence(ANALYTICS_EVENT_TYPE_KEY, eventType);
            msgData.putCharSequence(ANALYTICS_PAYLOAD_KEY, jsonPayload);

            Message msg = Message.obtain();
            msg.setData(msgData);

            try {
                serviceMessenger.send(msg);
            } catch (RemoteException e) {
                Log.d(TAG, "Sending SEND_ANALYTICS message failed!");
                e.printStackTrace();
            }
        }
    }


    private boolean bind(@NonNull LoggingInterfaceCallbackHandler handler, @NonNull Context c) {
        callbackHandler = handler;
        packageName = c.getPackageName();

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

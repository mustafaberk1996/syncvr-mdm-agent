package tech.syncvr.mdm_config_connector;

import static tech.syncvr.mdm_agent.mdm_common.Constants.AUTO_PLAY_AREA_KEY;
import static tech.syncvr.mdm_agent.mdm_common.Constants.AUTO_PLAY_AREA_NONE;
import static tech.syncvr.mdm_agent.mdm_common.Constants.AUTO_PLAY_AREA_SITTING;
import static tech.syncvr.mdm_agent.mdm_common.Constants.AUTO_PLAY_AREA_STANDING;

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

public class PlayAreaConfigInterfaceBinder extends Handler {
    private static final String TAG = "PlayAreaConfigInterfaceBinder";
    private static final String bindIntentPackageName = "tech.syncvr.mdm_agent";
    private static final String bindIntentClassName = "tech.syncvr.mdm_agent.repositories.play_area.PlayAreaConfigAppInterface";

    private static PlayAreaConfigInterfaceBinder instance;

    public static PlayAreaConfigInterfaceBinder getInstance() {
        if (instance == null) {
            instance = new PlayAreaConfigInterfaceBinder();
        }
        return instance;
    }

    private PlayAreaConfigInterfaceCallbackHandler callbackHandler = null;
    private Messenger serviceMessenger = null;

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

    private void getPlayAreaSetting() {
        if (serviceMessenger != null) {
            Message msg = Message.obtain();
            msg.replyTo = new Messenger(this);

            try {
                serviceMessenger.send(msg);
            } catch (RemoteException e) {
                Log.d(TAG, "Sending Get Play Area Setting message failed!");
                e.printStackTrace();
            }
        }
    }

    private void setAutoPlayAreaStanding() {
        if (serviceMessenger != null) {
            sendAutoPlaySettingMessage(AUTO_PLAY_AREA_STANDING);
        }
    }

    private void setAutoPlayAreaSitting() {
        if (serviceMessenger != null) {
            sendAutoPlaySettingMessage(AUTO_PLAY_AREA_SITTING);
        }
    }

    private void clearAutoPlayAreaSetting() {
        if (serviceMessenger != null) {
            sendAutoPlaySettingMessage(AUTO_PLAY_AREA_NONE);
        }
    }

    private void sendAutoPlaySettingMessage(String setting) {
        Bundle msgData = new Bundle();
        msgData.putCharSequence(AUTO_PLAY_AREA_KEY, setting);

        Message msg = Message.obtain();
        msg.replyTo = new Messenger(this);
        msg.setData(msgData);

        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            Log.d(TAG, "Sending Set Play Area message failed: " + setting);
            e.printStackTrace();
        }
    }

    public void handleMessage(Message msg) {
        if (callbackHandler != null) {
            String playAreaSetting = msg.getData().getString(AUTO_PLAY_AREA_KEY, "");
            callbackHandler.onPlayAreaSettingReceived(playAreaSetting);
        }
    }

    private boolean bind(@NonNull PlayAreaConfigInterfaceCallbackHandler handler, @NonNull Context c) {
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

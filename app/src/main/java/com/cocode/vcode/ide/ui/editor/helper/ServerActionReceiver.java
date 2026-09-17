package com.cocode.vcode.ide.ui.editor.helper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * BroadcastReceiver triggered by the Stop action on the live web server notification.
 */
public class ServerActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ServerNotificationHelper.ACTION_STOP_SERVER.equals(intent.getAction())) {
            ServerNotificationHelper.handleStopServer(context);
        }
    }
}

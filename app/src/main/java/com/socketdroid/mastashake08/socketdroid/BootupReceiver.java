package com.socketdroid.mastashake08.socketdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;

import static android.content.Context.MODE_PRIVATE;

public class BootupReceiver extends BroadcastReceiver {
    public BootupReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.

        if(isOnline(context)) {
            SharedPreferences prefs = context.getSharedPreferences("com.socketdroid.mastashake08.socketdroid", MODE_PRIVATE);
            if (prefs.getBoolean("service-running", false)) {
                context.startService(new Intent(context, MyService.class));
            }
        }


    }

    public boolean isOnline(Context context) {

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        //should check null because in airplane mode it will be null
        return (netInfo != null && netInfo.isConnected());
    }
}

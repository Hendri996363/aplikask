package com.rens.wamonitor;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

public class WatchdogReceiver extends BroadcastReceiver {

    private static final String TAG = "PenjagaAdek";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("com.rens.wamonitor.WATCHDOG_PING".equals(action)) {
            Log.d(TAG, "WatchdogReceiver: restart service...");
            restartService(context);
        }
    }

    static void restartService(Context context) {
        try {
            ComponentName cn = new ComponentName(context, NotifListenerService.class);
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
            Thread.sleep(500);
            pm.setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
            Log.d(TAG, "Service berhasil di-restart");
        } catch (Exception e) {
            Log.e(TAG, "Gagal restart service: " + e.getMessage());
        }
    }
}

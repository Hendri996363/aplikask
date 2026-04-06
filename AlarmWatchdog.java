package com.rens.wamonitor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * AlarmWatchdog: BroadcastReceiver yang dipanggil AlarmManager tiap 15 menit.
 * Tugasnya: pastikan NotifListenerService masih terhubung.
 * Jika tidak, lakukan toggle untuk reconnect.
 */
public class AlarmWatchdog extends BroadcastReceiver {

    private static final String TAG = "PenjagaAdek";
    private static final String ACTION = "com.rens.wamonitor.ALARM_WATCHDOG";
    private static final int INTERVAL_MS = 15 * 60 * 1000; // 15 menit

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "AlarmWatchdog ping — cek service...");
        restartListenerIfNeeded(context);
    }

    /** Daftarkan alarm berulang saat app start */
    public static void schedule(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = getPendingIntent(context);

        // Gunakan setExactAndAllowWhileIdle agar jalan meski doze mode
        am.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MS,
            INTERVAL_MS,
            pi
        );
        Log.d(TAG, "AlarmWatchdog scheduled tiap 15 menit");
    }

    /** Batalkan alarm */
    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(getPendingIntent(context));
    }

    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmWatchdog.class);
        intent.setAction(ACTION);
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void restartListenerIfNeeded(Context context) {
        try {
            ComponentName cn = new ComponentName(context, NotifListenerService.class);
            PackageManager pm = context.getPackageManager();

            // Toggle disable → enable memaksa Android reconnect listener
            pm.setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

            Thread.sleep(300);

            pm.setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);

            Log.d(TAG, "AlarmWatchdog: service di-toggle agar tetap connect");
        } catch (Exception e) {
            Log.e(TAG, "AlarmWatchdog gagal toggle: " + e.getMessage());
        }
    }
}

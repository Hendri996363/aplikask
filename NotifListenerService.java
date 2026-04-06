package com.rens.wamonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NotifListenerService extends NotificationListenerService {

    private static final String TAG = "PenjagaAdek";
    private static final String CHANNEL_ID = "penjaga_adek_fg";
    private static final int FG_NOTIF_ID = 1001;

    private static final String[] WA_PACKAGES = {
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.gbwhatsapp",
        "com.whatsapp.plus"
    };

    private static final String[] INSTAGRAM_PACKAGES = {
        "com.instagram.android",
        "com.instagram.lite"
    };

    private static final Map<String, String[]> APP_INFO = new HashMap<>();
    static {
        APP_INFO.put("com.whatsapp",          new String[]{"📱", "WhatsApp"});
        APP_INFO.put("com.whatsapp.w4b",      new String[]{"💼", "WA Business"});
        APP_INFO.put("com.gbwhatsapp",        new String[]{"📱", "GB WhatsApp"});
        APP_INFO.put("com.whatsapp.plus",     new String[]{"📱", "WhatsApp Plus"});
        APP_INFO.put("com.instagram.android", new String[]{"📸", "Instagram"});
        APP_INFO.put("com.instagram.lite",    new String[]{"📸", "Instagram Lite"});
    }

    private static final SimpleDateFormat SDF =
        new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // =============================================
    // LIFECYCLE
    // =============================================

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NotifListenerService v4 onCreate");
        createNotificationChannel();
        startForegroundService();
        AlarmWatchdog.schedule(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.w(TAG, "Service DESTROYED — trigger watchdog...");
        Intent restart = new Intent("com.rens.wamonitor.WATCHDOG_PING");
        restart.setPackage(getPackageName());
        sendBroadcast(restart);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Listener connected");
        updateForegroundNotif("Aktif — memantau notifikasi...");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "Listener DISCONNECTED — meminta reconnect");
        updateForegroundNotif("Terputus — mencoba reconnect...");
        requestRebind(new android.content.ComponentName(this, NotifListenerService.class));
    }

    // =============================================
    // FOREGROUND SERVICE
    // =============================================

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Penjaga Adek Monitor", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Service aktif memantau notifikasi");
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private void startForegroundService() {
        startForeground(FG_NOTIF_ID, buildForegroundNotif("Memulai pemantauan..."));
    }

    private void updateForegroundNotif(String status) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(FG_NOTIF_ID, buildForegroundNotif(status));
    }

    private Notification buildForegroundNotif(String status) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Penjaga Adek v4")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    // =============================================
    // MAIN LOGIC
    // =============================================

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String pkg = sbn.getPackageName();

        SharedPreferences prefs = getSharedPreferences("WAMonitor", MODE_PRIVATE);
        if (!prefs.getBoolean("active", true)) return;

        String token  = prefs.getString("bot_token", "");
        String chatId = prefs.getString("chat_id", "");
        if (token.isEmpty() || chatId.isEmpty()) return;

        boolean monitorWA    = prefs.getBoolean("monitor_wa", true);
        boolean monitorInsta = prefs.getBoolean("monitor_instagram", false);

        boolean isWA    = isInList(pkg, WA_PACKAGES);
        boolean isInsta = isInList(pkg, INSTAGRAM_PACKAGES);

        if (isWA && !monitorWA)       return;
        if (isInsta && !monitorInsta) return;
        if (!isWA && !isInsta)        return;

        Notification notif = sbn.getNotification();
        if (notif == null) return;
        Bundle extras = notif.extras;
        if (extras == null) return;

        String title   = safeStr(extras.getString(Notification.EXTRA_TITLE));
        String text    = safeStr(extras.getString(Notification.EXTRA_TEXT));
        String bigText = safeStr(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String subText = safeStr(extras.getString(Notification.EXTRA_SUB_TEXT));

        String content = bigText.length() > text.length() ? bigText : text;

        if (title.isEmpty() || content.isEmpty()) return;
        if (isSummaryOnly(content))               return;

        // Anti-duplikat (30 detik)
        if (DuplicateFilter.isDuplicate(pkg, title, content)) {
            Log.d(TAG, "Skip duplikat: " + title);
            return;
        }

        // Filter nomor tidak dikenal
        boolean unknownOnly = prefs.getBoolean("unknown_only", false);
        boolean isUnknown   = isUnknownNumber(title);
        if (unknownOnly && !isUnknown && isWA) return;

        Log.d(TAG, "Notif: " + pkg + " | " + title + " | " + content);
        updateForegroundNotif("Pesan dari " + title + " — " + SDF.format(new Date()));

        // Bangun pesan
        String[] info   = APP_INFO.getOrDefault(pkg, new String[]{"📱", pkg});
        String emoji    = info[0];
        String appLabel = info[1];
        String warnTag  = (isWA && isUnknown) ? "⚠️ *NOMOR TIDAK DIKENAL\\!*\n" : "";
        String groupTag = (!subText.isEmpty()) ? "👥 *Grup:* " + escape(subText) + "\n" : "";
        String timeStr  = escape(SDF.format(new Date(sbn.getPostTime())));

        String message = emoji + " *" + escape(appLabel) + "*\n"
            + warnTag
            + groupTag
            + "👤 *Dari:* " + escape(title) + "\n"
            + "💬 *Pesan:* " + escape(content) + "\n"
            + "🕐 *Waktu:* " + timeStr;

        // Offline → queue
        if (!NetworkReceiver.isConnected(this)) {
            Log.w(TAG, "Offline — pesan disimpan ke queue");
            MessageQueue.enqueue(this, new MessageQueue.QueuedMessage(token, chatId, message));
            return;
        }

        // Online → kirim dengan retry
        TelegramSender.sendWithRetry(token, chatId, message, 3, success -> {
            if (!success) {
                Log.e(TAG, "Gagal 3x retry — masuk queue");
                MessageQueue.enqueue(NotifListenerService.this,
                    new MessageQueue.QueuedMessage(token, chatId, message));
            }
        });
    }

    // =============================================
    // HELPERS
    // =============================================

    private boolean isInList(String pkg, String[] list) {
        for (String item : list) if (item.equals(pkg)) return true;
        return false;
    }

    private boolean isSummaryOnly(String text) {
        if (text == null) return true;
        String lower = text.toLowerCase().trim();
        return lower.matches(".*\\d+\\s*(pesan|message|msg|chat).*baru.*")
            || lower.matches(".*\\d+\\s*new\\s*(message|msg|chat).*")
            || lower.matches(".*\\d+\\s*(unread|belum dibaca).*")
            || lower.matches("^\\d+\\s*(pesan|message)s?$")
            || lower.matches(".*\\d+\\s*notifications?.*");
    }

    private boolean isUnknownNumber(String name) {
        if (name == null) return false;
        return name.matches(".*\\d{5,}.*") || name.startsWith("+");
    }

    private String safeStr(Object obj) {
        if (obj == null) return "";
        return obj.toString().trim();
    }

    private String escape(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\").replace("_", "\\_").replace("*", "\\*")
            .replace("[", "\\[").replace("]", "\\]").replace("(", "\\(")
            .replace(")", "\\)").replace("~", "\\~").replace("`", "\\`")
            .replace(">", "\\>").replace("#", "\\#").replace("+", "\\+")
            .replace("-", "\\-").replace("=", "\\=").replace("|", "\\|")
            .replace("{", "\\{").replace("}", "\\}").replace(".", "\\.")
            .replace("!", "\\!");
    }
}

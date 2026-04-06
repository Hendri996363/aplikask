package com.rens.wamonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.util.List;

/**
 * NetworkReceiver: mendeteksi saat internet kembali tersedia,
 * lalu mengirim ulang semua pesan yang tersimpan di antrian.
 */
public class NetworkReceiver extends BroadcastReceiver {

    private static final String TAG = "PenjagaAdek";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isConnected(context)) return;

        int pending = MessageQueue.pendingCount(context);
        if (pending == 0) return;

        Log.d(TAG, "Internet kembali! Flush " + pending + " pesan pending...");

        List<MessageQueue.QueuedMessage> messages = MessageQueue.dequeueAll(context);
        for (MessageQueue.QueuedMessage msg : messages) {
            // Tambahkan keterangan "tertunda" di pesan
            String note = msg.message + "\n\n_\\(Terkirim tertunda\\)_";
            TelegramSender.sendWithRetry(msg.token, msg.chatId, note, 3, success -> {
                if (success) {
                    Log.d(TAG, "✅ Pesan pending berhasil dikirim");
                } else {
                    // Jika masih gagal, simpan lagi
                    MessageQueue.enqueue(context, msg);
                    Log.e(TAG, "❌ Masih gagal, disimpan ulang ke queue");
                }
            });
        }
    }

    public static boolean isConnected(Context context) {
        try {
            ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }
}

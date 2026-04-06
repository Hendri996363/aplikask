package com.rens.wamonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * MessageQueue: menyimpan pesan yang gagal dikirim ke antrian,
 * lalu mengirim ulang saat koneksi tersedia.
 */
public class MessageQueue {

    private static final String TAG = "PenjagaAdek";
    private static final String PREF_NAME = "WAMonitor_Queue";
    private static final String KEY_QUEUE = "pending_messages";
    private static final int MAX_QUEUE_SIZE = 50;

    public static class QueuedMessage {
        public String token;
        public String chatId;
        public String message;
        public long timestamp;

        public QueuedMessage(String token, String chatId, String message) {
            this.token = token;
            this.chatId = chatId;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /** Simpan pesan ke antrian */
    public static synchronized void enqueue(Context ctx, QueuedMessage msg) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_QUEUE, "[]");
            JSONArray arr = new JSONArray(raw);

            // Jangan simpan lebih dari MAX
            if (arr.length() >= MAX_QUEUE_SIZE) {
                arr.remove(0); // buang yang paling lama
            }

            JSONObject obj = new JSONObject();
            obj.put("token", msg.token);
            obj.put("chatId", msg.chatId);
            obj.put("message", msg.message);
            obj.put("timestamp", msg.timestamp);
            arr.put(obj);

            prefs.edit().putString(KEY_QUEUE, arr.toString()).apply();
            Log.d(TAG, "Pesan di-queue, total: " + arr.length());
        } catch (Exception e) {
            Log.e(TAG, "Gagal enqueue: " + e.getMessage());
        }
    }

    /** Ambil semua pesan dari antrian */
    public static synchronized List<QueuedMessage> dequeueAll(Context ctx) {
        List<QueuedMessage> list = new ArrayList<>();
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_QUEUE, "[]");
            JSONArray arr = new JSONArray(raw);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                QueuedMessage msg = new QueuedMessage(
                    obj.getString("token"),
                    obj.getString("chatId"),
                    obj.getString("message")
                );
                msg.timestamp = obj.getLong("timestamp");
                list.add(msg);
            }

            // Kosongkan queue setelah diambil
            prefs.edit().putString(KEY_QUEUE, "[]").apply();
        } catch (Exception e) {
            Log.e(TAG, "Gagal dequeue: " + e.getMessage());
        }
        return list;
    }

    /** Cek apakah ada pesan pending */
    public static boolean hasPending(Context ctx) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_QUEUE, "[]");
            JSONArray arr = new JSONArray(raw);
            return arr.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Jumlah pesan pending */
    public static int pendingCount(Context ctx) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_QUEUE, "[]");
            return new JSONArray(raw).length();
        } catch (Exception e) {
            return 0;
        }
    }
}

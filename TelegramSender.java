package com.rens.wamonitor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TelegramSender {

    private static final String TAG = "PenjagaAdek";

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build();

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResult(boolean success);
    }

    /** Kirim sekali (untuk test button) */
    public static void send(String botToken, String chatId, String message, Callback callback) {
        sendWithRetry(botToken, chatId, message, 1, callback);
    }

    /** Kirim dengan retry otomatis */
    public static void sendWithRetry(String botToken, String chatId, String message,
                                     int maxRetry, Callback callback) {
        executor.execute(() -> {
            boolean success = false;
            int attempt = 0;

            while (attempt < maxRetry && !success) {
                attempt++;
                try {
                    if (attempt > 1) {
                        Log.d(TAG, "Retry ke-" + attempt + "...");
                        Thread.sleep(2000L * attempt);
                    }

                    success = doSend(botToken, chatId, message, true);

                    if (!success && attempt == maxRetry) {
                        // Fallback: coba plain text
                        Log.w(TAG, "MarkdownV2 gagal — coba plain text...");
                        success = doSend(botToken, chatId, stripMarkdown(message), false);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            final boolean result = success;
            if (callback != null) mainHandler.post(() -> callback.onResult(result));
        });
    }

    private static boolean doSend(String botToken, String chatId,
                                   String message, boolean useMarkdown) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            FormBody.Builder formBuilder = new FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", message);

            if (useMarkdown) formBuilder.add("parse_mode", "MarkdownV2");

            RequestBody body = formBuilder.build();
            Request request = new Request.Builder().url(url).post(body).build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "✅ Telegram OK (markdown=" + useMarkdown + ")");
                    return true;
                } else {
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    Log.w(TAG, "HTTP " + response.code() + ": " + bodyStr);
                    return false;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            return false;
        }
    }

    private static String stripMarkdown(String text) {
        return text
            .replace("\\*", "*").replace("\\_", "_").replace("\\[", "[")
            .replace("\\.", ".").replace("\\!", "!").replace("\\(", "(")
            .replace("\\)", ")").replace("\\-", "-").replace("\\>", ">")
            .replace("\\~", "~").replace("\\`", "`").replace("\\#", "#")
            .replace("*", "").replace("_", "").replace("`", "");
    }
}

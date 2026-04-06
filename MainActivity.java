package com.rens.wamonitor;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_POST_NOTIF = 101;

    private EditText etBotToken, etChatId;
    private Switch switchActive, switchUnknownOnly, switchInstagram;
    private TextView tvStatus, tvQueue;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("WAMonitor", MODE_PRIVATE);

        etBotToken       = findViewById(R.id.etBotToken);
        etChatId         = findViewById(R.id.etChatId);
        switchActive     = findViewById(R.id.switchActive);
        switchUnknownOnly= findViewById(R.id.switchUnknownOnly);
        switchInstagram  = findViewById(R.id.switchInstagram);
        tvStatus         = findViewById(R.id.tvStatus);
        tvQueue          = findViewById(R.id.tvQueue);

        etBotToken.setText(prefs.getString("bot_token", ""));
        etChatId.setText(prefs.getString("chat_id", ""));
        switchActive.setChecked(prefs.getBoolean("active", true));
        switchUnknownOnly.setChecked(prefs.getBoolean("unknown_only", false));
        switchInstagram.setChecked(prefs.getBoolean("monitor_instagram", false));

        findViewById(R.id.btnSave).setOnClickListener(v -> saveSettings());

        findViewById(R.id.btnPermission).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            Toast.makeText(this, "Cari 'Penjaga Adek' lalu aktifkan", Toast.LENGTH_LONG).show();
        });

        findViewById(R.id.btnBattery).setOnClickListener(v -> requestIgnoreBattery());

        findViewById(R.id.btnRestartService).setOnClickListener(v -> {
            toggleNotificationListenerService();
            Toast.makeText(this, "Service di-restart", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnTest).setOnClickListener(v -> sendTest());

        requestPostNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        updateQueueStatus();
    }

    // =============================================
    // SAVE
    // =============================================

    private void saveSettings() {
        String token  = etBotToken.getText().toString().trim();
        String chatId = etChatId.getText().toString().trim();
        if (token.isEmpty() || chatId.isEmpty()) {
            Toast.makeText(this, "Token & Chat ID tidak boleh kosong!", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit()
            .putString("bot_token", token)
            .putString("chat_id", chatId)
            .putBoolean("active", switchActive.isChecked())
            .putBoolean("unknown_only", switchUnknownOnly.isChecked())
            .putBoolean("monitor_wa", true)
            .putBoolean("monitor_instagram", switchInstagram.isChecked())
            .apply();
        Toast.makeText(this, "Disimpan!", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    // =============================================
    // STATUS
    // =============================================

    private void updateStatus() {
        boolean hasListener = isNotifListenerGranted();
        boolean isActive    = prefs.getBoolean("active", true);
        String  token       = prefs.getString("bot_token", "");
        String  chatId      = prefs.getString("chat_id", "");
        boolean batteryOk   = isBatteryOptIgnored();

        StringBuilder sb = new StringBuilder();
        sb.append(hasListener ? "✅ Izin Notifikasi: OK\n" : "❌ Izin Notifikasi: BELUM aktif\n");
        sb.append(batteryOk  ? "✅ Baterai: Tidak dioptimasi\n" : "⚠️ Baterai: Masih dioptimasi\n");

        if (token.isEmpty() || chatId.isEmpty()) {
            sb.append("⚠️ Token/Chat ID belum diisi");
            tvStatus.setTextColor(getColor(android.R.color.holo_orange_light));
        } else if (!hasListener) {
            sb.append("→ Tekan 'Beri Izin' untuk mengaktifkan");
            tvStatus.setTextColor(getColor(android.R.color.holo_red_light));
        } else if (!isActive) {
            sb.append("⏸️ Pemantauan dinonaktifkan");
            tvStatus.setTextColor(getColor(android.R.color.darker_gray));
        } else {
            sb.append("🛡️ AKTIF — Memantau WA adek");
            tvStatus.setTextColor(getColor(android.R.color.holo_green_light));
        }

        tvStatus.setText(sb.toString().trim());
    }

    private void updateQueueStatus() {
        int pending = MessageQueue.pendingCount(this);
        if (pending == 0) {
            tvQueue.setText("📤 Antrian pesan: kosong");
        } else {
            tvQueue.setText("📤 Antrian pesan: " + pending + " pending (akan dikirim saat online)");
        }
    }

    // =============================================
    // TEST
    // =============================================

    private void sendTest() {
        String token  = etBotToken.getText().toString().trim();
        String chatId = etChatId.getText().toString().trim();
        if (token.isEmpty() || chatId.isEmpty()) {
            Toast.makeText(this, "Isi Token & Chat ID dulu!", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Mengirim test...", Toast.LENGTH_SHORT).show();
        TelegramSender.send(token, chatId,
            "✅ *Test Penjaga Adek v4*\n\n🛡️ Sistem aktif dan siap memantau\\!",
            success -> runOnUiThread(() -> {
                Toast.makeText(this,
                    success ? "✅ Berhasil dikirim!" : "❌ Gagal! Cek token & chat ID",
                    Toast.LENGTH_LONG).show();
                updateQueueStatus();
            })
        );
    }

    // =============================================
    // PERMISSIONS
    // =============================================

    private boolean isNotifListenerGranted() {
        String flat = Settings.Secure.getString(
            getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            for (String s : flat.split(":")) {
                if (s.contains(getPackageName())) return true;
            }
        }
        return false;
    }

    private boolean isBatteryOptIgnored() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    private void requestIgnoreBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isBatteryOptIgnored()) {
                new AlertDialog.Builder(this)
                    .setTitle("Matikan Optimasi Baterai")
                    .setMessage("Agar Penjaga Adek tidak dimatikan saat layar mati, "
                        + "izinkan app ini mengabaikan optimasi baterai.")
                    .setPositiveButton("Izinkan", (d, w) -> {
                        Intent i = new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    })
                    .setNegativeButton("Nanti", null)
                    .show();
            } else {
                Toast.makeText(this, "✅ Sudah dimatikan!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIF);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void toggleNotificationListenerService() {
        ComponentName cn = new ComponentName(this, NotifListenerService.class);
        getPackageManager().setComponentEnabledSetting(cn,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        getPackageManager().setComponentEnabledSetting(cn,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }
}

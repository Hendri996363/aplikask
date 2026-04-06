# Penjaga Adek v3 — WAMonitor

## Perubahan dari v2 → v3

### 🔧 Perbaikan Utama

| Masalah di v2 | Solusi di v3 |
|---|---|
| Service dimatikan Android | **Foreground Service** dengan notifikasi permanen |
| Tidak minta izin baterai | Tombol **Matikan Optimasi Baterai** + dialog penjelasan |
| Tidak minta POST_NOTIFICATIONS | Otomatis minta saat buka app (Android 13+) |
| Tidak ada auto-restart | **WatchdogReceiver** restart service jika mati |
| Filter summary terlalu ketat | Logika **isSummaryOnly()** lebih cerdas, multi-bahasa |
| Kirim gagal = hilang | **Retry 3x** otomatis + fallback plain text jika parse gagal |
| Hanya WA | Sekarang support **Instagram** (toggle di UI) |
| MarkdownV2 tidak di-escape | Semua karakter MarkdownV2 di-escape dengan benar |

---

## File yang Diubah / Ditambah

```
WAMonitor_v3/
├── app/src/main/
│   ├── AndroidManifest.xml          ← +POST_NOTIFICATIONS, +WAKE_LOCK, +WatchdogReceiver
│   └── java/com/rens/wamonitor/
│       ├── NotifListenerService.java ← Foreground Service, support Instagram, logika baru
│       ├── MainActivity.java         ← Tombol baterai, switch Instagram, status lebih detail
│       ├── TelegramSender.java       ← Retry 3x, timeout, fallback plain text
│       ├── BootReceiver.java         ← Toggle service setelah boot
│       └── WatchdogReceiver.java     ← BARU: auto-restart jika service mati
│   └── res/layout/
│       └── activity_main.xml         ← Switch Instagram, tombol baterai, dark UI
```

---

## Setup (Urutan Wajib)

1. Install APK
2. Buka app → isi **Bot Token** & **Chat ID** → tekan **Simpan**
3. Tekan **Beri Izin Notifikasi** → aktifkan "Penjaga Adek"
4. Tekan **Matikan Optimasi Baterai** → izinkan
5. Tekan **Test Kirim Telegram** → pastikan pesan masuk
6. Aktifkan toggle **Instagram** jika ingin pantau Instagram juga

---

## Tips Tambahan

- Jika notifikasi tiba-tiba tidak masuk lagi, tekan **Restart Service**
- Di beberapa HP (Xiaomi, OPPO, Samsung), perlu juga:
  - Kunci app di Recent Apps (jangan di-swipe)
  - Aktifkan "Autostart" di pengaturan HP
  - Di Samsung: matikan "Put unused apps to sleep"

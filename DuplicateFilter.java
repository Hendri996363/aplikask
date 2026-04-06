package com.rens.wamonitor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DuplicateFilter: mencegah pesan yang sama dikirim berulang
 * dalam waktu singkat (misal notif muncul 3x karena Android).
 * Menggunakan cache in-memory dengan TTL 30 detik.
 */
public class DuplicateFilter {

    private static final long TTL_MS = 30_000; // 30 detik
    private static final int MAX_SIZE = 100;

    // LinkedHashMap sebagai LRU cache sederhana
    private static final Map<String, Long> cache = new LinkedHashMap<String, Long>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_SIZE;
        }
    };

    /**
     * Cek apakah pesan ini duplikat.
     * @return true jika DUPLIKAT (harus di-skip), false jika baru
     */
    public static synchronized boolean isDuplicate(String pkg, String title, String text) {
        String key = pkg + "|" + title + "|" + text.substring(0, Math.min(text.length(), 50));
        long now = System.currentTimeMillis();

        Long lastSeen = cache.get(key);
        if (lastSeen != null && (now - lastSeen) < TTL_MS) {
            return true; // duplikat dalam 30 detik
        }

        cache.put(key, now);
        return false;
    }
}

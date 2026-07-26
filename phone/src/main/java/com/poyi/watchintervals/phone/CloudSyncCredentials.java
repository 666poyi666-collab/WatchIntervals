package com.poyi.watchintervals.phone;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URI;

/** Owns the device-to-cloud endpoint and its upload-only credential. */
final class CloudSyncCredentials {
    static final class Config {
        final String endpoint;
        final String syncKey;

        Config(String endpoint, String syncKey) {
            this.endpoint = endpoint;
            this.syncKey = syncKey;
        }

        boolean configured() {
            return validEndpoint(endpoint) && syncKey.length() >= 32;
        }
    }

    private static final String PREFS = "cloud_snapshot_sync";
    private static final String ENDPOINT = "endpoint";
    private static final String SYNC_KEY = "sync_key";

    private CloudSyncCredentials() {}

    static Config load(Context context) {
        SharedPreferences values = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Config(values.getString(ENDPOINT, ""), values.getString(SYNC_KEY, ""));
    }

    static boolean save(Context context, String endpoint, String syncKey) {
        String normalizedEndpoint = endpoint == null ? "" : endpoint.trim();
        String normalizedKey = syncKey == null ? "" : syncKey.trim();
        if (!validEndpoint(normalizedEndpoint) || normalizedKey.length() < 32) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ENDPOINT, normalizedEndpoint).putString(SYNC_KEY, normalizedKey).commit();
    }

    static void recordResult(Context context, long syncedAt, String error) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("last_synced_at", syncedAt)
                .putString("last_error", error == null ? "" : error).apply();
    }

    private static boolean validEndpoint(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && "/sync/push".equals(uri.getPath()) && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}

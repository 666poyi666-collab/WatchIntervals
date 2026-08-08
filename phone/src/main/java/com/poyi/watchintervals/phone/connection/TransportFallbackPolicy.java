package com.poyi.watchintervals.phone.connection;

/** Retry policy for a read-only bulk request when the cached LAN endpoint goes stale. */
final class TransportFallbackPolicy {
    private TransportFallbackPolicy() {}

    static boolean shouldRetryOnBle(String method, TransportType selected,
            boolean bleAvailable) {
        return bleAvailable && selected == TransportType.LAN
                && "GET".equalsIgnoreCase(method);
    }
}

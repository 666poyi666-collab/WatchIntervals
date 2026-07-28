package com.poyi.watchintervals.phone.connection.lan;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import com.poyi.watchintervals.phone.WatchClient;
import com.poyi.watchintervals.phone.connection.WatchConnectionManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/**
 * Keeps the LAN endpoint of the watch fresh while the phone runs headless.
 *
 * <p>Discovery used to live only in {@code MainActivity}, so the background bridge service
 * depended on whatever host the user had last typed there. A DHCP lease change or a factory
 * reset of that preference left {@code lanAvailable=false} and the whole
 * {@code MCP -> phone -> watch} chain reported {@code watch_offline}, even when the watch was
 * answering on the same subnet. This locator runs the same {@code _watchintervals._tcp.}
 * discovery from the service, verifies the candidate before trusting it, and republishes the
 * result through {@link WatchConnectionManager}.
 */
public final class WatchLanLocator {
    private static final String TAG = "WatchLanLocator";
    private static final String SERVICE_TYPE = "_watchintervals._tcp.";
    private static final String CONNECTION_PREFERENCES = "connection";
    /** Re-probe quickly while the watch is missing, and idle back once it is verified. */
    private static final long RETRY_WHEN_OFFLINE_MILLIS = 60_000L;
    private static final long RETRY_WHEN_ONLINE_MILLIS = 600_000L;
    /** mDNS answers can lag a roaming watch; a sweep longer than this is treated as failed. */
    private static final long DISCOVERY_WINDOW_MILLIS = 8_000L;

    private final Context context;
    private final WatchConnectionManager connection;
    private final Handler scheduler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean sweeping = new AtomicBoolean();

    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private WifiManager.MulticastLock multicastLock;
    private boolean started;

    public WatchLanLocator(Context context, WatchConnectionManager connection) {
        this.context = context.getApplicationContext();
        this.connection = connection;
    }

    /** Applies the last verified endpoint immediately, then keeps it refreshed. */
    public synchronized void start() {
        if (started) return;
        started = true;
        applyPersistedHost();
        scheduler.post(this::sweep);
    }

    public synchronized void stop() {
        started = false;
        scheduler.removeCallbacksAndMessages(null);
        stopDiscovery();
    }

    /**
     * Restores the LAN transport from persisted state so the first background request after a
     * process restart does not have to wait for a BLE timeout followed by a discovery sweep.
     */
    private void applyPersistedHost() {
        String host = preferences().getString("host", "");
        if (!host.trim().isEmpty()) connection.configureLan(host.trim(), "");
    }

    private void sweep() {
        if (!started || !sweeping.compareAndSet(false, true)) return;
        stopDiscovery();

        WifiManager wifi = context.getSystemService(WifiManager.class);
        if (wifi != null) {
            // Android drops multicast for battery reasons unless a lock is held, and mDNS
            // resolution silently returns nothing without it.
            multicastLock = wifi.createMulticastLock("watchintervals-locator");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        }

        nsdManager = context.getSystemService(NsdManager.class);
        if (nsdManager == null) {
            finishSweep(false);
            return;
        }

        discoveryListener = new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String type) {}
            public void onStartDiscoveryFailed(String type, int code) { finishSweep(false); }
            public void onStopDiscoveryFailed(String type, int code) {}
            public void onDiscoveryStopped(String type) {}
            public void onServiceLost(NsdServiceInfo info) {}
            public void onServiceFound(NsdServiceInfo info) {
                if (info.getServiceType() == null
                        || !info.getServiceType().startsWith("_watchintervals._tcp")) return;
                resolve(info);
            }
        };

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception error) {
            android.util.Log.w(TAG, "mDNS discovery failed to start", error);
            finishSweep(false);
            return;
        }
        scheduler.postDelayed(() -> { if (sweeping.get()) finishSweep(false); }, DISCOVERY_WINDOW_MILLIS);
    }

    private void resolve(NsdServiceInfo info) {
        try {
            nsdManager.resolveService(info, new NsdManager.ResolveListener() {
                public void onResolveFailed(NsdServiceInfo service, int code) {}
                public void onServiceResolved(NsdServiceInfo service) {
                    String address = service.getHost() == null ? "" : service.getHost().getHostAddress();
                    if (address != null && !address.isEmpty()) io.execute(() -> verify(address));
                }
            });
        } catch (Exception ignored) {
            // Concurrent resolve requests throw on some vendor stacks; the sweep timeout covers it.
        }
    }

    /**
     * A LAN can carry stale or foreign advertisements, so a candidate only replaces the saved
     * host after {@code /v1/status} answers with the device id this phone is paired to.
     */
    private void verify(String address) {
        if (!sweeping.get()) return;
        String credential = connection.identity().lanCredential();
        if (credential.isEmpty()) credential = connection.identity().pairingCode();
        if (credential.isEmpty()) {
            // Unpaired: still record where the watch lives so the pairing screen can prefill it.
            preferences().edit().putString("host", address).apply();
            finishSweep(false);
            return;
        }
        try {
            JSONObject status = new JSONObject(new WatchClient(address, credential).get("/v1/status"));
            String discovered = status.optString("deviceId");
            String expected = connection.identity().watchDeviceId();
            if (!expected.isEmpty() && !expected.equals(discovered)) return;

            preferences().edit().putString("host", address).putString("watch_device_id", discovered).apply();
            connection.configureLan(address, credential);
            finishSweep(true);
        } catch (Exception error) {
            android.util.Log.d(TAG, "candidate " + address + " rejected: " + error.getMessage());
        }
    }

    private void finishSweep(boolean located) {
        if (!sweeping.compareAndSet(true, false)) return;
        scheduler.post(() -> {
            stopDiscovery();
            if (!started) return;
            scheduler.postDelayed(this::sweep,
                    located ? RETRY_WHEN_ONLINE_MILLIS : RETRY_WHEN_OFFLINE_MILLIS);
        });
    }

    private void stopDiscovery() {
        if (nsdManager != null && discoveryListener != null) {
            try { nsdManager.stopServiceDiscovery(discoveryListener); } catch (Exception ignored) {}
        }
        discoveryListener = null;
        if (multicastLock != null) {
            try { multicastLock.release(); } catch (Exception ignored) {}
            multicastLock = null;
        }
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(CONNECTION_PREFERENCES, Context.MODE_PRIVATE);
    }
}

package com.poyi.watchintervals.phone;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;

/** Foreground-only notification channel. Business payloads always travel through V3 exchange. */
final class CloudV3Channel {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
    private WebSocket socket;
    private boolean running;
    private boolean reconnectScheduled;
    private long retryMillis = 1_000L;
    private final Runnable reconnectTask = () -> {
        synchronized (CloudV3Channel.this) { reconnectScheduled = false; }
        connect();
    };

    CloudV3Channel(Context context) { this.context = context.getApplicationContext(); }

    synchronized void start() {
        if (!running) running = true;
        connect();
    }

    synchronized void stop() {
        running = false;
        handler.removeCallbacks(reconnectTask);
        reconnectScheduled = false;
        if (socket != null) socket.close(1000, "service stopped");
        socket = null;
    }

    private synchronized void connect() {
        if (!running || socket != null) return;
        if (!CloudSyncCredentials.readyForCloudV3(context)) {
            scheduleReconnect();
            return;
        }
        if (reconnectScheduled) {
            handler.removeCallbacks(reconnectTask);
            reconnectScheduled = false;
        }
        try {
            CloudSyncCredentials.Config config = CloudSyncCredentials.load(context);
            Request request = new Request.Builder().url(CloudV3Sync.channelEndpoint(config.endpoint))
                    .header("Authorization", "Bearer " + config.deviceToken).build();
            socket = client.newWebSocket(request, new WebSocketListener() {
                @Override public void onOpen(WebSocket webSocket, Response response) {
                    synchronized (CloudV3Channel.this) {
                        retryMillis = 1_000L;
                        if (reconnectScheduled) handler.removeCallbacks(reconnectTask);
                        reconnectScheduled = false;
                    }
                    CloudV3Sync.syncCommandAsync(context);
                    EncryptedWatchSyncWorker.schedule(context);
                }

                @Override public void onMessage(WebSocket webSocket, String text) {
                    try {
                        JSONObject message = new JSONObject(text);
                        if (message.length() == 1 && "sync_needed".equals(message.optString("type"))) {
                            CloudV3Sync.syncCommandAsync(context);
                            EncryptedWatchSyncWorker.schedule(context);
                        }
                    } catch (Exception ignored) { /* Unknown channel messages never execute work. */ }
                }

                @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                    reconnect(webSocket);
                }

                @Override public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                    reconnect(webSocket);
                }
            });
        } catch (Exception invalidConfiguration) { scheduleReconnect(); }
    }

    private void reconnect(WebSocket previous) {
        synchronized (this) { if (socket == previous) socket = null; }
        scheduleReconnect();
    }

    private synchronized void scheduleReconnect() {
        if (!running || reconnectScheduled) return;
        reconnectScheduled = true;
        long delay = retryMillis;
        retryMillis = Math.min(60_000L, retryMillis * 2L);
        handler.postDelayed(reconnectTask, delay);
    }
}

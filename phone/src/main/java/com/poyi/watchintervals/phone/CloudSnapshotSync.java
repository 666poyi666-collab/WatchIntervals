package com.poyi.watchintervals.phone;

import android.content.Context;
import com.poyi.watchintervals.phone.connection.WatchConnectionManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pushes read-only device snapshots without depending on the Windows bridge. */
final class CloudSnapshotSync {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private CloudSnapshotSync() {}

    static void syncAsync(Context context) {
        Context app = context.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) return;
        WORKER.execute(() -> {
            try { sync(app); }
            finally { RUNNING.set(false); }
        });
    }

    static boolean sync(Context context) {
        CloudSyncCredentials.Config config = CloudSyncCredentials.load(context);
        if (!config.configured()) return false;
        try {
            WatchConnectionManager connection = WatchConnectionManager.get(context);
            if (!connection.identity().isPaired()) return false;
            JSONObject status = null, sleep = null;
            JSONArray history = null;
            try { status = requestObject(connection, "/v1/status", 12_000L); }
            catch (Exception ignored) { /* Preserve the previous good status snapshot. */ }
            try { history = requestArray(connection, "/v1/history", 25_000L); }
            catch (Exception ignored) { /* Preserve the previous good workout snapshots. */ }
            try { sleep = requestObject(connection, "/v1/sleep?days=7", 25_000L); }
            catch (Exception ignored) { /* Preserve the previous good sleep snapshots. */ }
            JSONObject snapshots = CloudSnapshotPayload.build(
                    status, history, sleep, PhonePlanLibrary.load(context));
            if (snapshots.length() == 0) return false;
            JSONObject body = new JSONObject().put("source", "phone")
                    .put("snapshots", snapshots);
            push(config, body);
            CloudSyncCredentials.recordResult(context, System.currentTimeMillis(), "");
            return true;
        } catch (Exception failure) {
            CloudSyncCredentials.recordResult(context, 0, failure.getClass().getSimpleName());
            return false;
        }
    }

    private static JSONObject requestObject(WatchConnectionManager connection, String path,
                                            long timeout) throws Exception {
        return new JSONObject(connection.requestBlocking("GET", path, "", timeout));
    }

    private static JSONArray requestArray(WatchConnectionManager connection, String path,
                                          long timeout) throws Exception {
        return new JSONArray(connection.requestBlocking("GET", path, "", timeout));
    }

    private static void push(CloudSyncCredentials.Config config, JSONObject body) throws Exception {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(config.endpoint).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.setRequestProperty("Authorization", "Bearer " + config.syncKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            try (InputStream input = status >= 400 ? connection.getErrorStream()
                    : connection.getInputStream()) { drain(input); }
            if (status < 200 || status >= 300) throw new IllegalStateException("cloud_http_" + status);
        } finally {
            connection.disconnect();
        }
    }

    private static void drain(InputStream input) throws Exception {
        if (input == null) return;
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int total = 0, read;
        while ((read = input.read(buffer)) >= 0 && total < 32_768) {
            sink.write(buffer, 0, read);
            total += read;
        }
    }
}

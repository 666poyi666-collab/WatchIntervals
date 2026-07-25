package com.poyi.watchintervals;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.IBinder;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Paired local bridge used by the phone companion and desktop MCP server. */
public class WatchBridgeService extends Service {
    static final int PORT = 8765;
    private static final String CHANNEL = "phone_bridge";
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private volatile boolean closed;
    private ServerSocket server;
    private NsdManager.RegistrationListener registration;
    private SystemSleepBridge sleepBridge;

    static String pairingCode(Context context) {
        android.content.SharedPreferences preferences = context.getSharedPreferences("bridge", MODE_PRIVATE);
        String code = preferences.getString("pairing_code", null);
        if (code == null) {
            code = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1_000_000));
            preferences.edit().putString("pairing_code", code).apply();
        }
        return code;
    }

    @Override public void onCreate() {
        super.onCreate();
        sleepBridge = new SystemSleepBridge(this);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "手机与 MCP 连接", NotificationManager.IMPORTANCE_MIN);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher).setContentTitle("步序连接服务")
                .setContentText("手机与电脑可同步训练数据").setOngoing(true).build();
        startForeground(73, notification);
        workers.execute(this::serve);
        registerNsd();
    }

    private void serve() {
        try {
            server = new ServerSocket(PORT);
            while (!closed) {
                Socket socket = server.accept();
                workers.execute(() -> handle(socket));
            }
        } catch (Exception ignored) { if (!closed) stopSelf(); }
    }

    private void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String request = reader.readLine();
            if (request == null) return;
            String[] parts = request.split(" ");
            if (parts.length < 2) { respond(socket, 400, error("bad_request")); return; }
            String method = parts[0], path = parts[1];
            Map<String, String> headers = new HashMap<>();
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator > 0) headers.put(line.substring(0, separator).trim().toLowerCase(Locale.US), line.substring(separator + 1).trim());
            }
            try { contentLength = Integer.parseInt(headers.getOrDefault("content-length", "0")); } catch (NumberFormatException ignored) {}
            int expectedBytes = Math.max(0, Math.min(contentLength, 256_000));
            StringBuilder bodyBuilder = new StringBuilder(expectedBytes);
            int bodyBytes = 0;
            while (bodyBytes < expectedBytes) {
                int value = reader.read(); if (value < 0) break;
                char character = (char)value; bodyBuilder.append(character);
                bodyBytes += String.valueOf(character).getBytes(StandardCharsets.UTF_8).length;
            }
            String body = bodyBuilder.toString();

            if (!pairingCode(this).equals(headers.get("x-pairing-code"))) { respond(socket, 401, error("pairing_required")); return; }
            if ("GET".equals(method) && "/v1/status".equals(path)) {
                JSONObject status = new JSONObject().put("device", "OWW221").put("appVersion", BuildConfig.VERSION_NAME)
                        .put("activeSession", WorkoutService.hasRecoverableSession(this))
                        .put("backgroundLocation", checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        .put("transport", "lan-mdns").put("port", PORT);
                respond(socket, 200, status.toString());
            } else if ("GET".equals(method) && "/v1/plan".equals(path)) {
                respond(socket, 200, PlanStore.encode(PlanStore.load(this)));
            } else if ("GET".equals(method) && "/v1/plan/profile".equals(path)) {
                JSONObject profile = new JSONObject().put("name", PlanStore.name(this)).put("group", PlanStore.group(this))
                        .put("requirement", PlanStore.requirement(this)).put("stages", new org.json.JSONArray(PlanStore.encode(PlanStore.load(this))));
                respond(socket, 200, profile.toString());
            } else if ("GET".equals(method) && "/v1/plan-library".equals(path)) {
                respond(socket, 200, PlanLibraryStore.load(this).toString());
            } else if ("PUT".equals(method) && "/v1/plan-library".equals(path)) {
                JSONObject library = PlanLibraryStore.replace(this, new JSONObject(body));
                respond(socket, 200, new JSONObject().put("saved", true).put("revision", library.optLong("revision"))
                        .put("planCount", library.getJSONArray("plans").length()).put("selectedPlanId", library.optString("selectedPlanId")).toString());
            } else if ("PUT".equals(method) && "/v1/plan-selection".equals(path)) {
                JSONObject selected = PlanLibraryStore.select(this, new JSONObject(body).optString("planId"));
                respond(socket, 200, new JSONObject().put("selected", true).put("planId", selected.optString("id"))
                        .put("name", selected.optString("name")).toString());
            } else if ("PUT".equals(method) && "/v1/plan".equals(path)) {
                java.util.ArrayList<Stage> stages = PlanStore.decode(body);
                if (stages.isEmpty()) respond(socket, 422, error("invalid_plan"));
                else { PlanStore.save(this, stages); respond(socket, 200, new JSONObject().put("saved", true).put("stageCount", stages.size()).toString()); }
            } else if ("PUT".equals(method) && "/v1/plan/profile".equals(path)) {
                JSONObject profile = new JSONObject(body);
                java.util.ArrayList<Stage> stages = PlanStore.decode(profile.optJSONArray("stages") == null ? null : profile.optJSONArray("stages").toString());
                if (stages.isEmpty()) respond(socket, 422, error("invalid_plan"));
                else {
                    PlanStore.saveProfile(this, profile.optString("name", "自定义计划"), profile.optString("group", "我的计划"),
                            profile.optString("requirement", "按阶段顺序完成训练。"), stages);
                    respond(socket, 200, new JSONObject().put("saved", true).put("stageCount", stages.size()).toString());
                }
            } else if ("GET".equals(method) && "/v1/history".equals(path)) {
                respond(socket, 200, HistoryStore.toJson(this).toString());
            } else if ("GET".equals(method) && ("/v1/sleep".equals(path) || path.startsWith("/v1/sleep?"))) {
                int days = queryDays(path);
                JSONObject sleep = sleepBridge.read(days);
                respond(socket, 200, sleep.toString());
            } else if ("GET".equals(method) && path.startsWith("/v1/history/")) {
                WorkoutRecord record = HistoryStore.find(this, path.substring("/v1/history/".length()));
                if (record == null) respond(socket, 404, error("workout_not_found"));
                else respond(socket, 200, record.toJson().toString());
            } else if ("DELETE".equals(method) && path.startsWith("/v1/history/")) {
                HistoryStore.delete(this, path.substring("/v1/history/".length())); respond(socket, 200, "{\"deleted\":true}");
            } else if ("POST".equals(method) && "/v1/location".equals(path)) {
                JSONObject point = new JSONObject(body);
                double latitude = point.optDouble("latitude", Double.NaN), longitude = point.optDouble("longitude", Double.NaN);
                if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) respond(socket, 422, error("invalid_location"));
                else {
                    Intent relay = new Intent(this, WorkoutService.class).setAction(WorkoutService.ACTION_EXTERNAL_LOCATION)
                            .putExtra(WorkoutService.EXTRA_LATITUDE, latitude).putExtra(WorkoutService.EXTRA_LONGITUDE, longitude)
                            .putExtra(WorkoutService.EXTRA_ACCURACY, (float)point.optDouble("accuracy", 30d))
                            .putExtra(WorkoutService.EXTRA_SPEED, (float)point.optDouble("speed", -1d));
                    startService(relay); respond(socket, 200, "{\"accepted\":true}");
                }
            } else if ("POST".equals(method) && path.startsWith("/v1/control/")) {
                control(path.substring("/v1/control/".length())); respond(socket, 200, "{\"accepted\":true}");
            } else respond(socket, 404, error("not_found"));
        } catch (Exception ignored) {}
    }

    private void control(String action) {
        Intent intent = new Intent(this, WorkoutService.class);
        if ("start".equals(action)) intent.setAction(WorkoutService.ACTION_START).putExtra("plan", PlanStore.encode(PlanStore.load(this)));
        else if ("pause".equals(action) || "resume".equals(action) || "toggle".equals(action)) intent.setAction(WorkoutService.ACTION_TOGGLE);
        else if ("stop".equals(action)) intent.setAction(WorkoutService.ACTION_STOP);
        else return;
        startForegroundService(intent);
    }

    private String error(String code) { try { return new JSONObject().put("error", code).toString(); } catch (Exception ignored) { return "{}"; } }

    private int queryDays(String path) {
        int marker = path.indexOf("days=");
        if (marker < 0) return 7;
        int end = path.indexOf('&', marker);
        String value = path.substring(marker + 5, end < 0 ? path.length() : end);
        try { return Math.max(1, Math.min(31, Integer.parseInt(value))); }
        catch (NumberFormatException ignored) { return 7; }
    }

    private void respond(Socket socket, int status, String body) throws Exception {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : status == 401 ? "Unauthorized" : status == 404 ? "Not Found" : "Error";
        String header = "HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + data.length + "\r\nConnection: close\r\n\r\n";
        OutputStream output = socket.getOutputStream(); output.write(header.getBytes(StandardCharsets.US_ASCII)); output.write(data); output.flush();
    }

    private void registerNsd() {
        NsdServiceInfo info = new NsdServiceInfo(); info.setServiceName("WatchIntervals-OWW221"); info.setServiceType("_watchintervals._tcp."); info.setPort(PORT);
        registration = new NsdManager.RegistrationListener() {
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {}
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {}
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {}
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {}
        };
        try { getSystemService(NsdManager.class).registerService(info, NsdManager.PROTOCOL_DNS_SD, registration); } catch (Exception ignored) {}
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        closed = true; try { if (server != null) server.close(); } catch (Exception ignored) {}
        if (sleepBridge != null) sleepBridge.close();
        try { if (registration != null) getSystemService(NsdManager.class).unregisterService(registration); } catch (Exception ignored) {}
        workers.shutdownNow(); super.onDestroy();
    }
}

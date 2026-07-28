package com.poyi.watchintervals.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import org.json.JSONObject;
import com.poyi.watchintervals.phone.connection.WatchConnectionManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Phone GNSS fallback used when the watch's small antenna has no valid fix. */
public final class PhoneLocationRelayService extends Service implements LocationListener {
    private static final String CHANNEL = "location_relay";
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private LocationManager locations;
    private WatchConnectionManager connection;
    private long sequence;

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "手表轨迹辅助定位", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("正在辅助记录手表轨迹").setContentText("手表无有效定位时使用手机 GPS").setOngoing(true).build();
        try {
            startForeground(91, notification);
        } catch (RuntimeException notEligible) {
            // Android 14+ refuses a location-type FGS from a background caller. Dying here took
            // the whole process with it; stand down instead — the next foreground sync retries.
            stopSelf();
            return;
        }
        connection = WatchConnectionManager.get(this);
        locations = getSystemService(LocationManager.class);
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && locations != null) {
            try { locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1f, this); } catch (Exception ignored) {}
            try { locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 2f, this); } catch (Exception ignored) {}
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onLocationChanged(Location location) {
        if (location == null || (location.hasAccuracy() && location.getAccuracy() > 150f)) return;
        network.execute(() -> {
            try {
                JSONObject point = new JSONObject().put("sequence",++sequence).put("time",location.getTime()).put("elapsedRealtimeNanos",location.getElapsedRealtimeNanos())
                        .put("latitude", location.getLatitude()).put("longitude", location.getLongitude())
                        .put("accuracy", location.hasAccuracy() ? location.getAccuracy() : 30f)
                        .put("speed", location.hasSpeed() ? location.getSpeed() : -1f)
                        .put("bearing",location.hasBearing()?location.getBearing():-1f).put("source", "phone_gps");
                connection.requestBlocking("POST","/v1/location",point.toString(),8_000L);
            } catch (Exception ignored) {}
        });
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { if (locations != null) locations.removeUpdates(this); network.shutdownNow(); super.onDestroy(); }
}

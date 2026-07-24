package com.poyi.watchintervals;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import java.util.ArrayList;
import java.util.Locale;

public class WorkoutService extends Service implements LocationListener, SensorEventListener {
    public static final String ACTION_START = "com.poyi.watchintervals.START";
    public static final String ACTION_PREPARE = "com.poyi.watchintervals.PREPARE";
    public static final String ACTION_BEGIN = "com.poyi.watchintervals.BEGIN";
    public static final String ACTION_CANCEL_PREPARE = "com.poyi.watchintervals.CANCEL_PREPARE";
    public static final String ACTION_STOP = "com.poyi.watchintervals.STOP";
    public static final String ACTION_TOGGLE = "com.poyi.watchintervals.TOGGLE";
    public static final String ACTION_EXTERNAL_LOCATION = "com.poyi.watchintervals.EXTERNAL_LOCATION";
    public static final String EXTRA_LATITUDE = "latitude", EXTRA_LONGITUDE = "longitude", EXTRA_ACCURACY = "accuracy", EXTRA_SPEED = "speed";
    public static final String EXTRA_INITIAL_LOCATION = "com.poyi.watchintervals.INITIAL_LOCATION";
    public static final String EXTRA_INITIAL_HEART_RATE = "com.poyi.watchintervals.INITIAL_HEART_RATE";
    private static final String CHANNEL = "active_workout";
    private static final int NOTIFICATION_ID = 42;
    private static final String SESSION_PREF = "active_session";
    // Keep acquisition feedback separate from the stricter quality used to add distance.
    // Wearable GNSS often reports a coarse candidate fix before it converges.
    // OWW221's system GPS commonly exposes 100-150 m fixes while the sports
    // session is converging (the system sports app consumes the same provider).
    // The previous 60 m gate discarded every real sample, so distance, pace and
    // route all stayed empty.  Accept the wearable fix, then rely on the existing
    // displacement, elapsed-time and speed filters to reject stationary jumps.
    private static final float MAX_GPS_ACQUISITION_ACCURACY_METERS = 200f;
    private static final float MAX_GPS_TRACKING_ACCURACY_METERS = 150f;
    private static final float MIN_MOVING_SPEED_MPS = 0.15f;
    private static final float MAX_MOVING_SPEED_MPS = 15f;
    private static final long MAX_LOCATION_GAP_MILLIS = 60_000L;
    private static final long GPS_STALE_MILLIS = 75_000L;
    private static final long GPS_ROUTE_FRESH_MILLIS = 5_000L;
    private static final long HEART_RATE_STALE_MILLIS = 15_000L;
    private static final long SYSTEM_DISTANCE_STALE_MILLIS = 10_000L;
    private static final int MIN_HEART_RATE = 25;
    private static final int MAX_HEART_RATE = 240;
    private static final float DEFAULT_STEP_LENGTH_METERS = 0.72f;
    private static final int MAX_STEP_DELTA = 50;
    private final LocalBinder binder = new LocalBinder();
    private ArrayList<Stage> stages = new ArrayList<>();
    private int stageIndex = 0;
    private double totalMeters = 0, stageMeters = 0;
    private long activeMillis = 0, stageMillis = 0, lastTick = 0;
    private long workoutStartedAt = 0, heartRateTotal = 0;
    private int heartRateSamples = 0;
    private int heartRate = 0;
    private boolean running = false, preparing = false, paused = false, completed = false, historySaved = false;
    private boolean stageGpsReady = false;
    private boolean gpsPermissionGranted, gpsProviderEnabled, gpsUpdatesRegistered, gnssStatusRegistered;
    private boolean heartSensorAvailable, heartSensorRegistered, heartPermissionGranted;
    private boolean stepSensorAvailable, stepSensorRegistered, stepDetectorRegistered, activityRecognitionPermissionGranted;
    private long lastGpsFixElapsed = 0, lastTrackableGpsElapsed = 0;
    private long lastHeartRateElapsed = 0, lastHeartSensorEventElapsed = 0, heartSensorStartedElapsed = 0;
    private float lastGpsAccuracyMeters = -1f;
    private float lastStepCounterValue = Float.NaN;
    private int sessionSteps = 0;
    private int gpsSatelliteCount = 0, gpsSatellitesUsed = 0;
    private long lastCheckpoint = 0;
    private long lastForegroundCheckElapsed;
    private Location lastLocation;
    private Location latestGpsLocation;
    private final ArrayList<Location> routePoints = new ArrayList<>();
    private final ArrayList<Long> heartSampleTimes = new ArrayList<>();
    private final ArrayList<Integer> heartSampleValues = new ArrayList<>();
    private final org.json.JSONArray completedStageResults = new org.json.JSONArray();
    private long lastRecordedHeartAt;
    private boolean latestGpsLocationIsCached;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private SystemExerciseBridge systemExerciseBridge;
    private SystemGpsBridge systemGpsBridge;
    private SystemExerciseBridge.State systemExerciseState = SystemExerciseBridge.State.PROBING;
    private String systemExerciseDetail = "正在连接系统运动";
    private boolean systemExerciseAvailable, systemExerciseRegistered, systemExerciseDistanceActive;
    private boolean systemGpsAvailable, systemGpsLocated;
    private int systemGpsSnr;
    private String systemGpsDetail = "正在连接系统 GPS";
    private double lastSystemDistanceTotal = Double.NaN;
    private long lastSystemMetricElapsed, lastSystemDistanceElapsed;
    private PowerManager.WakeLock wakeLock;
    private CancellationSignal currentLocationSignal;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final GnssStatus.Callback gnssStatusCallback = new GnssStatus.Callback() {
        @Override public void onStarted() {
            synchronized (WorkoutService.this) { gpsProviderEnabled = true; }
        }

        @Override public void onStopped() {
            synchronized (WorkoutService.this) { gpsSatelliteCount = 0; gpsSatellitesUsed = 0; }
        }

        @Override public void onSatelliteStatusChanged(GnssStatus status) {
            int used = 0;
            for (int i = 0; i < status.getSatelliteCount(); i++) if (status.usedInFix(i)) used++;
            synchronized (WorkoutService.this) {
                gpsSatelliteCount = status.getSatelliteCount();
                gpsSatellitesUsed = used;
            }
        }
    };
    private final Runnable clock = new Runnable() {
        @Override public void run() {
            synchronized (WorkoutService.this) {
                if (running && !completed) {
                    tick();
                    keepTrainingTaskForeground();
                }
            }
            clockHandler.postDelayed(this, 500L);
        }
    };

    private void keepTrainingTaskForeground() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastForegroundCheckElapsed < 1000L) return;
        lastForegroundCheckElapsed = now;
        ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
        boolean alreadyForeground = false;
        try {
            java.util.List<ActivityManager.RunningTaskInfo> tasks = manager == null ? null : manager.getRunningTasks(1);
            alreadyForeground = tasks != null && !tasks.isEmpty() && tasks.get(0).topActivity != null
                    && getPackageName().equals(tasks.get(0).topActivity.getPackageName());
        } catch (RuntimeException ignored) { /* The activity-start fallback below is still valid. */ }
        if (alreadyForeground) return;
        Intent reopen = new Intent(this, TrainingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(TrainingActivity.EXTRA_PREPARED_SESSION, true);
        try { startActivity(reopen); }
        catch (RuntimeException error) { android.util.Log.w("WorkoutService", "Unable to restore training task", error); }
    }

    public final class LocalBinder extends Binder { public WorkoutService service() { return WorkoutService.this; } }
    @Override public IBinder onBind(Intent intent) { return binder; }

    public static boolean hasRecoverableSession(Context context) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(SESSION_PREF, MODE_PRIVATE);
        return preferences.getBoolean("active", false)
                && !PlanStore.decode(preferences.getString("plan", null)).isEmpty();
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "正在训练", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        locationManager = getSystemService(LocationManager.class);
        sensorManager = getSystemService(SensorManager.class);
        systemExerciseBridge = new SystemExerciseBridge(this, new SystemExerciseBridge.Listener() {
            @Override public void onSystemExerciseState(SystemExerciseBridge.State state, String detail) {
                synchronized (WorkoutService.this) {
                    systemExerciseState = state;
                    systemExerciseDetail = detail;
                    if (state == SystemExerciseBridge.State.READY
                            || state == SystemExerciseBridge.State.REGISTERED
                            || state == SystemExerciseBridge.State.PREPARING
                            || state == SystemExerciseBridge.State.ACTIVE
                            || state == SystemExerciseBridge.State.PAUSED) systemExerciseAvailable = true;
                    if (state == SystemExerciseBridge.State.REGISTERED) systemExerciseRegistered = true;
                    if (state == SystemExerciseBridge.State.UNAVAILABLE || state == SystemExerciseBridge.State.ERROR) {
                        systemExerciseAvailable = false;
                        systemExerciseRegistered = false;
                        systemExerciseDistanceActive = false;
                        lastSystemDistanceElapsed = 0;
                        lastLocation = null;
                    }
                }
            }

            @Override public void onSystemExerciseMetrics(SystemExerciseBridge.Metrics metrics) {
                handleSystemExerciseMetrics(metrics);
            }
        });
        systemExerciseBridge.probe();
        systemGpsBridge = new SystemGpsBridge(this, (available, located, snr, detail) -> {
            synchronized (WorkoutService.this) {
                systemGpsAvailable = available;
                systemGpsLocated = located;
                systemGpsSnr = snr;
                systemGpsDetail = detail;
                if (located && !stages.isEmpty() && currentStage().unit == Stage.Unit.DISTANCE) stageGpsReady = true;
            }
        });
        systemGpsBridge.probe();
        wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WatchIntervals:Workout");
        clockHandler.post(clock);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) { finishAndStop(); return START_NOT_STICKY; }
        if (ACTION_CANCEL_PREPARE.equals(action)) { cancelPreparation(); return START_NOT_STICKY; }
        if (ACTION_TOGGLE.equals(action)) { togglePause(); return START_NOT_STICKY; }
        if (ACTION_EXTERNAL_LOCATION.equals(action)) { acceptExternalLocation(intent); return START_NOT_STICKY; }
        if (ACTION_PREPARE.equals(action)) { startPreparation(intent); return START_NOT_STICKY; }
        if (ACTION_BEGIN.equals(action)) { beginWorkout(); return START_REDELIVER_INTENT; }
        if ((!running && !preparing || completed) && intent != null) startNewWorkout(intent);
        return START_REDELIVER_INTENT;
    }

    private synchronized void acceptExternalLocation(Intent intent) {
        if ((!running && !preparing) || completed || intent == null) return;
        double latitude = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN);
        double longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90d || latitude > 90d || longitude < -180d || longitude > 180d) return;
        Location location = new Location("phone_companion");
        location.setLatitude(latitude); location.setLongitude(longitude);
        location.setAccuracy(Math.max(1f, intent.getFloatExtra(EXTRA_ACCURACY, 30f)));
        float speed = intent.getFloatExtra(EXTRA_SPEED, -1f); if (speed >= 0f) location.setSpeed(speed);
        location.setTime(System.currentTimeMillis()); location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        onLocationChanged(location);
    }

    private synchronized void startNewWorkout(Intent intent) {
        boolean restored = !completed && restoreSession();
        if (!restored) {
            resetSession();
            stages = decodePlan(intent);
            running = true;
            workoutStartedAt = System.currentTimeMillis();
            applyWarmupData(intent);
        }
        lastTick = SystemClock.elapsedRealtime();
        startForeground(NOTIFICATION_ID, notification());
        startSensors();
        systemExerciseBridge.start();
        systemGpsBridge.start();
        if (restored) getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
        else announceStage();
        saveSession(true);
    }

    private synchronized void startPreparation(Intent intent) {
        if (running || preparing) return;
        resetSession();
        stages = decodePlan(intent);
        preparing = true;
        startForeground(NOTIFICATION_ID, notification());
        startSensors();
        systemExerciseBridge.prepare();
        systemGpsBridge.start();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
    }

    private ArrayList<Stage> decodePlan(Intent intent) {
        String plan = intent.getStringExtra("plan");
        String encodedPlan = intent.getStringExtra("plan_b64");
        if (plan == null && encodedPlan != null) {
            try { plan = new String(Base64.decode(encodedPlan, Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8); }
            catch (IllegalArgumentException ignored) {}
        }
        ArrayList<Stage> result = PlanStore.decode(plan);
        return result.isEmpty() ? PlanStore.defaultPlan() : result;
    }

    private void resetSession() {
        stopSensors();
        stageIndex = 0;
        totalMeters = 0;
        stageMeters = 0;
        activeMillis = 0;
        stageMillis = 0;
        heartRate = 0;
        heartRateTotal = 0;
        heartRateSamples = 0;
        workoutStartedAt = 0;
        preparing = false;
        paused = false;
        completed = false;
        historySaved = false;
        stageGpsReady = false;
        lastGpsFixElapsed = 0;
        lastTrackableGpsElapsed = 0;
        lastGpsAccuracyMeters = -1f;
        lastStepCounterValue = Float.NaN;
        sessionSteps = 0;
        systemExerciseDistanceActive = false;
        lastSystemDistanceTotal = Double.NaN;
        lastSystemMetricElapsed = 0;
        lastSystemDistanceElapsed = 0;
        lastHeartRateElapsed = 0;
        lastHeartSensorEventElapsed = 0;
        lastLocation = null;
        latestGpsLocation = null;
        latestGpsLocationIsCached = false;
        routePoints.clear();
        heartSampleTimes.clear(); heartSampleValues.clear();
        while (completedStageResults.length() > 0) completedStageResults.remove(0);
        lastRecordedHeartAt = 0;
    }

    private void applyWarmupData(Intent intent) {
        int initialHeartRate = intent.getIntExtra(EXTRA_INITIAL_HEART_RATE, 0);
        if (initialHeartRate >= MIN_HEART_RATE && initialHeartRate <= MAX_HEART_RATE) {
            heartRate = initialHeartRate;
            lastHeartRateElapsed = SystemClock.elapsedRealtime();
        }
        Location initialLocation = intent.getParcelableExtra(EXTRA_INITIAL_LOCATION);
        if (!isTrackableGpsLocation(initialLocation)) return;
        lastLocation = new Location(initialLocation);
        latestGpsLocation = new Location(initialLocation);
        latestGpsLocationIsCached = false;
        lastGpsFixElapsed = SystemClock.elapsedRealtime();
        lastTrackableGpsElapsed = lastGpsFixElapsed;
        lastGpsAccuracyMeters = initialLocation.getAccuracy();
        gpsProviderEnabled = true;
        if (!stages.isEmpty() && currentStage().unit == Stage.Unit.DISTANCE) stageGpsReady = true;
    }

    public synchronized boolean beginWorkout() {
        if (!preparing || stages.isEmpty()) return false;
        stageIndex = 0;
        totalMeters = 0;
        stageMeters = 0;
        activeMillis = 0;
        stageMillis = 0;
        paused = false;
        completed = false;
        running = true;
        preparing = false;
        workoutStartedAt = System.currentTimeMillis();
        lastTick = SystemClock.elapsedRealtime();
        resetStageGpsBaseline();
        lastSystemDistanceTotal = Double.NaN;
        systemExerciseDistanceActive = false;
        systemExerciseBridge.start();
        startForeground(NOTIFICATION_ID, notification());
        announceStage();
        saveSession(true);
        return true;
    }

    public synchronized void cancelPreparation() {
        if (!preparing || running) return;
        preparing = false;
        systemExerciseBridge.end();
        systemGpsBridge.stop();
        stopSensors();
        stopForeground(true);
        stopSelf();
    }

    private boolean restoreSession() {
        android.content.SharedPreferences preferences = getSharedPreferences(SESSION_PREF, MODE_PRIVATE);
        if (!preferences.getBoolean("active", false)) return false;
        ArrayList<Stage> restoredStages = PlanStore.decode(preferences.getString("plan", null));
        if (restoredStages.isEmpty()) { clearSession(); return false; }
        stages = restoredStages;
        stageIndex = Math.min(Math.max(0, preferences.getInt("stage_index", 0)), stages.size() - 1);
        totalMeters = preferences.getFloat("total_meters", 0);
        stageMeters = preferences.getFloat("stage_meters", 0);
        activeMillis = preferences.getLong("active_millis", 0);
        stageMillis = preferences.getLong("stage_millis", 0);
        heartRate = preferences.getInt("heart_rate", 0);
        heartRateTotal = preferences.getLong("heart_rate_total", 0);
        heartRateSamples = preferences.getInt("heart_rate_samples", 0);
        workoutStartedAt = preferences.getLong("started_at", System.currentTimeMillis());
        paused = preferences.getBoolean("paused", false);
        completed = false;
        historySaved = false;
        running = true;
        preparing = false;
        stageGpsReady = false;
        lastGpsFixElapsed = 0;
        lastTrackableGpsElapsed = 0;
        lastGpsAccuracyMeters = -1f;
        lastStepCounterValue = Float.NaN;
        sessionSteps = preferences.getInt("session_steps", 0);
        systemExerciseDistanceActive = false;
        lastSystemDistanceTotal = Double.NaN;
        lastSystemMetricElapsed = 0;
        lastSystemDistanceElapsed = 0;
        lastHeartRateElapsed = 0;
        lastHeartSensorEventElapsed = 0;
        lastLocation = null;
        latestGpsLocation = null;
        latestGpsLocationIsCached = false;
        restoreRoute(preferences.getString("route", null));
        restoreHeartSamples(preferences.getString("heart_samples_json", null));
        restoreStageResults(preferences.getString("stage_results_json", null));
        return true;
    }

    private void saveSession(boolean force) {
        if (!running || completed || stages.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastCheckpoint < 5000L) return;
        lastCheckpoint = now;
        getSharedPreferences(SESSION_PREF, MODE_PRIVATE).edit()
                .putBoolean("active", true)
                .putString("plan", PlanStore.encode(stages))
                .putInt("stage_index", stageIndex)
                .putFloat("total_meters", (float) totalMeters)
                .putFloat("stage_meters", (float) stageMeters)
                .putLong("active_millis", activeMillis)
                .putLong("stage_millis", stageMillis)
                .putInt("heart_rate", heartRate)
                .putLong("heart_rate_total", heartRateTotal)
                .putInt("heart_rate_samples", heartRateSamples)
                .putInt("session_steps", sessionSteps)
                .putLong("started_at", workoutStartedAt)
                .putBoolean("paused", paused)
                .putString("route", encodeRoute())
                .putString("heart_samples_json", encodeHeartSamples())
                .putString("stage_results_json", completedStageResults.toString())
                .apply();
    }

    private void clearSession() {
        getSharedPreferences(SESSION_PREF, MODE_PRIVATE).edit().clear().apply();
    }

    private String encodeRoute() {
        org.json.JSONArray encoded = new org.json.JSONArray();
        for (Location point : routePoints) {
            org.json.JSONObject item = new org.json.JSONObject();
            try {
                item.put("latitude", point.getLatitude());
                item.put("longitude", point.getLongitude());
                item.put("time", point.getTime());
                item.put("accuracy", point.hasAccuracy() ? point.getAccuracy() : 0);
                if (point.hasAltitude()) item.put("altitude", point.getAltitude());
                if (point.hasSpeed()) item.put("speed", point.getSpeed());
                encoded.put(item);
            } catch (org.json.JSONException ignored) {}
        }
        return encoded.toString();
    }

    private void restoreRoute(String encoded) {
        routePoints.clear();
        if (encoded == null || encoded.isEmpty()) return;
        if (encoded.startsWith("[")) {
            try {
                org.json.JSONArray points = new org.json.JSONArray(encoded);
                for (int index = 0; index < points.length() && routePoints.size() < 600; index++) {
                    org.json.JSONObject point = points.optJSONObject(index);
                    if (point == null) continue;
                    Location location = new Location("session");
                    location.setLatitude(point.getDouble("latitude"));
                    location.setLongitude(point.getDouble("longitude"));
                    location.setTime(point.optLong("time", 0));
                    location.setAccuracy((float) point.optDouble("accuracy", MAX_GPS_TRACKING_ACCURACY_METERS));
                    if (point.has("altitude")) location.setAltitude(point.getDouble("altitude"));
                    if (point.has("speed")) location.setSpeed((float) point.getDouble("speed"));
                    routePoints.add(location);
                }
            } catch (org.json.JSONException ignored) {}
            return;
        }
        // Compatibility with checkpoints written before schema 2.
        String[] points = encoded.split(";");
        for (String point : points) {
            String[] coordinates = point.split(",");
            if (coordinates.length != 2) continue;
            try {
                Location location = new Location("session");
                location.setLatitude(Double.parseDouble(coordinates[0]));
                location.setLongitude(Double.parseDouble(coordinates[1]));
                location.setAccuracy(MAX_GPS_TRACKING_ACCURACY_METERS);
                routePoints.add(location);
            } catch (NumberFormatException ignored) { /* Skip a damaged checkpoint point. */ }
            if (routePoints.size() >= 600) break;
        }
    }

    private String encodeHeartSamples() {
        org.json.JSONArray result = new org.json.JSONArray();
        for (int index = 0; index < Math.min(heartSampleTimes.size(), heartSampleValues.size()); index++) {
            result.put(new org.json.JSONArray()
                    .put(heartSampleTimes.get(index))
                    .put(heartSampleValues.get(index)));
        }
        return result.toString();
    }

    private void restoreHeartSamples(String encoded) {
        heartSampleTimes.clear();
        heartSampleValues.clear();
        if (encoded == null || encoded.isEmpty()) return;
        try {
            org.json.JSONArray values = new org.json.JSONArray(encoded);
            for (int index = 0; index < values.length() && index < 7200; index++) {
                org.json.JSONArray sample = values.optJSONArray(index);
                if (sample == null || sample.length() < 2) continue;
                heartSampleTimes.add(sample.optLong(0));
                heartSampleValues.add(sample.optInt(1));
            }
            if (!heartSampleTimes.isEmpty()) {
                lastRecordedHeartAt = heartSampleTimes.get(heartSampleTimes.size() - 1);
            }
        } catch (org.json.JSONException ignored) {}
    }

    private void restoreStageResults(String encoded) {
        while (completedStageResults.length() > 0) completedStageResults.remove(0);
        if (encoded == null || encoded.isEmpty()) return;
        try {
            org.json.JSONArray values = new org.json.JSONArray(encoded);
            for (int index = 0; index < values.length(); index++) {
                org.json.JSONObject value = values.optJSONObject(index);
                if (value != null) completedStageResults.put(value);
            }
        } catch (org.json.JSONException ignored) {}
    }

    private Notification notification() {
        Intent open = new Intent(this, preparing ? WarmupActivity.class : TrainingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("plan", PlanStore.encode(stages));
        PendingIntent content = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = completed ? "训练完成" : preparing ? "正在准备 " + currentStage().name() : currentStage().name() + " · 剩余 " + remainingText();
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(com.poyi.watchintervals.R.drawable.ic_launcher)
                .setContentTitle("步序").setContentText(text).setContentIntent(content).setOngoing(!completed);
        if (preparing) {
            Intent cancel = new Intent(this, WorkoutService.class).setAction(ACTION_CANCEL_PREPARE);
            PendingIntent cancelIntent = PendingIntent.getService(this, 3, cancel, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null, "取消", cancelIntent).build());
        } else if (!completed) {
            Intent toggle = new Intent(this, WorkoutService.class).setAction(ACTION_TOGGLE);
            Intent stop = new Intent(this, WorkoutService.class).setAction(ACTION_STOP);
            PendingIntent toggleIntent = PendingIntent.getService(this, 2, toggle, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stopIntent = PendingIntent.getService(this, 3, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null, paused ? "继续" : "暂停", toggleIntent).build());
            builder.addAction(new Notification.Action.Builder(null, "结束", stopIntent).build());
        }
        return builder.build();
    }

    private void startSensors() {
        gpsPermissionGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        gpsProviderEnabled = gpsPermissionGranted && isGpsProviderEnabled();
        if (gpsPermissionGranted && gpsProviderEnabled) {
            try {
                Location cached = newestLocation(
                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                        locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER));
                if (isFreshCachedGpsLocation(cached)) seedCachedGpsLocation(cached);
            } catch (SecurityException | IllegalArgumentException ignored) { /* A cache is only a warm-start hint. */ }
            try {
                // A one-second route cadence matches the visible sports metrics while avoiding
                // duplicate sub-second points and unnecessary GNSS wakeups on this watch.
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
                // System health and sports components also publish their accepted
                // fixes through the passive provider.  Subscribe to both sources;
                // route de-duplication below collapses identical GPS/passive points.
                locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
                gpsUpdatesRegistered = true;
            } catch (SecurityException | IllegalArgumentException ignored) { gpsUpdatesRegistered = false; }
            try { gnssStatusRegistered = locationManager.registerGnssStatusCallback(gnssStatusCallback, clockHandler); }
            catch (SecurityException | IllegalArgumentException ignored) { gnssStatusRegistered = false; }
            try {
                currentLocationSignal = new CancellationSignal();
                locationManager.getCurrentLocation(LocationManager.GPS_PROVIDER, currentLocationSignal, getMainExecutor(), location -> {
                    if (location != null) onLocationChanged(location);
                });
            } catch (SecurityException | IllegalArgumentException ignored) { currentLocationSignal = null; }
        }
        Sensor heart = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        heartSensorAvailable = heart != null;
        heartPermissionGranted = checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED;
        heartSensorStartedElapsed = SystemClock.elapsedRealtime();
        if (heart != null && heartPermissionGranted) heartSensorRegistered = sensorManager.registerListener(this, heart, SensorManager.SENSOR_DELAY_NORMAL);
        Sensor stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        Sensor stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        stepSensorAvailable = stepDetector != null || stepCounter != null;
        activityRecognitionPermissionGranted = checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        if (activityRecognitionPermissionGranted) {
            // This firmware's vendor "Step_detector" may report a cumulative value despite its
            // Android type. Prefer the cumulative counter and always derive an actual delta.
            if (stepCounter != null) {
                stepSensorRegistered = sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            } else if (stepDetector != null) {
                stepSensorRegistered = sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL);
                stepDetectorRegistered = stepSensorRegistered;
            }
        }
        if (!wakeLock.isHeld()) wakeLock.acquire(4 * 60 * 60 * 1000L);
    }

    private void refreshSensorRegistrations() {
        heartPermissionGranted = checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED;
        if (!heartSensorRegistered && heartPermissionGranted) {
            Sensor heart = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            heartSensorAvailable = heart != null;
            if (heart != null) {
                heartSensorStartedElapsed = SystemClock.elapsedRealtime();
                heartSensorRegistered = sensorManager.registerListener(this, heart, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
        activityRecognitionPermissionGranted = checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        if (!stepSensorRegistered && activityRecognitionPermissionGranted) {
            Sensor stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            Sensor stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            stepSensorAvailable = stepDetector != null || stepCounter != null;
            if (stepCounter != null) {
                stepSensorRegistered = sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            } else if (stepDetector != null) {
                stepSensorRegistered = sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL);
                stepDetectorRegistered = stepSensorRegistered;
            }
        }
    }

    private void stopSensors() {
        if (currentLocationSignal != null) { currentLocationSignal.cancel(); currentLocationSignal = null; }
        if (gpsUpdatesRegistered) locationManager.removeUpdates(this);
        if (gnssStatusRegistered) locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
        if (heartSensorRegistered || stepSensorRegistered) sensorManager.unregisterListener(this);
        gpsUpdatesRegistered = false;
        gnssStatusRegistered = false;
        heartSensorRegistered = false;
        stepSensorRegistered = false;
        stepDetectorRegistered = false;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    public synchronized void togglePause() {
        if (!running || completed) return;
        tick(); paused = !paused;
        if (paused) {
            lastLocation = null;
            stageGpsReady = false;
            systemExerciseBridge.pause();
        } else {
            resetStageGpsBaseline();
            systemExerciseBridge.resume();
        }
        lastTick = SystemClock.elapsedRealtime();
        vibrate(paused ? new long[]{0, 150} : new long[]{0, 100, 80, 100});
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
        saveSession(true);
    }

    public synchronized void finishAndStop() {
        tick(); saveHistoryIfNeeded(); running = false; preparing = false; clearSession(); systemExerciseBridge.end(); systemGpsBridge.stop(); stopSensors(); stopForeground(true); stopSelf();
    }

    private synchronized void tick() {
        long now = SystemClock.elapsedRealtime();
        if (running && !paused && !completed && lastTick > 0) {
            Stage stage = currentStage();
            long delta = Math.max(0, now - lastTick);
            activeMillis += delta;
            if (stage.unit == Stage.Unit.TIME) {
                stageMillis += delta;
                checkTransition();
            }
            saveSession(false);
        }
        lastTick = now;
    }

    private synchronized void handleSystemExerciseMetrics(SystemExerciseBridge.Metrics metrics) {
        if ((!running && !preparing) || completed) return;
        long now = SystemClock.elapsedRealtime();
        boolean distanceWasStale = systemExerciseDistanceActive
                && lastSystemDistanceElapsed > 0
                && now - lastSystemDistanceElapsed > SYSTEM_DISTANCE_STALE_MILLIS;
        lastSystemMetricElapsed = now;
        systemExerciseAvailable = true;
        systemExerciseRegistered = true;
        if (metrics.exerciseState != null && !metrics.exerciseState.isEmpty()) {
            systemExerciseDetail = "系统运动 · " + metrics.exerciseState;
        }
        if (metrics.heartRate >= MIN_HEART_RATE && metrics.heartRate <= MAX_HEART_RATE) {
            heartRate = metrics.heartRate;
            if (running && !paused) { heartRateTotal += metrics.heartRate; heartRateSamples++; }
            lastHeartRateElapsed = lastSystemMetricElapsed;
            lastHeartSensorEventElapsed = lastSystemMetricElapsed;
            recordHeartSample(metrics.heartRate);
        }
        if (metrics.stepsTotal >= 0) sessionSteps = Math.max(sessionSteps, metrics.stepsTotal);
        if (metrics.gpsSatelliteCount >= 0) gpsSatelliteCount = metrics.gpsSatelliteCount;
        if (Double.isFinite(metrics.latitude) && Double.isFinite(metrics.longitude)
                && metrics.latitude >= -90d && metrics.latitude <= 90d
                && metrics.longitude >= -180d && metrics.longitude <= 180d) {
            Location systemLocation = new Location("system_exercise");
            systemLocation.setLatitude(metrics.latitude);
            systemLocation.setLongitude(metrics.longitude);
            systemLocation.setAccuracy(metrics.locationAccuracyMeters > 0f
                    ? metrics.locationAccuracyMeters : MAX_GPS_TRACKING_ACCURACY_METERS);
            systemLocation.setTime(System.currentTimeMillis());
            systemLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            onLocationChanged(systemLocation);
        }
        if (!running || paused) {
            if (metrics.distanceTotalMeters >= 0) lastSystemDistanceTotal = metrics.distanceTotalMeters;
            return;
        }

        double distanceDelta = -1d;
        if (metrics.distanceTotalMeters >= 0) {
            if (Double.isNaN(lastSystemDistanceTotal) || distanceWasStale) {
                lastSystemDistanceTotal = metrics.distanceTotalMeters;
                distanceDelta = 0d;
            } else {
                distanceDelta = metrics.distanceTotalMeters - lastSystemDistanceTotal;
                lastSystemDistanceTotal = metrics.distanceTotalMeters;
            }
            systemExerciseDistanceActive = true;
        } else if (metrics.distanceSampleMeters >= 0) {
            if (Double.isNaN(lastSystemDistanceTotal) || distanceWasStale) {
                lastSystemDistanceTotal = metrics.distanceSampleMeters;
                distanceDelta = 0d;
            } else {
                distanceDelta = metrics.distanceSampleMeters - lastSystemDistanceTotal;
                lastSystemDistanceTotal = metrics.distanceSampleMeters;
            }
            systemExerciseDistanceActive = true;
        }
        if (!systemExerciseDistanceActive) return;
        lastSystemDistanceElapsed = now;
        lastLocation = null;
        if (!stages.isEmpty() && currentStage().unit == Stage.Unit.DISTANCE) stageGpsReady = true;
        if (distanceDelta > 0d && distanceDelta <= 1000d) {
            applyDistanceDelta(distanceDelta);
            saveSession(false);
        }
    }

    @Override public synchronized void onLocationChanged(Location location) {
        if ((!running && !preparing) || completed) return;
        tick();
        if (location != null && location.hasAccuracy()) lastGpsAccuracyMeters = location.getAccuracy();
        if (!isAcquiredGpsLocation(location)) {
            if (running && !paused) {
                lastLocation = null;
                if (!isSystemDistanceFresh() && !canEstimateDistanceFromSteps()) stageGpsReady = false;
            }
            return;
        }
        lastGpsFixElapsed = SystemClock.elapsedRealtime();
        lastGpsAccuracyMeters = location.getAccuracy();
        gpsProviderEnabled = true;
        latestGpsLocation = new Location(location);
        latestGpsLocationIsCached = false;
        if (isTrackableGpsLocation(location)) lastTrackableGpsElapsed = lastGpsFixElapsed;
        if (preparing || paused) return;
        if (!isTrackableGpsLocation(location)) {
            lastLocation = null;
            if (!isSystemDistanceFresh() && !canEstimateDistanceFromSteps()) stageGpsReady = false;
            saveSession(false);
            return;
        }
        recordRoutePoint(location);
        if (isSystemDistanceFresh()) {
            lastLocation = null;
            if (currentStage().unit == Stage.Unit.DISTANCE) stageGpsReady = true;
            saveSession(false);
            return;
        }
        if (lastLocation != null) {
            float delta = lastLocation.distanceTo(location);
            long dtNanos = location.getElapsedRealtimeNanos() - lastLocation.getElapsedRealtimeNanos();
            long dt = dtNanos > 0 ? dtNanos / 1_000_000L : location.getTime() - lastLocation.getTime();
            if (dt <= 0 || dt > MAX_LOCATION_GAP_MILLIS) {
                lastLocation = new Location(location);
                saveSession(false);
                return;
            }
            float metersPerSecond = delta * 1000f / dt;
            float reportedSpeed = location.hasSpeed() ? location.getSpeed() : -1f;
            float minDelta = Math.max(1.5f, Math.min(6f, Math.max(lastLocation.getAccuracy(), location.getAccuracy()) * 0.12f));
            if (delta < minDelta) {
                saveSession(false);
                return;
            }
            if (metersPerSecond >= MIN_MOVING_SPEED_MPS && metersPerSecond <= MAX_MOVING_SPEED_MPS
                    && (reportedSpeed < 0 || reportedSpeed <= MAX_MOVING_SPEED_MPS + 2f)) {
                int previousStage = stageIndex;
                applyDistanceDelta(delta);
                if (completed || stageIndex != previousStage) {
                    if (!completed) resetStageGpsBaseline();
                } else {
                    lastLocation = new Location(location);
                }
            } else {
                lastLocation = new Location(location);
            }
        } else {
            lastLocation = new Location(location);
            if (currentStage().unit == Stage.Unit.DISTANCE) stageGpsReady = true;
        }
        saveSession(false);
    }

    private void resetStageGpsBaseline() {
        lastLocation = null;
        stageGpsReady = false;
        if (stages.isEmpty() || currentStage().unit != Stage.Unit.DISTANCE) return;
        if (isSystemDistanceFresh()) {
            stageGpsReady = true;
        } else if (hasTrackableFreshGpsFix() && hasRecentTrackableGpsFix()) {
            lastLocation = new Location(latestGpsLocation);
            stageGpsReady = true;
        } else if (canEstimateDistanceFromSteps()) {
            // The counter baseline is established before training begins, so only new steps count.
            stageGpsReady = true;
        }
    }

    private void recordRoutePoint(Location location) {
        if (!running || paused || location == null) return;
        if (routePoints.isEmpty()) {
            routePoints.add(new Location(location));
            return;
        }
        Location previous = routePoints.get(routePoints.size() - 1);
        float deltaMeters = previous.distanceTo(location);
        long deltaNanos = location.getElapsedRealtimeNanos() - previous.getElapsedRealtimeNanos();
        long deltaMillis = deltaNanos > 0 ? deltaNanos / 1_000_000L : location.getTime() - previous.getTime();
        // The system route parser discards impossible GNSS jumps before map
        // framing. Do the same so one network/GPS hand-off cannot zoom a local
        // workout out to province level.
        if (deltaMeters > 1000f) return;
        if (deltaMillis > 0 && deltaMillis <= MAX_LOCATION_GAP_MILLIS
                && deltaMeters * 1000f / deltaMillis > MAX_MOVING_SPEED_MPS) return;
        float threshold = Math.max(2f, Math.min(8f, Math.max(previous.getAccuracy(), location.getAccuracy()) * 0.1f));
        if (deltaMeters < threshold) return;
        if (routePoints.size() >= 600) routePoints.remove(1);
        routePoints.add(new Location(location));
    }

    private void seedCachedGpsLocation(Location location) {
        lastGpsFixElapsed = SystemClock.elapsedRealtime();
        lastGpsAccuracyMeters = location.getAccuracy();
        gpsProviderEnabled = true;
        latestGpsLocation = new Location(location);
        latestGpsLocationIsCached = true;
    }

    private boolean isAcquiredGpsLocation(Location location) {
        return location != null && Double.isFinite(location.getLatitude()) && Double.isFinite(location.getLongitude())
                && location.getLatitude() >= -90d && location.getLatitude() <= 90d
                && location.getLongitude() >= -180d && location.getLongitude() <= 180d
                && (!location.hasAccuracy() || location.getAccuracy() <= MAX_GPS_ACQUISITION_ACCURACY_METERS);
    }

    private boolean isTrackableGpsLocation(Location location) {
        return isAcquiredGpsLocation(location)
                && (!location.hasAccuracy() || location.getAccuracy() <= MAX_GPS_TRACKING_ACCURACY_METERS);
    }

    private boolean isFreshCachedGpsLocation(Location location) {
        if (!isAcquiredGpsLocation(location)) return false;
        long elapsedNanos = location.getElapsedRealtimeNanos();
        if (elapsedNanos <= 0L) return false;
        long ageNanos = SystemClock.elapsedRealtimeNanos() - elapsedNanos;
        return ageNanos >= 0L && ageNanos <= GPS_STALE_MILLIS * 1_000_000L;
    }

    private Location newestLocation(Location first, Location second) {
        if (first == null) return second;
        if (second == null) return first;
        return second.getElapsedRealtimeNanos() > first.getElapsedRealtimeNanos() ? second : first;
    }

    private boolean isGpsProviderEnabled() {
        try { return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER); }
        catch (SecurityException ignored) { return false; }
    }

    private boolean hasFreshGpsFix() {
        return lastGpsFixElapsed > 0 && SystemClock.elapsedRealtime() - lastGpsFixElapsed <= GPS_STALE_MILLIS;
    }

    private boolean hasTrackableFreshGpsFix() {
        return hasFreshGpsFix() && latestGpsLocation != null && !latestGpsLocationIsCached
                && isTrackableGpsLocation(latestGpsLocation);
    }

    private boolean hasRecentTrackableGpsFix() {
        return lastTrackableGpsElapsed > 0
                && SystemClock.elapsedRealtime() - lastTrackableGpsElapsed <= GPS_ROUTE_FRESH_MILLIS;
    }

    private boolean canEstimateDistanceFromSteps() {
        return stepSensorRegistered && activityRecognitionPermissionGranted
                && (stepDetectorRegistered || !Float.isNaN(lastStepCounterValue));
    }

    private boolean hasFreshHeartRate() {
        return lastHeartRateElapsed > 0 && SystemClock.elapsedRealtime() - lastHeartRateElapsed <= HEART_RATE_STALE_MILLIS;
    }

    private boolean isSystemDistanceFresh() {
        return systemExerciseDistanceActive && lastSystemDistanceElapsed > 0
                && SystemClock.elapsedRealtime() - lastSystemDistanceElapsed <= SYSTEM_DISTANCE_STALE_MILLIS;
    }

    private void applyDistanceDelta(double meters) {
        if (meters <= 0d || completed || stages.isEmpty()) return;
        totalMeters += meters;
        double remainingDelta = meters;
        while (remainingDelta > 0d && !completed && currentStage().unit == Stage.Unit.DISTANCE) {
            double needed = Math.max(0d, currentStage().target - stageMeters);
            double consumed = Math.min(remainingDelta, needed);
            stageMeters += consumed;
            remainingDelta -= consumed;
            if (stageMeters + 0.0001d < currentStage().target) break;
            checkTransition();
        }
    }

    private void checkTransition() {
        if (completed || stages.isEmpty()) return;
        Stage stage = currentStage();
        boolean reached = stage.unit == Stage.Unit.DISTANCE ? stageMeters >= stage.target : stageMillis >= stage.target * 1000L;
        if (!reached) return;
        vibrate(new long[]{0, 260, 100, 260, 100, 500});
        try { completedStageResults.put(new org.json.JSONObject().put("index", stageIndex + 1).put("name", stage.name())
                .put("unit", stage.unit.name()).put("target", stage.target).put("completedAtMs", activeMillis)
                .put("totalDistanceMeters", Math.round(totalMeters * 10d) / 10d)); } catch (Exception ignored) {}
        stageIndex++;
        if (stageIndex >= stages.size()) {
            completed = true; paused = false; systemExerciseBridge.end(); systemGpsBridge.stop(); stopSensors();
            saveHistoryIfNeeded();
            clearSession();
            getSharedPreferences("history", MODE_PRIVATE).edit()
                    .putLong("last_duration_ms", activeMillis)
                    .putFloat("last_distance_m", (float)totalMeters)
                    .putInt("last_heart_rate", heartRate)
                    .putLong("last_completed_at", System.currentTimeMillis()).apply();
            // The bound activity keeps the completion screen alive, but the workout is no longer a foreground task.
            stopForeground(true);
            stopSelf();
        } else {
            stageMeters = 0; stageMillis = 0; resetStageGpsBaseline(); announceStage();
        }
    }

    private void announceStage() {
        vibrate(new long[]{0, 130, 80, 130});
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
    }

    private void vibrate(long[] pattern) {
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
    }

    private Stage currentStage() { return stages.get(Math.min(stageIndex, stages.size() - 1)); }

    public synchronized Snapshot snapshot() {
        // A notification/task restore can bind before an inactive service has
        // received ACTION_START. Keep that transient state renderable instead
        // of indexing an empty stage list.
        if (stages.isEmpty()) stages = PlanStore.load(this);
        if (stages.isEmpty()) stages = PlanStore.defaultPlan();
        stageIndex = Math.max(0, Math.min(stageIndex, stages.size() - 1));
        tick();
        if ((running || preparing) && !completed) refreshSensorRegistrations();
        Stage stage = currentStage();
        double progress = stage.unit == Stage.Unit.DISTANCE ? stageMeters / stage.target : stageMillis / (stage.target * 1000d);
        long remaining = stage.unit == Stage.Unit.DISTANCE ? Math.max(0, Math.round(stage.target - stageMeters)) : Math.max(0, (stage.target * 1000L - stageMillis + 999) / 1000);
        gpsPermissionGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        gpsProviderEnabled = gpsPermissionGranted && isGpsProviderEnabled();
        activityRecognitionPermissionGranted = checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        boolean usingSystemExerciseDistance = isSystemDistanceFresh();
        boolean usingStepDistance = stage.unit == Stage.Unit.DISTANCE && !usingSystemExerciseDistance
                && canEstimateDistanceFromSteps() && !hasRecentTrackableGpsFix();
        boolean waitingForGps = stage.unit == Stage.Unit.DISTANCE && !stageGpsReady;
        double stageProgressValue = stage.unit == Stage.Unit.DISTANCE ? stageMeters : stageMillis;
        int visibleHeartRate = completed || hasFreshHeartRate() ? heartRate : 0;
        boolean heartSensorWarmingUp = heartSensorRegistered && heartSensorStartedElapsed > 0
                && SystemClock.elapsedRealtime() - heartSensorStartedElapsed <= HEART_RATE_STALE_MILLIS;
        double[] routeLatitudes = new double[routePoints.size()];
        double[] routeLongitudes = new double[routePoints.size()];
        for (int index = 0; index < routePoints.size(); index++) {
            Location point = routePoints.get(index);
            routeLatitudes[index] = point.getLatitude();
            routeLongitudes[index] = point.getLongitude();
        }
        return new Snapshot(stage.name(), stage.unit, stage.target, stageProgressValue, remaining, Math.min(1, progress), Math.min(stageIndex + 1, stages.size()), stages.size(), totalMeters, activeMillis, visibleHeartRate,
                gpsPermissionGranted, gpsProviderEnabled, gpsUpdatesRegistered, hasTrackableFreshGpsFix(), latestGpsLocationIsCached, waitingForGps, gpsSatelliteCount, gpsSatellitesUsed, lastGpsAccuracyMeters,
                stepSensorAvailable, activityRecognitionPermissionGranted, stepSensorRegistered, usingStepDistance, sessionSteps,
                heartSensorAvailable, heartPermissionGranted, heartSensorRegistered, lastHeartSensorEventElapsed > 0, heartSensorWarmingUp,
                systemExerciseAvailable, systemExerciseRegistered, usingSystemExerciseDistance, systemExerciseState, systemExerciseDetail,
                systemGpsAvailable, systemGpsLocated, systemGpsSnr, systemGpsDetail,
                routeLatitudes, routeLongitudes,
                preparing, paused, completed);
    }

    private String remainingText() {
        Snapshot s = snapshot();
        if (s.unit == Stage.Unit.DISTANCE) return s.remaining + "米";
        return String.format(Locale.CHINA, "%d:%02d", s.remaining / 60, s.remaining % 60);
    }

    @Override public synchronized void onSensorChanged(SensorEvent event) {
        if ((!running && !preparing) || completed) return;
        if (event.values.length == 0) return;
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            lastHeartSensorEventElapsed = SystemClock.elapsedRealtime();
            int value = Math.round(event.values[0]);
            if (value >= MIN_HEART_RATE && value <= MAX_HEART_RATE) {
                heartRate = value;
                if (running && !paused) { heartRateTotal += value; heartRateSamples++; }
                lastHeartRateElapsed = lastHeartSensorEventElapsed;
                recordHeartSample(value);
            }
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            float value = event.values[0];
            if (value >= 0.5f && value <= 1.5f) handleStepDelta(1);
            else handleStepCounter(value);
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            handleStepCounter(event.values[0]);
        }
    }

    private void handleStepCounter(float counterValue) {
        if (Float.isNaN(lastStepCounterValue) || counterValue < lastStepCounterValue) {
            lastStepCounterValue = counterValue;
            return;
        }
        int steps = (int) Math.floor(counterValue) - (int) Math.floor(lastStepCounterValue);
        lastStepCounterValue = counterValue;
        handleStepDelta(steps);
    }

    private void handleStepDelta(int steps) {
        if (!running || paused || completed || steps <= 0 || steps > MAX_STEP_DELTA) return;
        sessionSteps += steps;
        if (currentStage().unit != Stage.Unit.DISTANCE || isSystemDistanceFresh() || hasRecentTrackableGpsFix()) return;
        double estimatedMeters = steps * DEFAULT_STEP_LENGTH_METERS;
        // Do not bridge the same GPS outage when a reliable fix returns.
        lastLocation = null;
        applyDistanceDelta(estimatedMeters);
        stageGpsReady = true;
        lastLocation = null;
        saveSession(false);
    }

    private void saveHistoryIfNeeded() {
        if (historySaved || workoutStartedAt <= 0 || activeMillis <= 0) return;
        WorkoutRecord record = new WorkoutRecord();
        record.id = workoutStartedAt + "-" + System.currentTimeMillis();
        record.startedAt = workoutStartedAt;
        record.endedAt = System.currentTimeMillis();
        record.durationMs = activeMillis;
        record.distanceMeters = totalMeters;
        record.steps = sessionSteps;
        record.averageHeartRate = heartRateSamples > 0 ? (int)Math.round((double)heartRateTotal / heartRateSamples) : heartRate;
        record.plan = PlanStore.encode(stages);
        record.planName = PlanStore.name(this); record.planGroup = PlanStore.group(this); record.planRequirement = PlanStore.requirement(this);
        for (Location point : routePoints) record.route.add(new Location(point));
        record.heartTimes.addAll(heartSampleTimes); record.heartValues.addAll(heartSampleValues);
        try { record.stageResults = new org.json.JSONArray(completedStageResults.toString()); } catch (Exception ignored) {}
        HistoryStore.append(this, record);
        historySaved = true;
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void recordHeartSample(int value) {
        if (!running || paused || value < MIN_HEART_RATE || value > MAX_HEART_RATE) return;
        long now = System.currentTimeMillis(); if (now - lastRecordedHeartAt < 1000L) return;
        lastRecordedHeartAt = now;
        if (heartSampleTimes.size() >= 7200) { heartSampleTimes.remove(0); heartSampleValues.remove(0); }
        heartSampleTimes.add(now); heartSampleValues.add(value);
    }
    @Override public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) gpsProviderEnabled = true;
    }
    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            gpsProviderEnabled = false;
            lastGpsFixElapsed = 0;
            lastTrackableGpsElapsed = 0;
            lastGpsAccuracyMeters = -1f;
            lastLocation = null;
            latestGpsLocation = null;
            latestGpsLocationIsCached = false;
            if (!isSystemDistanceFresh() && !canEstimateDistanceFromSteps()) stageGpsReady = false;
        }
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onDestroy() {
        saveSession(true);
        clockHandler.removeCallbacks(clock);
        stopSensors();
        if (systemExerciseBridge != null) {
            if (running || preparing) systemExerciseBridge.end();
            systemExerciseBridge.close();
        }
        if (systemGpsBridge != null) systemGpsBridge.close();
        super.onDestroy();
    }

    public static final class Snapshot {
        public final String stageName; public final Stage.Unit unit; public final int stageTarget; public final double stageProgressValue; public final long remaining;
        public final double progress, totalMeters; public final int stageNumber, stageCount, heartRate;
        public final long activeMillis;
        public final boolean gpsPermissionGranted, gpsProviderEnabled, gpsRequestActive, hasGpsFix, gpsFixFromCache, waitingForGps;
        public final int gpsSatelliteCount, gpsSatellitesUsed, sessionSteps;
        public final float gpsAccuracyMeters;
        public final boolean stepSensorAvailable, activityRecognitionPermissionGranted, stepSensorActive, usingStepDistance;
        public final boolean heartSensorAvailable, heartPermissionGranted, heartSensorActive, heartSensorHasEvent, heartSensorWarmingUp;
        public final boolean systemExerciseAvailable, systemExerciseConnected, usingSystemExerciseDistance;
        public final SystemExerciseBridge.State systemExerciseState;
        public final String systemExerciseStatus;
        public final boolean systemGpsAvailable, systemGpsLocated;
        public final int systemGpsSnr;
        public final String systemGpsStatus;
        public final double[] routeLatitudes, routeLongitudes;
        public final boolean preparing, paused, completed;
        Snapshot(String stageName, Stage.Unit unit, int stageTarget, double stageProgressValue, long remaining, double progress, int stageNumber, int stageCount, double totalMeters, long activeMillis, int heartRate,
                 boolean gpsPermissionGranted, boolean gpsProviderEnabled, boolean gpsRequestActive, boolean hasGpsFix, boolean gpsFixFromCache, boolean waitingForGps, int gpsSatelliteCount, int gpsSatellitesUsed, float gpsAccuracyMeters,
                 boolean stepSensorAvailable, boolean activityRecognitionPermissionGranted, boolean stepSensorActive, boolean usingStepDistance, int sessionSteps,
                 boolean heartSensorAvailable, boolean heartPermissionGranted, boolean heartSensorActive, boolean heartSensorHasEvent, boolean heartSensorWarmingUp,
                  boolean systemExerciseAvailable, boolean systemExerciseConnected, boolean usingSystemExerciseDistance, SystemExerciseBridge.State systemExerciseState, String systemExerciseStatus,
                  boolean systemGpsAvailable, boolean systemGpsLocated, int systemGpsSnr, String systemGpsStatus,
                  double[] routeLatitudes, double[] routeLongitudes,
                  boolean preparing, boolean paused, boolean completed) {
            this.stageName=stageName; this.unit=unit; this.stageTarget=stageTarget; this.stageProgressValue=stageProgressValue; this.remaining=remaining; this.progress=progress; this.stageNumber=stageNumber; this.stageCount=stageCount;
            this.totalMeters=totalMeters; this.activeMillis=activeMillis; this.heartRate=heartRate;
            this.gpsPermissionGranted=gpsPermissionGranted; this.gpsProviderEnabled=gpsProviderEnabled; this.gpsRequestActive=gpsRequestActive; this.hasGpsFix=hasGpsFix; this.gpsFixFromCache=gpsFixFromCache; this.waitingForGps=waitingForGps;
            this.gpsSatelliteCount=gpsSatelliteCount; this.gpsSatellitesUsed=gpsSatellitesUsed; this.gpsAccuracyMeters=gpsAccuracyMeters;
            this.stepSensorAvailable=stepSensorAvailable; this.activityRecognitionPermissionGranted=activityRecognitionPermissionGranted; this.stepSensorActive=stepSensorActive; this.usingStepDistance=usingStepDistance; this.sessionSteps=sessionSteps;
            this.heartSensorAvailable=heartSensorAvailable; this.heartPermissionGranted=heartPermissionGranted; this.heartSensorActive=heartSensorActive; this.heartSensorHasEvent=heartSensorHasEvent; this.heartSensorWarmingUp=heartSensorWarmingUp;
            this.systemExerciseAvailable=systemExerciseAvailable; this.systemExerciseConnected=systemExerciseConnected; this.usingSystemExerciseDistance=usingSystemExerciseDistance; this.systemExerciseState=systemExerciseState; this.systemExerciseStatus=systemExerciseStatus;
            this.systemGpsAvailable=systemGpsAvailable; this.systemGpsLocated=systemGpsLocated; this.systemGpsSnr=systemGpsSnr; this.systemGpsStatus=systemGpsStatus;
            this.routeLatitudes=routeLatitudes; this.routeLongitudes=routeLongitudes;
            this.preparing=preparing; this.paused=paused; this.completed=completed;
        }
    }
}

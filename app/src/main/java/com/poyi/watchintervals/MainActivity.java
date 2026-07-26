package com.poyi.watchintervals;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Single vertical home screen.
 *
 * <p>The previous home was a three-page horizontal pager whose second and third pages were
 * shrunken previews of {@link HistoryActivity} and {@link PlanActivity} — two navigation models
 * for the same destinations, and the previews always lagged the real screens. The restructure
 * keeps one canonical surface per concern and gives the home what it lacked: this week's volume,
 * the figure a runner actually opens the app to check. Horizontal gestures now do exactly one
 * thing each — right exits to the watch face, left opens history.
 */
public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 10;
    private static final int REQUEST_BACKGROUND_LOCATION = 11;
    private static final int REQUEST_SLEEP_PERMISSION = 12;
    private ArrayList<Stage> stages;
    private TextView ready, start, clock, weeklyFigures;
    private TextView planName, planLine, planSummary;
    private TextView latestDistance, latestMeta, sensorStatus;
    private LinearLayout latestBlock;
    private SwipeTracker swipeTracker;
    private String latestRecordId = "";
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockUpdater = new Runnable() { @Override public void run() {
        updateClock();
        long delay = 60_000L - (System.currentTimeMillis() % 60_000L);
        clockHandler.postDelayed(this, delay);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        startForegroundService(new Intent(this, WatchBridgeService.class));
        startForegroundService(new Intent(this, WatchLinkService.class));
        swipeTracker = new SwipeTracker(this, new SwipeTracker.Listener() {
            @Override public void onSwipeRight() { finish(); }
            @Override public void onSwipeLeft() { startActivity(new Intent(MainActivity.this, HistoryActivity.class)); }
        });
        buildUi();
        if (!getPreferences(MODE_PRIVATE).getBoolean("sleep_permission_prompted", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("sleep_permission_prompted", true).apply();
            if (!SystemSleepBridge.requestPermission(this, REQUEST_SLEEP_PERMISSION)) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("sleep_permission_prompted", false).apply();
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        stages = PlanStore.load(this);
        renderHome();
        updateSensorStatus();
        clockHandler.removeCallbacks(clockUpdater);
        clockHandler.post(clockUpdater);
    }

    @Override protected void onPause() {
        clockHandler.removeCallbacks(clockUpdater);
        super.onPause();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (swipeTracker != null) swipeTracker.observe(event);
        return super.dispatchTouchEvent(event);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, Ui.PAGE_MARGIN), Ui.dp(this, 8), Ui.dp(this, Ui.PAGE_MARGIN), Ui.dp(this, 10));
        root.setBackgroundColor(Ui.BLACK);

        TextView title = Ui.bold(this, "步序", 22, Ui.WHITE);
        clock = Ui.topBar(this, root, title);

        // This week's volume right under the top bar: the between-workouts reading.
        LinearLayout weekly = new LinearLayout(this);
        weekly.setGravity(Gravity.CENTER_VERTICAL);
        TextView weeklyLabel = Ui.text(this, "本周", Ui.LABEL, Ui.MUTED);
        LinearLayout.LayoutParams weeklyLabelParams = new LinearLayout.LayoutParams(-2, -1);
        weeklyLabelParams.rightMargin = Ui.dp(this, 10);
        weekly.addView(weeklyLabel, weeklyLabelParams);
        weeklyFigures = Ui.numeral(this, "", 15, Ui.WHITE);
        weekly.addView(weeklyFigures, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(weekly, new LinearLayout.LayoutParams(-1, Ui.dp(this, 24)));

        ready = Ui.text(this, "", Ui.LABEL, Ui.MUTED);
        ready.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams readyParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 20));
        readyParams.topMargin = Ui.dp(this, 2);
        root.addView(ready, readyParams);

        FrameLayout startBox = new FrameLayout(this);
        startBox.addView(Ui.glow(this, Ui.YELLOW, 78),
                new FrameLayout.LayoutParams(Ui.dp(this, 142), Ui.dp(this, 142), Gravity.CENTER));
        start = Ui.bold(this, "开始", 25, Ui.BLACK);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Ui.ovalAction(this, Ui.YELLOW));
        start.setClickable(true);
        start.setFocusable(true);
        startBox.addView(start, new FrameLayout.LayoutParams(Ui.dp(this, 104), Ui.dp(this, 104), Gravity.CENTER));
        root.addView(startBox, new LinearLayout.LayoutParams(-1, Ui.dp(this, 146)));
        start.setOnClickListener(v -> requestAndStart());

        // Current arrangement block — the whole block opens the plan selector.
        LinearLayout planBlock = new LinearLayout(this);
        planBlock.setOrientation(LinearLayout.VERTICAL);
        planBlock.setClickable(true);
        planBlock.setFocusable(true);
        LinearLayout planHeader = new LinearLayout(this);
        planHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView planLabel = Ui.text(this, "当前安排", Ui.LABEL, Ui.MUTED);
        planHeader.addView(planLabel, new LinearLayout.LayoutParams(0, -1, 1));
        TextView planChevron = Ui.text(this, "›", 16, Ui.MUTED);
        planHeader.addView(planChevron, new LinearLayout.LayoutParams(-2, -1));
        planBlock.addView(planHeader, new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));
        planName = Ui.bold(this, "", 24, Ui.WHITE);
        planBlock.addView(planName, new LinearLayout.LayoutParams(-1, Ui.dp(this, 33)));
        planLine = Ui.text(this, "", Ui.BODY, Ui.MUTED);
        planBlock.addView(planLine, new LinearLayout.LayoutParams(-1, Ui.dp(this, 21)));
        planSummary = Ui.text(this, "", Ui.CAPTION, Ui.MUTED);
        planBlock.addView(planSummary, new LinearLayout.LayoutParams(-1, Ui.dp(this, 17)));
        planBlock.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
        LinearLayout.LayoutParams planParams = new LinearLayout.LayoutParams(-1, -2);
        planParams.topMargin = Ui.dp(this, 4);
        root.addView(planBlock, planParams);

        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 1));
        dividerParams.topMargin = Ui.dp(this, 9);
        dividerParams.bottomMargin = Ui.dp(this, 7);
        View divider = new View(this);
        divider.setBackgroundColor(Ui.LINE);
        root.addView(divider, dividerParams);

        // Latest workout block — opens that record's detail directly.
        latestBlock = new LinearLayout(this);
        latestBlock.setOrientation(LinearLayout.VERTICAL);
        latestBlock.setClickable(true);
        latestBlock.setFocusable(true);
        TextView latestLabel = Ui.text(this, "最近训练", Ui.LABEL, Ui.MUTED);
        latestBlock.addView(latestLabel, new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));
        latestDistance = Ui.numeral(this, "", 22, Ui.WHITE);
        latestBlock.addView(latestDistance, new LinearLayout.LayoutParams(-1, Ui.dp(this, 28)));
        latestMeta = Ui.text(this, "", Ui.LABEL, Ui.MUTED);
        latestBlock.addView(latestMeta, new LinearLayout.LayoutParams(-1, Ui.dp(this, 19)));
        latestBlock.setOnClickListener(v -> {
            if (!latestRecordId.isEmpty()) {
                startActivity(new Intent(this, HistoryActivity.class).putExtra("record_id", latestRecordId));
            }
        });
        root.addView(latestBlock, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout allHistory = new LinearLayout(this);
        allHistory.setGravity(Gravity.CENTER_VERTICAL);
        allHistory.setClickable(true);
        allHistory.setFocusable(true);
        TextView allLabel = Ui.text(this, "全部历史", Ui.BODY, Ui.WHITE);
        allHistory.addView(allLabel, new LinearLayout.LayoutParams(0, -1, 1));
        TextView allChevron = Ui.text(this, "›", 16, Ui.MUTED);
        allHistory.addView(allChevron, new LinearLayout.LayoutParams(-2, -1));
        allHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 34));
        allParams.topMargin = Ui.dp(this, 2);
        root.addView(allHistory, allParams);

        root.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        sensorStatus = Ui.text(this, "", Ui.LABEL, Ui.MUTED);
        sensorStatus.setGravity(Gravity.CENTER);
        sensorStatus.setVisibility(View.GONE);
        root.addView(sensorStatus, new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));

        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    /** One pass over the history feeds the weekly strip and the latest-workout block. */
    private void renderHome() {
        updateClock();
        List<WorkoutRecord> records = HistoryStore.load(this);

        WeeklyStats week = WeeklyStats.of(records, System.currentTimeMillis());
        if (week.sessions == 0) {
            weeklyFigures.setText("还没有训练");
            weeklyFigures.setTextColor(Ui.MUTED);
        } else {
            weeklyFigures.setText(Format.distance(week.meters) + " · " + week.sessions + " 次 · "
                    + Format.duration(week.activeMillis));
            weeklyFigures.setTextColor(Ui.WHITE);
        }

        WorkoutRecord latest = records.isEmpty() ? null : records.get(0);
        if (latest == null) {
            latestRecordId = "";
            latestDistance.setText("还没有训练记录");
            latestDistance.setTextColor(Ui.MUTED);
            latestMeta.setText("完成第一次训练后在这里查看");
        } else {
            latestRecordId = latest.id;
            latestDistance.setText(Format.distance(latest.distanceMeters));
            latestDistance.setTextColor(Ui.WHITE);
            String when = new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(latest.startedAt));
            String pace = latest.distanceMeters > 0
                    ? SpeedFusion.formatPace(latest.durationMs / latest.distanceMeters)
                    : latest.steps + " 步";
            latestMeta.setText(when + " · " + Format.duration(latest.durationMs) + " · " + pace);
        }

        updatePlanPreview();
        updateSessionCallToAction();
    }

    private void updatePlanPreview() {
        if (planLine == null || stages == null || stages.isEmpty()) return;
        Stage first = stages.get(0);
        String line = first.name() + " " + first.targetText();
        if (stages.size() > 1) {
            Stage next = stages.get(1);
            line += "  →  " + next.name() + " " + next.targetText();
        }
        planLine.setText(line);
        long meters = 0, seconds = 0;
        for (Stage item : stages) if (item.unit == Stage.Unit.DISTANCE) meters += item.target; else seconds += item.target;
        String summary = stages.size() + " 项训练内容";
        if (meters > 0) summary += String.format(Locale.CHINA, " · %.2f km", meters / 1000d);
        if (seconds > 0) summary += String.format(Locale.CHINA, " · %d 分钟", Math.max(1, seconds / 60));
        planSummary.setText(summary);
        planName.setText(PlanStore.name(this));
    }

    private void updateClock() {
        if (clock != null) clock.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()));
    }

    private void updateSessionCallToAction() {
        boolean recoverable = WorkoutService.hasRecoverableSession(this);
        ready.setText(recoverable ? "上次训练可继续" : PlanStore.group(this) + " · 已就绪");
        start.setText(recoverable ? "继续" : "开始");
        start.setContentDescription(recoverable ? "继续上次训练" : "开始训练");
    }

    private void updateSensorStatus() {
        if (sensorStatus == null) return;
        LocationManager locations = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean locationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean gpsEnabled = locations != null && locations.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean hasHeartSensor = hasHeartSensor();
        boolean heartGranted = !hasHeartSensor || checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED;
        boolean hasStepSensor = hasStepSensor();
        boolean stepsGranted = !hasStepSensor || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        boolean gpsIssue = needsLocation() && (!locationGranted || !gpsEnabled);
        boolean heartIssue = hasHeartSensor && !heartGranted;
        boolean stepsIssue = needsLocation() && hasStepSensor && !stepsGranted;
        if (!gpsIssue && !heartIssue && !stepsIssue) {
            // The pairing code is setup material, not a permanent readout. Once a phone has paired
            // it only competes with the training controls for attention, so the row goes away.
            if (WatchPairingStore.hasPairedPhone(this)) {
                sensorStatus.setVisibility(View.GONE);
                return;
            }
            sensorStatus.setText("手机配对码  " + WatchBridgeService.pairingCode(this));
            sensorStatus.setTextColor(Ui.MUTED);
            sensorStatus.setVisibility(View.VISIBLE);
            return;
        }
        ArrayList<String> issues = new ArrayList<>();
        if (gpsIssue) issues.add(!locationGranted ? "定位未授权" : "定位未开启");
        if (heartIssue) issues.add("心率未授权");
        if (stepsIssue) issues.add("步数未授权");
        sensorStatus.setText(android.text.TextUtils.join("  ·  ", issues));
        sensorStatus.setTextColor(!locationGranted || heartIssue ? Ui.RED : Ui.AMBER);
        sensorStatus.setVisibility(View.VISIBLE);
    }

    private void requestAndStart() {
        ArrayList<String> missing = new ArrayList<>();
        if (needsLocation() && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (hasHeartSensor() && checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BODY_SENSORS);
        if (needsLocation() && hasStepSensor() && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) { requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS); return; }
        if (needsLocation() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_LOCATION);
            return;
        }
        startTraining();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_PERMISSIONS && (!needsLocation() || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) requestAndStart();
        else if (requestCode == REQUEST_BACKGROUND_LOCATION) startTraining();
        else Toast.makeText(this, "需要定位权限才能记录距离", Toast.LENGTH_LONG).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SLEEP_PERMISSION && resultCode != RESULT_OK) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("sleep_permission_prompted", false).apply();
        }
    }

    private boolean needsLocation() {
        if (stages == null) return true;
        for (Stage stage : stages) if (stage.unit == Stage.Unit.DISTANCE) return true;
        return false;
    }

    private boolean hasHeartSensor() {
        SensorManager sensors = getSystemService(SensorManager.class);
        return sensors != null && sensors.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null;
    }

    private boolean hasStepSensor() {
        SensorManager sensors = getSystemService(SensorManager.class);
        return sensors != null && (sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
                || sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null);
    }

    private void startTraining() {
        if (WorkoutService.hasRecoverableSession(this)) {
            startForegroundService(new Intent(this, WorkoutService.class).setAction(WorkoutService.ACTION_START));
            startActivity(new Intent(this, TrainingActivity.class)
                    .putExtra(TrainingActivity.EXTRA_PREPARED_SESSION, true));
            return;
        }
        Intent intent = new Intent(this, WarmupActivity.class);
        startActivity(intent.putExtra("plan", PlanStore.encode(stages)));
    }
}

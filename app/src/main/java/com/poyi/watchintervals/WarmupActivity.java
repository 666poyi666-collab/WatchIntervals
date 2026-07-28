package com.poyi.watchintervals;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;

public class WarmupActivity extends Activity {
    private String plan;
    private ArrayList<Stage> stages;
    private WorkoutService service;
    private boolean bound;
    private boolean countingDown;
    private int countdownValue;
    private TextView countdownOverlay;
    private TextView gpsStatus, sourceSummary, startButton, systemValue, gpsValue, stepsValue, heartValue, directStart, warmupClock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.text.SimpleDateFormat clockFormat =
            new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA);
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshUi();
            handler.postDelayed(this, 500L);
        }
    };
    /** Named callback so leaving the preparation screen can cancel the final GO hand-off too. */
    private final Runnable beginWorkoutAfterCountdown = this::beginWorkout;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((WorkoutService.LocalBinder) binder).service();
            bound = true;
            refreshUi();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        plan = getIntent().getStringExtra("plan");
        stages = PlanStore.decode(plan);
        if (stages.isEmpty()) stages = PlanStore.defaultPlan();
        plan = PlanStore.encode(stages);
        buildUi();
        startForegroundService(new Intent(this, WorkoutService.class).setAction(WorkoutService.ACTION_PREPARE).putExtra("plan", plan));
    }

    @Override protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this, WorkoutService.class), connection, Context.BIND_AUTO_CREATE);
        handler.post(refresh);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(refresh);
        // A countdown is only meaningful while this screen is visible. Letting delayed callbacks
        // survive Home/recents left the overlay on GO with service == null, permanently blocking
        // another start attempt when the user returned.
        cancelCountdown();
        if (bound) {
            unbindService(connection);
            bound = false;
            service = null;
        }
        super.onStop();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, Ui.PAGE_MARGIN), Ui.dp(this, 6), Ui.dp(this, Ui.PAGE_MARGIN), Ui.dp(this, 8));
        root.setBackgroundColor(Ui.BLACK);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.workoutGlyph(this, Ui.LIME),
                new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        TextView stageTitle = Ui.bold(this, PlanStore.name(this), 17, Ui.WHITE);
        identity.addView(stageTitle, new LinearLayout.LayoutParams(-1, Ui.dp(this, 23)));
        gpsStatus = Ui.bold(this, "GPS 准备中", Ui.CAPTION, Ui.AMBER);
        identity.addView(gpsStatus, new LinearLayout.LayoutParams(-1, Ui.dp(this, 15)));
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1);
        identityParams.leftMargin = Ui.dp(this, 9);
        header.addView(identity, identityParams);
        warmupClock = Ui.numeral(this, new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date()), 19, Ui.WHITE);
        warmupClock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(warmupClock, new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 40)));
        root.addView(header, new LinearLayout.LayoutParams(-1, Ui.dp(this, 42)));

        sourceSummary = Ui.text(this, "正在检测记录来源", Ui.LABEL, Ui.MUTED);
        sourceSummary.setGravity(Gravity.CENTER);
        root.addView(sourceSummary, new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));

        android.widget.FrameLayout startBox = new android.widget.FrameLayout(this);
        startBox.addView(Ui.glow(this, Ui.LIME, 72),
                new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 132), Ui.dp(this, 132), Gravity.CENTER));
        startButton = Ui.bold(this, "开始", 25, Ui.BLACK);
        startButton.setGravity(Gravity.CENTER);
        startButton.setBackground(Ui.gradientOvalAction(this, Ui.LIME, Ui.GREEN));
        Ui.pressable(startButton);
        startBox.addView(startButton,
                new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 98), Ui.dp(this, 98), Gravity.CENTER));
        root.addView(startBox, new LinearLayout.LayoutParams(-1, Ui.dp(this, 126)));

        LinearLayout readiness = new LinearLayout(this);
        readiness.setGravity(Gravity.CENTER);
        systemValue = readinessCell(readiness, "记录");
        gpsValue = readinessCell(readiness, "定位");
        stepsValue = readinessCell(readiness, "步数");
        heartValue = readinessCell(readiness, "心率");
        root.addView(readiness, new LinearLayout.LayoutParams(-1, Ui.dp(this, 54)));
        root.addView(new TextView(this), new LinearLayout.LayoutParams(-1, 0, 1));

        directStart = Ui.text(this, "定位未完成也可开始，运动后自动补充轨迹", 11, Ui.MUTED);
        directStart.setGravity(Gravity.CENTER);
        root.addView(directStart, new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));
        TextView back = Ui.action(this, "训练设置", 16, Ui.WHITE, Ui.PANEL);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 40));
        backParams.topMargin = Ui.dp(this, 4);
        root.addView(back, backParams);

        startButton.setOnClickListener(v -> { if (canStart()) beginCountdown(); });
        back.setOnClickListener(v -> cancelAndFinish());
        FrameLayout shell = new FrameLayout(this);
        shell.addView(root, new FrameLayout.LayoutParams(-1, -1));
        countdownOverlay = Ui.numeral(this, "", 112, Ui.LIME);
        countdownOverlay.setGravity(Gravity.CENTER);
        countdownOverlay.setBackgroundColor(Ui.BLACK);
        countdownOverlay.setVisibility(View.GONE);
        shell.addView(countdownOverlay, new FrameLayout.LayoutParams(-1, -1));
        setContentView(shell);
    }

    private TextView readinessCell(LinearLayout row, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(Ui.background(this, Ui.PANEL, 12));
        TextView caption = Ui.text(this, label, Ui.CAPTION, Ui.MUTED);
        caption.setGravity(Gravity.CENTER);
        TextView value = Ui.bold(this, "读取中", 11, Ui.WHITE);
        value.setGravity(Gravity.CENTER);
        cell.addView(caption, new LinearLayout.LayoutParams(-1, Ui.dp(this, 17)));
        cell.addView(value, new LinearLayout.LayoutParams(-1, Ui.dp(this, 28)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        params.leftMargin = Ui.dp(this, 2); params.rightMargin = Ui.dp(this, 2);
        row.addView(cell, params);
        return value;
    }

    private void refreshUi() {
        Ui.setTextIfChanged(warmupClock, clockFormat.format(new java.util.Date()));
        if (service == null) return;
        WorkoutService.Snapshot s = service.snapshot(false);
        // The disc is styled once at build time; recreating its ripple background on every 500 ms
        // tick restarted the press animation and wasted a layout pass. Reaching this method at
        // all means the service is bound, which is the only start precondition.
        updateSystemExercise(s);
        updateGps(s);
        updateSteps(s);
        updateHeart(s);
    }

    private void updateSystemExercise(WorkoutService.Snapshot s) {
        String value;
        String summary;
        int color;
        if (s.usingSystemExerciseDistance) {
            value = "记录中"; color = Ui.WHITE;
            summary = "原生运动正在记录距离";
        } else if (s.systemExerciseConnected) {
            value = "已连接"; color = Ui.WHITE;
            summary = "原生运动已连接 · 等待数据";
        } else if (s.systemExerciseState == SystemExerciseBridge.State.UNAVAILABLE) {
            value = "已就绪"; color = Ui.WHITE;
            summary = s.systemGpsAvailable && !s.systemGpsLocated
                    ? "系统定位搜星中 · 可开始，移动后步数估距"
                    : "轨迹定位优先 · 弱信号时步数估距";
        } else if (s.systemExerciseState == SystemExerciseBridge.State.ERROR) {
            value = "已回退"; color = Ui.AMBER;
            summary = "轨迹定位与实际步数正在记录";
        } else {
            value = "检测中"; color = Ui.AMBER;
            summary = "正在检测原生运动能力";
        }
        Ui.setTextAndColorIfChanged(systemValue, value, color);
        Ui.setTextIfChanged(sourceSummary, summary);
    }

    private void updateGps(WorkoutService.Snapshot s) {
        String status;
        String value;
        int statusColor;
        int valueColor;
        if (s.systemGpsLocated) {
            String signal = s.systemGpsSnr > 0 ? " " + Ui.systemGpsSignal(s.systemGpsSnr) : "";
            status = "● 系统定位完成" + signal; statusColor = Ui.LIME;
            value = "已定位"; valueColor = Ui.WHITE;
        } else if (s.systemGpsAvailable && s.systemGpsSnr > 0) {
            status = "● 系统定位 " + Ui.systemGpsSignal(s.systemGpsSnr); statusColor = Ui.AMBER;
            value = "搜星 " + Ui.systemGpsSignal(s.systemGpsSnr); valueColor = Ui.AMBER;
        } else if (!s.gpsPermissionGranted) {
            status = "● GPS 未授权"; statusColor = Ui.RED;
            value = "未授权"; valueColor = Ui.RED;
        } else if (!s.gpsProviderEnabled) {
            status = "● 定位已关闭"; statusColor = Ui.RED;
            value = "已关闭"; valueColor = Ui.RED;
        } else if (s.hasGpsFix && s.gpsFixFromCache) {
            status = "● GPS 缓存 ±" + Math.round(s.gpsAccuracyMeters) + "m"; statusColor = Ui.AMBER;
            value = "等待实时"; valueColor = Ui.AMBER;
        } else if (s.hasGpsFix) {
            status = "● GPS ±" + Math.round(s.gpsAccuracyMeters) + "m"; statusColor = Ui.LIME;
            value = "已定位"; valueColor = Ui.WHITE;
        } else if (s.gpsAccuracyMeters > 0) {
            status = "● GPS ±" + Math.round(s.gpsAccuracyMeters) + "m"; statusColor = Ui.AMBER;
            value = "校准中"; valueColor = Ui.AMBER;
        } else if (s.gpsSatelliteCount > 0) {
            String count = s.gpsSatellitesUsed > 0 ? s.gpsSatellitesUsed + "/" + s.gpsSatelliteCount : "搜星 " + s.gpsSatelliteCount;
            status = "● GPS " + count; statusColor = Ui.AMBER;
            value = "搜星中"; valueColor = Ui.AMBER;
        } else {
            status = s.systemGpsAvailable ? "● 系统定位搜星中" : "● 轨迹定位中";
            statusColor = Ui.AMBER; value = "可开始"; valueColor = Ui.WHITE;
        }
        Ui.setTextAndColorIfChanged(gpsStatus, status, statusColor);
        Ui.setTextAndColorIfChanged(gpsValue, value, valueColor);
    }

    private void updateHeart(WorkoutService.Snapshot s) {
        if (s.heartRate > 0) Ui.setTextAndColorIfChanged(heartValue, s.heartRate + " bpm", Ui.WHITE);
        else if (!s.heartSensorAvailable) Ui.setTextAndColorIfChanged(heartValue, "不可用", Ui.MUTED);
        else if (!s.heartPermissionGranted) Ui.setTextAndColorIfChanged(heartValue, "未授权", Ui.RED);
        else if (!s.heartSensorActive) Ui.setTextAndColorIfChanged(heartValue, "未连接", Ui.RED);
        else if (s.heartSensorWarmingUp) Ui.setTextAndColorIfChanged(heartValue, "读取中", Ui.AMBER);
        else Ui.setTextAndColorIfChanged(heartValue, "请佩戴", Ui.AMBER);
    }

    private void updateSteps(WorkoutService.Snapshot s) {
        if (!s.stepSensorAvailable) Ui.setTextAndColorIfChanged(stepsValue, "不可用", Ui.MUTED);
        else if (!s.activityRecognitionPermissionGranted) Ui.setTextAndColorIfChanged(stepsValue, "未授权", Ui.RED);
        else if (!s.stepSensorActive) Ui.setTextAndColorIfChanged(stepsValue, "未连接", Ui.RED);
        else if (s.usingStepDistance) Ui.setTextAndColorIfChanged(stepsValue, "估距中", Ui.AMBER);
        else Ui.setTextAndColorIfChanged(stepsValue, "已连接", Ui.WHITE);
    }

    private boolean canStart() {
        return service != null;
    }

    private void beginCountdown() {
        if (countingDown || service == null) return;
        countingDown = true;
        countdownValue = 3;
        // The successful button release already supplied the confirmation tick. Keep the first
        // numeral visual-only, then pulse once for each subsequent beat.
        showCountdownFrame(String.valueOf(countdownValue), false);
        handler.postDelayed(countdownTick, 850L);
    }

    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            countdownValue--;
            if (countdownValue > 0) {
                showCountdownFrame(String.valueOf(countdownValue));
                handler.postDelayed(this, 850L);
            } else {
                showCountdownFrame("GO");
                handler.postDelayed(beginWorkoutAfterCountdown, 350L);
            }
        }
    };

    private void showCountdownFrame(String value) {
        showCountdownFrame(value, true);
    }

    private void showCountdownFrame(String value, boolean haptic) {
        Ui.setTextIfChanged(countdownOverlay, value);
        Ui.popIn(countdownOverlay);
        if (haptic) countdownOverlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void beginWorkout() {
        if (service == null || !service.beginWorkout()) {
            cancelCountdown();
            return;
        }
        startActivity(new Intent(this, TrainingActivity.class)
                .putExtra("plan", plan)
                .putExtra(TrainingActivity.EXTRA_PREPARED_SESSION, true));
        finish();
    }

    private void cancelAndFinish() {
        cancelCountdown();
        cancelPreparation();
        finish();
    }

    private void cancelCountdown() {
        handler.removeCallbacks(countdownTick);
        handler.removeCallbacks(beginWorkoutAfterCountdown);
        countingDown = false;
        countdownValue = 0;
        if (countdownOverlay == null) return;
        countdownOverlay.animate().cancel();
        countdownOverlay.setAlpha(1f);
        countdownOverlay.setScaleX(1f);
        countdownOverlay.setScaleY(1f);
        countdownOverlay.setVisibility(View.GONE);
    }

    private void cancelPreparation() {
        if (service != null) service.cancelPreparation();
        else startService(new Intent(this, WorkoutService.class).setAction(WorkoutService.ACTION_CANCEL_PREPARE));
    }

    @Override public void onBackPressed() { cancelAndFinish(); }
}

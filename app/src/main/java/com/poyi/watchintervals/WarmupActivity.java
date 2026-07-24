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
    private TextView gpsStatus, sourceSummary, startButton, systemValue, gpsValue, stepsValue, heartValue, directStart;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshUi();
            handler.postDelayed(this, 500L);
        }
    };
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
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 12));
        root.setBackgroundColor(Ui.BLACK);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.bold(this, "户外跑", 22, Ui.WHITE);
        top.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1));
        gpsStatus = Ui.text(this, "● GPS 准备中", 10, Ui.AMBER);
        gpsStatus.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(gpsStatus, new LinearLayout.LayoutParams(Ui.dp(this, 118), Ui.dp(this, 34)));
        root.addView(top);

        Stage first = stages.get(0);
        TextView stageTitle = Ui.bold(this, PlanStore.name(this), 25, Ui.WHITE);
        stageTitle.setGravity(Gravity.CENTER);
        root.addView(stageTitle, new LinearLayout.LayoutParams(-1, Ui.dp(this, 32)));
        sourceSummary = Ui.text(this, "正在检测记录来源", 11, Ui.MUTED);
        sourceSummary.setGravity(Gravity.CENTER);
        root.addView(sourceSummary, new LinearLayout.LayoutParams(-1, Ui.dp(this, 18)));

        startButton = Ui.bold(this, "开始", 26, Ui.BLACK);
        startButton.setGravity(Gravity.CENTER);
        startButton.setBackground(Ui.ovalAction(this, Ui.YELLOW));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112));
        startParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(startButton, startParams);

        LinearLayout readiness = new LinearLayout(this);
        readiness.setGravity(Gravity.CENTER);
        systemValue = readinessCell(readiness, "记录");
        gpsValue = readinessCell(readiness, "定位");
        stepsValue = readinessCell(readiness, "步数");
        heartValue = readinessCell(readiness, "心率");
        root.addView(readiness, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));
        root.addView(new TextView(this), new LinearLayout.LayoutParams(-1, 0, 1));

        directStart = Ui.text(this, "定位未完成也可开始，运动后自动补充轨迹", 11, Ui.MUTED);
        directStart.setGravity(Gravity.CENTER);
        root.addView(directStart, new LinearLayout.LayoutParams(-1, Ui.dp(this, 30)));
        TextView back = Ui.action(this, "训练设置", 16, Ui.WHITE, Ui.PANEL);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 44));
        backParams.topMargin = Ui.dp(this, 6);
        root.addView(back, backParams);

        startButton.setOnClickListener(v -> { if (canStart()) beginCountdown(); });
        back.setOnClickListener(v -> cancelAndFinish());
        FrameLayout shell = new FrameLayout(this);
        shell.addView(root, new FrameLayout.LayoutParams(-1, -1));
        countdownOverlay = Ui.bold(this, "", 112, Ui.BLACK);
        countdownOverlay.setGravity(Gravity.CENTER);
        countdownOverlay.setBackgroundColor(Ui.YELLOW);
        countdownOverlay.setVisibility(View.GONE);
        shell.addView(countdownOverlay, new FrameLayout.LayoutParams(-1, -1));
        setContentView(shell);
    }

    private TextView readinessCell(LinearLayout row, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        TextView caption = Ui.text(this, label, 10, Ui.MUTED);
        caption.setGravity(Gravity.CENTER);
        TextView value = Ui.bold(this, "读取中", 14, Ui.WHITE);
        value.setGravity(Gravity.CENTER);
        cell.addView(caption, new LinearLayout.LayoutParams(-1, Ui.dp(this, 19)));
        cell.addView(value, new LinearLayout.LayoutParams(-1, Ui.dp(this, 28)));
        row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        return value;
    }

    private void refreshUi() {
        if (service == null) return;
        WorkoutService.Snapshot s = service.snapshot();
        boolean ready = service != null;
        startButton.setText("开始训练");
        startButton.setTextColor(ready ? Ui.BLACK : Ui.WHITE);
        startButton.setBackground(Ui.ovalAction(this, ready ? Ui.YELLOW : Ui.PANEL_ACTIVE));
        startButton.setClickable(ready);
        startButton.setEnabled(ready);
        startButton.setFocusable(ready);
        startButton.setContentDescription("开始训练");
        directStart.setVisibility(View.VISIBLE);
        updateSystemExercise(s);
        updateGps(s);
        updateSteps(s);
        updateHeart(s);
    }

    private void updateSystemExercise(WorkoutService.Snapshot s) {
        if (s.usingSystemExerciseDistance) {
            systemValue.setText("记录中");
            systemValue.setTextColor(Ui.LIME);
            sourceSummary.setText("原生运动正在记录距离");
        } else if (s.systemExerciseConnected) {
            systemValue.setText("已连接");
            systemValue.setTextColor(Ui.CYAN);
            sourceSummary.setText("原生运动已连接 · 等待数据");
        } else if (s.systemExerciseState == SystemExerciseBridge.State.UNAVAILABLE) {
            systemValue.setText("已就绪");
            systemValue.setTextColor(Ui.CYAN);
            sourceSummary.setText(s.systemGpsAvailable && !s.systemGpsLocated
                    ? "系统定位搜星中 · 可开始，移动后步数估距"
                    : "轨迹定位优先 · 弱信号时步数估距");
        } else if (s.systemExerciseState == SystemExerciseBridge.State.ERROR) {
            systemValue.setText("已回退");
            systemValue.setTextColor(Ui.AMBER);
            sourceSummary.setText("轨迹定位与实际步数正在记录");
        } else {
            systemValue.setText("检测中");
            systemValue.setTextColor(Ui.AMBER);
            sourceSummary.setText("正在检测原生运动能力");
        }
    }

    private void updateGps(WorkoutService.Snapshot s) {
        if (s.systemGpsLocated) {
            String signal = s.systemGpsSnr > 0 ? " " + Ui.systemGpsSignal(s.systemGpsSnr) : "";
            gpsStatus.setText("● 系统定位完成" + signal); gpsStatus.setTextColor(Ui.LIME); gpsValue.setText("系统已定位"); gpsValue.setTextColor(Ui.LIME);
        } else if (s.systemGpsAvailable && s.systemGpsSnr > 0) {
            gpsStatus.setText("● 系统定位 " + Ui.systemGpsSignal(s.systemGpsSnr)); gpsStatus.setTextColor(Ui.AMBER); gpsValue.setText("搜星 " + Ui.systemGpsSignal(s.systemGpsSnr)); gpsValue.setTextColor(Ui.AMBER);
        } else if (!s.gpsPermissionGranted) {
            gpsStatus.setText("● GPS 未授权"); gpsStatus.setTextColor(Ui.RED); gpsValue.setText("未授权"); gpsValue.setTextColor(Ui.RED);
        } else if (!s.gpsProviderEnabled) {
            gpsStatus.setText("● 定位已关闭"); gpsStatus.setTextColor(Ui.RED); gpsValue.setText("已关闭"); gpsValue.setTextColor(Ui.RED);
        } else if (s.hasGpsFix && s.gpsFixFromCache) {
            gpsStatus.setText("● GPS 缓存 ±" + Math.round(s.gpsAccuracyMeters) + "m"); gpsStatus.setTextColor(Ui.AMBER); gpsValue.setText("等待实时"); gpsValue.setTextColor(Ui.AMBER);
        } else if (s.hasGpsFix) {
            gpsStatus.setText("● GPS ±" + Math.round(s.gpsAccuracyMeters) + "m"); gpsStatus.setTextColor(Ui.LIME); gpsValue.setText("已定位"); gpsValue.setTextColor(Ui.LIME);
        } else if (s.gpsAccuracyMeters > 0) {
            gpsStatus.setText("● GPS ±" + Math.round(s.gpsAccuracyMeters) + "m"); gpsStatus.setTextColor(Ui.AMBER); gpsValue.setText("校准中"); gpsValue.setTextColor(Ui.AMBER);
        } else if (s.gpsSatelliteCount > 0) {
            String count = s.gpsSatellitesUsed > 0 ? s.gpsSatellitesUsed + "/" + s.gpsSatelliteCount : "搜星 " + s.gpsSatelliteCount;
            gpsStatus.setText("● GPS " + count); gpsStatus.setTextColor(Ui.AMBER); gpsValue.setText("搜星中"); gpsValue.setTextColor(Ui.AMBER);
        } else {
            gpsStatus.setText(s.systemGpsAvailable ? "● 系统定位搜星中" : "● 轨迹定位中"); gpsStatus.setTextColor(Ui.AMBER); gpsValue.setText("可开始"); gpsValue.setTextColor(Ui.CYAN);
        }
    }

    private void updateHeart(WorkoutService.Snapshot s) {
        if (s.heartRate > 0) { heartValue.setText(s.heartRate + " bpm"); heartValue.setTextColor(Ui.LIME); }
        else if (!s.heartSensorAvailable) { heartValue.setText("不可用"); heartValue.setTextColor(Ui.MUTED); }
        else if (!s.heartPermissionGranted) { heartValue.setText("未授权"); heartValue.setTextColor(Ui.RED); }
        else if (!s.heartSensorActive) { heartValue.setText("未连接"); heartValue.setTextColor(Ui.RED); }
        else if (s.heartSensorWarmingUp) { heartValue.setText("读取中"); heartValue.setTextColor(Ui.AMBER); }
        else { heartValue.setText("请佩戴"); heartValue.setTextColor(Ui.AMBER); }
    }

    private void updateSteps(WorkoutService.Snapshot s) {
        if (!s.stepSensorAvailable) { stepsValue.setText("不可用"); stepsValue.setTextColor(Ui.MUTED); }
        else if (!s.activityRecognitionPermissionGranted) { stepsValue.setText("未授权"); stepsValue.setTextColor(Ui.RED); }
        else if (!s.stepSensorActive) { stepsValue.setText("未连接"); stepsValue.setTextColor(Ui.RED); }
        else if (s.usingStepDistance) { stepsValue.setText("估距中"); stepsValue.setTextColor(Ui.AMBER); }
        else { stepsValue.setText("已连接"); stepsValue.setTextColor(Ui.CYAN); }
    }

    private boolean canStart() {
        return service != null;
    }

    private void beginCountdown() {
        if (countingDown || service == null) return;
        countingDown = true;
        countdownValue = 3;
        countdownOverlay.setText(String.valueOf(countdownValue));
        countdownOverlay.setVisibility(View.VISIBLE);
        handler.postDelayed(countdownTick, 850L);
    }

    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            countdownValue--;
            if (countdownValue > 0) {
                countdownOverlay.setText(String.valueOf(countdownValue));
                handler.postDelayed(this, 850L);
            } else {
                countdownOverlay.setText("GO");
                handler.postDelayed(() -> beginWorkout(), 350L);
            }
        }
    };

    private void beginWorkout() {
        if (service == null || !service.beginWorkout()) return;
        startActivity(new Intent(this, TrainingActivity.class)
                .putExtra("plan", plan)
                .putExtra(TrainingActivity.EXTRA_PREPARED_SESSION, true));
        finish();
    }

    private void cancelAndFinish() {
        handler.removeCallbacks(countdownTick);
        cancelPreparation();
        finish();
    }

    private void cancelPreparation() {
        if (service != null) service.cancelPreparation();
        else startService(new Intent(this, WorkoutService.class).setAction(WorkoutService.ACTION_CANCEL_PREPARE));
    }

    @Override public void onBackPressed() { cancelAndFinish(); }
}

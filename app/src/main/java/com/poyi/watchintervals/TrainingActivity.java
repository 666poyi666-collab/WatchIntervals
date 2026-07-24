package com.poyi.watchintervals;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.Locale;
import java.util.ArrayList;

public class TrainingActivity extends Activity {
    public static final String EXTRA_PREPARED_SESSION = "com.poyi.watchintervals.PREPARED_SESSION";
    private WorkoutService service;
    private boolean bound, workoutCompleted;
    private boolean allowTaskLeave;
    private int displayedStage = -1;
    private String lastStageSummary = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView stageName, remaining, remainingLabel, stageProgress, stageCounter, gps, distance, pace, heart, steps, duration, pause, stop;
    private LinearLayout controls, stopConfirmation, transitionNotice, routePanel;
    private View stopScrim;
    private TextView transitionTitle, transitionDetail, routeSummary;
    private WorkoutRouteView routeView;
    private ProgressBar progress;
    private WatchPagerLayout workoutPager;
    private final Runnable update = new Runnable() { @Override public void run() { refresh(); handler.postDelayed(this, 500); } };
    private final Runnable hideTransition = new Runnable() { @Override public void run() {
        if (transitionNotice != null) transitionNotice.setVisibility(View.GONE);
    }};
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) { service = ((WorkoutService.LocalBinder)binder).service(); bound = true; refresh(); }
        @Override public void onServiceDisconnected(ComponentName name) { service = null; bound = false; }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        if (!getIntent().getBooleanExtra(EXTRA_PREPARED_SESSION, false)) {
            Intent serviceIntent = new Intent(this, WorkoutService.class).setAction(WorkoutService.ACTION_START).putExtra("plan", getIntent().getStringExtra("plan"));
            if (getIntent().hasExtra(WorkoutService.EXTRA_INITIAL_LOCATION)) {
                android.location.Location initialLocation = getIntent().getParcelableExtra(WorkoutService.EXTRA_INITIAL_LOCATION);
                if (initialLocation != null) serviceIntent.putExtra(WorkoutService.EXTRA_INITIAL_LOCATION, initialLocation);
            }
            if (getIntent().hasExtra(WorkoutService.EXTRA_INITIAL_HEART_RATE)) {
                serviceIntent.putExtra(WorkoutService.EXTRA_INITIAL_HEART_RATE, getIntent().getIntExtra(WorkoutService.EXTRA_INITIAL_HEART_RATE, 0));
            }
            startForegroundService(serviceIntent);
        }
    }

    private void buildUi() {
        FrameLayout shell = new FrameLayout(this);
        LinearLayout controlPage = buildControlPage();
        LinearLayout dataPage = buildDataPage();
        routePanel = buildRoutePanel();
        workoutPager = new WatchPagerLayout(this);
        // Physical order follows the demonstrated system app. The workout opens
        // in the centre; dragging left reveals the page on the physical right.
        workoutPager.addView(controlPage);
        workoutPager.addView(dataPage);
        workoutPager.addView(routePanel);
        workoutPager.setCurrentItem(1, false);
        shell.addView(workoutPager, new FrameLayout.LayoutParams(-1, -1));

        transitionNotice = buildTransitionNotice();
        FrameLayout.LayoutParams transitionParams = new FrameLayout.LayoutParams(-1, Ui.dp(this, 124), Gravity.CENTER);
        transitionParams.leftMargin = Ui.dp(this, 16); transitionParams.rightMargin = Ui.dp(this, 16);
        shell.addView(transitionNotice, transitionParams);
        stopScrim = new View(this);
        stopScrim.setBackgroundColor(Color.argb(190, 0, 0, 0));
        stopScrim.setClickable(true);
        stopScrim.setVisibility(View.GONE);
        stopScrim.setOnClickListener(v -> hideStopConfirmation());
        shell.addView(stopScrim, new FrameLayout.LayoutParams(-1, -1));
        stopConfirmation = buildStopConfirmation();
        FrameLayout.LayoutParams confirmParams = new FrameLayout.LayoutParams(-1, Ui.dp(this, 164), Gravity.BOTTOM);
        confirmParams.leftMargin = Ui.dp(this, 12); confirmParams.rightMargin = Ui.dp(this, 12); confirmParams.bottomMargin = Ui.dp(this, 10);
        shell.addView(stopConfirmation, confirmParams);
        setContentView(shell);
    }

    private LinearLayout buildDataPage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        root.setBackgroundColor(Ui.BLACK);

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        stageCounter = Ui.bold(this, "阶段 1 / 2", 12, Ui.MUTED);
        top.addView(stageCounter, new LinearLayout.LayoutParams(0, Ui.dp(this, 30), 1));
        gps = Ui.text(this, "● 等待 GPS", 10, Ui.MUTED); gps.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(gps, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 30))); root.addView(top);

        stageName = Ui.bold(this, "准备", 24, Ui.LIME); stageName.setGravity(Gravity.CENTER);
        root.addView(stageName, new LinearLayout.LayoutParams(-1, Ui.dp(this, 32)));
        remainingLabel = Ui.text(this, "剩余距离", 11, Ui.MUTED); remainingLabel.setGravity(Gravity.CENTER);
        root.addView(remainingLabel, new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        remaining = Ui.bold(this, "--", 55, Ui.WHITE); remaining.setGravity(Gravity.CENTER);
        root.addView(remaining, new LinearLayout.LayoutParams(-1, Ui.dp(this, 60)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.LIME));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        root.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 6)));
        stageProgress = Ui.text(this, "本阶段 --", 11, Ui.MUTED); stageProgress.setGravity(Gravity.CENTER);
        root.addView(stageProgress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 18)));

        LinearLayout metrics = Ui.card(this); metrics.setPadding(Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5));
        LinearLayout first = new LinearLayout(this); distance = metric(first, "总距离", "0 m"); pace = metric(first, "平均配速", "-- /km");
        metrics.addView(first, new LinearLayout.LayoutParams(-1, Ui.dp(this, 50)));
        metrics.addView(Ui.divider(this));
        LinearLayout second = new LinearLayout(this); heart = metric(second, "心率", "-- bpm"); steps = metric(second, "实际步数", "0 步"); duration = metric(second, "用时", "00:00");
        metrics.addView(second, new LinearLayout.LayoutParams(-1, Ui.dp(this, 50)));
        LinearLayout.LayoutParams metricsParams = new LinearLayout.LayoutParams(-1, -2);
        metricsParams.topMargin = Ui.dp(this, 8); root.addView(metrics, metricsParams);
        root.addView(new TextView(this), new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(Ui.pagerDots(this, 1, 3), new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));
        TextView hint = Ui.text(this, "右侧轨迹", 10, Ui.MUTED); hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        return root;
    }

    private LinearLayout buildControlPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 10));
        page.setBackgroundColor(Ui.BLACK);
        TextView eyebrow = Ui.text(this, "训练控制", 12, Ui.MUTED); eyebrow.setGravity(Gravity.CENTER);
        page.addView(eyebrow, new LinearLayout.LayoutParams(-1, Ui.dp(this, 30)));
        TextView title = Ui.bold(this, "保持节奏", 26, Ui.WHITE); title.setGravity(Gravity.CENTER);
        page.addView(title, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));
        page.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        pause = roundControl("Ⅱ", "暂停", Ui.GREEN);
        stop = roundControl("■", "长按结束", Ui.RED);
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(Ui.dp(this, 104), Ui.dp(this, 104));
        action.rightMargin = Ui.dp(this, 18); controls.addView(pause, action);
        controls.addView(stop, new LinearLayout.LayoutParams(Ui.dp(this, 104), Ui.dp(this, 104)));
        page.addView(controls, new LinearLayout.LayoutParams(-1, Ui.dp(this, 122)));
        TextView instruction = Ui.text(this, "轻触暂停  ·  长按结束", 12, Ui.MUTED); instruction.setGravity(Gravity.CENTER);
        page.addView(instruction, new LinearLayout.LayoutParams(-1, Ui.dp(this, 34)));
        page.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        page.addView(Ui.pagerDots(this, 0, 3), new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));
        TextView hint = Ui.text(this, "向左滑返回数据", 10, Ui.MUTED); hint.setGravity(Gravity.CENTER);
        page.addView(hint, new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        pause.setOnClickListener(v -> { if (workoutCompleted) stopAndFinish(); else if (service != null) service.togglePause(); });
        stop.setOnLongClickListener(v -> { confirmStop(); return true; });
        stop.setOnClickListener(v -> android.widget.Toast.makeText(this, "长按结束训练", android.widget.Toast.LENGTH_SHORT).show());
        return page;
    }

    private TextView roundControl(String symbol, String label, int color) {
        TextView button = Ui.bold(this, symbol + "\n" + label, 17, Ui.BLACK);
        button.setSingleLine(false); button.setGravity(Gravity.CENTER); button.setLineSpacing(0, .86f);
        button.setBackground(Ui.ovalAction(this, color)); button.setClickable(true); button.setFocusable(true);
        return button;
    }

    private void setControlsForCompletion(boolean completed) {
        if (pause == null || stop == null) return;
        pause.setText(completed ? "✓\n完成" : service != null && service.snapshot().paused ? "▶\n继续" : "Ⅱ\n暂停");
        stop.setVisibility(completed ? View.GONE : View.VISIBLE);
    }

    private TextView metric(LinearLayout row, String label, String initial) {
        LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
        TextView caption = Ui.text(this, label, 10, Ui.MUTED); caption.setGravity(Gravity.CENTER);
        TextView value = Ui.bold(this, initial, 19, Ui.WHITE); value.setGravity(Gravity.CENTER);
        cell.addView(caption, new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));
        cell.addView(value, new LinearLayout.LayoutParams(-1, Ui.dp(this, 34)));
        row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        return value;
    }

    private LinearLayout buildTransitionNotice() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        panel.setBackground(Ui.background(this, Ui.PANEL_ACTIVE, 8));
        panel.setFocusable(true);
        panel.setFocusableInTouchMode(true);
        // The notice is an overlay, so it must start hidden.  Starting it visible
        // consumed every horizontal gesture before the first real stage change.
        panel.setVisibility(View.GONE);
        transitionTitle = Ui.bold(this, "阶段完成", 21, Ui.WHITE); transitionTitle.setGravity(Gravity.CENTER);
        transitionDetail = Ui.text(this, "下一项", 14, Ui.LIME); transitionDetail.setGravity(Gravity.CENTER);
        panel.addView(transitionTitle, new LinearLayout.LayoutParams(-1, Ui.dp(this, 40)));
        panel.addView(transitionDetail, new LinearLayout.LayoutParams(-1, Ui.dp(this, 28)));
        return panel;
    }

    private LinearLayout buildRoutePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        panel.setBackgroundColor(Ui.BLACK);
        panel.setClickable(true);
        panel.setFocusable(true);
        // This is the second child of WatchPagerLayout.  Keep it laid out and let
        // the pager move it off-screen; GONE would make the route page blank.
        panel.setVisibility(View.VISIBLE);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = Ui.action(this, "‹", 30, Ui.WHITE, Ui.PANEL);
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        TextView title = Ui.bold(this, "运动轨迹", 21, Ui.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        TextView live = Ui.text(this, "实时", 11, Ui.LIME);
        live.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(live, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 42)));
        panel.addView(header);

        routeView = new WorkoutRouteView(this);
        routeView.setBackground(Ui.background(this, Color.rgb(15, 22, 23), 18));
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, 0, 1);
        mapParams.topMargin = Ui.dp(this, 4); panel.addView(routeView, mapParams);
        routeSummary = Ui.text(this, "等待有效定位轨迹", 12, Ui.MUTED);
        routeSummary.setGravity(Gravity.CENTER);
        panel.addView(routeSummary, new LinearLayout.LayoutParams(-1, Ui.dp(this, 38)));
        TextView hint = Ui.text(this, "红色为起点 · 白色为当前位置", 11, Ui.MUTED);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));
        panel.addView(Ui.pagerDots(this, 2, 3), new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));
        close.setOnClickListener(v -> hideRoute());
        return panel;
    }

    private LinearLayout buildStopConfirmation() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        panel.setBackground(Ui.background(this, Ui.PANEL_ACTIVE, 18));
        panel.setVisibility(View.GONE);
        TextView title = Ui.bold(this, "结束本次训练？", 19, Ui.WHITE);
        panel.addView(title, new LinearLayout.LayoutParams(-1, Ui.dp(this, 30)));
        TextView hint = Ui.text(this, "记录会立即停止", 12, Ui.MUTED);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, Ui.dp(this, 24)));
        LinearLayout choices = new LinearLayout(this);
        TextView cancel = Ui.action(this, "继续训练", 15, Ui.WHITE, Ui.PANEL);
        TextView confirm = Ui.action(this, "结束", 15, Ui.WHITE, Ui.RED);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1);
        cancelParams.rightMargin = Ui.dp(this, 7);
        choices.addView(cancel, cancelParams);
        choices.addView(confirm, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
        panel.addView(choices);
        cancel.setOnClickListener(v -> hideStopConfirmation());
        confirm.setOnClickListener(v -> stopAndFinish());
        return panel;
    }

    @Override public void onStart() { super.onStart(); bound = bindService(new Intent(this, WorkoutService.class), connection, Context.BIND_AUTO_CREATE); handler.post(update); }
    @Override public void onStop() {
        handler.removeCallbacks(update);
        handler.removeCallbacks(hideTransition);
        if (bound) { unbindService(connection); bound = false; service = null; }
        super.onStop();
        // During an active workout this task owns the foreground just like the
        // system sports activity. Full-screen overlays and launcher transitions
        // are moved behind it until the user explicitly ends the workout.
        if (!allowTaskLeave && !workoutCompleted && !isFinishing()) {
            final int taskId = getTaskId();
            handler.postDelayed(() -> {
                if (allowTaskLeave || isFinishing()) return;
                ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
                if (manager != null) manager.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME);
                try {
                    startActivity(new Intent(this, TrainingActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra(EXTRA_PREPARED_SESSION, true));
                } catch (RuntimeException ignored) { /* Foreground service repeats the restore if needed. */ }
            }, 250L);
        }
    }

    private void refresh() {
        if (service == null) return;
        WorkoutService.Snapshot s = service.snapshot();
        String stageSummary = s.stageName + " " + stageTargetText(s);
        if (!s.completed && displayedStage > 0 && s.stageNumber > displayedStage) showTransition(lastStageSummary, stageSummary);
        displayedStage = s.stageNumber;
        if (!s.completed) lastStageSummary = stageSummary;
        workoutCompleted = s.completed;

        int accent = s.completed ? Ui.LIME : s.paused ? Ui.MUTED : s.waitingForGps ? Ui.AMBER : s.stageName.equals("快走") ? Ui.CYAN : s.stageName.equals("休息") ? Ui.AMBER : Ui.LIME;
        stageName.setText(s.completed ? "训练完成" : s.paused ? s.stageName + " · 已暂停" : s.waitingForGps ? s.stageName + " · 等待信号" : s.stageName);
        stageName.setTextColor(accent);
        stageCounter.setText(String.format(Locale.CHINA, "阶段 %d / %d", s.stageNumber, s.stageCount));
        if (s.completed) {
            remainingLabel.setText("本次计划");
            remaining.setText("完成");
            stageProgress.setText("训练记录已保存");
            hideStopConfirmation();
        } else if (s.waitingForGps && !s.paused) {
            remainingLabel.setText("移动后自动开始记录");
            remaining.setText("等待");
            stageProgress.setText(gpsAcquisitionDetail(s) + " · 目标 " + stageTargetText(s));
        } else {
            remainingLabel.setText(s.unit == Stage.Unit.DISTANCE ? "剩余距离" : "剩余时间");
            if (s.unit == Stage.Unit.DISTANCE) remaining.setText(String.format(Locale.CHINA, "%d m", s.remaining));
            else remaining.setText(String.format(Locale.CHINA, "%d:%02d", s.remaining / 60, s.remaining % 60));
            stageProgress.setText(s.paused ? "训练已暂停" : stageProgressText(s));
        }
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        progress.setProgress((int)(s.progress * 1000));
        distance.setText(formatDistance(s.totalMeters));
        pace.setText(formatPace(s));
        heart.setText(heartStatus(s));
        steps.setText(s.sessionSteps + " 步");
        duration.setText(formatDuration(s.activeMillis));
        if (routeView != null) routeView.setRoute(s.routeLatitudes, s.routeLongitudes);
        if (routeSummary != null) {
            int points = Math.min(s.routeLatitudes.length, s.routeLongitudes.length);
            routeSummary.setText(points > 0
                    ? formatDistance(s.totalMeters) + " · " + points + " 个轨迹点 · " + formatDuration(s.activeMillis)
                    : "等待有效定位轨迹 · 步数仍会准确记录");
        }
        updateGpsStatus(s);
        setControlsForCompletion(s.completed);
    }

    private void updateGpsStatus(WorkoutService.Snapshot s) {
        if (s.completed) {
            gps.setText("● 已保存"); gps.setTextColor(Ui.LIME);
        } else if (s.usingSystemExerciseDistance) {
            gps.setText("● 系统运动"); gps.setTextColor(Ui.LIME);
        } else if (s.systemGpsLocated) {
            String signal = s.systemGpsSnr > 0 ? " " + Ui.systemGpsSignal(s.systemGpsSnr) : "";
            gps.setText("● 系统定位" + signal); gps.setTextColor(Ui.LIME);
        } else if (s.systemExerciseConnected) {
            gps.setText("● 系统预热"); gps.setTextColor(Ui.CYAN);
        } else if (s.usingStepDistance) {
            gps.setText("● 实际步数估距"); gps.setTextColor(Ui.AMBER);
        } else if (!s.gpsPermissionGranted) {
            gps.setText("● GPS 未授权"); gps.setTextColor(Ui.RED);
        } else if (!s.gpsProviderEnabled) {
            gps.setText("● 定位已关闭"); gps.setTextColor(Ui.RED);
        } else if (!s.gpsRequestActive) {
            gps.setText("● GPS 未就绪"); gps.setTextColor(Ui.AMBER);
        } else if (s.hasGpsFix && s.gpsFixFromCache) {
            String accuracy = s.gpsAccuracyMeters > 0 ? " ±" + Math.round(s.gpsAccuracyMeters) + "m" : "";
            gps.setText("● GPS 缓存" + accuracy); gps.setTextColor(Ui.AMBER);
        } else if (s.hasGpsFix) {
            String accuracy = s.gpsAccuracyMeters > 0 ? " ±" + Math.round(s.gpsAccuracyMeters) + "m" : "";
            gps.setText("● GPS 轨迹" + accuracy); gps.setTextColor(Ui.LIME);
        } else if (s.gpsAccuracyMeters > 0) {
            gps.setText("● GPS ±" + Math.round(s.gpsAccuracyMeters) + "m"); gps.setTextColor(Ui.AMBER);
        } else if (s.gpsSatelliteCount > 0) {
            String visible = s.gpsSatellitesUsed > 0 ? s.gpsSatellitesUsed + "/" + s.gpsSatelliteCount : "搜星 " + s.gpsSatelliteCount;
            gps.setText("● GPS " + visible); gps.setTextColor(Ui.AMBER);
        } else {
            gps.setText(s.systemGpsAvailable ? "● 系统定位搜星" : "● 轨迹定位中"); gps.setTextColor(Ui.AMBER);
        }
    }

    private String gpsAcquisitionDetail(WorkoutService.Snapshot s) {
        if (s.usingSystemExerciseDistance) return "系统运动正在记录距离";
        if (s.systemGpsLocated) return "系统定位完成，正在记录轨迹";
        if (s.systemGpsAvailable && s.systemGpsSnr > 0) return "系统定位搜星中 · " + Ui.systemGpsSignal(s.systemGpsSnr);
        if (s.systemGpsAvailable) return "系统定位搜星中，步数同步估距";
        if (s.systemExerciseConnected) return "系统运动已连接，等待距离数据";
        if (s.usingStepDistance) return "步数估距中，GPS 恢复后自动切换";
        if (s.systemExerciseState == SystemExerciseBridge.State.UNAVAILABLE && s.gpsRequestActive) return "轨迹定位已启动，实际步数同步记录";
        if (s.stepSensorActive && s.activityRecognitionPermissionGranted) return "等待 GPS，移动可按步数估距";
        if (!s.gpsPermissionGranted) return "需要定位权限";
        if (!s.gpsProviderEnabled) return "请开启系统定位";
        if (!s.gpsRequestActive) return "正在准备 GPS";
        if (s.gpsFixFromCache) return "使用缓存，等待实时定位";
        if (s.gpsAccuracyMeters > 0) return "定位精度 ±" + Math.round(s.gpsAccuracyMeters) + "m，继续校准";
        if (s.gpsSatelliteCount > 0) return "正在搜星 " + s.gpsSatelliteCount + " 颗";
        return "请到开阔户外";
    }

    private String formatDistance(double meters) {
        if (meters < 1000) return Math.round(meters) + " m";
        return String.format(Locale.CHINA, "%.2f km", meters / 1000d);
    }

    private String formatPace(WorkoutService.Snapshot s) {
        if (s.totalMeters < 10 || s.activeMillis < 1000) return s.totalMeters > 0 ? "采集中" : "-- /km";
        long paceMillis = Math.round(s.activeMillis * 1000d / s.totalMeters);
        return (s.usingStepDistance ? "约 " : "") + formatDuration(paceMillis) + " /km";
    }

    private String heartStatus(WorkoutService.Snapshot s) {
        if (s.heartRate > 0) return s.heartRate + " bpm";
        if (!s.heartSensorAvailable) return "不可用";
        if (!s.heartPermissionGranted) return "未授权";
        if (!s.heartSensorActive && !s.completed) return "未连接";
        if (s.heartSensorWarmingUp) return "读取中";
        return s.completed ? "-- bpm" : "请佩戴";
    }

    private void showTransition(String previous, String next) {
        if (transitionNotice == null || previous.isEmpty()) return;
        transitionTitle.setText(previous + " 已完成");
        transitionDetail.setText("下一项：" + next);
        transitionNotice.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideTransition);
        handler.postDelayed(hideTransition, 2600L);
    }

    private String stageProgressText(WorkoutService.Snapshot s) {
        if (s.unit == Stage.Unit.DISTANCE) {
            return String.format(Locale.CHINA, "本阶段 %d / %s%s", Math.round(s.stageProgressValue), stageTargetText(s), s.usingStepDistance ? " · 步数估距" : "");
        }
        return "本阶段 " + formatDuration((long)s.stageProgressValue) + " / " + stageTargetText(s);
    }

    private String stageTargetText(WorkoutService.Snapshot s) {
        if (s.unit == Stage.Unit.DISTANCE) return s.stageTarget >= 1000 && s.stageTarget % 1000 == 0 ? (s.stageTarget / 1000) + " km" : s.stageTarget + " m";
        return formatDuration(s.stageTarget * 1000L);
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000; return String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void confirmStop() {
        if (workoutCompleted || stopConfirmation == null) return;
        if (stopScrim != null) stopScrim.setVisibility(View.VISIBLE);
        stopConfirmation.setVisibility(View.VISIBLE);
        stopConfirmation.requestFocus();
    }

    private void hideStopConfirmation() {
        if (stopConfirmation != null) stopConfirmation.setVisibility(View.GONE);
        if (stopScrim != null) stopScrim.setVisibility(View.GONE);
    }

    private void showRoute() {
        if (workoutPager != null) workoutPager.setCurrentItem(2,true);
    }

    private void hideRoute() {
        if (workoutPager != null) workoutPager.setCurrentItem(1,true);
    }

    private void stopAndFinish() { allowTaskLeave = true; if (service != null) service.finishAndStop(); finish(); }
    @Override public void onBackPressed() {
        if (workoutPager != null && workoutPager.getCurrentItem() != 1) { workoutPager.setCurrentItem(1, true); return; }
        if (workoutCompleted) { allowTaskLeave = true; finish(); return; }
        if (stopConfirmation != null && stopConfirmation.getVisibility() == View.VISIBLE) hideStopConfirmation();
        else confirmStop();
    }
}

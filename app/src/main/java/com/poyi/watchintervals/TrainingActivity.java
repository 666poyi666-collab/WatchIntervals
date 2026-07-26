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
import android.widget.TextView;
import java.util.Locale;
import java.util.ArrayList;

public class TrainingActivity extends Activity {
    public static final String EXTRA_PREPARED_SESSION = "com.poyi.watchintervals.PREPARED_SESSION";
    /** Index of the live route panel inside {@link #workoutPager}. */
    private static final int ROUTE_PAGE = 3;
    private WorkoutService service;
    private boolean bound, workoutCompleted;
    private boolean allowTaskLeave;
    private int displayedStage = -1;
    private String lastStageSummary = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView stageName, remaining, remainingLabel, stageProgress, stageCounter, gps, distance, pace, heart, steps, duration, pause, stop;
    private TextView coreHeader, speed, controlState, controlDuration, controlSummary;
    private LinearLayout controls, stopConfirmation, transitionNotice, routePanel;
    private View stopScrim;
    private TextView transitionTitle, transitionDetail, routeSummary;
    private WorkoutRouteView routeView;
    private Ui.Ring ring;
    private WatchPagerLayout workoutPager;
    // Every value on screen changes at most once a second, so a 2 Hz tick just doubled the CPU
    // wake-ups and layout passes for an identical picture.
    private static final long REFRESH_INTERVAL_MILLIS = 1_000L;
    private final Runnable update = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, REFRESH_INTERVAL_MILLIS); }
    };
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
        LinearLayout corePage = buildCorePage();
        LinearLayout dataPage = buildDataPage();
        routePanel = buildRoutePanel();
        workoutPager = new WatchPagerLayout(this);
        // Physical order follows the demonstrated system app. The workout opens
        // in the centre; dragging left reveals the page on the physical right.
        workoutPager.addView(controlPage);
        workoutPager.addView(corePage);
        workoutPager.addView(dataPage);
        workoutPager.addView(routePanel);   // index == ROUTE_PAGE
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

    /** Stage page: circular progress ring with the remaining value inside it. */
    private LinearLayout buildDataPage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 4));
        root.setBackgroundColor(Ui.BLACK);

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        stageCounter = Ui.bold(this, "第 1/2 项", Ui.LABEL, Ui.MUTED);
        top.addView(stageCounter, new LinearLayout.LayoutParams(0, Ui.dp(this, 22), 1));
        gps = Ui.text(this, "● 等待 GPS", Ui.CAPTION, Ui.MUTED); gps.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(gps, new LinearLayout.LayoutParams(Ui.dp(this, 118), Ui.dp(this, 22)));
        root.addView(top);

        stageName = Ui.bold(this, "准备", Ui.TITLE, Ui.LIME); stageName.setGravity(Gravity.CENTER);
        root.addView(stageName, new LinearLayout.LayoutParams(-1, Ui.dp(this, 34)));

        FrameLayout ringBox = new FrameLayout(this);
        ring = new Ui.Ring(this);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(Ui.dp(this, 198), Ui.dp(this, 198), Gravity.CENTER);
        ringBox.addView(ring, ringParams);
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        remainingLabel = Ui.text(this, "剩余距离", Ui.LABEL, Ui.MUTED); remainingLabel.setGravity(Gravity.CENTER);
        center.addView(remainingLabel, new LinearLayout.LayoutParams(-2, -2));
        remaining = Ui.numeral(this, "--", 44, Ui.WHITE); remaining.setGravity(Gravity.CENTER);
        center.addView(remaining, new LinearLayout.LayoutParams(-2, -2));
        ringBox.addView(center, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 210));
        boxParams.topMargin = Ui.dp(this, 4);
        root.addView(ringBox, boxParams);

        stageProgress = Ui.text(this, "本阶段 --", Ui.LABEL, Ui.MUTED); stageProgress.setGravity(Gravity.CENTER);
        root.addView(stageProgress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));

        root.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(Ui.pagerDots(this, 2, 4), new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        return root;
    }

    /**
     * Primary in-workout page, laid out like a sports instrument rather than a settings screen:
     * everything left-aligned on pure black, pace as the hero figure, and one colour per metric
     * (pace=stage accent, time=yellow, distance=white, heart=red, steps=cyan) so each row is
     * recognisable at a glance mid-run without reading its label.
     */
    private LinearLayout buildCorePage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 4));
        root.setBackgroundColor(Ui.BLACK);

        coreHeader = Ui.bold(this, "训练中", Ui.LABEL, Ui.MUTED);
        root.addView(coreHeader, new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));

        // Hero row: big pace figure with the unit hanging off its baseline.
        LinearLayout hero = new LinearLayout(this);
        pace = Ui.numeral(this, "--", 56, Ui.MUTED);
        hero.addView(pace, new LinearLayout.LayoutParams(-2, -2));
        TextView unit = Ui.text(this, "/公里", Ui.BODY, Ui.MUTED);
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(-2, -2);
        unitParams.leftMargin = Ui.dp(this, 6);
        hero.addView(unit, unitParams);
        root.addView(hero, new LinearLayout.LayoutParams(-1, Ui.dp(this, 62)));

        speed = Ui.text(this, "等待速度数据", Ui.LABEL, Ui.MUTED);
        root.addView(speed, new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));

        View gap = new View(this);
        root.addView(gap, new LinearLayout.LayoutParams(-1, Ui.dp(this, 8)));

        duration = metricRow(root, "时间", "00:00", Ui.YELLOW);
        distance = metricRow(root, "距离", "0 m", Ui.WHITE);
        heart = metricRow(root, "心率", "--", Ui.MUTED);
        steps = metricRow(root, "步数", "0", Ui.CYAN);

        root.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(Ui.pagerDots(this, 1, 4), new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        return root;
    }

    /** One instrument row: big colour-coded figure left, quiet label pinned to the right edge. */
    private TextView metricRow(LinearLayout parent, String label, String initial, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView value = Ui.numeral(this, initial, 29, color);
        row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
        TextView caption = Ui.text(this, label, Ui.LABEL, Ui.MUTED);
        row.addView(caption, new LinearLayout.LayoutParams(-2, -2));
        parent.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(this, 41)));
        return value;
    }

    /**
     * Control page: live session summary on top (state, elapsed time, distance/heart) so pausing
     * never means losing sight of the numbers, actions below. The old filler headline ("保持节奏")
     * carried no information and made the page read like a mock-up.
     */
    private LinearLayout buildControlPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 4));
        page.setBackgroundColor(Ui.BLACK);

        controlState = Ui.bold(this, "训练中", Ui.LABEL, Ui.MUTED); controlState.setGravity(Gravity.CENTER);
        page.addView(controlState, new LinearLayout.LayoutParams(-1, Ui.dp(this, 24)));
        controlDuration = Ui.numeral(this, "00:00", 46, Ui.YELLOW); controlDuration.setGravity(Gravity.CENTER);
        page.addView(controlDuration, new LinearLayout.LayoutParams(-1, Ui.dp(this, 54)));
        controlSummary = Ui.text(this, "", Ui.LABEL, Ui.MUTED); controlSummary.setGravity(Gravity.CENTER);
        page.addView(controlSummary, new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));

        page.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        pause = roundControl("Ⅱ", "暂停", Ui.GREEN, true);
        stop = roundControl("■", "结束", Ui.RED, false);
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(Ui.dp(this, 106), Ui.dp(this, 106));
        action.rightMargin = Ui.dp(this, 20); controls.addView(pause, action);
        controls.addView(stop, new LinearLayout.LayoutParams(Ui.dp(this, 106), Ui.dp(this, 106)));
        page.addView(controls, new LinearLayout.LayoutParams(-1, Ui.dp(this, 118)));
        TextView instruction = Ui.text(this, "轻触暂停 · 长按结束", Ui.CAPTION, Ui.MUTED); instruction.setGravity(Gravity.CENTER);
        page.addView(instruction, new LinearLayout.LayoutParams(-1, Ui.dp(this, 26)));
        page.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        page.addView(Ui.pagerDots(this, 0, 4), new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        pause.setOnClickListener(v -> { if (service != null) service.togglePause(); });
        stop.setOnLongClickListener(v -> { confirmStop(); return true; });
        stop.setOnClickListener(v -> android.widget.Toast.makeText(this, "长按结束训练", android.widget.Toast.LENGTH_SHORT).show());
        return page;
    }

    /**
     * @param primary the routine action gets the solid fill; the destructive one stays tonal so
     *                the two circles no longer compete for the same visual weight.
     */
    private TextView roundControl(String symbol, String label, int color, boolean primary) {
        TextView button = Ui.bold(this, symbol + "\n" + label, Ui.BODY, primary ? Ui.BLACK : color);
        button.setSingleLine(false);
        button.setGravity(Gravity.CENTER);
        button.setLineSpacing(0, 1.05f);
        button.setBackground(primary ? Ui.ovalAction(this, color) : Ui.tonalOvalAction(this, color, 38, 130));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void setControlsForCompletion(boolean ignored) {
        if (pause == null || stop == null) return;
        pause.setText(service != null && service.snapshot().paused ? "▶\n继续" : "Ⅱ\n暂停");
        stop.setVisibility(View.VISIBLE);
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
        TextView close = Ui.backButton(this);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36));
        closeParams.rightMargin = Ui.dp(this, 8);
        header.addView(close, closeParams);
        TextView title = Ui.bold(this, "运动轨迹", Ui.TITLE, Ui.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        TextView live = Ui.text(this, "实时", Ui.LABEL, Ui.LIME);
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
        panel.addView(Ui.pagerDots(this, 3, 4), new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));
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
        if (!s.planCompleted && displayedStage > 0 && s.stageNumber > displayedStage) showTransition(lastStageSummary, stageSummary);
        displayedStage = s.stageNumber;
        if (!s.planCompleted) lastStageSummary = stageSummary;
        workoutCompleted = false;

        int accent = s.planCompleted ? Ui.CYAN : s.paused ? Ui.MUTED : s.waitingForGps ? Ui.AMBER : s.stageName.equals("快走") ? Ui.CYAN : s.stageName.equals("休息") ? Ui.AMBER : Ui.LIME;
        stageName.setText(s.planCompleted ? (s.paused ? "自由记录 · 已暂停" : "自由记录") : s.paused ? s.stageName + " · 已暂停" : s.waitingForGps ? s.stageName + " · 等待信号" : s.stageName);
        stageName.setTextColor(accent);
        stageCounter.setText(String.format(Locale.CHINA, "第 %d/%d 项", s.stageNumber, s.stageCount));
        if (s.planCompleted) {
            remainingLabel.setText("计划已完成");
            remaining.setText(formatDuration(Math.max(0, s.activeMillis)));
            stageProgress.setText("继续记录中 · 手动结束后保存");
            hideStopConfirmation();
        } else if (s.waitingForGps && !s.paused) {
            remainingLabel.setText("移动后开始");
            remaining.setText("等待");
            stageProgress.setText(gpsAcquisitionDetail(s) + " · 目标 " + stageTargetText(s));
        } else {
            remainingLabel.setText(s.unit == Stage.Unit.DISTANCE ? "剩余距离" : "剩余时间");
            if (s.unit == Stage.Unit.DISTANCE) remaining.setText(String.format(Locale.CHINA, "%d m", s.remaining));
            else remaining.setText(String.format(Locale.CHINA, "%d:%02d", s.remaining / 60, s.remaining % 60));
            stageProgress.setText(s.paused ? "训练已暂停" : stageProgressText(s));
        }
        ring.set(s.planCompleted ? 1f : (float)s.progress, accent);
        // setTextIfChanged throughout: refresh runs on a timer, and TextView.setText relayouts
        // even when the string is identical, which is most ticks for slow-moving values.
        Ui.setTextIfChanged(coreHeader, s.planCompleted ? "自由记录"
                : String.format(Locale.CHINA, "%s · 第 %d/%d 项%s", s.stageName, s.stageNumber, s.stageCount, s.paused ? " · 已暂停" : ""));
        coreHeader.setTextColor(accent);
        Ui.setTextIfChanged(distance, formatDistance(s.totalMeters));
        Ui.setTextIfChanged(pace, formatCurrentPace(s));
        Ui.setTextIfChanged(speed, formatCurrentSpeed(s) + " · " + speedSourceText(s));
        // A live figure earns the accent colour; a placeholder stays muted so the eye is not
        // drawn to a number that is not there yet.
        boolean paceLive = Double.isFinite(s.currentSpeedMps) && s.currentSpeedMps >= SpeedFusion.MOVING_THRESHOLD_MPS;
        pace.setTextColor(paceLive ? accent : Ui.MUTED);
        Ui.setTextIfChanged(heart, heartStatus(s));
        heart.setTextColor(s.heartRate > 0 ? Ui.RED : Ui.MUTED);
        Ui.setTextIfChanged(steps, String.valueOf(s.sessionSteps));
        Ui.setTextIfChanged(duration, formatDuration(s.activeMillis));
        Ui.setTextIfChanged(controlState, s.paused ? "已暂停" : s.planCompleted ? "自由记录中" : "训练中");
        controlState.setTextColor(s.paused ? Ui.AMBER : accent);
        Ui.setTextIfChanged(controlDuration, formatDuration(s.activeMillis));
        Ui.setTextIfChanged(controlSummary, formatDistance(s.totalMeters)
                + (s.heartRate > 0 ? " · " + s.heartRate + " bpm" : ""));
        // The route view rasterises every point; only feed it while its page can actually be seen.
        if (routeView != null && workoutPager != null && workoutPager.getCurrentItem() == ROUTE_PAGE) {
            routeView.setRoute(s.routeLatitudes, s.routeLongitudes);
        }
        if (routeSummary != null) {
            int points = Math.min(s.routeLatitudes.length, s.routeLongitudes.length);
            routeSummary.setText(points > 0
                    ? formatDistance(s.totalMeters) + " · " + points + " 个轨迹点 · " + formatDuration(s.activeMillis)
                    : "等待有效定位轨迹 · 步数仍会准确记录");
        }
        updateGpsStatus(s);
        setControlsForCompletion(false);
    }

    private void updateGpsStatus(WorkoutService.Snapshot s) {
        if (s.usingSystemExerciseDistance) {
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

    private String formatCurrentSpeed(WorkoutService.Snapshot s) {
        if (!Double.isFinite(s.currentSpeedMps)) return "-- km/h";
        return (s.currentSpeedEstimated ? "约 " : "") + String.format(Locale.CHINA, "%.1f km/h", s.currentSpeedMps * 3.6d);
    }

    /** Minutes per kilometre, the reading runners actually pace by. */
    private String formatCurrentPace(WorkoutService.Snapshot s) {
        // A bare dash beats "--'--\"" as a placeholder: the prime marks read as broken glyphs at
        // display size when there is no number between them.
        if (!Double.isFinite(s.currentSpeedMps) || s.currentSpeedMps < SpeedFusion.MOVING_THRESHOLD_MPS) {
            return "--";
        }
        return SpeedFusion.formatPace(1000d / s.currentSpeedMps);
    }

    /** Names the measurement behind the readout so an estimated figure is never mistaken for GPS. */
    private String speedSourceText(WorkoutService.Snapshot s) {
        if (s.paused) return "已暂停";
        if (!Double.isFinite(s.currentSpeedMps)) return "等待速度数据";
        if (s.currentSpeedEstimated) return "步数估算";
        return s.hasGpsFix ? "卫星测速" : "轨迹推算";
    }

    private String heartStatus(WorkoutService.Snapshot s) {
        // Sensor prose ("请佩戴") set in 29dp condensed bold reads as a broken widget. The warmup
        // page already explains sensor states in full; mid-run the row just goes quiet.
        return s.heartRate > 0 ? s.heartRate + " bpm" : "--";
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
        if (workoutPager != null) workoutPager.setCurrentItem(3,true);
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

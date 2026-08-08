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
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 10;
    private static final int REQUEST_BACKGROUND_LOCATION = 11;
    private static final int REQUEST_SLEEP_PERMISSION = 12;
    private ArrayList<Stage> stages;
    private TextView ready, workout, start, planLine, planSummary, planDetails, sensorStatus, clock, activityTitle;
    private TextView weeklyLine;
    private LinearLayout pagerHistoryList, pagerPlanList;
    private TextView pagerHistorySummary, pagerPlanTitle;
    private boolean routingToTraining;
    private boolean sleepPermissionChecked;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockUpdater = new Runnable() { @Override public void run() {
        updateClock();
        long delay = 60_000L - (System.currentTimeMillis() % 60_000L);
        clockHandler.postDelayed(this, delay);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startForegroundService(new Intent(this, WatchBridgeService.class));
        startForegroundService(new Intent(this, WatchLinkService.class));
        buildUi();
        if (routeActiveWorkout()) return;
        requestSleepPermissionOnce();
    }

    private void requestSleepPermissionOnce() {
        if (sleepPermissionChecked) return;
        sleepPermissionChecked = true;
        if (!getPreferences(MODE_PRIVATE).getBoolean("sleep_permission_prompted", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("sleep_permission_prompted", true).apply();
            if (!SystemSleepBridge.requestPermission(this, REQUEST_SLEEP_PERMISSION)) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("sleep_permission_prompted", false).apply();
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (routeActiveWorkout()) {
            clockHandler.removeCallbacks(clockUpdater);
            return;
        }
        requestSleepPermissionOnce();
        stages = PlanStore.load(this);
        updatePlanPreview();
        updateSessionCallToAction();
        updateSensorStatus();
        renderPagerPages();
        clockHandler.removeCallbacks(clockUpdater);
        clockHandler.post(clockUpdater);
    }

    /**
     * The launcher is only an entry point while a checkpoint exists. WorkoutService owns and
     * restores the state; MainActivity immediately returns the user to its TrainingActivity view.
     */
    private boolean routeActiveWorkout() {
        boolean shouldRoute = WorkoutUxPolicy.shouldRouteAppEntryToTraining(
                WorkoutService.hasRecoverableSession(this), WorkoutUxPolicy.AppSurface.MAIN);
        if (!shouldRoute) {
            routingToTraining = false;
            return false;
        }
        if (!routingToTraining) {
            routingToTraining = true;
            openActiveWorkout();
        }
        return true;
    }

    /** Every active-session entry reuses the existing training task and the service snapshot. */
    private void openActiveWorkout() {
        startForegroundService(new Intent(this, WorkoutService.class)
                .setAction(WorkoutService.ACTION_START));
        startActivity(new Intent(this, TrainingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(TrainingActivity.EXTRA_PREPARED_SESSION, true));
    }

    @Override protected void onPause() {
        clockHandler.removeCallbacks(clockUpdater);
        // A completed launch is no longer "in flight" once Main leaves the foreground. If the
        // system later reclaims TrainingActivity, the next Main resume must be allowed to reopen it.
        routingToTraining = false;
        super.onPause();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, Ui.PAGE_MARGIN), Ui.dp(this, 6), Ui.dp(this, Ui.PAGE_MARGIN), Ui.dp(this, 4));
        root.setBackgroundColor(Ui.BLACK);

        // Compact workout identity, matching the shared interval-route mark + title + clock.
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.workoutGlyph(this, Ui.LIME),
                new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        activityTitle = Ui.bold(this, "户外训练", 18, Ui.WHITE);
        identity.addView(activityTitle, new LinearLayout.LayoutParams(-1, Ui.dp(this, 23)));
        TextView brand = Ui.text(this, "步序 · 间歇训练", Ui.CAPTION, Ui.MUTED);
        identity.addView(brand, new LinearLayout.LayoutParams(-1, Ui.dp(this, 15)));
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1);
        identityParams.leftMargin = Ui.dp(this, 9);
        header.addView(identity, identityParams);
        clock = Ui.numeral(this, "", 20, Ui.WHITE);
        clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(clock, new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 40)));
        root.addView(header, new LinearLayout.LayoutParams(-1, Ui.dp(this, 42)));

        ready = Ui.bold(this, "训练安排已就绪", Ui.LABEL, Ui.LIME);
        LinearLayout.LayoutParams readyParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 20));
        readyParams.topMargin = Ui.dp(this, 3);
        root.addView(ready, readyParams);
        workout = Ui.bold(this, "1千米 + 200米", 27, Ui.WHITE);
        root.addView(workout, new LinearLayout.LayoutParams(-1, Ui.dp(this, 34)));
        planLine = Ui.text(this, "", Ui.BODY, Ui.MUTED);
        root.addView(planLine, new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));
        planSummary = Ui.text(this, "", Ui.CAPTION, Ui.MUTED);
        root.addView(planSummary, new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        weeklyLine = Ui.bold(this, "", Ui.LABEL, Ui.CYAN);
        LinearLayout.LayoutParams weeklyParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 18));
        weeklyParams.topMargin = Ui.dp(this, 2);
        root.addView(weeklyLine, weeklyParams);
        planDetails = Ui.text(this, "", 12, Ui.MUTED);
        planDetails.setVisibility(View.GONE);
        root.addView(planDetails, new LinearLayout.LayoutParams(0, 0));

        root.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        // The single saturated action owns the page, as in Apple Workout. Green means exercise;
        // yellow and red remain reserved for time and heart rate once the session is running.
        android.widget.FrameLayout startBox = new android.widget.FrameLayout(this);
        startBox.addView(Ui.glow(this, Ui.LIME, 70),
                new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 128), Ui.dp(this, 128), Gravity.CENTER));
        start = Ui.bold(this, "开始", 25, Ui.BLACK);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Ui.gradientOvalAction(this, Ui.LIME, Ui.GREEN));
        start.setClickable(true);
        start.setFocusable(true);
        Ui.pressable(start);
        startBox.addView(start, new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 100), Ui.dp(this, 100), Gravity.CENTER));
        root.addView(startBox, new LinearLayout.LayoutParams(-1, Ui.dp(this, 124)));
        root.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));

        sensorStatus = Ui.text(this, "", Ui.LABEL, Ui.MUTED); sensorStatus.setGravity(Gravity.CENTER);
        sensorStatus.setVisibility(View.GONE);
        root.addView(sensorStatus, new LinearLayout.LayoutParams(-1, Ui.dp(this, 18)));
        TextView edit = Ui.action(this, "选择训练安排", 15, Ui.WHITE, Ui.PANEL);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 40));
        editParams.topMargin = Ui.dp(this, 3); root.addView(edit, editParams);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 14));
        dotParams.topMargin = Ui.dp(this, 2);
        root.addView(Ui.pagerDots(this, 0, 3), dotParams);
        start.setOnClickListener(v -> requestAndStart());
        edit.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
        scroll.addView(root);
        ArrayList<View> pagesList = new ArrayList<>();
        // Same order as the system pager: the next destination is physically on
        // the right, so a leftward finger drag reveals it pixel-for-pixel.
        pagesList.add(scroll); pagesList.add(buildHistoryPagerPage()); pagesList.add(buildPlanPagerPage());
        WatchPagerLayout pager = new WatchPagerLayout(this); for(View page:pagesList)pager.addView(page); pager.setCurrentItem(0, false);
        pager.setPageIndicatorEnabled(true);
        // These three pages only change on resume or once per minute, so prewarming their render
        // layers while idle makes finger-following cheap. The live five-page workout opts out.
        pager.setStaticPageCachingEnabled(true);
        // Watch-wide convention: dragging right past the first page leaves the app (back to the
        // dial). The workout pager deliberately does not register this.
        pager.setOnExitListener(this::finish);
        setContentView(pager);
    }

    private void updatePlanPreview() {
        if (planLine == null || stages == null) return;
        if (stages.isEmpty()) {
            planLine.setText("暂无训练计划");
            planSummary.setText("0 项训练内容");
            workout.setText(PlanStore.name(this));
            activityTitle.setText("请先添加计划");
            planDetails.setText(PlanStore.requirement(this));
            ready.setText(PlanStore.group(this));
            return;
        }
        updateClock();
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
        workout.setText(PlanStore.name(this));
        activityTitle.setText(activityTitle(stages));
        planDetails.setText(PlanStore.requirement(this));
        ready.setText(PlanStore.group(this) + " · 当前安排");
    }

    private void updateClock() {
        if (clock != null) clock.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()));
    }

    private void updateSessionCallToAction() {
        boolean recoverable = WorkoutService.hasRecoverableSession(this);
        boolean hasPlan = stages != null && !stages.isEmpty();
        ready.setText(recoverable ? "上次训练可继续"
                : hasPlan ? PlanStore.group(this) + " · 已就绪" : PlanStore.group(this));
        ready.setTextColor(recoverable ? Ui.YELLOW : hasPlan ? Ui.LIME : Ui.MUTED);
        workout.setText(recoverable ? "继续上次训练" : PlanStore.name(this));
        activityTitle.setText(recoverable ? "间歇训练"
                : hasPlan ? activityTitle(stages) : "请先添加计划");
        start.setText(recoverable ? "继续" : hasPlan ? "开始" : "不可用");
        start.setContentDescription(recoverable ? "继续上次训练"
                : hasPlan ? "开始训练" : "当前没有训练计划");
        start.setEnabled(recoverable || hasPlan);
        start.setAlpha(recoverable || hasPlan ? 1f : 0.45f);
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
        if (!WorkoutService.hasRecoverableSession(this)
                && (stages == null || stages.isEmpty())) {
            Toast.makeText(this, "请先在手机或云端添加训练计划", Toast.LENGTH_LONG).show();
            return;
        }
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
            openActiveWorkout();
            return;
        }
        if (stages == null || stages.isEmpty()) {
            Toast.makeText(this, "当前没有可开始的训练计划", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, WarmupActivity.class);
        startActivity(intent.putExtra("plan", PlanStore.encode(stages)));
    }

    private View buildHistoryPagerPage() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(Ui.BLACK);
        page.setPadding(Ui.dp(this,Ui.PAGE_MARGIN),Ui.dp(this,8),Ui.dp(this,Ui.PAGE_MARGIN),Ui.dp(this,4));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.workoutGlyph(this,Ui.RED),new LinearLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,36)));
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(Ui.bold(this,"训练历史",20,Ui.WHITE),new LinearLayout.LayoutParams(-1,Ui.dp(this,24)));
        heading.addView(Ui.text(this,"最近完成的户外训练",Ui.CAPTION,Ui.MUTED),new LinearLayout.LayoutParams(-1,Ui.dp(this,14)));
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(0,Ui.dp(this,40),1);headingParams.leftMargin=Ui.dp(this,9);header.addView(heading,headingParams);page.addView(header,new LinearLayout.LayoutParams(-1,Ui.dp(this,42)));
        pagerHistorySummary=Ui.bold(this,"",12,Ui.RED);page.addView(pagerHistorySummary,new LinearLayout.LayoutParams(-1,Ui.dp(this,24)));
        ScrollView scroll=new ScrollView(this);pagerHistoryList=new LinearLayout(this);pagerHistoryList.setOrientation(LinearLayout.VERTICAL);scroll.addView(pagerHistoryList);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView all=Ui.action(this,"查看完整历史",15,Ui.WHITE,Ui.PANEL);all.setOnClickListener(v->startActivity(new Intent(this,HistoryActivity.class)));page.addView(all,new LinearLayout.LayoutParams(-1,Ui.dp(this,42)));
        page.addView(Ui.pagerDots(this,1,3),new LinearLayout.LayoutParams(-1,Ui.dp(this,14)));
        return page;
    }

    private String activityTitle(List<Stage> plan) {
        boolean hasRun = false, hasWalk = false;
        if (plan != null) for (Stage stage : plan) {
            hasRun |= stage.kind == Stage.Kind.RUN;
            hasWalk |= stage.kind == Stage.Kind.WALK;
        }
        if (hasRun) return "户外跑步";
        if (hasWalk) return "户外快走";
        return "间歇训练";
    }

    private View buildPlanPagerPage() {
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Ui.BLACK);page.setPadding(Ui.dp(this,Ui.PAGE_MARGIN),Ui.dp(this,8),Ui.dp(this,Ui.PAGE_MARGIN),Ui.dp(this,4));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.workoutGlyph(this,Ui.LIME),new LinearLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,36)));
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);
        pagerPlanTitle=Ui.bold(this,"训练安排",20,Ui.WHITE);heading.addView(pagerPlanTitle,new LinearLayout.LayoutParams(-1,Ui.dp(this,24)));
        heading.addView(Ui.text(this,"按阶段完成本次训练",Ui.CAPTION,Ui.MUTED),new LinearLayout.LayoutParams(-1,Ui.dp(this,14)));
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(0,Ui.dp(this,40),1);headingParams.leftMargin=Ui.dp(this,9);header.addView(heading,headingParams);page.addView(header,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        ScrollView scroll=new ScrollView(this);pagerPlanList=new LinearLayout(this);pagerPlanList.setOrientation(LinearLayout.VERTICAL);scroll.addView(pagerPlanList);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView edit=Ui.action(this,"选择训练安排",16,Ui.BLACK,Ui.LIME);edit.setOnClickListener(v->startActivity(new Intent(this,PlanActivity.class)));page.addView(edit,new LinearLayout.LayoutParams(-1,Ui.dp(this,44)));
        page.addView(Ui.pagerDots(this,2,3),new LinearLayout.LayoutParams(-1,Ui.dp(this,14)));
        return page;
    }

    private void renderPagerPages() {
        List<WorkoutRecord> records = HistoryStore.load(this);
        WeeklyStats week = WeeklyStats.of(records, System.currentTimeMillis());
        if (weeklyLine != null) Ui.setTextIfChanged(weeklyLine, week.sessions == 0 ? "本周还没有训练"
                : "本周 " + Format.distance(week.meters) + " · " + week.sessions + " 次 · " + Format.duration(week.activeMillis));
        if(pagerHistoryList!=null){pagerHistoryList.removeAllViews();Ui.setTextIfChanged(pagerHistorySummary,records.isEmpty()?"还没有训练记录":records.size()+" 次训练 · 本周 "+Format.distance(week.meters));
            // Distance-first rows, matching HistoryActivity: the figure a runner scans by leads,
            // the timestamp becomes quiet metadata on the right.
            SimpleDateFormat whenFormat=new SimpleDateFormat("MM/dd HH:mm",Locale.CHINA);
            int previewCount=Math.min(4,records.size());
            for(int index=0;index<previewCount;index++){WorkoutRecord record=records.get(index);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8));row.setBackground(Ui.background(this,Ui.PANEL,18));
                LinearLayout headline=new LinearLayout(this);headline.setGravity(Gravity.CENTER_VERTICAL);
                TextView value=Ui.numeral(this,Format.distance(record.distanceMeters),21,Ui.LIME);headline.addView(value,new LinearLayout.LayoutParams(0,-2,1));
                TextView when=Ui.text(this,whenFormat.format(new Date(record.startedAt)),Ui.CAPTION,Ui.MUTED);headline.addView(when,new LinearLayout.LayoutParams(-2,-2));
                row.addView(headline,new LinearLayout.LayoutParams(-1,Ui.dp(this,26)));
                TextView data=Ui.text(this,Format.duration(record.durationMs)+" · "+(record.distanceMeters>0
                        ?SpeedFusion.formatPace(record.durationMs/record.distanceMeters):record.steps+" 步"),Ui.LABEL,Ui.MUTED);
                row.addView(data,new LinearLayout.LayoutParams(-1,Ui.dp(this,20)));
                row.setOnClickListener(v->startActivity(new Intent(this,HistoryActivity.class).putExtra("record_id",record.id)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Ui.dp(this,62));p.bottomMargin=Ui.dp(this,7);pagerHistoryList.addView(row,p);}}
        if(pagerPlanList!=null){pagerPlanList.removeAllViews();ArrayList<Stage> current=PlanStore.load(this);Ui.setTextIfChanged(pagerPlanTitle,PlanStore.name(this));TextView group=Ui.text(this,PlanStore.group(this)+" · "+current.size()+" 项内容",13,Ui.MUTED);pagerPlanList.addView(group,new LinearLayout.LayoutParams(-1,Ui.dp(this,34)));TextView req=Ui.text(this,PlanStore.requirement(this),12,Ui.MUTED);pagerPlanList.addView(req,new LinearLayout.LayoutParams(-1,-2));
            for(int i=0;i<current.size();i++){LinearLayout row=Ui.stageRow(this,i+1,current.get(i),Ui.PANEL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Ui.dp(this,52));p.topMargin=Ui.dp(this,7);pagerPlanList.addView(row,p);}}
    }


}

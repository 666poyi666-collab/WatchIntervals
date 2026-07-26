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
import android.view.WindowManager;
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
    private TextView ready, workout, start, planLine, planSummary, planDetails, sensorStatus, clock;
    private LinearLayout pagerHistoryList, pagerPlanList;
    private TextView pagerHistorySummary, pagerPlanTitle;
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
        updatePlanPreview();
        updateSessionCallToAction();
        updateSensorStatus();
        renderPagerPages();
        clockHandler.removeCallbacks(clockUpdater);
        clockHandler.post(clockUpdater);
    }

    @Override protected void onPause() {
        clockHandler.removeCallbacks(clockUpdater);
        super.onPause();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, Ui.PAGE_MARGIN), Ui.dp(this, 8), Ui.dp(this, Ui.PAGE_MARGIN), Ui.dp(this, 6));
        root.setBackgroundColor(Ui.BLACK);

        // Stock-sports top bar: bold app title left, white clock right.
        TextView title = Ui.bold(this, "步序", 22, Ui.WHITE);
        clock = Ui.topBar(this, root, title);

        // Editorial block on plain black. The old version boxed everything into one giant card
        // with five competing centred text styles — the main "home-made" tell of the app.
        ready = Ui.text(this, "训练安排已就绪", Ui.LABEL, Ui.MUTED);
        LinearLayout.LayoutParams readyParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 20));
        readyParams.topMargin = Ui.dp(this, 4);
        root.addView(ready, readyParams);
        workout = Ui.bold(this, "1千米 + 200米", 27, Ui.WHITE);
        root.addView(workout, new LinearLayout.LayoutParams(-1, Ui.dp(this, 38)));
        planLine = Ui.text(this, "", Ui.BODY, Ui.MUTED);
        root.addView(planLine, new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));
        planSummary = Ui.text(this, "", Ui.CAPTION, Ui.MUTED);
        root.addView(planSummary, new LinearLayout.LayoutParams(-1, Ui.dp(this, 17)));
        planDetails = Ui.text(this, "", 12, Ui.MUTED);
        planDetails.setVisibility(View.GONE);
        root.addView(planDetails, new LinearLayout.LayoutParams(0, 0));

        root.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        // Glowing primary disc, as on the stock prepare screen.
        android.widget.FrameLayout startBox = new android.widget.FrameLayout(this);
        startBox.addView(Ui.glow(this, Ui.YELLOW, 82),
                new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 152), Ui.dp(this, 152), Gravity.CENTER));
        start = Ui.bold(this, "开始", 26, Ui.BLACK);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Ui.ovalAction(this, Ui.YELLOW));
        start.setClickable(true);
        start.setFocusable(true);
        startBox.addView(start, new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112), Gravity.CENTER));
        root.addView(startBox, new LinearLayout.LayoutParams(-1, Ui.dp(this, 156)));
        root.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));

        sensorStatus = Ui.text(this, "", Ui.LABEL, Ui.MUTED); sensorStatus.setGravity(Gravity.CENTER);
        sensorStatus.setVisibility(View.GONE);
        root.addView(sensorStatus, new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));
        TextView edit = Ui.action(this, "选择训练安排", 15, Ui.WHITE, Ui.PANEL);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 42));
        editParams.topMargin = Ui.dp(this, 4); root.addView(edit, editParams);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 16));
        dotParams.topMargin = Ui.dp(this, 4);
        root.addView(Ui.pagerDots(this, 0, 3), dotParams);
        start.setOnClickListener(v -> requestAndStart());
        edit.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
        scroll.addView(root);
        ArrayList<View> pagesList = new ArrayList<>();
        // Same order as the system pager: the next destination is physically on
        // the right, so a leftward finger drag reveals it pixel-for-pixel.
        pagesList.add(scroll); pagesList.add(buildHistoryPagerPage()); pagesList.add(buildPlanPagerPage());
        WatchPagerLayout pager = new WatchPagerLayout(this); for(View page:pagesList)pager.addView(page); pager.setCurrentItem(0, false);
        // Watch-wide convention: dragging right past the first page leaves the app (back to the
        // dial). The workout pager deliberately does not register this.
        pager.setOnExitListener(this::finish);
        setContentView(pager);
    }

    private void updatePlanPreview() {
        if (planLine == null || stages == null || stages.isEmpty()) return;
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
        planDetails.setText(PlanStore.requirement(this));
        ready.setText(PlanStore.group(this) + " · 当前安排");
    }

    private void updateClock() {
        if (clock != null) clock.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()));
    }

    private void updateSessionCallToAction() {
        boolean recoverable = WorkoutService.hasRecoverableSession(this);
        ready.setText(recoverable ? "上次训练可继续" : PlanStore.group(this) + " · 已就绪");
        workout.setText(recoverable ? "继续上次训练" : PlanStore.name(this));
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

    private View buildHistoryPagerPage() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(Ui.BLACK);
        page.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,42));
        TextView title=Ui.bold(this,"训练历史",23,Ui.WHITE); page.addView(title,new LinearLayout.LayoutParams(-1,Ui.dp(this,42)));
        page.addView(Ui.pagerDots(this,1,3),new LinearLayout.LayoutParams(-1,Ui.dp(this,24)));
        pagerHistorySummary=Ui.text(this,"",12,Ui.MUTED);pagerHistorySummary.setGravity(Gravity.CENTER);page.addView(pagerHistorySummary,new LinearLayout.LayoutParams(-1,Ui.dp(this,28)));
        ScrollView scroll=new ScrollView(this);pagerHistoryList=new LinearLayout(this);pagerHistoryList.setOrientation(LinearLayout.VERTICAL);scroll.addView(pagerHistoryList);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView all=Ui.action(this,"查看完整历史",15,Ui.WHITE,Ui.PANEL);all.setOnClickListener(v->startActivity(new Intent(this,HistoryActivity.class)));page.addView(all,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));
        return page;
    }

    private View buildPlanPagerPage() {
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Ui.BLACK);page.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,42));
        pagerPlanTitle=Ui.bold(this,"训练安排",23,Ui.WHITE);page.addView(pagerPlanTitle,new LinearLayout.LayoutParams(-1,Ui.dp(this,42)));
        page.addView(Ui.pagerDots(this,2,3),new LinearLayout.LayoutParams(-1,Ui.dp(this,24)));
        ScrollView scroll=new ScrollView(this);pagerPlanList=new LinearLayout(this);pagerPlanList.setOrientation(LinearLayout.VERTICAL);scroll.addView(pagerPlanList);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView edit=Ui.action(this,"选择训练安排",16,Ui.BLACK,Ui.LIME);edit.setOnClickListener(v->startActivity(new Intent(this,PlanActivity.class)));page.addView(edit,new LinearLayout.LayoutParams(-1,Ui.dp(this,56)));
        return page;
    }

    private void renderPagerPages() {
        if(pagerHistoryList!=null){pagerHistoryList.removeAllViews();List<WorkoutRecord> records=HistoryStore.load(this);pagerHistorySummary.setText(records.isEmpty()?"还没有训练记录":records.size()+" 次训练");
            // Distance-first rows, matching HistoryActivity: the figure a runner scans by leads,
            // the timestamp becomes quiet metadata on the right.
            for(WorkoutRecord record:records){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8));row.setBackground(Ui.background(this,Ui.PANEL,18));
                LinearLayout headline=new LinearLayout(this);headline.setGravity(Gravity.CENTER_VERTICAL);
                TextView value=Ui.numeral(this,Format.distance(record.distanceMeters),21,Ui.WHITE);headline.addView(value,new LinearLayout.LayoutParams(0,-2,1));
                TextView when=Ui.text(this,new SimpleDateFormat("MM/dd HH:mm",Locale.CHINA).format(new Date(record.startedAt)),Ui.CAPTION,Ui.MUTED);headline.addView(when,new LinearLayout.LayoutParams(-2,-2));
                row.addView(headline,new LinearLayout.LayoutParams(-1,Ui.dp(this,26)));
                TextView data=Ui.text(this,Format.duration(record.durationMs)+" · "+(record.distanceMeters>0
                        ?SpeedFusion.formatPace(record.durationMs/record.distanceMeters):record.steps+" 步"),Ui.LABEL,Ui.MUTED);
                row.addView(data,new LinearLayout.LayoutParams(-1,Ui.dp(this,20)));
                row.setOnClickListener(v->startActivity(new Intent(this,HistoryActivity.class).putExtra("record_id",record.id)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Ui.dp(this,62));p.bottomMargin=Ui.dp(this,7);pagerHistoryList.addView(row,p);}}
        if(pagerPlanList!=null){pagerPlanList.removeAllViews();ArrayList<Stage> current=PlanStore.load(this);pagerPlanTitle.setText(PlanStore.name(this));TextView group=Ui.text(this,PlanStore.group(this)+" · "+current.size()+" 项内容",13,Ui.MUTED);pagerPlanList.addView(group,new LinearLayout.LayoutParams(-1,Ui.dp(this,34)));TextView req=Ui.text(this,PlanStore.requirement(this),12,Ui.MUTED);pagerPlanList.addView(req,new LinearLayout.LayoutParams(-1,-2));
            for(int i=0;i<current.size();i++){LinearLayout row=Ui.stageRow(this,i+1,current.get(i),Ui.PANEL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Ui.dp(this,52));p.topMargin=Ui.dp(this,7);pagerPlanList.addView(row,p);}}
    }


}

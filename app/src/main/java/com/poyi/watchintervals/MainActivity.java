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
        buildUi();
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
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 10));
        root.setBackgroundColor(Ui.BLACK);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.bold(this, "步序", 27, Ui.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        clock = Ui.text(this, "", 16, Ui.MUTED); clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(clock, new LinearLayout.LayoutParams(Ui.dp(this, 74), Ui.dp(this, 42)));
        root.addView(header);

        LinearLayout hero = Ui.card(this);
        ready = Ui.text(this, "训练安排已就绪", 12, Ui.MUTED);
        ready.setGravity(Gravity.CENTER); hero.addView(ready, new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));
        workout = Ui.bold(this, "1千米 + 200米", 25, Ui.WHITE);
        workout.setGravity(Gravity.CENTER); hero.addView(workout, new LinearLayout.LayoutParams(-1, Ui.dp(this, 38)));

        start = Ui.bold(this, "开始", 29, Ui.BLACK);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Ui.ovalAction(this, Ui.YELLOW));
        start.setClickable(true);
        start.setFocusable(true);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112));
        startParams.gravity = Gravity.CENTER_HORIZONTAL;
        startParams.topMargin = Ui.dp(this, 5); hero.addView(start, startParams);

        planLine = Ui.text(this, "", 15, Ui.WHITE); planLine.setGravity(Gravity.CENTER);
        hero.addView(planLine, new LinearLayout.LayoutParams(-1, Ui.dp(this, 28)));
        planSummary = Ui.text(this, "", 12, Ui.LIME); planSummary.setGravity(Gravity.CENTER);
        hero.addView(planSummary, new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));
        planDetails = Ui.text(this, "", 12, Ui.MUTED);
        planDetails.setGravity(Gravity.CENTER);
        planDetails.setSingleLine(false); planDetails.setMaxLines(3);
        planDetails.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));
        hero.addView(planDetails, new LinearLayout.LayoutParams(-1, -2));
        root.addView(hero, new LinearLayout.LayoutParams(-1, -2));
        sensorStatus = Ui.text(this, "", 11, Ui.MUTED); sensorStatus.setGravity(Gravity.CENTER);
        sensorStatus.setVisibility(View.GONE);
        root.addView(sensorStatus, new LinearLayout.LayoutParams(-1, Ui.dp(this, 24)));
        TextView edit = Ui.action(this, "选择训练安排", 17, Ui.WHITE, Ui.PANEL);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 50));
        editParams.topMargin = Ui.dp(this, 4); root.addView(edit, editParams);
        root.addView(Ui.pagerDots(this, 0, 3), new LinearLayout.LayoutParams(-1, Ui.dp(this, 24)));
        TextView pages = Ui.text(this, "向左滑查看历史与计划", 10, Ui.MUTED);
        pages.setGravity(Gravity.CENTER); root.addView(pages, new LinearLayout.LayoutParams(-1, Ui.dp(this, 18)));
        start.setOnClickListener(v -> requestAndStart());
        edit.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
        scroll.addView(root);
        ArrayList<View> pagesList = new ArrayList<>();
        // Same order as the system pager: the next destination is physically on
        // the right, so a leftward finger drag reveals it pixel-for-pixel.
        pagesList.add(scroll); pagesList.add(buildHistoryPagerPage()); pagesList.add(buildPlanPagerPage());
        WatchPagerLayout pager = new WatchPagerLayout(this); for(View page:pagesList)pager.addView(page); pager.setCurrentItem(0, false);
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
            sensorStatus.setText("手机配对码  " + WatchBridgeService.pairingCode(this));
            sensorStatus.setTextColor(Ui.CYAN);
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
            for(WorkoutRecord record:records){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(this,14),Ui.dp(this,9),Ui.dp(this,14),Ui.dp(this,9));row.setBackground(Ui.background(this,Ui.PANEL,18));
                TextView date=Ui.bold(this,new SimpleDateFormat("MM月dd日  HH:mm",Locale.CHINA).format(new Date(record.startedAt)),15,Ui.WHITE);TextView data=Ui.text(this,formatDistance(record.distanceMeters)+" · "+formatDuration(record.durationMs)+" · "+record.steps+" 步",12,Ui.MUTED);row.addView(date);row.addView(data);row.setOnClickListener(v->startActivity(new Intent(this,HistoryActivity.class).putExtra("record_id",record.id)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Ui.dp(this,64));p.bottomMargin=Ui.dp(this,7);pagerHistoryList.addView(row,p);}}
        if(pagerPlanList!=null){pagerPlanList.removeAllViews();ArrayList<Stage> current=PlanStore.load(this);pagerPlanTitle.setText(PlanStore.name(this));TextView group=Ui.text(this,PlanStore.group(this)+" · "+current.size()+" 项内容",13,Ui.LIME);pagerPlanList.addView(group,new LinearLayout.LayoutParams(-1,Ui.dp(this,34)));TextView req=Ui.text(this,PlanStore.requirement(this),12,Ui.MUTED);pagerPlanList.addView(req,new LinearLayout.LayoutParams(-1,-2));
            for(int i=0;i<current.size();i++){Stage item=current.get(i);TextView row=Ui.text(this,(i+1)+"   "+item.name()+"   "+item.targetText(),15,Ui.WHITE);row.setBackground(Ui.background(this,Ui.PANEL,18));row.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Ui.dp(this,52));p.topMargin=Ui.dp(this,7);pagerPlanList.addView(row,p);}}
    }

    private String formatDistance(double meters){return meters<1000?Math.round(meters)+" m":String.format(Locale.CHINA,"%.2f km",meters/1000d);}
    private String formatDuration(long millis){long seconds=millis/1000;return String.format(Locale.CHINA,"%02d:%02d",seconds/60,seconds%60);}

}

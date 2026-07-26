package com.poyi.watchintervals;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Calendar;

public class HistoryActivity extends Activity {
    private WorkoutRecord selected;
    private SwipeTracker swipeTracker;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        swipeTracker = new SwipeTracker(this, new SwipeTracker.Listener() {
            @Override public void onSwipeRight() { if (selected == null) startActivity(new Intent(HistoryActivity.this, PlanActivity.class)); }
            @Override public void onSwipeLeft() { if (selected == null) startActivity(new Intent(HistoryActivity.this, PlanActivity.class)); }
        });
        String recordId=getIntent().getStringExtra("record_id");
        WorkoutRecord record=recordId==null?null:HistoryStore.find(this,recordId);
        if(record!=null)showDetail(record);else showList();
    }

    private void showList() {
        selected = null;
        LinearLayout root = base();
        root.addView(header("训练历史", this::finish));
        List<WorkoutRecord> records = HistoryStore.load(this);
        TextView summary = Ui.text(this, records.isEmpty() ? "还没有训练记录" : records.size() + " 次训练", Ui.LABEL, Ui.MUTED);
        root.addView(summary, new LinearLayout.LayoutParams(-1, Ui.dp(this, 26)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        String lastDay = null;
        for (WorkoutRecord record : records) {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                    .format(new Date(record.startedAt));
            if (!day.equals(lastDay)) {
                TextView group = Ui.bold(this, dayLabel(record.startedAt), 14, Ui.MUTED);
                group.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                list.addView(group, new LinearLayout.LayoutParams(-1, Ui.dp(this, 38)));
                lastDay = day;
            }
            list.addView(recordRow(record));
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    /** Distance leads each entry — the figure a runner scans a history list by — with the
     *  start time as quiet metadata instead of the headline. */
    private View recordRow(WorkoutRecord record) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
        row.setBackground(Ui.background(this, Ui.PANEL, 18));
        LinearLayout headline = new LinearLayout(this);
        headline.setGravity(Gravity.CENTER_VERTICAL);
        TextView value = Ui.numeral(this, formatDistance(record.distanceMeters), 22, Ui.WHITE);
        headline.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
        TextView when = Ui.text(this, new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(record.startedAt)), Ui.LABEL, Ui.MUTED);
        headline.addView(when, new LinearLayout.LayoutParams(-2, -2));
        row.addView(headline, new LinearLayout.LayoutParams(-1, Ui.dp(this, 28)));
        TextView data = Ui.text(this, formatDuration(record.durationMs) + " · " + record.steps + " 步"
                + (record.averageHeartRate > 0 ? " · " + record.averageHeartRate + " bpm" : ""), Ui.LABEL, Ui.MUTED);
        row.addView(data, new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));
        row.setClickable(true); row.setFocusable(true); row.setOnClickListener(v -> showDetail(record));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(this, 66));
        params.bottomMargin = Ui.dp(this, 7); row.setLayoutParams(params);
        return row;
    }

    private void showDetail(WorkoutRecord record) {
        selected = record;
        LinearLayout page = base();
        page.addView(header("训练详情", this::showList));
        TextView date = Ui.text(this, new SimpleDateFormat("yyyy年MM月dd日  HH:mm", Locale.CHINA).format(new Date(record.startedAt)), 12, Ui.MUTED);
        date.setGravity(Gravity.CENTER); page.addView(date, new LinearLayout.LayoutParams(-1, Ui.dp(this, 28)));

        TextView plan = Ui.bold(this, record.planName.isEmpty() ? "户外训练" : record.planName, 20, Ui.WHITE); plan.setGravity(Gravity.CENTER);
        page.addView(plan, new LinearLayout.LayoutParams(-1, Ui.dp(this, 34)));

        // Same colour semantics as the live training page, so the summary reads as a continuation
        // of the workout rather than an unrelated report.
        LinearLayout metricsCard = Ui.card(this); LinearLayout metrics = new LinearLayout(this);
        addMetric(metrics, "距离", formatDistance(record.distanceMeters), Ui.WHITE);
        addMetric(metrics, "用时", formatDuration(record.durationMs), Ui.YELLOW);
        addMetric(metrics, "步数", record.steps + "", Ui.CYAN);
        metricsCard.addView(metrics, new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));
        LinearLayout metrics2 = new LinearLayout(this);
        addMetric(metrics2, "平均心率", record.averageHeartRate > 0 ? record.averageHeartRate + " bpm" : "--",
                record.averageHeartRate > 0 ? Ui.RED : Ui.MUTED);
        double pace = record.distanceMeters > 0 ? record.durationMs * 1000d / record.distanceMeters : 0;
        addMetric(metrics2, "平均配速", pace > 0 ? formatDuration((long)pace) + "/km" : "--",
                pace > 0 ? Ui.LIME : Ui.MUTED);
        if(record.steps>0&&record.durationMs>=30_000)addMetric(metrics2,"平均步频",Math.round(record.steps*60_000d/record.durationMs)+" spm",Ui.WHITE);
        metricsCard.addView(metrics2, new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));page.addView(metricsCard);

        WorkoutRouteView route = new WorkoutRouteView(this);
        double[] latitudes = new double[record.route.size()], longitudes = new double[record.route.size()];
        for (int index = 0; index < record.route.size(); index++) {
            latitudes[index] = record.route.get(index).getLatitude();
            longitudes[index] = record.route.get(index).getLongitude();
        }
        route.setRoute(latitudes, longitudes);
        TextView routeTitle=Ui.bold(this,"运动轨迹",17,Ui.WHITE);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,Ui.dp(this,36));titleParams.topMargin=Ui.dp(this,10);page.addView(routeTitle,titleParams);
        page.addView(route, new LinearLayout.LayoutParams(-1, Ui.dp(this, 230)));

        try {
            org.json.JSONObject json=record.toJson();
            if(json.has("bestPaceSecondsPerKm")){LinearLayout card=detailCard("配速表现");card.addView(detailLine("最佳瞬时配速",formatDuration(json.optLong("bestPaceSecondsPerKm")*1000L)+" /km"));page.addView(card,sectionParams());}
            org.json.JSONArray splits=json.optJSONArray("splits");if(splits!=null&&splits.length()>0){LinearLayout card=detailCard("分段");for(int i=0;i<splits.length();i++){org.json.JSONObject split=splits.getJSONObject(i);card.addView(detailLine(split.optInt("index")+" 公里",formatDuration(split.optLong("durationMs"))+"  ·  "+formatDuration(split.optLong("paceSecondsPerKm")*1000L)+" /km"));}page.addView(card,sectionParams());}
            if(json.has("heartRateRange")){org.json.JSONObject range=json.getJSONObject("heartRateRange");LinearLayout card=detailCard("心率");card.addView(detailLine("平均心率",record.averageHeartRate+" bpm"));card.addView(detailLine("实测范围",range.optInt("min")+"–"+range.optInt("max")+" bpm"));page.addView(card,sectionParams());}
            if(json.has("elevationGainMeters")){LinearLayout card=detailCard("海拔");card.addView(detailLine("累计爬升",json.optDouble("elevationGainMeters")+" m"));page.addView(card,sectionParams());}
            org.json.JSONArray stages=json.optJSONArray("stageResults");if(stages!=null&&stages.length()>0){LinearLayout card=detailCard("训练阶段");for(int i=0;i<stages.length();i++){org.json.JSONObject stage=stages.getJSONObject(i);card.addView(detailLine(stage.optInt("index")+"  "+stage.optString("name"),formatDuration(stage.optLong("completedAtMs"))));}page.addView(card,sectionParams());}
        } catch(Exception ignored) {}
        TextView delete = Ui.action(this, "删除本次记录", 15, Ui.WHITE, Ui.RED);
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 48));
        deleteParams.topMargin = Ui.dp(this, 12); page.addView(delete, deleteParams);
        delete.setOnClickListener(v -> { HistoryStore.delete(this, record.id); showList(); });
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.addView(page,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
    }

    private LinearLayout detailCard(String title){LinearLayout card=Ui.card(this);card.addView(Ui.bold(this,title,16,Ui.WHITE),new LinearLayout.LayoutParams(-1,Ui.dp(this,30)));return card;}
    private View detailLine(String label,String value){LinearLayout row=new LinearLayout(this);TextView left=Ui.text(this,label,13,Ui.MUTED);TextView right=Ui.bold(this,value,14,Ui.WHITE);right.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(left,new LinearLayout.LayoutParams(0,Ui.dp(this,34),1));row.addView(right,new LinearLayout.LayoutParams(Ui.dp(this,180),Ui.dp(this,34)));return row;}
    private LinearLayout.LayoutParams sectionParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=Ui.dp(this,8);return p;}

    private LinearLayout base() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 12));
        root.setBackgroundColor(Ui.BLACK); return root;
    }

    private View header(String titleText, Runnable backAction) {
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.backButton(this); back.setOnClickListener(v -> backAction.run());
        TextView title = Ui.bold(this, titleText, Ui.TITLE, Ui.WHITE);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36));
        backParams.rightMargin = Ui.dp(this, 8);
        header.addView(back, backParams);
        header.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1)); return header;
    }

    private void addMetric(LinearLayout row, String label, String value, int color) {
        LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
        TextView caption = Ui.text(this, label, Ui.CAPTION, Ui.MUTED); caption.setGravity(Gravity.CENTER);
        TextView data = Ui.numeral(this, value, 17, color); data.setGravity(Gravity.CENTER);
        cell.addView(caption, new LinearLayout.LayoutParams(-1, Ui.dp(this, 22)));
        cell.addView(data, new LinearLayout.LayoutParams(-1, Ui.dp(this, 30)));
        row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private String formatDistance(double meters) { return meters < 1000 ? Math.round(meters) + " m" : String.format(Locale.CHINA, "%.2f km", meters / 1000d); }
    private String formatDuration(long millis) { long seconds = millis / 1000; return String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60); }
    private String dayLabel(long timestamp) {
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestamp);
        Calendar today = Calendar.getInstance();
        if (sameDay(target, today)) return "今天";
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(target, today)) return "昨天";
        return new SimpleDateFormat("M月d日  EEEE", Locale.CHINA)
                .format(new Date(timestamp));
    }
    private boolean sameDay(Calendar first, Calendar second) {
        return first.get(Calendar.ERA) == second.get(Calendar.ERA)
                && first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    @Override public void onBackPressed() { if (selected != null) showList(); else super.onBackPressed(); }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (swipeTracker != null) swipeTracker.observe(event);
        return super.dispatchTouchEvent(event);
    }
}

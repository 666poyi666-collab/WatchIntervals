package com.poyi.watchintervals.phone;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.poyi.watchintervals.phone.connection.WatchConnectionManager;

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION_RELAY = 44;
    private static final int REQUEST_BLUETOOTH = 45;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> stages = new ArrayList<>();
    private EditText host, code, planName, planGroup, planRequirement;
    private TextView connection, historySummary, sleepSummary, currentWatchPlan;
    private LinearLayout planList, historyList, sleepList, savedPlanList, planCard, controlCard, historyCard, sleepCard, planLibraryPanel, planEditorPanel;
    private String editingPlanId = "";
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private WifiManager.MulticastLock multicastLock;
    private boolean resolving;
    private boolean foreground;
    private WatchConnectionManager watchConnection;
    private boolean autoSynced;
    private View statusDot;
    private TextView setupChevron;
    private LinearLayout setupPanel;
    private ScrollView planScroll, controlScroll, historyScroll, sleepScroll;
    private TextView[] navItems;
    private int currentSection;
    private TextView liveState, liveTime, liveMeta;
    private ActivityRing liveRing;
    private LinearLayout liveActions;
    private String liveActionsState = "";
    private volatile boolean livePollInFlight;
    private final android.os.Handler liveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable livePoller = new Runnable() { @Override public void run() {
        pollLiveStatus();
        liveHandler.postDelayed(this, 5_000L);
    }};
    private final WatchConnectionManager.Observer connectionObserver=snapshot->{connection.setText(connectionLabel(snapshot));updateStatusDot(snapshot);if(watchConnection!=null&&watchConnection.identity().isPaired()&&code!=null){code.setText("");code.setHint("已完成安全配对");}if(!autoSynced&&(snapshot.state==com.poyi.watchintervals.phone.connection.ConnectionState.CONNECTED_BLE||snapshot.state==com.poyi.watchintervals.phone.connection.ConnectionState.CONNECTED_BLE_LAN)){autoSynced=true;syncAll();}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startForegroundService(new Intent(this, PhonePlanBridgeService.class));
        buildUi();
        watchConnection=WatchConnectionManager.get(this);
        watchConnection.observe(connectionObserver);
        android.content.SharedPreferences preferences = getSharedPreferences("connection", MODE_PRIVATE);
        host.setText(preferences.getString("host", ""));
        if(watchConnection.identity().isPaired()){code.setText("");code.setHint("已完成安全配对");}else code.setText(preferences.getString("code", ""));
        watchConnection.configurePairing(code.getText().toString().trim());
        watchConnection.configureLan(host.getText().toString().trim(),code.getText().toString().trim());
        toggleSetup(!watchConnection.identity().isPaired());
        ensureBluetoothConnection();
        discoverWatch();
    }

    @Override protected void onResume() {
        super.onResume(); foreground = true;
        if (currentSection == 1) { liveHandler.removeCallbacks(livePoller); liveHandler.post(livePoller); }
    }
    @Override protected void onPause() { foreground = false; liveHandler.removeCallbacks(livePoller); super.onPause(); }

    private void buildUi() {
        int statusBarResource=getResources().getIdentifier("status_bar_height","dimen","android");
        int topInset=(statusBarResource>0?getResources().getDimensionPixelSize(statusBarResource):0)+dp(10);
        int navBarResource=getResources().getIdentifier("navigation_bar_height","dimen","android");
        int bottomInset=navBarResource>0?getResources().getDimensionPixelSize(navBarResource):0;
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(Palette.BG);

        // Fixed header: product title plus a one-line connection status. The old full-height
        // "连接手表" card topped every tab forever; for a paired phone, connection is status, not
        // a task, so setup collapses behind a tap on the status row.
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setPadding(dp(20),topInset,dp(20),dp(4));
        TextView productTitle=text("步序",32,true,Palette.TEXT);
        productTitle.setLetterSpacing(.04f);
        header.addView(productTitle);
        LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.CENTER_VERTICAL);statusRow.setClickable(true);statusRow.setFocusable(true);
        statusDot=new View(this);statusDot.setBackground(rounded(Color.GRAY,10));
        LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(10),dp(10));dotParams.rightMargin=dp(8);statusRow.addView(statusDot,dotParams);
        connection=text("尚未连接",14,false,Palette.TEXT_DIM);statusRow.addView(connection,new LinearLayout.LayoutParams(0,-2,1));
        setupChevron=text("连接设置 ▾",13,false,Palette.TEXT_DIM);statusRow.addView(setupChevron,new LinearLayout.LayoutParams(-2,-2));
        statusRow.setOnClickListener(v->toggleSetup(setupPanel.getVisibility()!=View.VISIBLE));
        header.addView(statusRow,new LinearLayout.LayoutParams(-1,dp(38)));

        setupPanel=compactCard();
        host=input("LAN 诊断地址");host.setVisibility(View.GONE);
        code=input("手表上的 6 位配对码");code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        setupPanel.addView(host);setupPanel.addView(code);
        LinearLayout connectActions=new LinearLayout(this);
        Button discover=button("连接手表",Palette.CARD_HIGH,Palette.TEXT);
        Button connect=button("立即同步",Palette.EXERCISE,Palette.TEXT);
        connectActions.addView(discover,weight());connectActions.addView(connect,weight());setupPanel.addView(connectActions);
        LinearLayout.LayoutParams setupParams=new LinearLayout.LayoutParams(-1,-2);setupParams.topMargin=dp(6);
        header.addView(setupPanel,setupParams);
        shell.addView(header);

        planCard = section();
        planCard.addView(pageTitle("训练计划", "把每一天的训练，排成看得懂的节奏"));
        planLibraryPanel=new LinearLayout(this);planLibraryPanel.setOrientation(LinearLayout.VERTICAL);
        currentWatchPlan=text("手表当前安排  ·  连接后读取",13,false,Palette.TEXT_DIM);
        currentWatchPlan.setPadding(dp(14),dp(8),dp(14),dp(8));
        currentWatchPlan.setBackground(rounded(Palette.CARD,14));
        LinearLayout.LayoutParams currentPlanParams=new LinearLayout.LayoutParams(-1,dp(42));currentPlanParams.topMargin=dp(14);planLibraryPanel.addView(currentWatchPlan,currentPlanParams);
        LinearLayout libraryHeader = new LinearLayout(this); libraryHeader.setGravity(Gravity.CENTER_VERTICAL);
        libraryHeader.addView(text("我的计划",20,true,Palette.TEXT),new LinearLayout.LayoutParams(0,dp(64),1));
        Button createGroup=button("＋",Palette.MOVE,Color.WHITE); createGroup.setTextSize(25); libraryHeader.addView(createGroup,new LinearLayout.LayoutParams(dp(48),dp(48)));
        planLibraryPanel.addView(libraryHeader);
        savedPlanList=new LinearLayout(this); savedPlanList.setOrientation(LinearLayout.VERTICAL); planLibraryPanel.addView(savedPlanList);
        planCard.addView(planLibraryPanel);

        planEditorPanel=new LinearLayout(this);planEditorPanel.setOrientation(LinearLayout.VERTICAL);planEditorPanel.setVisibility(View.GONE);
        LinearLayout editorHeader=new LinearLayout(this);editorHeader.setGravity(Gravity.CENTER_VERTICAL);
        Button closeEditor=button("‹ 计划列表",Color.TRANSPARENT,Palette.MOVE);closeEditor.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);editorHeader.addView(closeEditor,new LinearLayout.LayoutParams(dp(118),dp(48)));
        planEditorPanel.addView(editorHeader);
        planEditorPanel.addView(text("编辑安排",22,true,Palette.TEXT));
        planEditorPanel.addView(text("安排信息",16,true,Palette.TEXT));
        planName = input("安排名称，例如：第1天"); planGroup = input("所属训练计划，例如：减肥计划");
        planRequirement = input("今天的训练说明（可选）");
        planRequirement.setSingleLine(false); planRequirement.setMinLines(2); planRequirement.setGravity(Gravity.TOP);LinearLayout.LayoutParams requirementParams=new LinearLayout.LayoutParams(-1,dp(92));requirementParams.topMargin=dp(7);planRequirement.setLayoutParams(requirementParams);
        planEditorPanel.addView(planName); planEditorPanel.addView(planGroup); planEditorPanel.addView(planRequirement);
        planEditorPanel.addView(text("快速填充训练内容",16,true,Palette.TEXT));
        LinearLayout templates = new LinearLayout(this);
        Button intervalPlan = button("1千米 + 200米", Palette.CARD_HIGH, Palette.TEXT);
        Button fartlekPlan = button("法特莱克跑", Palette.CARD_HIGH, Palette.TEXT);
        templates.addView(intervalPlan, weight()); templates.addView(fartlekPlan, weight()); planEditorPanel.addView(templates);
        planEditorPanel.addView(text("训练内容",18,true,Palette.TEXT));
        planEditorPanel.addView(text("每项都可选择按时间或距离，支持交替组合",13,false,Palette.TEXT_DIM));
        planList = new LinearLayout(this); planList.setOrientation(LinearLayout.VERTICAL); planEditorPanel.addView(planList);
        LinearLayout additions = new LinearLayout(this);
        Button addRun = button("+ 跑步", Palette.FILL_RUN, Palette.EXERCISE);
        Button addWalk = button("+ 快走", Palette.FILL_WALK, Palette.STAND);
        Button addRest = button("+ 休息", Palette.FILL_REST, Palette.YELLOW);
        additions.addView(addRun, weight()); additions.addView(addWalk, weight()); additions.addView(addRest, weight()); planEditorPanel.addView(additions);
        LinearLayout planActions=new LinearLayout(this);
        Button saveLocal=button("保存安排",Palette.CARD_HIGH,Palette.TEXT);
        Button save=button("保存并同步",Palette.EXERCISE,Palette.TEXT);
        planActions.addView(saveLocal,weight()); planActions.addView(save,weight()); planEditorPanel.addView(planActions);
        planCard.addView(planEditorPanel);

        // Live remote: the watch's /v1/status workout block drives the readout and which actions
        // even exist. Four state-blind buttons were a prototype leftover — pressing 开始 mid-run
        // or 继续 while idle only produced STATE_MISMATCH errors.
        controlCard = section();
        controlCard.addView(pageTitle("训练", "手表实时数据与训练控制"));
        // Fitness-style hero: the active clock sits inside a gradient progress ring that fills
        // as plan stages complete.
        FrameLayout ringBox=new FrameLayout(this);
        liveRing=new ActivityRing(this);
        ringBox.addView(liveRing,new FrameLayout.LayoutParams(dp(176),dp(176),Gravity.CENTER));
        LinearLayout ringCenter=new LinearLayout(this);ringCenter.setOrientation(LinearLayout.VERTICAL);ringCenter.setGravity(Gravity.CENTER);
        liveTime=text("--:--",28,true,Palette.TEXT);liveTime.setFontFeatureSettings("tnum");liveTime.setGravity(Gravity.CENTER);
        liveState=text("未在训练",13,true,Palette.TEXT_DIM);liveState.setGravity(Gravity.CENTER);
        ringCenter.addView(liveTime);ringCenter.addView(liveState);
        ringBox.addView(ringCenter,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        ringBox.setBackground(rounded(Palette.CARD,24));
        LinearLayout.LayoutParams ringBoxParams=new LinearLayout.LayoutParams(-1,dp(220));ringBoxParams.topMargin=dp(16);
        controlCard.addView(ringBox,ringBoxParams);
        liveMeta=text("连接手表后显示实时数据",14,false,Palette.TEXT_DIM);liveMeta.setGravity(Gravity.CENTER_HORIZONTAL);controlCard.addView(liveMeta);
        liveActions=new LinearLayout(this);controlCard.addView(liveActions);
        rebuildLiveActions("idle");
        TextView controlHint=text("操作即时发送到手表 · 训练状态每 5 秒刷新",12,false,Palette.TEXT_DIM);controlHint.setGravity(Gravity.CENTER_HORIZONTAL);controlCard.addView(controlHint);

        historyCard = section(); historyCard.addView(pageTitle("训练历史", "每次出发，都留下可复盘的数据"));
        historySummary = text("连接后读取", 14, false, Palette.TEXT_DIM); historyCard.addView(historySummary);
        historyList = new LinearLayout(this); historyList.setOrientation(LinearLayout.VERTICAL); historyCard.addView(historyList);
        sleepCard = section(); sleepCard.addView(pageTitle("睡眠", "恢复也是训练的一部分"));
        sleepSummary = text("连接后读取手表系统睡眠", 14, false, Palette.TEXT_DIM); sleepCard.addView(sleepSummary);
        sleepList = new LinearLayout(this); sleepList.setOrientation(LinearLayout.VERTICAL); sleepCard.addView(sleepList);

        FrameLayout content=new FrameLayout(this);
        planScroll=wrapContent(planCard);controlScroll=wrapContent(controlCard);historyScroll=wrapContent(historyCard);sleepScroll=wrapContent(sleepCard);
        content.addView(planScroll);content.addView(controlScroll);content.addView(historyScroll);content.addView(sleepScroll);
        shell.addView(content,new LinearLayout.LayoutParams(-1,0,1));

        // Real bottom navigation: destinations stay put while content scrolls beneath them. The
        // old tab chips lived inside the scroll and drifted away with the content.
        LinearLayout nav=new LinearLayout(this);nav.setBackground(roundedStroke(Palette.NAV,0,Palette.CARD_HIGH,1));
        nav.setPadding(dp(8),dp(4),dp(8),dp(4)+bottomInset);
        navItems=new TextView[]{navItem("▦","计划"),navItem("▶","训练"),navItem("◷","历史"),navItem("☾","睡眠")};
        for(int i=0;i<navItems.length;i++){final int destination=i;navItems[i].setOnClickListener(v->{showSection(destination);if(destination==3)loadSleep();});nav.addView(navItems[i],new LinearLayout.LayoutParams(0,dp(62),1));}
        shell.addView(nav);
        setContentView(shell);

        connect.setOnClickListener(v -> syncAll());
        discover.setOnClickListener(v -> discoverWatch());
        addRun.setOnClickListener(v -> addStage("RUN", "DISTANCE", 1000));
        addWalk.setOnClickListener(v -> addStage("WALK", "DISTANCE", 200));
        addRest.setOnClickListener(v -> addStage("REST", "TIME", 60));
        save.setOnClickListener(v -> savePlan());
        saveLocal.setOnClickListener(v -> { if(saveLocalPlan(true)) showPlanLibrary(); });
        createGroup.setOnClickListener(v -> showGroupNameDialog(null, ""));
        closeEditor.setOnClickListener(v->showPlanLibrary());
        intervalPlan.setOnClickListener(v -> applyTemplate(false));
        fartlekPlan.setOnClickListener(v -> applyTemplate(true));
        showSection(0);
        renderSavedPlans();
    }

    private ScrollView wrapContent(LinearLayout cardBody){
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),dp(16));
        body.addView(cardBody,new LinearLayout.LayoutParams(-1,-2));
        scroll.addView(body,new FrameLayout.LayoutParams(-1,-2));
        return scroll;
    }

    private void showSection(int section){
        currentSection=section;
        planScroll.setVisibility(section==0?View.VISIBLE:View.GONE);
        controlScroll.setVisibility(section==1?View.VISIBLE:View.GONE);
        historyScroll.setVisibility(section==2?View.VISIBLE:View.GONE);
        sleepScroll.setVisibility(section==3?View.VISIBLE:View.GONE);
        if(section==0)showPlanLibrary();
        for(int i=0;i<navItems.length;i++){boolean selected=i==section;
            navItems[i].setBackground(rounded(Color.TRANSPARENT,16));
            navItems[i].setTextColor(selected?Palette.MOVE:Palette.TEXT_DIM);
            navItems[i].setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);}
        liveHandler.removeCallbacks(livePoller);
        if(section==1)liveHandler.post(livePoller);
    }

    private void updateStatusDot(WatchConnectionManager.Snapshot snapshot){
        if(statusDot==null)return;
        int color;
        switch(snapshot.state){
            case CONNECTED_BLE_LAN:case CONNECTED_BLE:color=Palette.GREEN;break;
            case CONNECTED_LAN:color=Palette.STAND;break;
            case BLUETOOTH_DISABLED:case UNPAIRED:color=Palette.RED;break;
            case SCANNING:case CONNECTING_BLE:case DISCOVERING_SERVICES:case SUBSCRIBING:case AUTHENTICATING:case BACKOFF:color=Palette.ORANGE;break;
            default:color=Palette.TEXT_DIM;
        }
        statusDot.setBackground(rounded(color,10));
    }

    private void toggleSetup(boolean show){
        if(setupPanel==null)return;
        setupPanel.setVisibility(show?View.VISIBLE:View.GONE);
        if(setupChevron!=null)setupChevron.setText(show?"收起 ▴":"连接设置 ▾");
    }

    private void rebuildLiveActions(String state){
        if(liveActions==null||state.equals(liveActionsState))return;
        liveActionsState=state;
        liveActions.removeAllViews();
        if("RUNNING".equals(state)){addLiveAction("暂停","pause",Palette.CARD_HIGH,Palette.TEXT);addLiveAction("结束","stop",Palette.FILL_DANGER,Palette.RED);}
        else if("PAUSED".equals(state)){addLiveAction("继续","resume",Palette.EXERCISE,Palette.TEXT);addLiveAction("结束","stop",Palette.FILL_DANGER,Palette.RED);}
        else if("PREPARING".equals(state)){addLiveAction("结束准备","stop",Palette.CARD_HIGH,Palette.TEXT);}
        else if("unavailable".equals(state)){
            Button connect=button("打开连接设置",Palette.CARD_HIGH,Palette.TEXT);
            connect.setOnClickListener(v->toggleSetup(true));
            liveActions.addView(connect,weight());
        }
        else {addLiveAction("开始训练","start",Palette.EXERCISE,Palette.TEXT);}
    }

    private void addLiveAction(String label,String action,int bg,int fg){
        Button item=button(label,bg,fg);item.setOnClickListener(v->control(action));liveActions.addView(item,weight());
    }

    private void pollLiveStatus(){
        if(watchConnection==null||livePollInFlight)return;
        livePollInFlight=true;
        io.execute(()->{
            try{
                JSONObject status=new JSONObject(watchConnection.requestBlocking("GET","/v1/status","",8_000L));
                JSONObject workout=status.optJSONObject("workout");
                runOnUiThread(()->renderLiveStatus(workout,null));
            }catch(Exception error){
                runOnUiThread(()->renderLiveStatus(null,error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));
            }finally{livePollInFlight=false;}
        });
    }

    private void renderLiveStatus(JSONObject workout,String error){
        if(liveState==null)return;
        if(workout==null){
            rebuildLiveActions(error!=null?"unavailable":"idle");
            if(liveRing!=null)liveRing.set(0f,Palette.MOVE,Palette.ORANGE);
            liveState.setText(error!=null?"无法读取手表状态":"未在训练");
            liveState.setTextColor(Palette.TEXT_DIM);
            liveTime.setText("--:--");
            liveMeta.setText(error!=null?"请检查连接后重试":"在手表上开始，或点击下方按钮远程开始当前安排");
            return;
        }
        String state=workout.optString("state");
        rebuildLiveActions(state);
        boolean paused="PAUSED".equals(state);
        liveState.setText("PREPARING".equals(state)?"准备中":paused?"已暂停":("COMPLETED".equals(workout.optString("planState"))?"自由记录中":"训练中"));
        liveState.setTextColor(paused?Palette.YELLOW:Palette.GREEN);
        liveTime.setText(PhoneFormat.duration(workout.optLong("activeDurationMs")));
        StringBuilder meta=new StringBuilder(PhoneFormat.distance(workout.optDouble("distanceMeters",0)));
        long avgPace=workout.optLong("avgPaceSecondsPerKm");
        if(avgPace>0)meta.append(" · ").append(PhoneFormat.paceSeconds(avgPace));
        int heart=workout.optInt("heartRate");
        if(heart>0)meta.append(" · ").append(heart).append(" bpm");
        int stageCount=workout.optInt("stageCount");
        if(stageCount>0)meta.append(" · ").append(workout.optString("stageName")).append(" ").append(workout.optInt("stageNumber")).append("/").append(stageCount);
        liveMeta.setText(meta.toString());
        if(liveRing!=null){
            boolean planDone="COMPLETED".equals(workout.optString("planState"));
            float fraction=planDone?1f:stageCount>0?(workout.optInt("stageNumber")-1f)/stageCount:0f;
            liveRing.set(fraction,Palette.MOVE,Palette.ORANGE);
        }
    }

    private void discoverWatch() {
        stopDiscovery();
        if(watchConnection==null||watchConnection.snapshot().primaryTransport==null)connection.setText("正在寻找手表…");
        WifiManager wifi = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("watchintervals-discovery");
            multicastLock.setReferenceCounted(false); multicastLock.acquire();
        }
        nsdManager = (NsdManager)getSystemService(NSD_SERVICE);
        discoveryListener = new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String type) {}
            public void onStartDiscoveryFailed(String type, int code) { stopDiscovery(); }
            public void onStopDiscoveryFailed(String type, int code) { releaseMulticast(); }
            public void onDiscoveryStopped(String type) { releaseMulticast(); }
            public void onServiceLost(NsdServiceInfo info) {}
            public void onServiceFound(NsdServiceInfo info) {
                if (!info.getServiceType().startsWith("_watchintervals._tcp") || resolving) return;
                resolving = true;
                nsdManager.resolveService(info, new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo service, int code) { resolving=false; }
                    public void onServiceResolved(NsdServiceInfo service) {
        String address = service.getHost() == null ? "" : service.getHost().getHostAddress();
                        String credential = watchConnection.identity().lanCredential();if(credential.isEmpty())credential=code.getText().toString().trim();final String pairing=credential;
                        if (address.isEmpty()) { resolving=false; return; }
                        // The six-digit rule only applies to a first-time pairing code typed by the
                        // user. A paired phone holds a long-term LAN credential whose length is
                        // never 6, and checking it here told paired users to re-enter a code.
                        if (!watchConnection.identity().isPaired() && pairing.length() != 6) {
                            resolving=false;
                            runOnUiThread(() -> { host.setText(address); connection.setText("已发现手表，请输入配对码"); });
                            stopDiscovery(); return;
                        }
                        // A LAN can contain stale/debug advertisements. Verify the pairing API before replacing the saved host.
                        io.execute(() -> {
                            try {
                                JSONObject status = new JSONObject(new WatchClient(address, pairing).get("/v1/status"));
                                String discoveredId=status.optString("deviceId");String expectedId=getSharedPreferences("connection",MODE_PRIVATE).getString("watch_device_id","");
                                if(!expectedId.isEmpty()&&!expectedId.equals(discoveredId)){resolving=false;return;}
                                runOnUiThread(() -> {
                                    host.setText(address);
                                    getSharedPreferences("connection",MODE_PRIVATE).edit().putString("watch_device_id",discoveredId).apply();
                                    connection.setText("已发现 " + status.optString("device") + " · LAN 加速可用");
                                    stopDiscovery();
                                    syncAll();
                                });
                            } catch (Exception ignored) { resolving=false; }
                        });
                    }
                });
            }
        };
        try { nsdManager.discoverServices("_watchintervals._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener); }
        catch (Exception error) { stopDiscovery(); }
    }

    private void stopDiscovery() {
        if (nsdManager != null && discoveryListener != null) {
            try { nsdManager.stopServiceDiscovery(discoveryListener); } catch (Exception ignored) { releaseMulticast(); }
        } else releaseMulticast();
        discoveryListener = null;
    }

    private void releaseMulticast() {
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        multicastLock = null;
    }

    private void syncAll() {
        getSharedPreferences("connection", MODE_PRIVATE).edit().putString("host", host.getText().toString().trim()).putString("code", code.getText().toString().trim()).apply();
        runIo(() -> {
            String pairing=code.getText().toString().trim();if(!watchConnection.identity().isPaired()&&pairing.length()!=6)throw new IllegalArgumentException("请输入手表上的 6 位配对码");
            watchConnection.configurePairing(pairing);watchConnection.configureLan(host.getText().toString().trim(),pairing);
            // BLE is preferred but not required: with a verified LAN transport the request layer
            // routes around a failed connect on its own. Blocking the whole sync on connect()
            // was why history stayed on "连接后读取" while the MCP chain over LAN worked fine.
            try{watchConnection.connect().get(25,java.util.concurrent.TimeUnit.SECONDS);}
            catch(Exception bleError){
                if(!watchConnection.snapshot().lanAvailable)
                    throw new IllegalStateException("蓝牙连接失败，且局域网不可达；请靠近手表或连接同一 Wi-Fi",bleError);
            }
            JSONObject status = new JSONObject(watchConnection.requestBlocking("GET","/v1/status","",20_000L));
            String expected=getSharedPreferences("connection",MODE_PRIVATE).getString("watch_device_id","");String actual=status.optString("deviceId");if(!expected.isEmpty()&&!expected.equals(actual))throw new IllegalStateException("发现的设备身份与已配对手表不一致");
            if(expected.isEmpty()&&!actual.isEmpty())getSharedPreferences("connection",MODE_PRIVATE).edit().putString("watch_device_id",actual).apply();
            JSONObject library = PhonePlanLibrary.load(this); if(PhoneSyncOutbox.size(this)==0)PhoneSyncOutbox.enqueueLibrary(this,library,"upsert","library");PhoneSyncOutbox.drain(this,watchConnection);
            JSONObject plan = new JSONObject(watchConnection.requestBlocking("GET","/v1/plan/profile","",20_000L)); JSONArray history = new JSONArray(watchConnection.requestBlocking("GET","/v1/history","",20_000L));
            runOnUiThread(() -> { connection.setText("已连接 " + status.optString("device") + " · " + transportLabel(watchConnection.snapshot())); showPlan(plan); showHistory(history); ensureLocationRelay(); });
        });
    }

    private void ensureBluetoothConnection(){
        if(android.os.Build.VERSION.SDK_INT>=31&&(checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)!=android.content.pm.PackageManager.PERMISSION_GRANTED||checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED)){
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_SCAN,android.Manifest.permission.BLUETOOTH_CONNECT},REQUEST_BLUETOOTH);return;
        }
        startForegroundService(new Intent(this,PhoneCompanionService.class));
    }

    private String connectionLabel(WatchConnectionManager.Snapshot value){
        switch(value.state){case CONNECTED_BLE_LAN:return "蓝牙连接 · LAN 加速";case CONNECTED_BLE:return "蓝牙已连接";case CONNECTED_LAN:return "LAN 已连接 · 正在恢复蓝牙";case SCANNING:return "正在通过蓝牙寻找手表";case CONNECTING_BLE:case DISCOVERING_SERVICES:case SUBSCRIBING:case AUTHENTICATING:return "正在建立蓝牙连接";case BLUETOOTH_DISABLED:return "蓝牙已关闭";case UNPAIRED:return "请输入手表上的配对码";case BACKOFF:return "手表不在附近，稍后自动重连";default:return "尚未连接";}
    }
    private String transportLabel(WatchConnectionManager.Snapshot value){return value.primaryTransport==null?"连接可用":value.primaryTransport==com.poyi.watchintervals.phone.connection.TransportType.BLE?(value.lanAvailable?"蓝牙 · LAN 加速":"蓝牙"):"LAN";}

    private void ensureLocationRelay() {
        // Reached from async sync callbacks. A location-type FGS may only start while the app is
        // foreground (Android 14+); a late callback after onPause used to crash the process.
        if (!foreground) return;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_RELAY);
            return;
        }
        try { startForegroundService(new android.content.Intent(this, PhoneLocationRelayService.class)); }
        catch (RuntimeException ignored) { /* Next successful sync retries. */ }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION_RELAY && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) ensureLocationRelay();
        if(requestCode==REQUEST_BLUETOOTH&&checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)==android.content.pm.PackageManager.PERMISSION_GRANTED&&checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)==android.content.pm.PackageManager.PERMISSION_GRANTED)ensureBluetoothConnection();
    }

    private void showPlan(JSONObject profile) {
        if(currentWatchPlan!=null)currentWatchPlan.setText("手表当前安排："+profile.optString("name")+" · "+profile.optString("group"));
        planName.setText(profile.optString("name")); planGroup.setText(profile.optString("group")); planRequirement.setText(profile.optString("requirement"));
        JSONArray array = profile.optJSONArray("stages"); if (array == null) array = new JSONArray();
        stages.clear(); for (int index=0; index<array.length(); index++) { JSONObject item=array.optJSONObject(index); if(item!=null) stages.add(item); } renderPlan();
    }

    private void renderPlan() {
        planList.removeAllViews();
        for (int index=0; index<stages.size(); index++) {
            final int position=index; JSONObject stage=stages.get(index);
            LinearLayout stageCard=cardHigh(); stageCard.setPadding(dp(14),dp(10),dp(14),dp(10));
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            String kind=stage.optString("kind"), unit=stage.optString("unit"); int target=stage.optInt("target");
            TextView label=text((position+1)+"  "+kindName(kind),17,true,Palette.TEXT); row.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));
            EditText value=input(""); value.setText(String.valueOf(target)); value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); row.addView(value,new LinearLayout.LayoutParams(dp(90),dp(48)));
            Button unitButton=button("DISTANCE".equals(unit)?"距离 · 米":"时间 · 秒",Palette.CARD_HIGH,Palette.TEXT);unitButton.setTextSize(13); row.addView(unitButton,new LinearLayout.LayoutParams(dp(105),dp(48)));
            stageCard.addView(row);
            LinearLayout actions=new LinearLayout(this);
            Button up=button("上移",Palette.CARD_HIGH,Palette.TEXT);
            Button down=button("下移",Palette.CARD_HIGH,Palette.TEXT);
            Button delete=button("删除阶段",Palette.FILL_DANGER,Palette.RED);
            up.setEnabled(position>0); down.setEnabled(position<stages.size()-1);
            actions.addView(up,weight());actions.addView(down,weight());actions.addView(delete,weight());
            stageCard.addView(actions);
            value.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){try{if(s.length()>0)stage.put("target",Math.max(1,Integer.parseInt(s.toString())));}catch(Exception ignored){}}
                public void afterTextChanged(android.text.Editable value){}
            });
            unitButton.setOnClickListener(v->{ try{ stage.put("unit","DISTANCE".equals(stage.optString("unit"))?"TIME":"DISTANCE"); }catch(Exception ignored){} renderPlan(); });
            up.setOnClickListener(v->{if(position>0){JSONObject moved=stages.remove(position);stages.add(position-1,moved);renderPlan();}});
            down.setOnClickListener(v->{if(position<stages.size()-1){JSONObject moved=stages.remove(position);stages.add(position+1,moved);renderPlan();}});
            delete.setOnClickListener(v->{ stages.remove(position); renderPlan(); });
            LinearLayout.LayoutParams params=margin();params.topMargin=dp(8);planList.addView(stageCard,params);
        }
    }

    private void addStage(String kind,String unit,int target){ try{ stages.add(new JSONObject().put("kind",kind).put("unit",unit).put("target",target)); }catch(Exception ignored){} renderPlan(); }
    private void savePlan(){ if(!saveLocalPlan(false)) return; showPlanLibrary(); try {
        PhonePlanLibrary.select(this, editingPlanId); JSONObject library=PhonePlanLibrary.load(this);
        queueAndSyncLibrary(library,"完整计划库已同步到手表");
    } catch(Exception error) { connection.setText("计划格式错误"); } }
    private void applyTemplate(boolean fartlek){ stages.clear(); try {
        if(fartlek){ if(planName.getText().toString().trim().isEmpty())planName.setText("变速跑安排"); planRequirement.setText("快跑 2 分钟，快走恢复 1 分钟，连续完成 6 组。"); for(int i=0;i<6;i++){stages.add(new JSONObject().put("kind","RUN").put("unit","TIME").put("target",120));stages.add(new JSONObject().put("kind","WALK").put("unit","TIME").put("target",60));} }
        else { if(planName.getText().toString().trim().isEmpty())planName.setText("距离间歇安排"); planRequirement.setText("跑步 1 千米，随后快走恢复 200 米；按阶段顺序完成。"); stages.add(new JSONObject().put("kind","RUN").put("unit","DISTANCE").put("target",1000)); stages.add(new JSONObject().put("kind","WALK").put("unit","DISTANCE").put("target",200)); }
    }catch(Exception ignored){} renderPlan(); }

    private void newPlan(){
        editingPlanId=""; stages.clear(); planName.setText(""); planGroup.setText(""); planRequirement.setText("");
        addStage("RUN","DISTANCE",1000); showPlanEditor(); planName.requestFocus();
        Toast.makeText(this,"已新建空白计划，请填写名称和分组",Toast.LENGTH_SHORT).show();
    }

    private void newPlanInGroup(JSONObject group){
        String groupId=group.optString("id"),groupName=group.optString("name");
        JSONObject library=PhonePlanLibrary.load(this);JSONArray plans=library.optJSONArray("plans");int day=1;
        if(plans!=null)for(int i=0;i<plans.length();i++){JSONObject item=plans.optJSONObject(i);if(item!=null&&groupId.equals(item.optString("groupId")))day++;}
        editingPlanId="";stages.clear();planName.setText("第"+day+"天");planGroup.setText(groupName);
        planRequirement.setText("设置当天独立的跑步、快走与恢复内容。");
        addStage("RUN","TIME",1200);showPlanEditor();planName.requestFocus();
    }

    private void showGroupNameDialog(JSONObject group,String initialName){
        EditText input=input("例如：30日减肥计划");input.setText(initialName);input.setSelectAllOnFocus(true);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
                .setTitle(group==null?"新建训练计划":"修改计划名称")
                .setView(input)
                .setNegativeButton("取消",null)
                .setPositiveButton("保存",null)
                .create();
        dialog.setOnShowListener(ignored->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("请输入计划名称");return;}
            try{
                if(group==null)PhonePlanLibrary.createGroup(this,name);else PhonePlanLibrary.renameGroup(this,group.optString("id"),name);
                dialog.dismiss();renderSavedPlans();syncLibraryQuietly(group==null?"训练计划已创建":"计划名称已更新");
            }catch(Exception error){input.setError(error.getMessage());}
        }));
        dialog.show();
    }

    private void confirmDeleteGroup(JSONObject group){
        new android.app.AlertDialog.Builder(this)
                .setTitle("删除“"+group.optString("name")+"”？")
                .setMessage("该计划中的安排会移动到“我的计划”，内容仍会保留。")
                .setNegativeButton("取消",null)
                .setPositiveButton("删除",(dialog,which)->{
                    try{PhonePlanLibrary.deleteGroup(this,group.optString("id"));renderSavedPlans();syncLibraryQuietly("训练计划已删除");}
                    catch(Exception error){connection.setText("删除计划失败："+error.getMessage());}
                }).show();
    }

    private void syncLibraryQuietly(String successText){
        JSONObject library=PhonePlanLibrary.load(this);
        queueAndSyncLibrary(library,successText);
    }

    private void queueAndSyncLibrary(JSONObject library,String successText){
        runIo(()->{
            try {
                PhoneSyncOutbox.enqueueLibrary(this,library,"upsert","library");
                JSONObject sync=PhoneSyncOutbox.drain(this,watchConnection);
                runOnUiThread(()->connection.setText("synced".equals(sync.optString("state"))?successText:"计划已保存，等待手表连接"));
            } catch(Exception error) {
                runOnUiThread(()->connection.setText("计划已保存，等待手表连接"));
            }
        });
    }

    private JSONArray copyStages(){
        JSONArray array=new JSONArray();
        for(JSONObject stage:stages) try{ array.put(new JSONObject(stage.toString())); }catch(Exception ignored){}
        return array;
    }

    private JSONArray loadSavedPlans(){
        try{return PhonePlanLibrary.load(this).getJSONArray("plans");}
        catch(Exception ignored){return new JSONArray();}
    }

    private void persistSavedPlans(JSONArray plans){
        try{JSONObject library=PhonePlanLibrary.load(this);library.put("plans",plans).put("revision",System.currentTimeMillis());PhonePlanLibrary.save(this,library);}catch(Exception ignored){}
    }

    private boolean saveLocalPlan(boolean announce){
        String name=planName.getText().toString().trim(), group=planGroup.getText().toString().trim();
        if(name.isEmpty()){planName.setError("请填写安排名称");planName.requestFocus();return false;}
        if(group.isEmpty()){planGroup.setError("请选择所属训练计划");planGroup.requestFocus();return false;}
        if(stages.isEmpty()){Toast.makeText(this,"至少添加一项训练内容",Toast.LENGTH_SHORT).show();return false;}
        try{
            if(editingPlanId.isEmpty()) editingPlanId=UUID.randomUUID().toString();
            JSONObject item=new JSONObject().put("id",editingPlanId).put("name",name).put("group",group)
                    .put("requirement",planRequirement.getText().toString().trim()).put("stages",copyStages());
            PhonePlanLibrary.upsert(this,item); renderSavedPlans();
            if(announce)Toast.makeText(this,"安排已保存",Toast.LENGTH_SHORT).show(); return true;
        }catch(Exception error){Toast.makeText(this,"保存失败："+error.getMessage(),Toast.LENGTH_LONG).show();return false;}
    }

    private void renderSavedPlans(){
        if(savedPlanList==null)return; savedPlanList.removeAllViews(); JSONObject library=PhonePlanLibrary.load(this);JSONArray plans=library.optJSONArray("plans");
        if(plans==null)plans=new JSONArray();
        if(plans.length()==0)savedPlanList.addView(text("还没有安排。先新建训练计划，再点击“添加安排”。",14,false,Palette.TEXT_DIM));
        JSONArray groups=library.optJSONArray("groups");java.util.HashSet<String> rendered=new java.util.HashSet<>();
        if(groups!=null)for(int groupIndex=0;groupIndex<groups.length();groupIndex++){
            JSONObject group=groups.optJSONObject(groupIndex);if(group==null)continue;String groupId=group.optString("id");
            int arrangementCount=0;for(int i=0;i<plans.length();i++){JSONObject item=plans.optJSONObject(i);if(item!=null&&groupId.equals(item.optString("groupId")))arrangementCount++;}
            LinearLayout planBlock=card();planBlock.setPadding(dp(16),dp(14),dp(16),dp(14));planBlock.setBackground(rounded(Palette.CARD,22));
            LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView header=text(group.optString("name"),19,true,Palette.TEXT);header.setSingleLine(false);titleRow.addView(header,new LinearLayout.LayoutParams(0,-2,1));
            TextView count=text(arrangementCount+" 个安排",12,false,Palette.TEXT_DIM);count.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);titleRow.addView(count,new LinearLayout.LayoutParams(dp(82),dp(38)));
            planBlock.addView(titleRow);
            LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
            Button addDay=button("＋ 添加安排",Palette.FILL_RUN,Palette.EXERCISE);actions.addView(addDay,new LinearLayout.LayoutParams(0,dp(44),1));
            Button rename=button("编辑",Palette.CARD_HIGH,Palette.TEXT_DIM);actions.addView(rename,new LinearLayout.LayoutParams(dp(68),dp(44)));
            Button delete=button("删除",Color.TRANSPARENT,Palette.RED);actions.addView(delete,new LinearLayout.LayoutParams(dp(62),dp(44)));
            planBlock.addView(actions,new LinearLayout.LayoutParams(-1,dp(54)));
            addDay.setOnClickListener(v->newPlanInGroup(group));rename.setOnClickListener(v->showGroupNameDialog(group,group.optString("name")));delete.setOnClickListener(v->confirmDeleteGroup(group));
            if(arrangementCount==0){TextView empty=text("暂无安排 · 点击上方按钮添加第1天",13,false,Palette.TEXT_DIM);empty.setGravity(Gravity.CENTER);empty.setBackground(rounded(Palette.CARD_HIGH,14));planBlock.addView(empty,new LinearLayout.LayoutParams(-1,dp(48)));}
            for(int i=0;i<plans.length();i++){JSONObject item=plans.optJSONObject(i);if(item!=null&&groupId.equals(item.optString("groupId"))){addSavedPlanRow(planBlock,library,item);rendered.add(item.optString("id"));}}
            LinearLayout.LayoutParams blockParams=margin();blockParams.topMargin=dp(10);savedPlanList.addView(planBlock,blockParams);
        }
        for(int i=0;i<plans.length();i++){JSONObject item=plans.optJSONObject(i);if(item!=null&&!rendered.contains(item.optString("id")))addSavedPlanRow(savedPlanList,library,item);}
    }

    private void addSavedPlanRow(LinearLayout parent,JSONObject library,JSONObject item){
        String id=item.optString("id");LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(5),0,0);
        JSONArray savedStages=item.optJSONArray("stages");int count=savedStages==null?0:savedStages.length();boolean selected=id.equals(library.optString("selectedPlanId"));
        String requirement=item.optString("requirement").trim();String summary=count+" 项训练内容"+(requirement.isEmpty()?"":" · "+requirement);
        LinearLayout open=new LinearLayout(this);open.setOrientation(LinearLayout.VERTICAL);open.setPadding(dp(14),dp(9),dp(12),dp(9));open.setBackground(selected?roundedStroke(Palette.FILL_SELECTED,16,Palette.EXERCISE,1):rounded(Palette.CARD_HIGH,16));
        TextView name=text((selected?"✓  ":"")+item.optString("name"),16,true,Palette.TEXT);open.addView(name,new LinearLayout.LayoutParams(-1,dp(30)));
        TextView detail=text(summary,12,false,Palette.TEXT_DIM);detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);open.addView(detail,new LinearLayout.LayoutParams(-1,dp(26)));
        row.addView(open,new LinearLayout.LayoutParams(0,dp(76),1));
        Button remove=button("删除",Color.TRANSPARENT,Palette.RED);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(58),dp(76));rp.leftMargin=dp(4);row.addView(remove,rp);
        open.setOnClickListener(v->openSavedPlan(item));remove.setOnClickListener(v->deleteSavedPlan(id));parent.addView(row);
    }

    private void openSavedPlan(JSONObject item){
        editingPlanId=item.optString("id");planName.setText(item.optString("name"));planGroup.setText(PhonePlanLibrary.groupName(PhonePlanLibrary.load(this),item.optString("groupId")));planRequirement.setText(item.optString("requirement"));
        JSONArray array=item.optJSONArray("stages");stages.clear();if(array!=null)for(int i=0;i<array.length();i++)try{stages.add(new JSONObject(array.getJSONObject(i).toString()));}catch(Exception ignored){}
        renderPlan();showPlanEditor();Toast.makeText(this,"已打开“"+item.optString("name")+"”",Toast.LENGTH_SHORT).show();
    }

    private void showPlanLibrary(){
        if(planLibraryPanel!=null)planLibraryPanel.setVisibility(View.VISIBLE);if(planEditorPanel!=null)planEditorPanel.setVisibility(View.GONE);renderSavedPlans();
    }

    private void showPlanEditor(){
        if(planLibraryPanel!=null)planLibraryPanel.setVisibility(View.GONE);if(planEditorPanel!=null)planEditorPanel.setVisibility(View.VISIBLE);
    }

    private void deleteSavedPlan(String id){
        try{PhonePlanLibrary.deletePlan(this,id);if(id.equals(editingPlanId))editingPlanId="";renderSavedPlans();syncLibraryQuietly("安排已删除");}catch(Exception error){connection.setText("删除计划失败："+error.getMessage());}
    }
    private void control(String action){ runIo(()->{ try {
        String expected="pause".equals(action)?"RUNNING":"resume".equals(action)?"PAUSED":"start".equals(action)?"STOPPED":"";
        JSONObject command=new JSONObject().put("commandId",java.util.UUID.randomUUID().toString()).put("expiresAt",System.currentTimeMillis()+30_000L);
        if(!expected.isEmpty())command.put("expectedState",expected);
        watchConnection.requestBlocking("POST","/v1/control/"+action,command.toString(),30_000L); runOnUiThread(()->connection.setText("训练操作已发送："+action));
    } catch(Exception error){runOnUiThread(()->connection.setText("训练操作失败："+error.getMessage()));} }); }

    private void showHistory(JSONArray array){
        historyList.removeAllViews(); historySummary.setText(array.length()+" 次训练 · 点击查看地图轨迹与完整数据");
        for(int i=0;i<array.length();i++){
            JSONObject record=array.optJSONObject(i); if(record==null)continue;
            LinearLayout row=cardHigh(); row.setPadding(dp(16),dp(14),dp(16),dp(14));
            TextView date=text(new SimpleDateFormat("MM月dd日  HH:mm",Locale.CHINA).format(new Date(record.optLong("startedAt"))),15,true,Palette.TEXT_DIM);
            row.addView(date);
            long duration=record.optLong("durationMs"); double meters=record.optDouble("distanceMeters");
            TextView primary=text(PhoneFormat.distance(meters)+"  ·  "+PhoneFormat.duration(duration)+"  ·  "+PhoneFormat.pace(duration,meters),17,true,Palette.TEXT);
            row.addView(primary);
            TextView secondary=text(record.optInt("steps")+" 步  ·  平均心率 "+(record.optInt("averageHeartRate")>0?record.optInt("averageHeartRate")+" bpm":"--")+"  ·  "+record.optInt("routePointCount")+" 个轨迹点",13,false,Palette.TEXT_DIM);
            row.addView(secondary);
            row.setOnClickListener(v->runIo(()->{try{String detail=watchConnection.requestBlocking("GET","/v1/history/"+android.net.Uri.encode(record.optString("id")),"",20_000L);runOnUiThread(()->startActivity(new Intent(this,HistoryDetailActivity.class).putExtra("record",detail)));}catch(Exception error){runOnUiThread(()->connection.setText("读取训练详情失败："+error.getMessage()));}}));
            LinearLayout.LayoutParams params=margin(); params.setMargins(0,dp(10),0,0); historyList.addView(row,params);
        }
    }

    private void loadSleep(){
        sleepSummary.setText("正在读取手表系统睡眠…");
        io.execute(()->{try{
            JSONObject result=new JSONObject(watchConnection.requestBlocking("GET","/v1/sleep?days=14","",20_000L));
            runOnUiThread(()->showSleep(result));
        }catch(Exception error){runOnUiThread(()->sleepSummary.setText("读取失败："+error.getMessage()));}});
    }

    private void showSleep(JSONObject result){
        sleepList.removeAllViews();
        String state=result.optString("state");
        if("permission_required".equals(state)){sleepSummary.setText("请在手表端打开步序并允许读取睡眠");return;}
        if(!"ready".equals(state)){sleepSummary.setText("系统睡眠暂不可用："+result.optString("error","未知错误"));return;}
        JSONArray records=result.optJSONArray("records");if(records==null)records=new JSONArray();
        sleepSummary.setText("最近 14 天 · "+records.length()+" 条系统记录");
        for(int i=0;i<records.length();i++){
            JSONObject record=records.optJSONObject(i);if(record==null)continue;
            JSONArray sessions=record.optJSONArray("sessions");
            long start=record.optLong("timestamp");int deep=0,rem=0,stageCount=0,sessionCount=sessions==null?0:sessions.length();
            for(int j=0;j<sessionCount;j++){
                JSONObject session=sessions.optJSONObject(j);if(session==null)continue;
                long sessionStart=session.optLong("startTime");if(sessionStart>0&&(start<=0||sessionStart<start))start=sessionStart;
                deep+=session.optInt("deepDurationMinutes");rem+=session.optInt("remDurationMinutes");
                JSONArray stages=session.optJSONArray("stages");stageCount+=stages==null?0:stages.length();
            }
            int duration=record.optInt("totalDurationMinutes");
            int score=record.optInt("sleepScore"),spo2=record.optInt("spo2AveragePercent");
            LinearLayout row=cardHigh();row.setPadding(dp(16),dp(12),dp(16),dp(12));
            row.addView(text(new SimpleDateFormat("MM月dd日  HH:mm",Locale.CHINA).format(new Date(start)),15,true,Palette.TEXT_DIM));
            row.addView(text(PhoneFormat.minutesHuman(duration)+" · 评分 "+(score>0?score:"--")+" · 平均血氧 "+(spo2>0?spo2+"%":"--"),16,true,Palette.TEXT));
            String sessionLabel=sessionCount>1?" · "+sessionCount+" 段睡眠":"";
            row.addView(text("深睡 "+PhoneFormat.minutesHuman(deep)+" · REM "+PhoneFormat.minutesHuman(rem)+" · "+stageCount+" 个阶段"+sessionLabel,13,false,Palette.TEXT_DIM));
            LinearLayout.LayoutParams params=margin();params.topMargin=dp(10);sleepList.addView(row,params);
        }
    }
    private void runIo(Throwing action){ connection.setText("正在同步…"); io.execute(()->{ try{action.run();}catch(Exception error){
        // ExecutionException and friends often carry a null message; surface the cause instead
        // of the literal text "null".
        Throwable cause=error; while(cause.getCause()!=null&&cause.getMessage()==null)cause=cause.getCause();
        String reason=cause.getMessage()!=null?cause.getMessage():cause.getClass().getSimpleName();
        runOnUiThread(()->connection.setText("连接失败："+reason));
    } }); }
    interface Throwing{void run()throws Exception;}
    private String kindName(String kind){return "WALK".equals(kind)?"快走":"REST".equals(kind)?"休息":"跑步";}
    private LinearLayout section(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout card(){LinearLayout v=section();v.setPadding(dp(18),dp(16),dp(18),dp(16));v.setBackground(rounded(Palette.CARD,22));return v;}
    private LinearLayout compactCard(){LinearLayout v=section();v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(rounded(Palette.CARD,20));return v;}
    private LinearLayout cardHigh(){LinearLayout v=card();v.setBackground(rounded(Palette.CARD_HIGH,18));return v;}
    private LinearLayout.LayoutParams margin(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(16);return p;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(3),dp(6),dp(3),dp(6));return p;}
    private TextView text(String s,int sp,boolean bold,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);v.setTypeface(null,bold?Typeface.BOLD:Typeface.NORMAL);v.setPadding(0,dp(5),0,dp(5));return v;}
    private LinearLayout pageTitle(String title,String subtitle){LinearLayout box=section();TextView heading=text(title,28,true,Palette.TEXT);heading.setLetterSpacing(.015f);box.addView(heading,new LinearLayout.LayoutParams(-1,dp(44)));box.addView(text(subtitle,14,false,Palette.TEXT_DIM),new LinearLayout.LayoutParams(-1,dp(30)));return box;}
    private TextView navItem(String symbol,String label){TextView v=text(symbol+"\n"+label,12,false,Palette.TEXT_DIM);v.setGravity(Gravity.CENTER);v.setLineSpacing(dp(1),1f);v.setClickable(true);v.setFocusable(true);v.setPadding(0,dp(4),0,dp(3));return v;}
    private EditText input(String hint){EditText v=new EditText(this);v.setHint(hint);v.setTextSize(16);v.setSingleLine(true);v.setTextColor(Palette.TEXT);v.setHintTextColor(Palette.HINT);v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(rounded(Palette.CARD_HIGH,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(7);v.setLayoutParams(p);return v;}
    private Button button(String s,int bg,int fg){Button v=new Button(this);v.setText(s);v.setTextSize(15);v.setTextColor(fg);v.setBackground(rounded(bg,16));v.setAllCaps(false);v.setStateListAnimator(null);return v;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    private GradientDrawable roundedStroke(int color,int radius,int strokeColor,int strokeWidth){GradientDrawable shape=rounded(color,radius);shape.setStroke(dp(strokeWidth),strokeColor);return shape;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){stopDiscovery();if(watchConnection!=null)watchConnection.removeObserver(connectionObserver);io.shutdownNow();super.onDestroy();}
}

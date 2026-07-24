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

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION_RELAY = 44;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> stages = new ArrayList<>();
    private EditText host, code, planName, planGroup, planRequirement;
    private TextView connection, historySummary, currentWatchPlan;
    private LinearLayout planList, historyList, savedPlanList, planCard, controlCard, historyCard, planLibraryPanel, planEditorPanel;
    private String editingPlanId = "";
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private WifiManager.MulticastLock multicastLock;
    private boolean resolving;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startForegroundService(new Intent(this, PhonePlanBridgeService.class));
        buildUi();
        android.content.SharedPreferences preferences = getSharedPreferences("connection", MODE_PRIVATE);
        host.setText(preferences.getString("host", "192.168.1.44"));
        code.setText(preferences.getString("code", ""));
        discoverWatch();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        int statusBarResource=getResources().getIdentifier("status_bar_height","dimen","android");
        int topInset=(statusBarResource>0?getResources().getDimensionPixelSize(statusBarResource):0)+dp(10);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), topInset, dp(20), dp(28)); root.setBackgroundColor(Color.rgb(244,245,241));
        root.addView(text("步序", 30, true, Color.rgb(22,24,22)));
        root.addView(text("把长期目标拆成每天都能完成的安排", 14, false, Color.DKGRAY));

        LinearLayout connectCard = card();
        connectCard.addView(text("连接手表", 20, true, Color.BLACK));
        host = input("手表 IP"); code = input("手表上的 6 位配对码"); code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        connectCard.addView(host); connectCard.addView(code);
        LinearLayout connectActions = new LinearLayout(this);
        Button discover = button("自动发现", Color.rgb(232,234,229), Color.BLACK);
        Button connect = button("连接并同步", Color.rgb(124,203,43), Color.BLACK);
        connectActions.addView(discover, weight()); connectActions.addView(connect, weight()); connectCard.addView(connectActions);
        connection = text("尚未连接", 14, false, Color.DKGRAY); connectCard.addView(connection);
        root.addView(connectCard, margin());

        LinearLayout tabs=new LinearLayout(this);
        Button planTab=button("计划",Color.rgb(28,31,29),Color.WHITE), controlTab=button("训练",Color.rgb(232,234,229),Color.BLACK), historyTab=button("历史",Color.rgb(232,234,229),Color.BLACK);
        tabs.addView(planTab,weight());tabs.addView(controlTab,weight());tabs.addView(historyTab,weight());root.addView(tabs,margin());

        planCard = card();
        planCard.addView(text("训练计划", 24, true, Color.BLACK));
        planLibraryPanel=new LinearLayout(this);planLibraryPanel.setOrientation(LinearLayout.VERTICAL);
        planLibraryPanel.addView(text("先建立计划，再为计划逐日添加安排", 13, false, Color.DKGRAY));
        currentWatchPlan=text("手表当前安排：连接后读取",14,false,Color.DKGRAY);planLibraryPanel.addView(currentWatchPlan);
        LinearLayout libraryHeader = new LinearLayout(this); libraryHeader.setGravity(Gravity.CENTER_VERTICAL);
        libraryHeader.addView(text("全部计划",18,true,Color.BLACK),new LinearLayout.LayoutParams(0,dp(58),1));
        Button createGroup=button("＋ 新建计划",Color.rgb(28,31,29),Color.WHITE); libraryHeader.addView(createGroup,new LinearLayout.LayoutParams(dp(145),dp(48)));
        planLibraryPanel.addView(libraryHeader);
        savedPlanList=new LinearLayout(this); savedPlanList.setOrientation(LinearLayout.VERTICAL); planLibraryPanel.addView(savedPlanList);
        planCard.addView(planLibraryPanel);

        planEditorPanel=new LinearLayout(this);planEditorPanel.setOrientation(LinearLayout.VERTICAL);planEditorPanel.setVisibility(View.GONE);
        LinearLayout editorHeader=new LinearLayout(this);editorHeader.setGravity(Gravity.CENTER_VERTICAL);
        Button closeEditor=button("‹ 返回计划",Color.rgb(232,234,229),Color.BLACK);editorHeader.addView(closeEditor,new LinearLayout.LayoutParams(dp(135),dp(48)));
        editorHeader.addView(text("编辑安排",19,true,Color.BLACK),new LinearLayout.LayoutParams(0,dp(52),1));planEditorPanel.addView(editorHeader);
        planEditorPanel.addView(text("安排信息",16,true,Color.BLACK));
        planName = input("安排名称，例如：第1天"); planGroup = input("所属训练计划，例如：减肥计划");
        planRequirement = input("今天的训练说明（可选）");
        planRequirement.setSingleLine(false); planRequirement.setMinLines(2); planRequirement.setGravity(Gravity.TOP);LinearLayout.LayoutParams requirementParams=new LinearLayout.LayoutParams(-1,dp(92));requirementParams.topMargin=dp(7);planRequirement.setLayoutParams(requirementParams);
        planEditorPanel.addView(planName); planEditorPanel.addView(planGroup); planEditorPanel.addView(planRequirement);
        planEditorPanel.addView(text("快速填充训练内容",16,true,Color.BLACK));
        LinearLayout templates = new LinearLayout(this);
        Button intervalPlan = button("1千米 + 200米", Color.rgb(232,234,229), Color.BLACK);
        Button fartlekPlan = button("法特莱克跑", Color.rgb(232,234,229), Color.BLACK);
        templates.addView(intervalPlan, weight()); templates.addView(fartlekPlan, weight()); planEditorPanel.addView(templates);
        planEditorPanel.addView(text("训练内容",18,true,Color.BLACK));
        planEditorPanel.addView(text("每项都可选择按时间或距离，支持交替组合",13,false,Color.DKGRAY));
        planList = new LinearLayout(this); planList.setOrientation(LinearLayout.VERTICAL); planEditorPanel.addView(planList);
        LinearLayout additions = new LinearLayout(this);
        Button addRun = button("+ 跑步", Color.rgb(215,245,183), Color.BLACK);
        Button addWalk = button("+ 快走", Color.rgb(196,237,240), Color.BLACK);
        Button addRest = button("+ 休息", Color.rgb(255,227,174), Color.BLACK);
        additions.addView(addRun, weight()); additions.addView(addWalk, weight()); additions.addView(addRest, weight()); planEditorPanel.addView(additions);
        LinearLayout planActions=new LinearLayout(this);
        Button saveLocal=button("保存安排",Color.rgb(28,31,29),Color.WHITE);
        Button save=button("保存并同步",Color.rgb(124,203,43),Color.BLACK);
        planActions.addView(saveLocal,weight()); planActions.addView(save,weight()); planEditorPanel.addView(planActions);
        planCard.addView(planEditorPanel);
        root.addView(planCard, margin());

        controlCard = card(); controlCard.addView(text("训练控制", 22, true, Color.BLACK));
        controlCard.addView(text("远程操作会立即发送到当前连接的手表",13,false,Color.DKGRAY));
        LinearLayout controls = new LinearLayout(this);
        for (String[] item : new String[][]{{"开始","start"},{"暂停/继续","toggle"},{"结束","stop"}}) {
            Button action = button(item[0], Color.rgb(232,234,229), Color.BLACK); action.setOnClickListener(v -> control(item[1])); controls.addView(action, weight());
        }
        controlCard.addView(controls); root.addView(controlCard, margin());

        historyCard = card(); historyCard.addView(text("训练历史", 22, true, Color.BLACK));
        historySummary = text("连接后读取", 14, false, Color.DKGRAY); historyCard.addView(historySummary);
        historyList = new LinearLayout(this); historyList.setOrientation(LinearLayout.VERTICAL); historyCard.addView(historyList);
        root.addView(historyCard, margin());
        controlCard.setVisibility(View.GONE); historyCard.setVisibility(View.GONE);
        scroll.addView(root); setContentView(scroll);

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
        planTab.setOnClickListener(v->showSection(0,planTab,controlTab,historyTab));
        controlTab.setOnClickListener(v->showSection(1,planTab,controlTab,historyTab));
        historyTab.setOnClickListener(v->showSection(2,planTab,controlTab,historyTab));
        renderSavedPlans();
    }

    private void showSection(int section,Button planTab,Button controlTab,Button historyTab){
        planCard.setVisibility(section==0?View.VISIBLE:View.GONE);controlCard.setVisibility(section==1?View.VISIBLE:View.GONE);historyCard.setVisibility(section==2?View.VISIBLE:View.GONE);
        if(section==0)showPlanLibrary();
        Button[] tabs={planTab,controlTab,historyTab};for(int i=0;i<tabs.length;i++){tabs[i].setBackground(rounded(i==section?Color.rgb(28,31,29):Color.rgb(232,234,229),16));tabs[i].setTextColor(i==section?Color.WHITE:Color.BLACK);}
    }

    private void discoverWatch() {
        stopDiscovery();
        connection.setText("正在局域网自动发现手表…");
        WifiManager wifi = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("watchintervals-discovery");
            multicastLock.setReferenceCounted(false); multicastLock.acquire();
        }
        nsdManager = (NsdManager)getSystemService(NSD_SERVICE);
        discoveryListener = new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String type) {}
            public void onStartDiscoveryFailed(String type, int code) { runOnUiThread(() -> connection.setText("自动发现暂不可用，可直接输入手表 IP")); stopDiscovery(); }
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
                        String pairing = code.getText().toString().trim();
                        if (address.isEmpty()) { resolving=false; return; }
                        if (pairing.length() != 6) {
                            resolving=false;
                            runOnUiThread(() -> { host.setText(address); connection.setText("已发现手表 " + address + "，请输入配对码后同步"); });
                            stopDiscovery(); return;
                        }
                        // A LAN can contain stale/debug advertisements. Verify the pairing API before replacing the saved host.
                        io.execute(() -> {
                            try {
                                JSONObject status = new JSONObject(new WatchClient(address, pairing).get("/v1/status"));
                                runOnUiThread(() -> {
                                    host.setText(address);
                                    connection.setText("已发现并连接 " + status.optString("device") + " · " + address);
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
        catch (Exception error) { connection.setText("自动发现暂不可用，可直接输入手表 IP"); stopDiscovery(); }
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
            WatchClient client = client(); JSONObject status = new JSONObject(client.get("/v1/status"));
            JSONObject library = PhonePlanLibrary.load(this); client.put("/v1/plan-library", library.toString());
            JSONObject plan = new JSONObject(client.get("/v1/plan/profile")); JSONArray history = new JSONArray(client.get("/v1/history"));
            runOnUiThread(() -> { connection.setText("已连接 " + status.optString("device") + " · 手表端 " + status.optString("appVersion") + " · 手机辅助轨迹"); showPlan(plan); showHistory(history); ensureLocationRelay(); });
        });
    }

    private void ensureLocationRelay() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_RELAY);
            return;
        }
        startForegroundService(new android.content.Intent(this, PhoneLocationRelayService.class));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION_RELAY && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) ensureLocationRelay();
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
            LinearLayout stageCard=card(); stageCard.setPadding(dp(14),dp(10),dp(14),dp(10));
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            String kind=stage.optString("kind"), unit=stage.optString("unit"); int target=stage.optInt("target");
            TextView label=text((position+1)+"  "+kindName(kind),17,true,Color.BLACK); row.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));
            EditText value=input(""); value.setText(String.valueOf(target)); value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); row.addView(value,new LinearLayout.LayoutParams(dp(90),dp(48)));
            Button unitButton=button("DISTANCE".equals(unit)?"距离 · 米":"时间 · 秒",Color.rgb(232,234,229),Color.BLACK);unitButton.setTextSize(13); row.addView(unitButton,new LinearLayout.LayoutParams(dp(105),dp(48)));
            stageCard.addView(row);
            LinearLayout actions=new LinearLayout(this);
            Button up=button("上移",Color.rgb(238,240,236),Color.BLACK);
            Button down=button("下移",Color.rgb(238,240,236),Color.BLACK);
            Button delete=button("删除阶段",Color.rgb(255,226,226),Color.rgb(170,30,30));
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
        runIo(()->{ client().put("/v1/plan-library",library.toString()); runOnUiThread(()->{connection.setText("完整计划库已同步到手表");renderSavedPlans();}); });
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
        runIo(()->{client().put("/v1/plan-library",library.toString());runOnUiThread(()->connection.setText(successText));});
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
        if(plans.length()==0)savedPlanList.addView(text("还没有安排。先新建训练计划，再点击“添加安排”。",14,false,Color.DKGRAY));
        JSONArray groups=library.optJSONArray("groups");java.util.HashSet<String> rendered=new java.util.HashSet<>();
        if(groups!=null)for(int groupIndex=0;groupIndex<groups.length();groupIndex++){
            JSONObject group=groups.optJSONObject(groupIndex);if(group==null)continue;String groupId=group.optString("id");
            int arrangementCount=0;for(int i=0;i<plans.length();i++){JSONObject item=plans.optJSONObject(i);if(item!=null&&groupId.equals(item.optString("groupId")))arrangementCount++;}
            LinearLayout planBlock=card();planBlock.setPadding(dp(15),dp(14),dp(15),dp(14));planBlock.setBackground(rounded(Color.rgb(248,250,246),22));
            LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView header=text(group.optString("name"),19,true,Color.rgb(28,31,29));header.setSingleLine(false);titleRow.addView(header,new LinearLayout.LayoutParams(0,-2,1));
            TextView count=text(arrangementCount+" 个安排",12,false,Color.DKGRAY);count.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);titleRow.addView(count,new LinearLayout.LayoutParams(dp(82),dp(38)));
            planBlock.addView(titleRow);
            LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
            Button addDay=button("＋ 添加安排",Color.rgb(190,255,72),Color.BLACK);actions.addView(addDay,new LinearLayout.LayoutParams(0,dp(46),1));
            Button rename=button("设置",Color.rgb(232,234,229),Color.BLACK);actions.addView(rename,new LinearLayout.LayoutParams(dp(72),dp(46)));
            Button delete=button("删除",Color.rgb(255,232,232),Color.rgb(150,35,35));actions.addView(delete,new LinearLayout.LayoutParams(dp(72),dp(46)));
            planBlock.addView(actions,new LinearLayout.LayoutParams(-1,dp(54)));
            addDay.setOnClickListener(v->newPlanInGroup(group));rename.setOnClickListener(v->showGroupNameDialog(group,group.optString("name")));delete.setOnClickListener(v->confirmDeleteGroup(group));
            if(arrangementCount==0){TextView empty=text("暂无安排 · 点击上方按钮添加第1天",13,false,Color.GRAY);empty.setGravity(Gravity.CENTER);empty.setBackground(rounded(Color.rgb(238,241,235),14));planBlock.addView(empty,new LinearLayout.LayoutParams(-1,dp(48)));}
            for(int i=0;i<plans.length();i++){JSONObject item=plans.optJSONObject(i);if(item!=null&&groupId.equals(item.optString("groupId"))){addSavedPlanRow(planBlock,library,item);rendered.add(item.optString("id"));}}
            LinearLayout.LayoutParams blockParams=margin();blockParams.topMargin=dp(10);savedPlanList.addView(planBlock,blockParams);
        }
        for(int i=0;i<plans.length();i++){JSONObject item=plans.optJSONObject(i);if(item!=null&&!rendered.contains(item.optString("id")))addSavedPlanRow(savedPlanList,library,item);}
    }

    private void addSavedPlanRow(LinearLayout parent,JSONObject library,JSONObject item){
        String id=item.optString("id");LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(5),0,0);
        JSONArray savedStages=item.optJSONArray("stages");int count=savedStages==null?0:savedStages.length();boolean selected=id.equals(library.optString("selectedPlanId"));
        String requirement=item.optString("requirement").trim();String summary=count+" 项训练内容"+(requirement.isEmpty()?"":" · "+requirement);
        LinearLayout open=new LinearLayout(this);open.setOrientation(LinearLayout.VERTICAL);open.setPadding(dp(14),dp(9),dp(12),dp(9));open.setBackground(rounded(selected?Color.rgb(220,248,183):Color.WHITE,16));
        TextView name=text((selected?"✓  ":"")+item.optString("name"),16,true,Color.rgb(28,31,29));open.addView(name,new LinearLayout.LayoutParams(-1,dp(30)));
        TextView detail=text(summary,12,false,Color.DKGRAY);detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);open.addView(detail,new LinearLayout.LayoutParams(-1,dp(26)));
        row.addView(open,new LinearLayout.LayoutParams(0,dp(76),1));
        Button remove=button("删除",Color.rgb(255,235,235),Color.rgb(160,36,36));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(66),dp(76));rp.leftMargin=dp(6);row.addView(remove,rp);
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
        try{PhonePlanLibrary.deletePlan(this,id);if(id.equals(editingPlanId))editingPlanId="";renderSavedPlans();JSONObject library=PhonePlanLibrary.load(this);runIo(()->client().put("/v1/plan-library",library.toString()));}catch(Exception error){connection.setText("删除计划失败："+error.getMessage());}
    }
    private void control(String action){ runIo(()->{ client().post("/v1/control/"+action); runOnUiThread(()->connection.setText("训练操作已发送："+action)); }); }

    private void showHistory(JSONArray array){
        historyList.removeAllViews(); historySummary.setText(array.length()+" 次训练 · 点击查看地图轨迹与完整数据");
        for(int i=0;i<array.length();i++){
            JSONObject record=array.optJSONObject(i); if(record==null)continue;
            LinearLayout row=card(); row.setPadding(dp(16),dp(14),dp(16),dp(14));
            TextView date=text(new SimpleDateFormat("MM月dd日  HH:mm",Locale.CHINA).format(new Date(record.optLong("startedAt"))),15,true,Color.BLACK);
            row.addView(date);
            long duration=record.optLong("durationMs"); double meters=record.optDouble("distanceMeters");
            TextView primary=text(formatDistance(meters)+"  ·  "+formatDuration(duration)+"  ·  "+formatPace(duration,meters),17,true,Color.rgb(28,31,29));
            row.addView(primary);
            JSONArray route=record.optJSONArray("route");
            TextView secondary=text(record.optInt("steps")+" 步  ·  平均心率 "+(record.optInt("averageHeartRate")>0?record.optInt("averageHeartRate")+" bpm":"--")+"  ·  "+(route==null?0:route.length())+" 个轨迹点",13,false,Color.DKGRAY);
            row.addView(secondary);
            row.setOnClickListener(v->startActivity(new Intent(this,HistoryDetailActivity.class).putExtra("record",record.toString())));
            LinearLayout.LayoutParams params=margin(); params.setMargins(0,dp(10),0,0); historyList.addView(row,params);
        }
    }
    private String formatDistance(double meters){return meters<1000?Math.round(meters)+" 米":String.format(Locale.CHINA,"%.2f 公里",meters/1000d);}
    private String formatDuration(long millis){long total=Math.max(0,millis/1000),hours=total/3600,minutes=(total%3600)/60,seconds=total%60;return hours>0?String.format(Locale.CHINA,"%d:%02d:%02d",hours,minutes,seconds):String.format(Locale.CHINA,"%02d:%02d",minutes,seconds);}
    private String formatPace(long millis,double meters){if(meters<1||millis<=0)return "-- /公里";long seconds=Math.round(millis/1000d*1000d/meters);return String.format(Locale.CHINA,"%d:%02d /公里",seconds/60,seconds%60);}
    private WatchClient client(){ return new WatchClient(host.getText().toString().trim(),code.getText().toString().trim()); }
    private void runIo(Throwing action){ connection.setText("正在同步…"); io.execute(()->{ try{action.run();}catch(Exception error){runOnUiThread(()->connection.setText("连接失败："+error.getMessage()));} }); }
    interface Throwing{void run()throws Exception;}
    private String kindName(String kind){return "WALK".equals(kind)?"快走":"REST".equals(kind)?"休息":"跑步";}
    private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(18),dp(16),dp(18),dp(16));v.setBackground(rounded(Color.WHITE,22));v.setElevation(dp(1));return v;}
    private LinearLayout.LayoutParams margin(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(16);return p;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(3),dp(6),dp(3),dp(6));return p;}
    private TextView text(String s,int sp,boolean bold,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);v.setTypeface(null,bold?Typeface.BOLD:Typeface.NORMAL);v.setPadding(0,dp(5),0,dp(5));return v;}
    private EditText input(String hint){EditText v=new EditText(this);v.setHint(hint);v.setTextSize(16);v.setSingleLine(true);v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(rounded(Color.rgb(239,241,237),14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(7);v.setLayoutParams(p);return v;}
    private Button button(String s,int bg,int fg){Button v=new Button(this);v.setText(s);v.setTextSize(15);v.setTextColor(fg);v.setBackground(rounded(bg,16));v.setAllCaps(false);v.setStateListAnimator(null);return v;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){stopDiscovery();io.shutdownNow();super.onDestroy();}
}

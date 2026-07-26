package com.poyi.watchintervals;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Offline selector for the complete phone-authoritative plan library mirror. */
public class PlanActivity extends Activity {
    private LinearLayout libraryList, previewStages, confirmationPanel;
    private View confirmationScrim;
    private TextView previewName, previewMeta, previewRequirement, confirmationTitle, confirmationHint;
    private JSONObject library, previewPlan;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        library = PlanLibraryStore.load(this); buildUi(); renderLibrary(); renderSelectedPreview();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.BLACK);
        root.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,12));
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=Ui.backButton(this);back.setOnClickListener(v->finish());
        LinearLayout.LayoutParams backParams=new LinearLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,36));backParams.rightMargin=Ui.dp(this,8);
        nav.addView(back,backParams);
        TextView title=Ui.bold(this,"选择训练安排",Ui.TITLE,Ui.WHITE);nav.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));root.addView(nav);
        TextView source=Ui.text(this,"按训练计划查看 · 离线可用",Ui.LABEL,Ui.MUTED);root.addView(source,new LinearLayout.LayoutParams(-1,Ui.dp(this,24)));

        libraryList=new LinearLayout(this);libraryList.setOrientation(LinearLayout.VERTICAL);root.addView(libraryList,new LinearLayout.LayoutParams(-1,-2));
        TextView previewLabel=Ui.bold(this,"安排详情",16,Ui.WHITE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,34));lp.topMargin=Ui.dp(this,10);root.addView(previewLabel,lp);
        LinearLayout preview=Ui.card(this);
        previewName=Ui.bold(this,"",20,Ui.WHITE);previewName.setSingleLine(false);previewName.setMaxLines(2);preview.addView(previewName,new LinearLayout.LayoutParams(-1,-2));
        previewMeta=Ui.text(this,"",12,Ui.MUTED);preview.addView(previewMeta,new LinearLayout.LayoutParams(-1,Ui.dp(this,26)));
        previewRequirement=Ui.text(this,"",12,Ui.MUTED);previewRequirement.setSingleLine(false);previewRequirement.setMaxLines(5);preview.addView(previewRequirement,new LinearLayout.LayoutParams(-1,-2));
        previewStages=new LinearLayout(this);previewStages.setOrientation(LinearLayout.VERTICAL);preview.addView(previewStages,new LinearLayout.LayoutParams(-1,-2));root.addView(preview);

        TextView select=Ui.action(this,"使用这个安排",17,Ui.BLACK,Ui.YELLOW);LinearLayout.LayoutParams selectLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,56));selectLp.topMargin=Ui.dp(this,10);root.addView(select,selectLp);
        TextView hint=Ui.text(this,"计划与安排请在手机端或 MCP 编辑",11,Ui.MUTED);hint.setGravity(Gravity.CENTER);root.addView(hint,new LinearLayout.LayoutParams(-1,Ui.dp(this,30)));
        select.setOnClickListener(v->confirmSelection());

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        FrameLayout shell=new FrameLayout(this);shell.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        confirmationScrim=new View(this);confirmationScrim.setBackgroundColor(android.graphics.Color.argb(185,0,0,0));confirmationScrim.setVisibility(View.GONE);confirmationScrim.setClickable(true);confirmationScrim.setOnClickListener(v->hideConfirmation());shell.addView(confirmationScrim,new FrameLayout.LayoutParams(-1,-1));
        confirmationPanel=buildConfirmation();FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,Ui.dp(this,160),Gravity.BOTTOM);cp.leftMargin=Ui.dp(this,12);cp.rightMargin=Ui.dp(this,12);cp.bottomMargin=Ui.dp(this,10);shell.addView(confirmationPanel,cp);
        setContentView(shell);
    }

    private void renderLibrary() {
        libraryList.removeAllViews(); JSONArray groups=library.optJSONArray("groups"),plans=library.optJSONArray("plans");if(groups==null||plans==null)return;
        String selectedId=library.optString("selectedPlanId");
        for(int g=0;g<groups.length();g++){
            JSONObject group=groups.optJSONObject(g);if(group==null)continue;String groupId=group.optString("id");
            ArrayList<JSONObject> matches=new ArrayList<>();for(int p=0;p<plans.length();p++){JSONObject item=plans.optJSONObject(p);if(item!=null&&groupId.equals(item.optString("groupId")))matches.add(item);}
            TextView heading=Ui.bold(this,group.optString("name")+"  ·  "+matches.size()+" 个安排",14,Ui.MUTED);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,Ui.dp(this,36));hp.topMargin=Ui.dp(this,5);libraryList.addView(heading,hp);
            if(matches.isEmpty()){TextView empty=Ui.text(this,"暂无安排 · 请在手机端添加",12,Ui.MUTED);empty.setBackground(Ui.background(this,Ui.PANEL,16));empty.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,Ui.dp(this,46));ep.bottomMargin=Ui.dp(this,8);libraryList.addView(empty,ep);}
            for(JSONObject item:matches){
                boolean selected=selectedId.equals(item.optString("id"));LinearLayout card=Ui.card(this);card.setBackground(Ui.outlinedBackground(this,selected?Ui.PANEL_ACTIVE:Ui.PANEL,selected?Ui.LIME:Ui.LINE,18));
                TextView name=Ui.bold(this,(selected?"✓  ":"")+item.optString("name"),17,Ui.WHITE);name.setSingleLine(false);name.setMaxLines(2);card.addView(name,new LinearLayout.LayoutParams(-1,-2));
                ArrayList<Stage> stages=PlanStore.decode(item.optJSONArray("stages").toString());TextView meta=Ui.text(this,summary(stages),12,selected?Ui.LIME:Ui.MUTED);card.addView(meta,new LinearLayout.LayoutParams(-1,Ui.dp(this,24)));
                card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->{previewPlan=item;renderPreview();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=Ui.dp(this,8);libraryList.addView(card,cp);
            }
        }
    }

    private void renderSelectedPreview(){JSONArray plans=library.optJSONArray("plans");if(plans==null||plans.length()==0)return;String selected=library.optString("selectedPlanId");previewPlan=plans.optJSONObject(0);for(int i=0;i<plans.length();i++){JSONObject p=plans.optJSONObject(i);if(p!=null&&selected.equals(p.optString("id")))previewPlan=p;}renderPreview();}
    private void renderPreview(){if(previewPlan==null)return;previewName.setText(previewPlan.optString("name"));ArrayList<Stage> stages=PlanStore.decode(previewPlan.optJSONArray("stages").toString());previewMeta.setText(PlanLibraryStore.groupName(library,previewPlan.optString("groupId"))+" · "+summary(stages));String req=previewPlan.optString("requirement");previewRequirement.setText(req.isEmpty()?"按下方训练内容顺序完成。":req);previewStages.removeAllViews();for(int i=0;i<stages.size();i++){LinearLayout row=Ui.stageRow(this,i+1,stages.get(i),Ui.PANEL_ACTIVE);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,Ui.dp(this,46));rp.topMargin=Ui.dp(this,6);previewStages.addView(row,rp);}}
    private String summary(ArrayList<Stage> stages){long meters=0,seconds=0;for(Stage s:stages)if(s.unit==Stage.Unit.DISTANCE)meters+=s.target;else seconds+=s.target;String text=stages.size()+" 项内容";if(meters>0)text+=String.format(Locale.CHINA," · %.2f km",meters/1000d);if(seconds>0)text+=" · "+Math.max(1,seconds/60)+" 分钟";return text;}

    private LinearLayout buildConfirmation(){LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,12));panel.setBackground(Ui.background(this,Ui.PANEL_ACTIVE,18));panel.setVisibility(View.GONE);confirmationTitle=Ui.bold(this,"使用这个安排？",19,Ui.WHITE);confirmationHint=Ui.text(this,"",12,Ui.MUTED);panel.addView(confirmationTitle,new LinearLayout.LayoutParams(-1,Ui.dp(this,34)));panel.addView(confirmationHint,new LinearLayout.LayoutParams(-1,Ui.dp(this,28)));LinearLayout buttons=new LinearLayout(this);TextView cancel=Ui.action(this,"取消",15,Ui.WHITE,Ui.PANEL);TextView use=Ui.action(this,"使用",15,Ui.BLACK,Ui.YELLOW);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);bp.rightMargin=Ui.dp(this,7);buttons.addView(cancel,bp);buttons.addView(use,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));panel.addView(buttons);cancel.setOnClickListener(v->hideConfirmation());use.setOnClickListener(v->applySelection());return panel;}
    private void confirmSelection(){if(previewPlan==null)return;confirmationTitle.setText("使用安排“"+previewPlan.optString("name")+"”？");confirmationHint.setText(PlanLibraryStore.groupName(library,previewPlan.optString("groupId"))+" · 选择后主页立即更新");confirmationScrim.setVisibility(View.VISIBLE);confirmationPanel.setVisibility(View.VISIBLE);}
    private void hideConfirmation(){confirmationScrim.setVisibility(View.GONE);confirmationPanel.setVisibility(View.GONE);}
    private void applySelection(){try{PlanLibraryStore.select(this,previewPlan.optString("id"));library=PlanLibraryStore.load(this);hideConfirmation();renderLibrary();finish();}catch(Exception error){confirmationHint.setText("计划数据无效");}}
    @Override public void onBackPressed(){if(confirmationPanel.getVisibility()==View.VISIBLE)hideConfirmation();else super.onBackPressed();}
}

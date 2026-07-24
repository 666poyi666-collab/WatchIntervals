package com.poyi.watchintervals;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public class StageEditorActivity extends Activity {
    private Stage.Kind kind;
    private Stage.Unit unit;
    private int target, index;
    private TextView value, unitLabel;
    private LinearLayout quick;
    private final TextView[] kindButtons = new TextView[3];
    private final TextView[] unitButtons = new TextView[2];

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Intent source = getIntent();
        index = source.getIntExtra("index", -1);
        kind = Stage.Kind.valueOf(source.getStringExtra("kind"));
        unit = Stage.Unit.valueOf(source.getStringExtra("unit"));
        target = source.getIntExtra("target", 1000);
        buildUi(); updateAll();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 8));
        root.setBackgroundColor(Ui.BLACK);

        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.action(this, "‹", 30, Ui.WHITE, Ui.BLACK);
        nav.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 32)));
        TextView title = Ui.bold(this, index >= 0 ? "编辑阶段" : "添加阶段", 21, Ui.WHITE);
        nav.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 32), 1)); root.addView(nav);
        back.setOnClickListener(v -> finish());

        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(true);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content.addView(Ui.text(this, "运动类型", 12, Ui.MUTED), new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        LinearLayout kinds = new LinearLayout(this);
        String[] kindNames = {"跑步", "快走", "休息"};
        for (int i = 0; i < kindButtons.length; i++) {
            kindButtons[i] = Ui.action(this, kindNames[i], 15, Ui.WHITE, Ui.PANEL);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1); if (i > 0) p.leftMargin = Ui.dp(this, 5);
            kinds.addView(kindButtons[i], p); final int selected = i;
            kindButtons[i].setOnClickListener(v -> {
                kind = Stage.Kind.values()[selected];
                if (kind == Stage.Kind.REST && unit != Stage.Unit.TIME) { unit = Stage.Unit.TIME; target = 60; }
                updateAll();
            });
        }
        content.addView(kinds);

        content.addView(Ui.text(this, "目标方式", 12, Ui.MUTED), new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        LinearLayout units = new LinearLayout(this);
        String[] unitNames = {"距离", "时间"};
        for (int i = 0; i < unitButtons.length; i++) {
            unitButtons[i] = Ui.action(this, unitNames[i], 15, Ui.WHITE, Ui.PANEL);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1); if (i > 0) p.leftMargin = Ui.dp(this, 6);
            units.addView(unitButtons[i], p); final int selected = i;
            unitButtons[i].setOnClickListener(v -> {
                Stage.Unit next = Stage.Unit.values()[selected];
                if (next != unit) { unit = next; target = unit == Stage.Unit.DISTANCE ? 1000 : 60; }
                updateAll();
            });
        }
        content.addView(units);

        LinearLayout stepper = new LinearLayout(this); stepper.setGravity(Gravity.CENTER); stepper.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 2));
        TextView minus = Ui.action(this, "−", 30, Ui.WHITE, Ui.PANEL);
        stepper.addView(minus, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));
        LinearLayout number = new LinearLayout(this); number.setOrientation(LinearLayout.VERTICAL); number.setGravity(Gravity.CENTER);
        value = Ui.bold(this, "1000", 36, Ui.WHITE); value.setGravity(Gravity.CENTER);
        unitLabel = Ui.text(this, "米", 13, Ui.MUTED); unitLabel.setGravity(Gravity.CENTER);
        number.addView(value, new LinearLayout.LayoutParams(-1, Ui.dp(this, 39))); number.addView(unitLabel, new LinearLayout.LayoutParams(-1, Ui.dp(this, 16)));
        stepper.addView(number, new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1));
        TextView plus = Ui.action(this, "＋", 27, Ui.BLACK, Ui.LIME);
        stepper.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54))); content.addView(stepper);
        minus.setOnClickListener(v -> { target = Math.max(step(), target - step()); updateAll(); });
        plus.setOnClickListener(v -> { target += step(); updateAll(); });

        content.addView(Ui.text(this, "常用目标", 12, Ui.MUTED), new LinearLayout.LayoutParams(-1, Ui.dp(this, 14)));
        quick = new LinearLayout(this); quick.setOrientation(LinearLayout.VERTICAL);
        content.addView(quick);

        TextView save = Ui.action(this, "保存阶段", 18, Ui.BLACK, Ui.LIME);
        root.addView(save, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        save.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra("index", index).putExtra("kind", kind.name()).putExtra("unit", unit.name()).putExtra("target", target));
            finish();
        });
        setContentView(root);
    }

    private int step() { return unit == Stage.Unit.DISTANCE ? 50 : 15; }
    private String quickLabel(int option) { return unit == Stage.Unit.DISTANCE ? option + " 米" : option < 60 ? option + " 秒" : (option / 60) + " 分"; }

    private void updateAll() {
        for (int i = 0; i < kindButtons.length; i++) {
            boolean selected = i == kind.ordinal();
            kindButtons[i].setTextColor(selected ? Ui.BLACK : Ui.WHITE);
            kindButtons[i].setBackground(Ui.background(this, selected ? Ui.stageColor(kind) : Ui.PANEL, 8));
        }
        for (int i = 0; i < unitButtons.length; i++) {
            boolean selected = i == unit.ordinal();
            boolean enabled = kind != Stage.Kind.REST || i == Stage.Unit.TIME.ordinal();
            unitButtons[i].setTextColor(selected ? Ui.BLACK : enabled ? Ui.WHITE : Ui.MUTED);
            unitButtons[i].setBackground(Ui.background(this, selected ? Ui.LIME : Ui.PANEL, 8));
            unitButtons[i].setClickable(enabled);
            unitButtons[i].setAlpha(enabled ? 1f : 0.5f);
        }
        value.setText(String.format(Locale.CHINA, "%d", target));
        unitLabel.setText(unit == Stage.Unit.DISTANCE ? "米" : "秒");
        renderQuickOptions();
    }

    private void renderQuickOptions() {
        quick.removeAllViews();
        int[] options = unit == Stage.Unit.DISTANCE ? new int[]{200, 500, 1000, 2000} : new int[]{30, 60, 120, 300};
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            if (rowIndex > 0) row.setPadding(0, Ui.dp(this, 6), 0, 0);
            for (int column = 0; column < 2; column++) {
                int option = options[rowIndex * 2 + column];
                boolean selected = target == option;
                TextView chip = Ui.action(this, quickLabel(option), 14, selected ? Ui.BLACK : Ui.WHITE, selected ? Ui.stageColor(kind) : Ui.PANEL);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1);
                if (column == 0) p.rightMargin = Ui.dp(this, 4);
                else p.leftMargin = Ui.dp(this, 4);
                row.addView(chip, p);
                chip.setOnClickListener(v -> { target = option; updateAll(); });
            }
            quick.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(this, rowIndex == 0 ? 36 : 42)));
        }
    }
}

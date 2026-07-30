package com.poyi.watchintervals.phone;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One accessible destination inside the floating phone navigation surface. */
final class PhoneTabView extends LinearLayout {
    private final PhoneNavigationSpec.Item item;
    private final PhoneSymbolView symbol;
    private final TextView label;

    PhoneTabView(Context context, PhoneNavigationSpec.Item item) {
        super(context);
        this.item = item;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(4), dp(5), dp(4), dp(4));
        setClickable(true);
        setFocusable(true);
        setMinimumHeight(dp(56));

        symbol = new PhoneSymbolView(context, item.symbol);
        addView(symbol, new LayoutParams(dp(25), dp(25)));
        label = new TextView(context);
        label.setText(item.label);
        label.setTextSize(11);
        label.setSingleLine(true);
        label.setMinHeight(dp(20));
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setActive(false);
    }

    void setActive(boolean active) {
        int tint = active ? Palette.MOVE : Palette.TEXT_DIM;
        symbol.setTint(tint);
        symbol.setEmphasized(active);
        label.setTextColor(tint);
        GradientDrawable background = new GradientDrawable();
        background.setColor(active ? Palette.GLASS_SELECTED : Color.TRANSPARENT);
        background.setCornerRadius(dp(18));
        setBackground(background);
        setContentDescription(item.accessibilityLabel + (active ? "，已选择" : ""));
        setSelected(active);
        if (Build.VERSION.SDK_INT >= 30) setStateDescription(active ? "已选择" : "未选择");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

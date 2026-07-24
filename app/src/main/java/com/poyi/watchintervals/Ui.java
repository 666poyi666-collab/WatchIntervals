package com.poyi.watchintervals;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    private static final float WATCH_SCALE = 1.35f;
    static final int BLACK = Color.rgb(7, 9, 10);
    static final int PANEL = Color.rgb(22, 25, 27);
    static final int PANEL_ACTIVE = Color.rgb(35, 39, 41);
    static final int WHITE = Color.rgb(248, 249, 246);
    static final int MUTED = Color.rgb(151, 158, 155);
    static final int LINE = Color.rgb(47, 51, 52);
    static final int LIME = Color.rgb(190, 255, 71);
    static final int YELLOW = Color.rgb(255, 215, 52);
    static final int CYAN = Color.rgb(83, 218, 229);
    static final int AMBER = Color.rgb(255, 183, 66);
    static final int RED = Color.rgb(255, 86, 79);
    static final int GREEN = Color.rgb(70, 226, 129);

    private Ui() {}

    private static float scale(Context context) {
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float widthScale = metrics.widthPixels / 378f;
        float heightScale = metrics.heightPixels / 496f;
        return WATCH_SCALE * Math.min(widthScale, heightScale);
    }

    static int dp(Context context, float value) {
        // Keep the OWW221 calibration while preserving proportions on other watch canvases.
        return Math.round(value * scale(context));
    }

    static TextView text(Context context, String value, float sizeDp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeDp * scale(context));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setLetterSpacing(0);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    static TextView bold(Context context, String value, float sizeDp, int color) {
        TextView view = text(context, value, sizeDp, color);
        view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        return view;
    }

    static GradientDrawable background(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    static RippleDrawable ovalAction(Context context, int color) {
        return new RippleDrawable(
                ColorStateList.valueOf(Color.argb(55, 255, 255, 255)),
                oval(color),
                oval(Color.WHITE));
    }

    static GradientDrawable outlinedBackground(Context context, int color, int stroke, float radiusDp) {
        GradientDrawable drawable = background(context, color, radiusDp);
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    static TextView action(Context context, String value, float sizeDp, int foreground, int background) {
        TextView view = bold(context, value, sizeDp, foreground);
        view.setGravity(Gravity.CENTER);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), background(context, background, 16), null));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    static TextView iconAction(Context context, int drawableRes, String description, int foreground, int background) {
        TextView view = action(context, "", 1, foreground, background);
        view.setCompoundDrawablesWithIntrinsicBounds(drawableRes, 0, 0, 0);
        view.setCompoundDrawableTintList(ColorStateList.valueOf(foreground));
        view.setContentDescription(description);
        return view;
    }

    static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(LINE);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(context, 1)));
        return view;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        card.setBackground(background(context, PANEL, 18));
        return card;
    }

    static TextView pagerDots(Context context, int active, int count) {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) dots.append("   ");
            dots.append(i == active ? "●" : "○");
        }
        TextView view = text(context, dots.toString(), 9, MUTED);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    static int stageColor(Stage.Kind kind) {
        if (kind == Stage.Kind.WALK) return CYAN;
        if (kind == Stage.Kind.REST) return AMBER;
        return LIME;
    }

    /** Signal bands used by the stock sports preparation screen. */
    static String systemGpsSignal(int snr) {
        if (snr <= 0) return "";
        if (snr < 18) return "弱 " + snr;
        if (snr < 25) return "中 " + snr;
        return "强 " + snr;
    }
}

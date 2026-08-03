package com.poyi.watchintervals.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Compact proportional chart for deep, light, REM and awake duration. */
final class SleepStageBarView extends View {
    private static final int[] COLORS = new int[]{Palette.SLEEP_DEEP, Palette.SLEEP_LIGHT,
            Palette.SLEEP_REM, Palette.SLEEP_AWAKE};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path clip = new Path();
    private long[] minutes = new long[4];

    SleepStageBarView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setOverview(PhoneSleepOverview overview) {
        minutes = overview.stageMinutes();
        setContentDescription("睡眠阶段比例：深睡 " + minutes[0] + " 分钟，浅睡 "
                + minutes[1] + " 分钟，REM " + minutes[2] + " 分钟，清醒 "
                + minutes[3] + " 分钟");
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(dp(240), widthMeasureSpec),
                resolveSize(dp(18), heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = dp(9);
        bounds.set(0f, 0f, getWidth(), getHeight());
        paint.setColor(Palette.CARD_DEEP);
        canvas.drawRoundRect(bounds, radius, radius, paint);
        long total = 0L;
        for (long value : minutes) total += Math.max(0L, value);
        if (total <= 0L) return;
        clip.reset();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        float left = 0f;
        for (int index = 0; index < minutes.length; index++) {
            if (minutes[index] <= 0L) continue;
            float right = index == minutes.length - 1 ? getWidth()
                    : left + getWidth() * minutes[index] / (float) total;
            paint.setColor(COLORS[index]);
            canvas.drawRect(left, 0f, right, getHeight(), paint);
            left = right;
        }
        canvas.restore();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

package com.poyi.watchintervals.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Compact duration trend for the latest seven locally cached nights. */
final class SleepWeekTrendView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private PhoneSleepWeek week = PhoneSleepWeek.from(null);

    SleepWeekTrendView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setWeek(PhoneSleepWeek value) {
        week = value == null ? PhoneSleepWeek.from(null) : value;
        StringBuilder description = new StringBuilder("近七晚睡眠趋势");
        for (PhoneSleepWeek.Night night : week.nights) description.append("，")
                .append(new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date(night.timestamp)))
                .append(" ").append(PhoneFormat.minutesHuman((int) night.durationMinutes));
        setContentDescription(description.toString());
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(dp(280), widthMeasureSpec),
                resolveSize(dp(154), heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (week.nights.isEmpty()) return;
        float left = dp(4), right = getWidth() - dp(4), top = dp(24), bottom = getHeight() - dp(28);
        long maximum = Math.max(1L, week.maximumMinutes());
        float referenceY = bottom - (8f * 60f / maximum) * (bottom - top);
        paint.setStrokeWidth(dp(1));paint.setColor(Palette.BORDER);
        canvas.drawLine(left, referenceY, right, referenceY, paint);
        paint.setTextSize(dp(10));paint.setColor(Palette.TEXT_DIM);paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("8小时", left, Math.max(dp(10), referenceY - dp(4)), paint);
        float slot = (right - left) / week.nights.size();
        for (int index = 0; index < week.nights.size(); index++) {
            PhoneSleepWeek.Night night = week.nights.get(index);
            float center = left + slot * (index + .5f);
            float height = Math.max(dp(3), night.durationMinutes / (float) maximum * (bottom - top));
            float width = Math.min(dp(24), slot * .58f);
            bar.set(center - width / 2f, bottom - height, center + width / 2f, bottom);
            paint.setColor(night.durationMinutes >= 7L * 60L ? Palette.SLEEP_LIGHT : Palette.SLEEP_DEEP);
            canvas.drawRoundRect(bar, dp(6), dp(6), paint);
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(10));paint.setColor(Palette.TEXT);
            canvas.drawText(String.format(Locale.CHINA,"%.1f",night.durationMinutes/60d),center,
                    Math.max(dp(11),bottom-height-dp(5)),paint);
            paint.setColor(Palette.TEXT_DIM);
            canvas.drawText(new SimpleDateFormat("M/d",Locale.CHINA).format(new Date(night.timestamp)),
                    center,getHeight()-dp(8),paint);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

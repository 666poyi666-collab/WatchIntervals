package com.poyi.watchintervals.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A real start-to-end sleep timeline; gaps stay visible instead of being converted into sleep. */
final class SleepStageTimelineView extends View {
    private static final String[] ROW_LABELS = new String[]{"清醒", "REM", "浅睡", "深睡"};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private PhoneSleepTimeline timeline = PhoneSleepTimeline.from(null);

    SleepStageTimelineView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setTimeline(PhoneSleepTimeline value) {
        timeline = value == null ? PhoneSleepTimeline.from(null) : value;
        if (!timeline.available()) {
            setContentDescription("系统没有返回可绘制的睡眠阶段时间线");
        } else {
            setContentDescription("睡眠阶段时间线，从 " + clock(timeline.startTime) + " 到 "
                    + clock(timeline.endTime) + "，共 " + timeline.segments.size() + " 段，深睡 "
                    + timeline.durationMinutes(PhoneSleepTimeline.DEEP) + " 分钟，浅睡 "
                    + timeline.durationMinutes(PhoneSleepTimeline.LIGHT) + " 分钟，REM "
                    + timeline.durationMinutes(PhoneSleepTimeline.REM) + " 分钟，清醒 "
                    + timeline.durationMinutes(PhoneSleepTimeline.AWAKE) + " 分钟");
        }
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(dp(280), widthMeasureSpec),
                resolveSize(dp(154), heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float labelWidth = dp(38);
        float rightPadding = dp(4);
        float top = dp(8);
        float rowHeight = dp(24);
        float graphLeft = labelWidth;
        float graphRight = Math.max(graphLeft + dp(20), getWidth() - rightPadding);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        paint.setTextSize(dp(10));
        paint.setStrokeWidth(dp(1));
        for (int row = 0; row < ROW_LABELS.length; row++) {
            float centerY = top + row * rowHeight + rowHeight / 2f;
            paint.setColor(Palette.TEXT_DIM);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(ROW_LABELS[row], 0f, centerY + dp(4), paint);
            paint.setColor(Palette.BORDER);
            canvas.drawLine(graphLeft, centerY, graphRight, centerY, paint);
        }
        if (!timeline.available()) return;
        double duration = timeline.endTime - timeline.startTime;
        PhoneSleepTimeline.Segment previous = null;
        for (PhoneSleepTimeline.Segment segment : timeline.segments) {
            float left = graphLeft + (float) ((segment.startTime - timeline.startTime)
                    / duration * (graphRight - graphLeft));
            float right = graphLeft + (float) ((segment.endTime - timeline.startTime)
                    / duration * (graphRight - graphLeft));
            int row = rowFor(segment.type);
            float centerY = top + row * rowHeight + rowHeight / 2f;
            if (previous != null && previous.sessionIndex == segment.sessionIndex
                    && segment.startTime - previous.endTime <= 5 * 60_000L) {
                int previousRow = rowFor(previous.type);
                float previousY = top + previousRow * rowHeight + rowHeight / 2f;
                paint.setColor(Palette.BORDER);
                canvas.drawLine(left, previousY, left, centerY, paint);
            }
            paint.setColor(colorFor(segment.type));
            rect.set(left, centerY - dp(5), Math.max(left + dp(2), right), centerY + dp(5));
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
            previous = segment;
        }
        paint.setTextSize(dp(10));
        paint.setColor(Palette.TEXT_DIM);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(clock(timeline.startTime), graphLeft, top + 4 * rowHeight + dp(17), paint);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(clock(timeline.startTime + (timeline.endTime - timeline.startTime) / 2L),
                (graphLeft + graphRight) / 2f, top + 4 * rowHeight + dp(17), paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(clock(timeline.endTime), graphRight, top + 4 * rowHeight + dp(17), paint);
    }

    private int rowFor(int type) {
        if (type == PhoneSleepTimeline.AWAKE) return 0;
        if (type == PhoneSleepTimeline.REM) return 1;
        if (type == PhoneSleepTimeline.LIGHT) return 2;
        if (type == PhoneSleepTimeline.DEEP) return 3;
        return 2;
    }

    private int colorFor(int type) {
        if (type == PhoneSleepTimeline.DEEP) return Palette.SLEEP_DEEP;
        if (type == PhoneSleepTimeline.LIGHT) return Palette.SLEEP_LIGHT;
        if (type == PhoneSleepTimeline.REM) return Palette.SLEEP_REM;
        if (type == PhoneSleepTimeline.AWAKE) return Palette.SLEEP_AWAKE;
        return Palette.HINT;
    }

    private String clock(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(millis));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

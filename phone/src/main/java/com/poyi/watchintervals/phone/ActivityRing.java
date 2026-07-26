package com.poyi.watchintervals.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;

/**
 * Single fitness-style progress ring: dim track, gradient arc with round caps starting at
 * twelve o'clock. The one visual element that makes the live remote read as a fitness surface
 * rather than a settings page.
 */
final class ActivityRing extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float fraction;
    private int startColor = Palette.MOVE, endColor = Palette.ORANGE;

    ActivityRing(Context context) {
        super(context);
        track.setStyle(Paint.Style.STROKE);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
    }

    void set(float value, int start, int end) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (clamped == fraction && start == startColor && end == endColor) return;
        fraction = clamped;
        startColor = start;
        endColor = end;
        arc.setShader(null);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float stroke = getWidth() / 9f;
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        float inset = stroke / 2f + 2f;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        track.setColor(Color.argb(56, Color.red(startColor), Color.green(startColor), Color.blue(startColor)));
        canvas.drawArc(bounds, 0f, 360f, false, track);
        if (fraction <= 0f) return;
        if (arc.getShader() == null) {
            SweepGradient gradient = new SweepGradient(getWidth() / 2f, getHeight() / 2f,
                    new int[]{startColor, endColor, startColor}, new float[]{0f, 0.75f, 1f});
            Matrix rotate = new Matrix();
            rotate.postRotate(-90f, getWidth() / 2f, getHeight() / 2f);
            gradient.setLocalMatrix(rotate);
            arc.setShader(gradient);
        }
        canvas.drawArc(bounds, -90f, fraction * 360f, false, arc);
    }
}

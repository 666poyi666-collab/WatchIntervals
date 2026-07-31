package com.poyi.watchintervals.phone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Small code-native vector renderer for the phone UI.
 *
 * <p>The drawings intentionally use an original interval/route language instead of font glyphs
 * or copied SF Symbols. Geometry lives in a 24-unit viewport and therefore remains crisp at any
 * density.</p>
 */
@SuppressLint("ViewConstructor") // Programmatic-only view requires an explicit product symbol.
final class PhoneSymbolView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final PhoneSymbol symbol;
    private int tint = Palette.TEXT_DIM;
    private boolean emphasized;

    PhoneSymbolView(Context context, PhoneSymbol symbol) {
        super(context);
        this.symbol = symbol;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setTint(int color) {
        if (tint == color) return;
        tint = color;
        invalidate();
    }

    void setEmphasized(boolean value) {
        if (emphasized == value) return;
        emphasized = value;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int preferred = dp(26);
        setMeasuredDimension(resolveSize(preferred, widthMeasureSpec), resolveSize(preferred, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float scale = size / 24f;
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        paint.setColor(tint);
        paint.setShader(null);
        paint.setStrokeWidth(emphasized ? 2.25f : 1.9f);
        paint.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (symbol) {
            case PLAN:
                rect.set(3.25f, 3.75f, 20.75f, 20.25f);
                canvas.drawRoundRect(rect, 4f, 4f, paint);
                canvas.drawLine(7f, 8.5f, 17f, 8.5f, paint);
                canvas.drawLine(7f, 12.25f, 14.75f, 12.25f, paint);
                canvas.drawLine(7f, 16f, 11.5f, 16f, paint);
                break;
            case WORKOUT:
                rect.set(3.4f, 3.4f, 20.6f, 20.6f);
                canvas.drawArc(rect, -78f, 293f, false, paint);
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(10f, 8.3f);
                path.lineTo(16.6f, 12f);
                path.lineTo(10f, 15.7f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case HISTORY:
                rect.set(4.1f, 4.1f, 19.9f, 19.9f);
                canvas.drawArc(rect, -58f, 290f, false, paint);
                path.moveTo(4.05f, 5.2f);
                path.lineTo(4.25f, 9.15f);
                path.lineTo(7.8f, 7.45f);
                canvas.drawPath(path, paint);
                canvas.drawLine(12f, 7.4f, 12f, 12.2f, paint);
                canvas.drawLine(12f, 12.2f, 15.35f, 14.1f, paint);
                break;
            case SLEEP:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(16.85f, 3.75f);
                path.cubicTo(12.95f, 4.65f, 10.35f, 8.15f, 10.85f, 12.1f);
                path.cubicTo(11.35f, 16.15f, 14.85f, 19.15f, 19.15f, 18.65f);
                path.cubicTo(17.45f, 20.15f, 15.2f, 21f, 12.8f, 20.65f);
                path.cubicTo(7.55f, 19.9f, 3.9f, 15.05f, 4.65f, 9.8f);
                path.cubicTo(5.2f, 5.9f, 8.3f, 2.85f, 12.15f, 2.25f);
                path.cubicTo(13.95f, 1.95f, 15.6f, 2.55f, 16.85f, 3.75f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case BACK:
                path.moveTo(14.8f, 5.2f);
                path.lineTo(8f, 12f);
                path.lineTo(14.8f, 18.8f);
                canvas.drawPath(path, paint);
                break;
            case LOCATION:
                path.moveTo(12f, 21f);
                path.cubicTo(10.2f, 18.45f, 5.65f, 14.5f, 5.65f, 9.75f);
                path.cubicTo(5.65f, 6.25f, 8.5f, 3.4f, 12f, 3.4f);
                path.cubicTo(15.5f, 3.4f, 18.35f, 6.25f, 18.35f, 9.75f);
                path.cubicTo(18.35f, 14.5f, 13.8f, 18.45f, 12f, 21f);
                path.close();
                canvas.drawPath(path, paint);
                rect.set(9.6f, 7.35f, 14.4f, 12.15f);
                canvas.drawOval(rect, paint);
                break;
        }
        canvas.restore();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

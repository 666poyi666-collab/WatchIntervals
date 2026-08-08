package com.poyi.watchintervals;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    private static final float WATCH_SCALE = 1.35f;
    private static final float MIN_TOUCH_TARGET = 40f;
    private static final PathInterpolator PRESS_IN = new PathInterpolator(.2f, 0f, .2f, 1f);
    private static final PathInterpolator PRESS_OUT = new PathInterpolator(.33f, 0f, .67f, 1f);

    // OWW221 is AMOLED: a pure black background leaves those pixels physically unlit, which both
    // saves panel power on a long run and gives the bezel-less look a real product has. The old
    // near-black (7,9,10) lit every pixel on screen for no visual gain.
    static final int BLACK = Color.rgb(0, 0, 0);
    // Watch workout palette: true-black canvas, Apple's neutral greys and one semantic colour
    // per metric family. Bright colours are reserved for live data, never used as decoration.
    static final int PANEL = Color.rgb(28, 28, 30);
    static final int PANEL_ACTIVE = Color.rgb(44, 44, 46);
    static final int WHITE = Color.rgb(248, 248, 250);
    static final int MUTED = Color.rgb(142, 142, 147);
    static final int LINE = Color.rgb(44, 44, 46);
    static final int LIME = Color.rgb(184, 255, 47);
    static final int YELLOW = Color.rgb(255, 214, 10);
    static final int CYAN = Color.rgb(100, 210, 255);
    static final int AMBER = Color.rgb(255, 159, 10);
    static final int RED = Color.rgb(255, 55, 95);
    static final int GREEN = Color.rgb(48, 209, 88);

    // One type scale instead of per-screen magic numbers, so headings and labels line up across
    // the home, training, plan and history pages. Figure sizes are measured off the stock
    // HeySports workout screen (378px captures / 1.35 canvas scale).
    static final float DISPLAY = 44f;
    static final float TITLE = 22f;
    static final float HEADLINE = 17f;
    static final float BODY = 13f;
    static final float LABEL = 11f;
    static final float CAPTION = 9.5f;
    /** Stock sports app: the leading elapsed-time figure. */
    static final float FIGURE_HERO = 52f;
    /** Stock sports app: every other metric figure on the workout page. */
    static final float FIGURE = 38f;
    /** Inline unit/label that trails a figure at its baseline. */
    static final float FIGURE_LABEL = 15f;
    /** Page side padding. The stock app runs nearly edge-to-edge. */
    static final float PAGE_MARGIN = 14f;

    private Ui() {}

    static int rgb(int red, int green, int blue) { return Color.rgb(red, green, blue); }

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

    /**
     * Respect the system's readable-text preference without letting a 378 x 496 fixed-height
     * instrument panel collapse. Body copy gets the largest bounded increase; hero workout
     * figures keep their calibrated size because their surrounding unit/label already conveys
     * the value and their rows cannot safely grow.
     */
    private static float textScale(Context context, float sizeDp) {
        float requested = context.getResources().getConfiguration().fontScale;
        if (!Float.isFinite(requested) || requested <= 0f) requested = 1f;
        if (sizeDp > TITLE) return 1f;
        float maximum = sizeDp <= BODY ? 1.25f : sizeDp <= HEADLINE ? 1.20f : 1.12f;
        return Math.max(.90f, Math.min(maximum, requested));
    }

    private static float textPixels(Context context, float sizeDp) {
        return sizeDp * scale(context) * textScale(context, sizeDp);
    }

    private static <T extends TextView> T configureText(
            T view, Context context, String value, float sizeDp, int color) {
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPixels(context, sizeDp));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setLetterSpacing(0);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    static TextView text(Context context, String value, float sizeDp, int color) {
        return configureText(new TextView(context), context, value, sizeDp, color);
    }

    static TextView bold(Context context, String value, float sizeDp, int color) {
        TextView view = text(context, value, sizeDp, color);
        view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        return view;
    }

    // A broad tabular face is closer to the legibility of a modern sports watch than condensed
    // digits. It also keeps punctuation such as 00:18:42 from visually collapsing.
    private static final Typeface NUMERAL_FACE = Typeface.create("sans-serif", Typeface.BOLD);

    /**
     * Big-figure text: condensed bold with tabular digits so a ticking value keeps a fixed width
     * instead of wobbling every second.
     */
    static TextView numeral(Context context, String value, float sizeDp, int color) {
        TextView view = text(context, value, sizeDp, color);
        view.setTypeface(NUMERAL_FACE);
        view.setFontFeatureSettings("tnum");
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

    /** Two-tone disc for the primary start action — the fitness-ring style of fill. */
    static RippleDrawable gradientOvalAction(Context context, int topColor, int bottomColor) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{topColor, bottomColor});
        base.setShape(GradientDrawable.OVAL);
        return new RippleDrawable(
                ColorStateList.valueOf(Color.argb(55, 255, 255, 255)), base, oval(Color.WHITE));
    }

    /**
     * Tonal circular button: a dark fill tinted toward {@code color} with a matching ring.
     *
     * <p>Two fully saturated circles side by side read as unfinished — nothing else on the watch
     * is that loud, and neither action looks more primary than the other. A tonal treatment keeps
     * the colour coding while letting the destructive action sit back from the routine one.
     */
    static RippleDrawable tonalOvalAction(Context context, int color, int fillAlpha, int strokeAlpha) {
        GradientDrawable base = new GradientDrawable();
        base.setShape(GradientDrawable.OVAL);
        base.setColor(Color.argb(fillAlpha, Color.red(color), Color.green(color), Color.blue(color)));
        base.setStroke(dp(context, 1.5f),
                Color.argb(strokeAlpha, Color.red(color), Color.green(color), Color.blue(color)));
        return new RippleDrawable(
                ColorStateList.valueOf(Color.argb(60, Color.red(color), Color.green(color), Color.blue(color))),
                base,
                oval(Color.WHITE));
    }

    static GradientDrawable outlinedBackground(Context context, int color, int stroke, float radiusDp) {
        GradientDrawable drawable = background(context, color, radiusDp);
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    static TextView action(Context context, String value, float sizeDp, int foreground, int background) {
        TextView view = configureText(new MinimumTouchTargetTextView(context),
                context, value, sizeDp, foreground);
        view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), background(context, background, 16), null));
        view.setClickable(true);
        view.setFocusable(true);
        pressable(view);
        return view;
    }

    /**
     * Tactile press treatment shared by real actions. The scale animation runs on RenderThread;
     * it adds physical feedback without forcing a layout pass or allocating a new drawable.
     */
    static <T extends View> T pressable(T view) {
        int minimum = dp(view.getContext(), MIN_TOUCH_TARGET);
        view.setMinimumWidth(Math.max(view.getMinimumWidth(), minimum));
        view.setMinimumHeight(Math.max(view.getMinimumHeight(), minimum));
        view.setOnTouchListener((target, event) -> {
            if (!target.isEnabled()) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                target.animate().cancel();
                target.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.90f)
                        .setDuration(66L)
                        .setInterpolator(PRESS_IN)
                        .start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                target.animate().cancel();
                target.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(150L)
                        .setInterpolator(PRESS_OUT)
                        .start();
                // A pager/ScrollView cancels the child once the gesture becomes navigation.
                // Confirm only a real in-bounds release, so swiping from a large button does not
                // feel like an accidental tap.
                if (action == MotionEvent.ACTION_UP
                        && event.getX() >= 0f && event.getX() < target.getWidth()
                        && event.getY() >= 0f && event.getY() < target.getHeight()) {
                    target.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
            // Let the View keep ownership of click/long-click and ripple semantics.
            return false;
        });
        return view;
    }

    /**
     * Layouts predating the accessibility pass still request 34/36dp back discs. Android's
     * ordinary minimum size loses to an EXACTLY spec, so actions enforce the 40dp hit target at
     * measurement time. The largest growth is 6dp and fits the existing 40dp watch headers.
     */
    private static final class MinimumTouchTargetTextView extends TextView {
        private final int minimumTarget;

        MinimumTouchTargetTextView(Context context) {
            super(context);
            minimumTarget = dp(context, MIN_TOUCH_TARGET);
            setMinimumWidth(minimumTarget);
            setMinimumHeight(minimumTarget);
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(Math.max(getMeasuredWidth(), minimumTarget),
                    Math.max(getMeasuredHeight(), minimumTarget));
        }
    }

    /** One-shot entrance for countdown figures and transient workout cards. */
    static void popIn(View view) {
        view.animate().cancel();
        view.setAlpha(0.18f);
        view.setScaleX(0.72f);
        view.setScaleY(0.72f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(220L).setInterpolator(PRESS_OUT).start();
    }

    /** Compact live-state chip used beside a page title or GPS readout. */
    static TextView chip(Context context, String value, int foreground, int background) {
        TextView view = bold(context, value, LABEL, foreground);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        view.setBackground(background(context, background, 14));
        return view;
    }

    /**
     * Apple-style metric cell: semantic label first, large tabular figure below, small unit on
     * the same baseline. The caller receives the figure so the one-second refresh only updates
     * the changing value.
     */
    static TextView metricCell(Context context, LinearLayout row, String label, String initial,
                               String unit, int color, float figureSize) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        TextView caption = bold(context, label, LABEL, color);
        cell.addView(caption, new LinearLayout.LayoutParams(-1, dp(context, 18)));
        LinearLayout figure = new LinearLayout(context);
        figure.setGravity(Gravity.BOTTOM);
        TextView value = numeral(context, initial, figureSize, color);
        figure.addView(value, new LinearLayout.LayoutParams(-2, -1));
        if (unit != null && !unit.isEmpty()) {
            TextView suffix = text(context, unit, LABEL, MUTED);
            LinearLayout.LayoutParams suffixParams = new LinearLayout.LayoutParams(-2, -1);
            suffixParams.leftMargin = dp(context, 5);
            figure.addView(suffix, suffixParams);
        }
        cell.addView(figure, new LinearLayout.LayoutParams(-1, 0, 1));
        row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        return value;
    }

    /**
     * Original interval-route mark shared with the phone's WORKOUT symbol language.
     *
     * <p>A single open route and forward action stay crisp at 34dp and avoid the anatomical noise
     * of the previous tiny runner. Geometry is code-native and scales from a normalized viewport;
     * no font, vendor glyph or bitmap is involved.</p>
     */
    static final class WorkoutGlyph extends View {
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint route = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint action = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path actionPath = new Path();
        private final RectF routeBounds = new RectF();
        private final int color;
        private float left, top, scale;

        WorkoutGlyph(Context context, int color) {
            super(context);
            this.color = color;
            route.setStyle(Paint.Style.STROKE);
            route.setStrokeCap(Paint.Cap.ROUND);
            route.setStrokeJoin(Paint.Join.ROUND);
            action.setStyle(Paint.Style.FILL);
            // Every current instance sits next to a semantic title or inside an already-labelled
            // card. Announcing a generic "训练" for the decoration only duplicates TalkBack.
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private float x(float value) { return left + value * scale; }
        private float y(float value) { return top + value * scale; }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            scale = Math.min(width, height);
            left = (width - scale) / 2f;
            top = (height - scale) / 2f;

            halo.setShader(new RadialGradient(x(.50f), y(.50f), scale * .50f,
                    Color.argb(54, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(7, Color.red(color), Color.green(color), Color.blue(color)),
                    Shader.TileMode.CLAMP));
            route.setShader(null);
            action.setShader(null);
            route.setColor(color);
            action.setColor(color);
            route.setStrokeWidth(scale * .078f);
            routeBounds.set(x(.20f), y(.20f), x(.80f), y(.80f));

            // Same open-ring/forward-action grammar as PhoneSymbol.WORKOUT, tightened for 34dp.
            actionPath.reset();
            actionPath.moveTo(x(.43f), y(.36f));
            actionPath.lineTo(x(.68f), y(.50f));
            actionPath.lineTo(x(.43f), y(.64f));
            actionPath.close();
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            if (scale <= 0f) return;
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, scale * .48f, halo);
            canvas.drawArc(routeBounds, -78f, 293f, false, route);
            canvas.drawPath(actionPath, action);
        }
    }

    static WorkoutGlyph workoutGlyph(Context context, int color) {
        return new WorkoutGlyph(context, color);
    }

    /**
     * A truthful live heart-rate trace. Samples are added from real service snapshots only;
     * missing heart data leaves the graph empty instead of drawing a decorative fake waveform.
     */
    static final class HeartTrace extends View {
        private static final int MAX_SAMPLES = 48;
        private final int[] samples = new int[MAX_SAMPLES];
        private final int[] scratch = new int[MAX_SAMPLES];
        private int count;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path linePath = new Path();
        private final Path fillPath = new Path();
        private boolean pathDirty = true;
        private boolean expanded;

        HeartTrace(Context context) {
            super(context);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(dp(context, 2.4f));
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(RED);
            fill.setStyle(Paint.Style.FILL);
            panel.setColor(Color.rgb(12, 14, 16));
            guide.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            guide.setTextSize(textPixels(context, 11f));
        }

        void setExpanded(boolean value) {
            if (expanded == value) return;
            expanded = value;
            pathDirty = true;
            invalidate();
        }

        void addSample(int value) {
            if (value < 25 || value > 240) return;
            if (count < MAX_SAMPLES) samples[count++] = value;
            else {
                System.arraycopy(samples, 1, samples, 0, MAX_SAMPLES - 1);
                samples[MAX_SAMPLES - 1] = value;
            }
            pathDirty = true;
            invalidate();
        }

        void setSamples(java.util.List<Integer> values) {
            if (values == null || values.isEmpty()) { applyScratch(0); return; }
            int size = values.size();
            int nextCount = 0;
            for (int slot = 0; slot < Math.min(MAX_SAMPLES, size); slot++) {
                int index = Math.min(size - 1,
                        Math.round(slot * (size - 1f) / Math.max(1, Math.min(MAX_SAMPLES, size) - 1)));
                Integer value = values.get(index);
                if (value != null && value >= 25 && value <= 240) scratch[nextCount++] = value;
            }
            applyScratch(nextCount);
        }

        void setSamples(int[] values) {
            if (values == null || values.length == 0) { applyScratch(0); return; }
            int size = values.length;
            int nextCount = 0;
            for (int slot = 0; slot < Math.min(MAX_SAMPLES, size); slot++) {
                int index = Math.min(size - 1,
                        Math.round(slot * (size - 1f) / Math.max(1, Math.min(MAX_SAMPLES, size) - 1)));
                int value = values[index];
                if (value >= 25 && value <= 240) scratch[nextCount++] = value;
            }
            applyScratch(nextCount);
        }

        private void applyScratch(int nextCount) {
            if (nextCount == count) {
                boolean equal = true;
                for (int index = 0; index < nextCount; index++) {
                    if (samples[index] != scratch[index]) { equal = false; break; }
                }
                if (equal) return;
            }
            if (nextCount > 0) System.arraycopy(scratch, 0, samples, 0, nextCount);
            count = nextCount;
            pathDirty = true;
            invalidate();
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            fill.setShader(new LinearGradient(0, 0, 0, Math.max(1, height),
                    Color.argb(90, 255, 55, 95), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            pathDirty = true;
        }

        private void rebuildPaths() {
            linePath.reset();
            fillPath.reset();
            pathDirty = false;
            if (count < 2 || getWidth() <= 0 || getHeight() <= 0) return;
            int min = samples[0], max = samples[0];
            for (int index = 1; index < count; index++) {
                min = Math.min(min, samples[index]);
                max = Math.max(max, samples[index]);
            }
            int spread = Math.max(12, max - min);
            float top = expanded ? dp(getContext(), 25) : dp(getContext(), 3);
            float bottom = expanded ? dp(getContext(), 7) : dp(getContext(), 3);
            float usableHeight = Math.max(1f, getHeight() - top - bottom);
            for (int index = 0; index < count; index++) {
                float x = count == 1 ? 0 : index * (getWidth() - 1f) / (count - 1f);
                float y = top + (max - samples[index] + (spread - (max - min)) / 2f)
                        * usableHeight / spread;
                if (index == 0) {
                    linePath.moveTo(x, y);
                    fillPath.moveTo(x, getHeight() - bottom);
                    fillPath.lineTo(x, y);
                } else {
                    linePath.lineTo(x, y);
                    fillPath.lineTo(x, y);
                }
            }
            fillPath.lineTo(getWidth(), getHeight() - bottom);
            fillPath.close();
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            if (expanded) {
                float radius = dp(getContext(), 12);
                canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, panel);
                guide.setColor(MUTED);
                guide.setTextAlign(Paint.Align.LEFT);
                canvas.drawText("实时心率趋势", dp(getContext(), 10), dp(getContext(), 17), guide);
                if (count < 2) {
                    guide.setColor(Color.rgb(112, 112, 118));
                    guide.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText("佩戴后显示真实曲线",
                            getWidth() - dp(getContext(), 10), dp(getContext(), 17), guide);
                }
            }
            if (pathDirty) rebuildPaths();
            if (count < 2) return;
            canvas.drawPath(fillPath, fill);
            canvas.drawPath(linePath, line);
        }
    }

    static TextView iconAction(Context context, int drawableRes, String description, int foreground, int background) {
        TextView view = action(context, "", 1, foreground, background);
        view.setCompoundDrawablesWithIntrinsicBounds(drawableRes, 0, 0, 0);
        view.setCompoundDrawableTintList(ColorStateList.valueOf(foreground));
        view.setContentDescription(description);
        return view;
    }

    /**
     * Metric line in the stock sports idiom: a big figure with its unit/label trailing the
     * baseline ("0 公里", "--'--" 配速"), not pushed to the far edge. Returns the figure view;
     * the label view is returned via {@code labelOut[0]} when the caller needs to update it.
     */
    static TextView figureLine(Context context, LinearLayout parent, String initial, String label,
                               int color, float figureSize, float rowHeight, TextView[] labelOut) {
        LinearLayout row = new LinearLayout(context);
        // Baseline alignment keeps the small label sitting on the figure's baseline.
        TextView value = numeral(context, initial, figureSize, color);
        row.addView(value, new LinearLayout.LayoutParams(-2, -2));
        TextView caption = text(context, label, FIGURE_LABEL, MUTED);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-2, -2);
        captionParams.leftMargin = dp(context, 6);
        row.addView(caption, captionParams);
        if (labelOut != null && labelOut.length > 0) labelOut[0] = caption;
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(context, rowHeight)));
        return value;
    }

    /** Zone colours indexed 1..5 — the classic blue/green/yellow/orange/red training bands. */
    static final int[] ZONE_COLORS = {CYAN, GREEN, YELLOW, AMBER, RED};

    /**
     * Five-segment heart-rate zone band, the visual every serious running watch carries. The
     * current zone's segment is lit solid; the rest stay dim so intensity reads at a glance
     * without any numbers.
     */
    static final class ZoneBar extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();
        private int zone;

        ZoneBar(Context context) { super(context); }

        void set(int value) {
            int clamped = Math.max(0, Math.min(5, value));
            if (clamped == zone) return;
            zone = clamped;
            invalidate();
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float gap = dp(getContext(), 3);
            float segment = (getWidth() - gap * 4f) / 5f;
            float radius = getHeight() / 2f;
            for (int index = 0; index < 5; index++) {
                boolean current = zone == index + 1;
                int color = ZONE_COLORS[index];
                paint.setColor(current ? color
                        : Color.argb(zone == 0 ? 46 : 34, Color.red(color), Color.green(color), Color.blue(color)));
                float left = index * (segment + gap);
                float top = current ? 0f : getHeight() * 0.18f;
                float bottom = current ? getHeight() : getHeight() * 0.82f;
                rect.set(left, top, left + segment, bottom);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
        }
    }

    /** Top bar shared by every page: small title left, live clock right, stock-sports style. */
    static TextView topBar(Context context, LinearLayout parent, TextView titleView) {
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1));
        TextView clock = numeral(context, "", 20, WHITE);
        clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bar.addView(clock, new LinearLayout.LayoutParams(dp(context, 64), -1));
        parent.addView(bar, new LinearLayout.LayoutParams(-1, dp(context, 34)));
        return clock;
    }

    /**
     * Garmin-style grid cell: big figure over a small label, filling an equal share of its row.
     * Returns the figure view for updates.
     */
    static TextView gridCell(Context context, LinearLayout row, String initial, String label,
                             int color, float figureSize) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        TextView value = numeral(context, initial, figureSize, color);
        cell.addView(value, new LinearLayout.LayoutParams(-2, -2));
        TextView caption = text(context, label, CAPTION, MUTED);
        cell.addView(caption, new LinearLayout.LayoutParams(-2, -2));
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        return value;
    }

    /** Soft radial glow behind a primary action disc, as on the stock start button. */
    static View glow(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        drawable.setGradientRadius(dp(context, radiusDp));
        drawable.setColors(new int[]{
                Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))});
        View view = new View(context);
        view.setBackground(drawable);
        return view;
    }

    /** Circular back affordance shared by every secondary screen (minimum 40dp hit target). */
    static TextView backButton(Context context) {
        TextView view = configureText(new MinimumTouchTargetTextView(context),
                context, "‹", 22, WHITE);
        view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), oval(PANEL), null));
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription("返回");
        pressable(view);
        return view;
    }

    /**
     * Plan step row: stage-colour accent bar, muted order number, stage name, target figure
     * right-aligned in the numeral face. The previous rows glued index, name and target into one
     * plain string — the last "three fields in a sentence" list left after the instrument
     * redesign, with no hierarchy and none of the stage colour semantics the training pages use.
     */
    static LinearLayout stageRow(Context context, int index, Stage stage, int backgroundColor) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(background(context, backgroundColor, 14));
        row.setPadding(dp(context, 12), 0, dp(context, 14), 0);
        View accent = new View(context);
        accent.setBackground(background(context, stageColor(stage.kind), 2));
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(context, 3.5f), dp(context, 16));
        accentParams.rightMargin = dp(context, 10);
        row.addView(accent, accentParams);
        TextView order = text(context, String.valueOf(index), BODY, MUTED);
        row.addView(order, new LinearLayout.LayoutParams(dp(context, 22), -1));
        TextView name = bold(context, stage.name(), BODY, WHITE);
        row.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
        TextView target = numeral(context, stage.targetText(), 17, WHITE);
        target.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(target, new LinearLayout.LayoutParams(-2, -1));
        return row;
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

    /**
     * Page indicator drawn as real geometry.
     *
     * <p>The previous version rendered "●   ○   ○" as text, so the dot size, spacing and vertical
     * alignment were all at the mercy of the font's glyph metrics — the visible unevenness was the
     * single clearest "hand-made" tell on the home screen.
     */
    static final class PagerDots extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float radius, spacing;
        private final int count;
        private int active;

        PagerDots(Context context, int count, int active) {
            super(context);
            this.count = Math.max(1, count);
            this.active = active;
            radius = dp(context, 2.6f);
            spacing = dp(context, 9f);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setActive(int value) {
            if (value == active) return;
            active = value;
            invalidate();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(resolveSize((int)Math.ceil(count * spacing), widthSpec),
                    resolveSize((int)Math.ceil(radius * 4f), heightSpec));
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float centerY = getHeight() / 2f;
            float startX = (getWidth() - (count - 1) * spacing) / 2f;
            for (int index = 0; index < count; index++) {
                boolean current = index == active;
                paint.setColor(current ? WHITE : LINE);
                canvas.drawCircle(startX + index * spacing, centerY, current ? radius : radius * 0.72f, paint);
            }
        }
    }

    static PagerDots pagerDots(Context context, int active, int count) {
        return new PagerDots(context, count, active);
    }

    /**
     * Circular stage-progress ring — the signature sports-watch visual. Track in {@link #LINE},
     * progress arc with round caps in the stage colour, starting at 12 o'clock.
     */
    static final class Ring extends View {
        private final android.graphics.Paint track = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint arc = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF bounds = new android.graphics.RectF();
        private float fraction;

        Ring(Context context) {
            super(context);
            float stroke = dp(context, 9);
            track.setStyle(android.graphics.Paint.Style.STROKE);
            track.setStrokeWidth(stroke);
            track.setColor(LINE);
            arc.setStyle(android.graphics.Paint.Style.STROKE);
            arc.setStrokeWidth(stroke);
            arc.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            arc.setColor(LIME);
        }

        void set(float value, int color) {
            float clamped = Math.max(0f, Math.min(1f, value));
            if (clamped == fraction && color == arc.getColor()) return;
            fraction = clamped;
            arc.setColor(color);
            invalidate();
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float inset = track.getStrokeWidth() / 2f + dp(getContext(), 1);
            bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawArc(bounds, 0f, 360f, false, track);
            if (fraction > 0f) canvas.drawArc(bounds, -90f, fraction * 360f, false, arc);
        }
    }

    /** Skips the relayout that {@link TextView#setText} forces even when the string is unchanged. */
    static void setTextIfChanged(TextView view, CharSequence value) {
        if (view == null || value == null) return;
        if (!value.toString().contentEquals(view.getText())) view.setText(value);
    }

    /** Avoids invalidating text display lists when a semantic colour has not changed. */
    static void setTextColorIfChanged(TextView view, int color) {
        if (view != null && view.getCurrentTextColor() != color) view.setTextColor(color);
    }

    static void setTextAndColorIfChanged(TextView view, CharSequence value, int color) {
        setTextIfChanged(view, value);
        setTextColorIfChanged(view, color);
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

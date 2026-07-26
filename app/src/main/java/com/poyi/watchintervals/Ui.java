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

    // OWW221 is AMOLED: a pure black background leaves those pixels physically unlit, which both
    // saves panel power on a long run and gives the bezel-less look a real product has. The old
    // near-black (7,9,10) lit every pixel on screen for no visual gain.
    static final int BLACK = Color.rgb(0, 0, 0);
    static final int PANEL = Color.rgb(19, 21, 24);
    static final int PANEL_ACTIVE = Color.rgb(32, 35, 39);
    static final int WHITE = Color.rgb(245, 246, 247);
    static final int MUTED = Color.rgb(138, 145, 153);
    static final int LINE = Color.rgb(42, 45, 50);
    static final int LIME = Color.rgb(190, 255, 71);
    static final int YELLOW = Color.rgb(255, 215, 52);
    static final int CYAN = Color.rgb(83, 218, 229);
    static final int AMBER = Color.rgb(255, 183, 66);
    static final int RED = Color.rgb(255, 86, 79);
    static final int GREEN = Color.rgb(70, 226, 129);

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

    // Roboto Condensed ships with AOSP; on watch dials it is the difference between "settings
    // screen" and "sports instrument". Falls back to default sans if the vendor stripped it.
    private static final Typeface NUMERAL_FACE = Typeface.create("sans-serif-condensed", Typeface.BOLD);

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

    /** Circular back affordance shared by every secondary screen (36dp panel disc with "‹"). */
    static TextView backButton(Context context) {
        TextView view = bold(context, "‹", 22, WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), oval(PANEL), null));
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription("返回");
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

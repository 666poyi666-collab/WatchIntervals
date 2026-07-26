package com.poyi.watchintervals;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

/**
 * Watch Sports style route map.
 *
 * The project packages the matching Baidu 7.5.9 engine and dark style, but an
 * independent companion signature cannot reuse the system application's
 * package-bound map credential.  This renderer keeps the same WGS84 -> China
 * map coordinate chain and visual language while using the already available
 * road-tile transport, so route viewing stays useful offline from that
 * credential and the pager never becomes a black surface.
 */
final class WorkoutRouteView extends FrameLayout {
    private final MapView mapView;
    private final TextView empty;
    private int renderedCount = -1;
    private double renderedLastLatitude = Double.NaN;
    private double renderedLastLongitude = Double.NaN;

    WorkoutRouteView(Context context) {
        super(context);
        Configuration.getInstance().setUserAgentValue(context.getPackageName());
        setBackground(Ui.background(context, Color.rgb(18, 22, 23), 18));
        setClipToOutline(true);

        mapView = new MapView(context);
        mapView.setTileSource(new AmapTileSource());
        mapView.setTilesScaledToDpi(true);
        mapView.setMultiTouchControls(false);
        mapView.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        mapView.setBuiltInZoomControls(false);
        mapView.setMinZoomLevel(3d);
        mapView.setMaxZoomLevel(20d);
        mapView.getController().setZoom(14d);
        mapView.getController().setCenter(AmapTileSource.fromWgs84(39.915, 116.404));
        applyDarkTileFilter();
        // The parent pager observes/intercepts every MOVE; the map itself does
        // not pan, zoom, or retain a horizontal gesture.
        mapView.setOnTouchListener((v, event) -> true);
        addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        empty = new TextView(context);
        empty.setText("等待有效定位轨迹");
        empty.setTextColor(Ui.MUTED);
        empty.setTextSize(12);
        empty.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(Ui.background(context, Color.argb(230, 31, 35, 36), 16));
        FrameLayout.LayoutParams ep =
                new FrameLayout.LayoutParams(-1, Ui.dp(context, 58), Gravity.CENTER);
        ep.leftMargin = Ui.dp(context, 28);
        ep.rightMargin = Ui.dp(context, 28);
        addView(empty, ep);
    }

    private void applyDarkTileFilter() {
        // Invert luminance rather than scaling it down. The previous matrix multiplied every
        // channel by ~0.1 and then added a constant, so a blank map tile (near-white, which is
        // most of a raster basemap) landed on RGB(58,71,80): a flat slate slab that read as a
        // broken placeholder instead of a dark map.
        //
        // Inverting luminance and keeping the result near-grey gives the real thing: white paper
        // becomes near-black, dark roads and labels become light, and a plain colour inversion's
        // hue flip (green parks turning purple) never happens because hue is discarded.
        final float weight = 0.80f;
        final float lumR = 0.299f * weight, lumG = 0.587f * weight, lumB = 0.114f * weight;
        ColorMatrix dark = new ColorMatrix(new float[] {
            -lumR, -lumG, -lumB, 0, 200,
            -lumR, -lumG, -lumB, 0, 204,
            -lumR, -lumG, -lumB, 0, 212,   // a touch more blue keeps it from reading brown
            0, 0, 0, 1, 0
        });
        mapView.getOverlayManager().getTilesOverlay()
                .setColorFilter(new ColorMatrixColorFilter(dark));
        // Without these, osmdroid paints its default slate-grey placeholder wherever a tile has
        // not loaded. Outdoors with no data connection that is the entire panel, which made the
        // map look like an unfinished placeholder rather than a dark map.
        mapView.setBackgroundColor(Ui.BLACK);
        mapView.getOverlayManager().getTilesOverlay().setLoadingBackgroundColor(Color.rgb(14, 16, 18));
        mapView.getOverlayManager().getTilesOverlay().setLoadingLineColor(Color.rgb(26, 29, 32));
    }

    void setRoute(double[] latitudes, double[] longitudes) {
        int count = Math.min(
                latitudes == null ? 0 : latitudes.length,
                longitudes == null ? 0 : longitudes.length);
        double lastLat = count == 0 ? Double.NaN : latitudes[count - 1];
        double lastLon = count == 0 ? Double.NaN : longitudes[count - 1];
        if (count == renderedCount
                && Double.compare(lastLat, renderedLastLatitude) == 0
                && Double.compare(lastLon, renderedLastLongitude) == 0) return;
        renderedCount = count;
        renderedLastLatitude = lastLat;
        renderedLastLongitude = lastLon;
        mapView.getOverlays().clear();

        ArrayList<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (Double.isFinite(latitudes[i]) && Double.isFinite(longitudes[i])) {
                points.add(AmapTileSource.fromWgs84(latitudes[i], longitudes[i]));
            }
        }
        if (points.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            mapView.invalidate();
            return;
        }
        empty.setVisibility(View.GONE);

        if (points.size() > 1) {
            Polyline route = new Polyline(mapView);
            route.setPoints(points);
            route.getOutlinePaint().setColor(Ui.LIME);
            route.getOutlinePaint().setStrokeWidth(Ui.dp(getContext(), 5));
            route.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
            route.getOutlinePaint().setStrokeJoin(Paint.Join.ROUND);
            mapView.getOverlays().add(route);
        }
        addMarker(points.get(0), Ui.RED, false, "起点");
        addMarker(points.get(points.size() - 1), Color.WHITE, true, "当前位置");

        if (points.size() == 1) {
            mapView.getController().setZoom(17d);
            mapView.getController().animateTo(points.get(0));
        } else {
            double north = -90, south = 90, east = -180, west = 180;
            for (GeoPoint p : points) {
                north = Math.max(north, p.getLatitude());
                south = Math.min(south, p.getLatitude());
                east = Math.max(east, p.getLongitude());
                west = Math.min(west, p.getLongitude());
            }
            BoundingBox box = new BoundingBox(north, east, south, west);
            post(() -> mapView.zoomToBoundingBox(box, true, Ui.dp(getContext(), 30), 18d, 300L));
        }
        mapView.invalidate();
    }

    private void addMarker(GeoPoint point, int color, boolean hollow, String title) {
        Marker marker = new Marker(mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle(title);
        marker.setIcon(new BitmapDrawable(getResources(), markerBitmap(color, hollow)));
        mapView.getOverlays().add(marker);
    }

    private Bitmap markerBitmap(int color, boolean hollow) {
        int size = Ui.dp(getContext(), 17);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(hollow ? Paint.Style.STROKE : Paint.Style.FILL);
        paint.setStrokeWidth(Ui.dp(getContext(), 3));
        paint.setColor(color);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - Ui.dp(getContext(), 2), paint);
        if (!hollow) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(getContext(), 2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - Ui.dp(getContext(), 1), paint);
        }
        return bitmap;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mapView.onResume();
    }

    @Override protected void onDetachedFromWindow() {
        mapView.onPause();
        mapView.onDetach();
        super.onDetachedFromWindow();
    }
}

package com.poyi.watchintervals;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

final class WorkoutMetricsAccumulator {
    static final long WINDOW_MILLIS = 10_000L;
    static final long STALE_MILLIS = 5_000L;
    static final long MIN_COVERAGE_MILLIS = 4_000L;

    enum Source {
        SYSTEM_EXERCISE("system_exercise", false),
        WATCH_GPS("watch_gps", false),
        PHONE_GPS("phone_gps", false),
        STEPS_ESTIMATE("steps_estimate", true);

        final String wireName;
        final boolean estimated;
        Source(String wireName, boolean estimated) {
            this.wireName = wireName;
            this.estimated = estimated;
        }
    }

    private static final class Delta {
        final long at;
        final double meters;
        Delta(long at, double meters) { this.at = at; this.meters = meters; }
    }

    private final ArrayDeque<Delta> window = new ArrayDeque<>();
    private final LinkedHashMap<Source, Double> distanceBySource = new LinkedHashMap<>();
    private Source source;
    private long windowStartedAt;
    private long lastDeltaAt;
    private double maxSmoothedSpeedMps;

    WorkoutMetricsAccumulator() {
        for (Source value : Source.values()) distanceBySource.put(value, 0d);
    }

    void add(long at, double meters, Source nextSource) {
        if (meters <= 0d || !Double.isFinite(meters) || nextSource == null) return;
        if (source != nextSource) {
            resetWindow();
            source = nextSource;
        }
        if (window.isEmpty()) windowStartedAt = at;
        window.addLast(new Delta(at, meters));
        lastDeltaAt = at;
        distanceBySource.put(nextSource, distanceBySource.get(nextSource) + meters);
        trim(at);
        double speed = currentSpeedMps(at);
        if (Double.isFinite(speed)) maxSmoothedSpeedMps = Math.max(maxSmoothedSpeedMps, speed);
    }

    void resetWindow() {
        window.clear();
        windowStartedAt = 0L;
        lastDeltaAt = 0L;
    }

    double currentSpeedMps(long now) {
        trim(now);
        if (window.isEmpty() || now - lastDeltaAt > STALE_MILLIS) return Double.NaN;
        long coverage = lastDeltaAt - windowStartedAt;
        if (coverage < MIN_COVERAGE_MILLIS) return Double.NaN;
        double meters = 0d;
        for (Delta delta : window) meters += delta.meters;
        return coverage > 0L ? meters * 1000d / coverage : Double.NaN;
    }

    boolean currentSpeedEstimated() { return source != null && source.estimated; }
    double maxSmoothedSpeedMps() { return maxSmoothedSpeedMps; }
    Source currentSource() { return source; }

    Map<String, Double> distanceBySource() {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Source, Double> entry : distanceBySource.entrySet()) {
            result.put(entry.getKey().wireName, entry.getValue());
        }
        return result;
    }

    void restoreDistance(Source value, double meters) {
        if (value != null && Double.isFinite(meters) && meters >= 0d) distanceBySource.put(value, meters);
    }

    void restoreMaxSpeed(double speed) {
        if (Double.isFinite(speed) && speed >= 0d) maxSmoothedSpeedMps = speed;
    }

    private void trim(long now) {
        long cutoff = now - WINDOW_MILLIS;
        while (!window.isEmpty() && window.peekFirst().at < cutoff) window.removeFirst();
        if (!window.isEmpty()) windowStartedAt = window.peekFirst().at;
    }
}

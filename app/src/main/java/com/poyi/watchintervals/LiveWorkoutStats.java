package com.poyi.watchintervals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * In-run metrics a serious runner steers by, computed live instead of after the fact.
 *
 * <p>Historically splits, climb and heart aggregates were derived once at save time from the
 * 600-point preview polyline. During the workout the screen only had current pace, distance and
 * raw bpm — nowhere near what Garmin/COROS/Apple show mid-run. This class accumulates:
 *
 * <ul>
 *   <li>automatic 1 km splits with pace, as they happen (drives the lap card + buzz)</li>
 *   <li>cadence over a sliding window of step counts</li>
 *   <li>total ascent from smoothed GPS altitude</li>
 *   <li>average / maximum heart rate and the current heart-rate zone</li>
 *   <li>a distance-based calorie estimate</li>
 * </ul>
 *
 * <p>Pure Java on purpose: every rule here is unit-testable without a device.
 */
final class LiveWorkoutStats {
    /** Default zone ceiling when the wearer has not configured one (220 - ~30y). */
    static final int DEFAULT_MAX_HEART_RATE = 190;
    /** Z1 starts at 50% of max; below that we call it warm-up (zone 0). */
    private static final double[] ZONE_FLOORS = {0.50d, 0.60d, 0.70d, 0.80d, 0.90d};
    /** Cadence window; Garmin uses a comparable smoothing span. */
    private static final long CADENCE_WINDOW_MS = 20_000L;
    private static final long CADENCE_MIN_SPAN_MS = 8_000L;
    /** Altitude changes below this are GPS noise, not climbing (OpenTracks uses a similar gate). */
    private static final double CLIMB_THRESHOLD_METERS = 2.0d;
    /** EMA factor for altitude smoothing; ~5 samples to settle. */
    private static final double ALTITUDE_SMOOTHING = 0.35d;
    /** Rough net running cost per kilogram per kilometre. */
    private static final double KCAL_PER_KG_KM = 1.036d;
    private static final double DEFAULT_WEIGHT_KG = 65d;

    /** One completed kilometre. */
    static final class Split {
        final int index;            // 1-based kilometre number
        final long durationMs;      // active time spent on this kilometre
        final int paceSecondsPerKm;
        Split(int index, long durationMs) {
            this.index = index;
            this.durationMs = durationMs;
            this.paceSecondsPerKm = (int)Math.round(durationMs / 1000d);
        }
    }

    private static final class StepSample {
        final long activeMs; final int steps;
        StepSample(long activeMs, int steps) { this.activeMs = activeMs; this.steps = steps; }
    }

    private final int maxHeartRate;
    private final ArrayDeque<StepSample> stepWindow = new ArrayDeque<>();
    private final List<Split> splits = new ArrayList<>();

    private long heartTotal; private int heartSamples; private int maxHeart;
    private double smoothedAltitude = Double.NaN;
    private double climbBaseline = Double.NaN;
    private double elevationGain;
    private long splitStartActiveMs;
    private long lastSplitAtActiveMs = Long.MIN_VALUE;

    LiveWorkoutStats() { this(DEFAULT_MAX_HEART_RATE); }
    LiveWorkoutStats(int maxHeartRate) { this.maxHeartRate = Math.max(120, maxHeartRate); }

    /** Feed once per second with the running active-time clock and cumulative session steps. */
    void onTick(long activeMs, int sessionSteps) {
        stepWindow.addLast(new StepSample(activeMs, sessionSteps));
        while (!stepWindow.isEmpty() && activeMs - stepWindow.peekFirst().activeMs > CADENCE_WINDOW_MS) {
            stepWindow.removeFirst();
        }
    }

    void onHeartRate(int bpm) {
        if (bpm <= 0) return;
        heartTotal += bpm; heartSamples++;
        if (bpm > maxHeart) maxHeart = bpm;
    }

    /** Feed raw GPS altitude (metres); noise is smoothed and only sustained rises count. */
    void onAltitude(double altitudeMeters) {
        if (!Double.isFinite(altitudeMeters)) return;
        if (Double.isNaN(smoothedAltitude)) {
            smoothedAltitude = altitudeMeters;
            climbBaseline = altitudeMeters;
            return;
        }
        smoothedAltitude += ALTITUDE_SMOOTHING * (altitudeMeters - smoothedAltitude);
        if (smoothedAltitude - climbBaseline >= CLIMB_THRESHOLD_METERS) {
            elevationGain += smoothedAltitude - climbBaseline;
            climbBaseline = smoothedAltitude;
        } else if (smoothedAltitude < climbBaseline) {
            // Descending: the next climb is measured from the new low, not the old peak.
            climbBaseline = smoothedAltitude;
        }
    }

    /**
     * Feed after total distance advances. Returns the newly completed split, or null. A single
     * update can cross at most one boundary per call in practice; loop anyway for robustness.
     */
    Split onDistance(double totalMeters, long activeMs) {
        Split newest = null;
        while (totalMeters >= (splits.size() + 1) * 1000d) {
            long duration = Math.max(1L, activeMs - splitStartActiveMs);
            newest = new Split(splits.size() + 1, duration);
            splits.add(newest);
            splitStartActiveMs = activeMs;
            lastSplitAtActiveMs = activeMs;
        }
        return newest;
    }

    int cadenceSpm(long activeMs) {
        if (stepWindow.size() < 2) return 0;
        StepSample first = stepWindow.peekFirst(), last = stepWindow.peekLast();
        long span = last.activeMs - first.activeMs;
        if (span < CADENCE_MIN_SPAN_MS) return 0;
        int steps = last.steps - first.steps;
        if (steps <= 0) return 0;
        return (int)Math.round(steps * 60_000d / span);
    }

    int averageHeartRate() { return heartSamples > 0 ? (int)Math.round((double)heartTotal / heartSamples) : 0; }
    int maxHeartRateSeen() { return maxHeart; }

    /** 0 = below Z1 (warm-up / no reading), 1..5 = the classic five training zones. */
    int heartRateZone(int currentBpm) {
        if (currentBpm <= 0) return 0;
        double fraction = currentBpm / (double)maxHeartRate;
        int zone = 0;
        for (double floor : ZONE_FLOORS) if (fraction >= floor) zone++;
        return zone;
    }

    double elevationGainMeters() { return elevationGain; }

    int calories(double totalMeters) {
        return (int)Math.round(KCAL_PER_KG_KM * DEFAULT_WEIGHT_KG * totalMeters / 1000d);
    }

    /** Average pace over the whole session, seconds per km; 0 while too short to be meaningful. */
    static int averagePaceSecondsPerKm(double totalMeters, long activeMs) {
        if (totalMeters < 50d || activeMs <= 0) return 0;
        return (int)Math.round(activeMs / 1000d * 1000d / totalMeters);
    }

    int splitCount() { return splits.size(); }
    Split lastSplit() { return splits.isEmpty() ? null : splits.get(splits.size() - 1); }
    /** Active-time stamp of the newest split, for the UI to detect fresh laps. */
    long lastSplitAtActiveMs() { return lastSplitAtActiveMs; }
    List<Split> splits() { return splits; }

    /** Restores the split boundary after process recovery so km counting stays aligned. */
    void restore(double totalMeters, long activeMs) {
        int completed = (int)(totalMeters / 1000d);
        splits.clear();
        for (int index = 1; index <= completed; index++) {
            // Durations of pre-recovery kilometres are unknown; approximate with the average.
            splits.add(new Split(index, completed > 0 ? activeMs / completed : activeMs));
        }
        splitStartActiveMs = activeMs;
    }
}

package com.poyi.watchintervals;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkoutMetricsAccumulatorTest {
    @Test public void requiresCoverageAndExpires() {
        WorkoutMetricsAccumulator metrics = new WorkoutMetricsAccumulator();
        metrics.add(1_000, 2, WorkoutMetricsAccumulator.Source.WATCH_GPS);
        metrics.add(5_000, 6, WorkoutMetricsAccumulator.Source.WATCH_GPS);
        assertEquals(2d, metrics.currentSpeedMps(5_000), .001d);
        assertTrue(Double.isNaN(metrics.currentSpeedMps(11_000)));
    }

    @Test public void sourceChangeResetsWindowAndTracksDistance() {
        WorkoutMetricsAccumulator metrics = new WorkoutMetricsAccumulator();
        metrics.add(1_000, 4, WorkoutMetricsAccumulator.Source.WATCH_GPS);
        metrics.add(5_000, 4, WorkoutMetricsAccumulator.Source.WATCH_GPS);
        assertFalse(Double.isNaN(metrics.currentSpeedMps(5_000)));
        metrics.add(6_000, 1, WorkoutMetricsAccumulator.Source.STEPS_ESTIMATE);
        assertTrue(Double.isNaN(metrics.currentSpeedMps(6_000)));
        assertTrue(metrics.currentSpeedEstimated());
        assertEquals(8d, metrics.distanceBySource().get("watch_gps"), .001d);
        assertEquals(1d, metrics.distanceBySource().get("steps_estimate"), .001d);
    }

    @Test public void mixedSourceStressPreservesAllDistanceWithBoundedWindow() {
        WorkoutMetricsAccumulator metrics = new WorkoutMetricsAccumulator();
        WorkoutMetricsAccumulator.Source[] sources = WorkoutMetricsAccumulator.Source.values();
        double expected = 0d;
        for (int index = 0; index < 14_400; index++) {
            double meters = 0.75d + (index % 4) * 0.25d;
            metrics.add(index * 1_000L, meters, sources[(index / 1_800) % sources.length]);
            expected += meters;
        }
        double actual = 0d;
        for (double value : metrics.distanceBySource().values()) actual += value;
        assertEquals(expected, actual, .001d);
        assertTrue(Double.isFinite(metrics.currentSpeedMps(14_399_000L)));
        assertTrue(Double.isFinite(metrics.maxSmoothedSpeedMps()));
    }
}

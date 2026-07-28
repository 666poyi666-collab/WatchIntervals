package com.poyi.watchintervals;

import static org.junit.Assert.*;
import org.junit.Test;

public class LiveWorkoutStatsTest {
    @Test public void splitsFireOncePerKilometreWithActiveTimePace() {
        LiveWorkoutStats stats = new LiveWorkoutStats();
        assertNull(stats.onDistance(999.9d, 300_000L));
        LiveWorkoutStats.Split first = stats.onDistance(1000.1d, 305_000L);
        assertNotNull(first);
        assertEquals(1, first.index);
        assertEquals(305, first.paceSecondsPerKm);
        assertNull(stats.onDistance(1500d, 400_000L));
        LiveWorkoutStats.Split second = stats.onDistance(2001d, 600_000L);
        assertNotNull(second);
        assertEquals(2, second.index);
        assertEquals(295, second.paceSecondsPerKm);   // 600s - 305s on km 2
        assertEquals(2, stats.splitCount());
    }

    @Test public void aBigDeltaCrossingTwoBoundariesStillCountsBothSplits() {
        LiveWorkoutStats stats = new LiveWorkoutStats();
        stats.onDistance(2050d, 700_000L);
        assertEquals(2, stats.splitCount());
    }

    @Test public void cadenceUsesSlidingWindowAndNeedsMinimumSpan() {
        LiveWorkoutStats stats = new LiveWorkoutStats();
        stats.onTick(1_000L, 0);
        assertEquals(0, stats.cadenceSpm(1_000L));         // span too short
        for (int second = 2; second <= 21; second++) stats.onTick(second * 1_000L, (second - 1) * 3);
        // 3 steps per second sustained = 180 spm.
        assertEquals(180, stats.cadenceSpm(21_000L), 2);
        // A stop shows up: same step count for a while collapses cadence.
        for (int second = 22; second <= 41; second++) stats.onTick(second * 1_000L, 60);
        assertEquals(0, stats.cadenceSpm(41_000L));
    }

    @Test public void climbAccumulatesSustainedRisesAndIgnoresJitter() {
        LiveWorkoutStats stats = new LiveWorkoutStats();
        stats.onAltitude(100d);
        for (int i = 0; i < 30; i++) stats.onAltitude(100d + (i % 2 == 0 ? 0.4d : -0.4d));
        assertEquals(0d, stats.elevationGainMeters(), 0.5d);   // jitter is not climbing
        for (int i = 1; i <= 40; i++) stats.onAltitude(100d + i * 0.5d);   // steady 20 m rise
        assertTrue("expected most of the 20 m climb, got " + stats.elevationGainMeters(),
                stats.elevationGainMeters() > 15d);
        double afterClimb = stats.elevationGainMeters();
        for (int i = 0; i < 40; i++) stats.onAltitude(120d - i * 0.5d);    // descent
        // The smoothed value finishes its lag-catch-up (<3 m) but the descent itself adds nothing.
        assertTrue("descent must not accumulate, got +" + (stats.elevationGainMeters() - afterClimb),
                stats.elevationGainMeters() - afterClimb < 3d);
    }

    @Test public void heartAggregatesAndZones() {
        LiveWorkoutStats stats = new LiveWorkoutStats(190);
        stats.onHeartRate(120); stats.onHeartRate(150); stats.onHeartRate(180);
        assertEquals(150, stats.averageHeartRate());
        assertEquals(180, stats.maxHeartRateSeen());
        assertEquals(0, stats.heartRateZone(0));       // no reading
        assertEquals(0, stats.heartRateZone(90));      // below 50%
        assertEquals(1, stats.heartRateZone(105));     // 55%
        assertEquals(2, stats.heartRateZone(125));     // 66%
        assertEquals(3, stats.heartRateZone(140));     // 74%
        assertEquals(4, stats.heartRateZone(161));     // 85%
        assertEquals(5, stats.heartRateZone(175));     // 92%
    }

    @Test public void averagePaceAndCaloriesAreDistanceBased() {
        assertEquals(0, LiveWorkoutStats.averagePaceSecondsPerKm(20d, 60_000L));   // too short
        assertEquals(300, LiveWorkoutStats.averagePaceSecondsPerKm(2000d, 600_000L));
        LiveWorkoutStats stats = new LiveWorkoutStats();
        assertEquals(Math.round(1.036d * 65d * 5d), stats.calories(5000d), 1);
    }

    @Test public void restoreRealignsSplitBoundaryAfterRecovery() {
        LiveWorkoutStats stats = new LiveWorkoutStats();
        stats.restore(2600d, 780_000L);
        assertEquals(2, stats.splitCount());
        // The next boundary is 3 km, not 1 km again.
        assertNull(stats.onDistance(2900d, 900_000L));
        LiveWorkoutStats.Split third = stats.onDistance(3000d, 930_000L);
        assertNotNull(third);
        assertEquals(3, third.index);
        assertEquals(150, third.paceSecondsPerKm);   // 930s - 780s
    }
}

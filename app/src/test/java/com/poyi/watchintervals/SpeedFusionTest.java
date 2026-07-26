package com.poyi.watchintervals;

import static org.junit.Assert.*;
import org.junit.Test;

public class SpeedFusionTest {
    @Test public void prefersNativeDopplerOverDistanceWindow() {
        SpeedFusion fusion = new SpeedFusion();
        fusion.addWindowSpeed(1_000, 2.0d, false);
        fusion.addGnssSpeed(1_000, 3.2d, 0.4d);
        // First sample seeds the filter directly, so the Doppler value shows immediately.
        assertEquals(3.2d, fusion.speedMps(1_000), .001d);
        assertEquals(SpeedFusion.Source.GNSS_DOPPLER, fusion.source());
        assertFalse(fusion.estimated());
    }

    @Test public void fallsBackToWindowWhenDopplerGoesStale() {
        SpeedFusion fusion = new SpeedFusion();
        fusion.addGnssSpeed(1_000, 3.0d, 0.4d);
        fusion.speedMps(1_000);
        fusion.addWindowSpeed(5_000, 2.0d, true);
        // 4 s after the last fix the Doppler sample no longer describes the current effort.
        assertTrue(Double.isFinite(fusion.speedMps(5_000)));
        assertEquals(SpeedFusion.Source.DISTANCE_WINDOW, fusion.source());
        assertTrue(fusion.estimated());
    }

    @Test public void rejectsInaccurateAndImplausibleDoppler() {
        SpeedFusion fusion = new SpeedFusion();
        fusion.addGnssSpeed(1_000, 3.0d, 4.0d);            // accuracy worse than the gate
        assertTrue(Double.isNaN(fusion.speedMps(1_000)));
        fusion.addGnssSpeed(2_000, 40d, 0.2d);             // vehicle-speed glitch
        assertTrue(Double.isNaN(fusion.speedMps(2_000)));
        fusion.addGnssSpeed(3_000, 3.0d, -1d);             // no accuracy reported: still usable
        assertEquals(3.0d, fusion.speedMps(3_000), .001d);
    }

    @Test public void smoothingDampensJitterButStillConverges() {
        SpeedFusion fusion = new SpeedFusion();
        fusion.addGnssSpeed(0, 3.0d, 0.3d);
        fusion.speedMps(0);
        // A single 6 m/s outlier must not double the displayed speed.
        fusion.addGnssSpeed(1_000, 6.0d, 0.3d);
        double afterSpike = fusion.speedMps(1_000);
        assertTrue("spike should be damped, was " + afterSpike, afterSpike < 4.0d);
        // Sustained effort still reaches the new value.
        for (int second = 2; second <= 30; second++) {
            fusion.addGnssSpeed(second * 1_000L, 6.0d, 0.3d);
            fusion.speedMps(second * 1_000L);
        }
        assertEquals(6.0d, fusion.speedMps(30_000), .1d);
    }

    @Test public void reportsStoppedInsteadOfJitterAndDropsWhenAllSourcesExpire() {
        SpeedFusion fusion = new SpeedFusion();
        fusion.addGnssSpeed(0, 0.2d, 0.3d);
        assertEquals(0d, fusion.speedMps(0), .0001d);
        assertTrue(Double.isNaN(fusion.paceSecondsPerKm(0)));
        // Nothing new arrives: after both freshness windows lapse the readout must go blank.
        assertTrue(Double.isNaN(fusion.speedMps(60_000)));
        assertEquals(SpeedFusion.Source.NONE, fusion.source());
    }

    @Test public void paceMatchesSpeedAndFormatsForRunners() {
        SpeedFusion fusion = new SpeedFusion();
        fusion.addGnssSpeed(0, 1000d / 180d, 0.3d);        // 5.56 m/s == 3'00" per km
        assertEquals(180d, fusion.paceSecondsPerKm(0), 1d);
        assertEquals("3'00\"", SpeedFusion.formatPace(fusion.paceSecondsPerKm(0)));
        assertEquals("5'30\"", SpeedFusion.formatPace(330d));
        assertEquals("--'--\"", SpeedFusion.formatPace(Double.NaN));
        assertEquals("--'--\"", SpeedFusion.formatPace(0d));
    }
}

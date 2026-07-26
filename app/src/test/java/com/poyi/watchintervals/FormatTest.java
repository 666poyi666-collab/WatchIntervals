package com.poyi.watchintervals;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatTest {
    @Test public void durationUnderAnHourStaysMinutesSeconds() {
        assertEquals("00:00", Format.duration(0));
        assertEquals("00:59", Format.duration(59_999));
        assertEquals("45:07", Format.duration((45 * 60 + 7) * 1000L));
        assertEquals("59:59", Format.duration((59 * 60 + 59) * 1000L));
    }

    @Test public void durationRollsToHoursPastSixtyMinutes() {
        assertEquals("1:00:00", Format.duration(3_600_000));
        assertEquals("1:15:32", Format.duration((3600 + 15 * 60 + 32) * 1000L));
        assertEquals("2:05:09", Format.duration((2 * 3600 + 5 * 60 + 9) * 1000L));
        assertEquals("10:00:01", Format.duration((10 * 3600 + 1) * 1000L));
    }

    @Test public void durationClampsNegativeToZero() {
        assertEquals("00:00", Format.duration(-1_000));
    }

    @Test public void distanceBelowOneKilometreIsWholeMetres() {
        assertEquals("0 m", Format.distance(0));
        assertEquals("13 m", Format.distance(12.6));
        assertEquals("999 m", Format.distance(999.4));
    }

    @Test public void distanceFromOneKilometreUsesTwoDecimals() {
        assertEquals("1.00 km", Format.distance(1000));
        assertEquals("5.42 km", Format.distance(5424.9));
        assertEquals("21.10 km", Format.distance(21_097.5));
    }
}

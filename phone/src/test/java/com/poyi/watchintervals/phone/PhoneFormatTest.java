package com.poyi.watchintervals.phone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PhoneFormatTest {
    @Test public void durationRollsToHoursPastSixtyMinutes() {
        assertEquals("00:00", PhoneFormat.duration(0));
        assertEquals("59:59", PhoneFormat.duration((59 * 60 + 59) * 1000L));
        assertEquals("1:15:32", PhoneFormat.duration((3600 + 15 * 60 + 32) * 1000L));
        assertEquals("00:00", PhoneFormat.duration(-5_000));
    }

    @Test public void distanceSwitchesUnitsAtOneKilometre() {
        assertEquals("999 米", PhoneFormat.distance(999.4));
        assertEquals("5.42 公里", PhoneFormat.distance(5424.9));
    }

    @Test public void paceFromDurationAndMetres() {
        assertEquals("5:32 /公里", PhoneFormat.pace(332_000, 1000));
        assertEquals("-- /公里", PhoneFormat.pace(0, 1000));
        assertEquals("-- /公里", PhoneFormat.pace(332_000, 0));
    }

    @Test public void paceFromStoredSecondsMatchesDurationForm() {
        assertEquals("5:32 /公里", PhoneFormat.paceSeconds(332));
        assertEquals("10:05 /公里", PhoneFormat.paceSeconds(605));
        assertEquals("-- /公里", PhoneFormat.paceSeconds(0));
    }

    @Test public void minutesReadLikeANight() {
        assertEquals("45分", PhoneFormat.minutesHuman(45));
        assertEquals("7小时12分", PhoneFormat.minutesHuman(432));
        assertEquals("8小时", PhoneFormat.minutesHuman(480));
        assertEquals("0分", PhoneFormat.minutesHuman(-3));
    }
}

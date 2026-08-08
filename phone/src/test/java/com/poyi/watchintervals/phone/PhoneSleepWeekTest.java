package com.poyi.watchintervals.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PhoneSleepWeekTest {
    @Test public void trendKeepsSevenNewestNightsInChronologicalOrder() throws Exception {
        JSONArray records = new JSONArray();
        for (int index = 10; index >= 1; index--) records.put(new JSONObject()
                .put("timestamp", index * 1000L).put("totalDurationMinutes", index * 60)
                .put("sessions", new JSONArray()));

        PhoneSleepWeek week = PhoneSleepWeek.from(records);

        assertEquals(7, week.nights.size());
        assertEquals(4_000L, week.nights.get(0).timestamp);
        assertEquals(10_000L, week.nights.get(6).timestamp);
        assertEquals(10L * 60L, week.maximumMinutes());
    }
}

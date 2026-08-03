package com.poyi.watchintervals.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneSleepOverviewTest {
    @Test public void overviewIncludesAllFourStagesAcrossEverySession() throws Exception {
        JSONObject first = session(2000L, 300, 80, 150, 50, 20, 3);
        JSONObject second = session(1000L, 120, 30, 60, 20, 10, 2);
        JSONObject record = new JSONObject().put("timestamp", 3000L)
                .put("totalDurationMinutes", 420).put("sleepScore", 86)
                .put("spo2AveragePercent", 97).put("heartRateBenchmarkBpm", 52)
                .put("breathRateBenchmarkPerMinute", 14.5)
                .put("sessions", new JSONArray().put(first).put(second));

        PhoneSleepOverview overview = PhoneSleepOverview.from(record);

        assertEquals(1000L, overview.timestamp);
        assertEquals(420L, overview.totalDurationMinutes);
        assertTrue(overview.durationAvailable);
        assertTrue(overview.scoreAvailable);
        assertEquals(86, overview.sleepScore);
        assertTrue(overview.stageBreakdownAvailable);
        assertTrue(overview.deepAvailable);
        assertTrue(overview.lightAvailable);
        assertTrue(overview.remAvailable);
        assertTrue(overview.awakeAvailable);
        assertArrayEquals(new long[]{110, 210, 70, 30}, overview.stageMinutes());
        assertEquals(2, overview.sessionCount);
        assertEquals(5, overview.rawStageCount);
    }

    @Test public void missingScoreAndStageFieldsStayExplicitlyUnavailable() throws Exception {
        JSONObject session = new JSONObject().put("startTime", 42L)
                .put("sleepDurationMinutes", 90).put("stages", new JSONArray());
        PhoneSleepOverview overview = PhoneSleepOverview.from(new JSONObject()
                .put("sessions", new JSONArray().put(session)));

        assertEquals(90L, overview.totalDurationMinutes);
        assertTrue(overview.durationAvailable);
        assertFalse(overview.scoreAvailable);
        assertFalse(overview.stageBreakdownAvailable);
        assertFalse(overview.deepAvailable);
        assertFalse(overview.lightAvailable);
        assertFalse(overview.remAvailable);
        assertFalse(overview.awakeAvailable);
        assertFalse(overview.spo2Available);
        assertFalse(overview.heartRateAvailable);
        assertFalse(overview.breathRateAvailable);
    }

    private static JSONObject session(long start, int sleep, int deep, int light, int rem,
            int awake, int stageCount) throws Exception {
        JSONArray stages = new JSONArray();
        for (int index = 0; index < stageCount; index++) stages.put(new JSONObject()
                .put("type", index).put("label", "system_" + index)
                .put("startTime", start + index).put("endTime", start + index + 1));
        return new JSONObject().put("startTime", start).put("sleepDurationMinutes", sleep)
                .put("deepDurationMinutes", deep).put("lightDurationMinutes", light)
                .put("remDurationMinutes", rem).put("awakeDurationMinutes", awake)
                .put("stages", stages);
    }
}

package com.poyi.watchintervals.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneSleepTimelineTest {
    @Test public void stagesAreChronologicalMappedAndAdjacentSamplesMerge() throws Exception {
        JSONObject session = new JSONObject().put("stages", new JSONArray()
                .put(stage(2, 121_000L, 181_000L))
                .put(stage(1, 1_000L, 61_000L))
                .put(stage(1, 61_000L, 121_000L))
                .put(stage(3, 181_000L, 241_000L))
                .put(stage(4, 241_000L, 271_000L)));

        PhoneSleepTimeline timeline = PhoneSleepTimeline.from(new JSONObject()
                .put("sessions", new JSONArray().put(session)));

        assertTrue(timeline.available());
        assertEquals(4, timeline.segments.size());
        assertEquals(1_000L, timeline.startTime);
        assertEquals(271_000L, timeline.endTime);
        assertEquals(2L, timeline.durationMinutes(PhoneSleepTimeline.DEEP));
        assertEquals("REM", PhoneSleepTimeline.typeName(PhoneSleepTimeline.REM, 3));
    }

    @Test public void invalidRangesAreDiscardedAndUnknownTypesStayExplicit() throws Exception {
        JSONObject session = new JSONObject().put("stages", new JSONArray()
                .put(stage(9, 1_000L, 61_000L))
                .put(stage(2, 80_000L, 70_000L)));

        PhoneSleepTimeline timeline = PhoneSleepTimeline.from(new JSONObject()
                .put("sessions", new JSONArray().put(session)));

        assertTrue(timeline.available());
        assertEquals(1, timeline.unknownCount());
        assertEquals(1, timeline.discardedStageCount);
        assertEquals("系统阶段 9", PhoneSleepTimeline.typeName(PhoneSleepTimeline.UNKNOWN, 9));
        assertFalse(PhoneSleepTimeline.from(new JSONObject()).available());
    }

    private static JSONObject stage(int type, long start, long end) throws Exception {
        return new JSONObject().put("type", type).put("startTime", start).put("endTime", end);
    }
}

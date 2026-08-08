package com.poyi.watchintervals.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneSleepSyncTest {
    @Test public void boundedPagesMergeSortAndDeduplicateBoundaryRecords() throws Exception {
        JSONObject newest = record(300L);
        JSONObject boundary = record(200L);
        JSONArray pages = new JSONArray()
                .put(page(2_000L, 3_000L, true, new JSONArray().put(newest).put(boundary)))
                .put(page(1_000L, 2_000L, true, new JSONArray().put(boundary).put(record(100L))));

        JSONObject merged = PhoneSleepSync.mergePages(pages, 31, 4_000L);

        assertTrue(merged.getBoolean("complete"));
        assertEquals(1_000L, merged.getLong("coverageStart"));
        assertEquals(3_000L, merged.getLong("coverageEnd"));
        assertEquals(3, merged.getJSONArray("records").length());
        assertEquals(300L, merged.getJSONArray("records").getJSONObject(0).getLong("timestamp"));
    }

    @Test public void anyIncompleteHealthKitWindowMarksAggregateIncomplete() throws Exception {
        JSONArray pages = new JSONArray().put(page(1L, 2L, false,
                new JSONArray().put(record(1L))));

        JSONObject merged = PhoneSleepSync.mergePages(pages, 7, 3L);

        assertFalse(merged.getBoolean("complete"));
        assertTrue(merged.getBoolean("hasMore"));
    }

    private static JSONObject page(long start, long end, boolean complete, JSONArray records)
            throws Exception {
        return new JSONObject().put("state", "ready").put("coverageStart", start)
                .put("coverageEnd", end).put("complete", complete)
                .put("hasMore", !complete).put("records", records);
    }

    private static JSONObject record(long timestamp) throws Exception {
        return new JSONObject().put("timestamp", timestamp).put("sessions", new JSONArray());
    }
}

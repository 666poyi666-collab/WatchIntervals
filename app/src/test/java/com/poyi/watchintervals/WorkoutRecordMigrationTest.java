package com.poyi.watchintervals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.json.JSONObject;
import org.junit.Test;

public class WorkoutRecordMigrationTest {
    @Test public void schemaTwoWithoutSchemaThreeMetricsProducesFiniteSummary() throws Exception {
        JSONObject legacy = new JSONObject()
                .put("schemaVersion", 2)
                .put("id", "legacy-record")
                .put("startedAt", 1_000L)
                .put("endedAt", 61_000L)
                .put("durationMs", 60_000L)
                .put("distanceMeters", 125d)
                .put("steps", 180)
                .put("plan", "[]");

        JSONObject summary = WorkoutRecord.fromJson(legacy).toSummaryJson();

        assertEquals(0d, summary.getDouble("planDistanceMeters"), 0d);
        assertEquals(0d, summary.getDouble("freeRecordingDistanceMeters"), 0d);
        assertEquals(0d, summary.getDouble("maxSmoothedSpeedMps"), 0d);
        assertTrue(Double.isFinite(summary.getDouble("distanceMeters")));
    }

    @Test public void nonFiniteOptionalMetricsAreSanitized() throws Exception {
        WorkoutRecord record = new WorkoutRecord();
        record.id = "damaged-record";
        record.distanceMeters = Double.NaN;
        record.planDistanceMeters = Double.POSITIVE_INFINITY;
        record.freeRecordingDistanceMeters = Double.NaN;
        record.maxSmoothedSpeedMps = Double.NEGATIVE_INFINITY;

        JSONObject summary = record.toSummaryJson();

        assertEquals(0d, summary.getDouble("distanceMeters"), 0d);
        assertEquals(0d, summary.getDouble("planDistanceMeters"), 0d);
        assertEquals(0d, summary.getDouble("freeRecordingDistanceMeters"), 0d);
        assertEquals(0d, summary.getDouble("maxSmoothedSpeedMps"), 0d);
    }
}

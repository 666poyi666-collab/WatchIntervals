package com.poyi.watchintervals.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class CloudSnapshotPayloadTest {
    @Test public void buildsMcpCompatibleSnapshotsWithoutSleepSessions() throws Exception {
        JSONObject status = new JSONObject().put("device", "watch").put("state", "IDLE");
        JSONArray history = new JSONArray()
                .put(new JSONObject().put("distanceMeters", 2500).put("activeDurationMs", 900000)
                        .put("steps", 3200).put("averageHeartRate", 130))
                .put(new JSONObject().put("distanceMeters", 1500).put("durationMs", 600000)
                        .put("steps", 1800).put("averageHeartRate", 150));
        JSONObject sleep = new JSONObject().put("state", "ready").put("source", "health_store")
                .put("records", new JSONArray().put(new JSONObject().put("timestamp", 200)
                        .put("totalDurationMinutes", 420).put("sleepScore", 86)
                        .put("spo2AveragePercent", 96.5).put("sessions", new JSONArray().put("private"))));
        JSONObject library = new JSONObject().put("revision", 4).put("selectedPlanId", "p1")
                .put("plans", new JSONArray().put(new JSONObject().put("id", "p1")));

        JSONObject snapshots = CloudSnapshotPayload.build(status, history, sleep, library);

        assertEquals(6, snapshots.length());
        JSONObject workouts = snapshots.getJSONObject("watch_summarize_workouts");
        assertEquals(2, workouts.getInt("workoutCount"));
        assertEquals(4000.0, workouts.getDouble("totalDistanceMeters"), 0.001);
        assertEquals(1500000.0, workouts.getDouble("totalActiveDurationMs"), 0.001);
        assertEquals(140, workouts.getInt("averageHeartRate"));
        JSONObject latest = snapshots.getJSONObject("watch_get_latest_sleep")
                .getJSONObject("record");
        assertFalse(latest.has("sessions"));
        assertEquals(420, snapshots.getJSONObject("watch_summarize_sleep")
                .getInt("averageDurationMinutes"));
        assertEquals("online", snapshots.getJSONObject("watch_get_status")
                .getJSONObject("connection").getString("watch"));
    }

    @Test public void leavesUnavailableDataPlanesOutOfThePush() throws Exception {
        JSONObject snapshots = CloudSnapshotPayload.build(null, null, null,
                new JSONObject().put("plans", new JSONArray()).put("revision", 1));
        assertEquals(1, snapshots.length());
        assertTrue(snapshots.has("watch_list_plans"));
    }
}

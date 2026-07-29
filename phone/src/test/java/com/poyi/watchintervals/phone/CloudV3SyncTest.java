package com.poyi.watchintervals.phone;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class CloudV3SyncTest {
    @Test public void normalizesLegacyEndpointToV3AndWebSocketChannel() throws Exception {
        assertEquals("https://watch.example/sync/v3/exchange",
                CloudV3Sync.exchangeEndpoint("https://watch.example/sync/v2/exchange"));
        assertEquals("wss://watch.example/sync/v3/channel",
                CloudV3Sync.channelEndpoint("https://watch.example/sync/v3/exchange"));
    }

    @Test public void rejectsRouteCoordinatesAndHeartSamplesAtAnyDepth() throws Exception {
        assertTrue(CloudV3Sync.containsForbidden(new JSONObject().put("route", new JSONArray())));
        assertTrue(CloudV3Sync.containsForbidden(new JSONObject().put("nested",
                new JSONObject().put("latitude", 1d))));
        assertTrue(CloudV3Sync.containsForbidden(new JSONObject().put("heartRateSamples",
                new JSONArray().put(new JSONArray().put(1).put(80)))));
        assertFalse(CloudV3Sync.containsForbidden(new JSONObject()
                .put("distanceMeters", 5000).put("averageHeartRate", 150)
                .put("heartRateRange", new JSONObject().put("min", 90).put("max", 180))));
    }

    @Test public void conflictMovesCandidateToPersistentConflictStoreWithoutReceipt() throws Exception {
        JSONObject state = stateWithOutbox(new JSONObject().put("kind", "plan")
                .put("entityId", "library").put("fingerprint", "10")
                .put("payload", new JSONObject().put("operationId", "plan-op")
                        .put("expectedRevision", 0).put("library", cloudLibrary("Local candidate"))));
        JSONObject response = new JSONObject().put("acknowledgements", new JSONArray().put(
                        new JSONObject().put("operationId", "plan-op").put("outcome", "conflict")
                                .put("error", "revision_conflict").put("currentRevision", 2)))
                .put("planLibrary", cloudLibrary("Cloud authority").put("revision", 2));

        CloudV3Sync.applyAcknowledgements(state, response);

        assertEquals(0, state.getJSONArray("outbox").length());
        assertEquals(1, state.getJSONArray("conflicts").length());
        JSONObject preserved = state.getJSONArray("conflicts").getJSONObject(0);
        assertEquals("Local candidate", preserved.getJSONObject("candidate")
                .getJSONObject("library").getJSONArray("plans").getJSONObject(0).getString("name"));
        assertEquals("Cloud authority", preserved.getJSONObject("serverLibrary")
                .getJSONArray("plans").getJSONObject(0).getString("name"));
        assertEquals(0, state.getJSONObject("workoutReceipts").length());
    }

    @Test public void immutableWorkoutConflictIsPreservedButTombstoneStopsReupload() throws Exception {
        JSONObject item = new JSONObject().put("kind", "workout").put("entityId", "workout-1")
                .put("fingerprint", "summary-hash").put("payload", new JSONObject()
                        .put("operationId", "workout-op").put("workout", new JSONObject().put("id", "workout-1")));
        JSONObject state = stateWithOutbox(item);
        CloudV3Sync.applyAcknowledgements(state, new JSONObject().put("acknowledgements",
                new JSONArray().put(new JSONObject().put("operationId", "workout-op")
                        .put("outcome", "conflict").put("error", "workout_immutable"))));
        assertEquals(1, state.getJSONArray("conflicts").length());
        assertFalse(state.getJSONObject("workoutReceipts").has("workout-1"));

        state = stateWithOutbox(item);
        CloudV3Sync.applyAcknowledgements(state, new JSONObject().put("acknowledgements",
                new JSONArray().put(new JSONObject().put("operationId", "workout-op")
                        .put("outcome", "conflict").put("error", "workout_deleted"))));
        assertEquals(0, state.getJSONArray("conflicts").length());
        assertEquals("summary-hash", state.getJSONObject("workoutReceipts").getString("workout-1"));
    }

    @Test public void cloudPlanApplyGuardRejectsConcurrentLocalEdit() throws Exception {
        JSONObject original = localLibrary(40, "Before request");
        JSONObject active = new JSONObject().put("planLocalRevision", 40)
                .put("planLocalFingerprint", CloudV3Sync.planFingerprint(original));
        assertTrue(CloudV3Sync.shouldApplyCloudPlan(active, original));

        JSONObject edited = localLibrary(41, "Edited during HTTP round trip");
        assertFalse(CloudV3Sync.shouldApplyCloudPlan(active, edited));
        assertFalse(CloudV3Sync.shouldApplyCloudPlan(new JSONObject(), original));
    }

    @Test public void cursorAheadClearsOnlyActiveRequestAndKeepsOutbox() throws Exception {
        JSONObject state = stateWithOutbox(new JSONObject().put("kind", "sleep")
                .put("entityId", "sleep-1").put("payload", new JSONObject().put("operationId", "sleep-op")));
        state.put("cursor", "v3czz").put("activeRequest", new JSONObject().put("body", new JSONObject()));
        assertTrue(CloudV3Sync.applyCursorReset(state, 409,
                new JSONObject().put("error", "cursor_ahead").put("latestCursor", "v3c2")
                        .put("resetCursor", JSONObject.NULL).toString()));
        assertTrue(state.isNull("cursor"));
        assertFalse(state.has("activeRequest"));
        assertEquals(1, state.getJSONArray("outbox").length());
    }

    @Test public void commandResultRequiresImmediateFollowUpAndSurvivesRestartState() throws Exception {
        JSONObject state = emptyState();
        AtomicInteger executions = new AtomicInteger();
        JSONObject command = command("command-1", Instant.ofEpochMilli(20_000));
        boolean followUp = CloudV3Sync.processCommands(state, new JSONArray().put(command), 10_000,
                value -> {
                    executions.incrementAndGet();
                    return new JSONObject().put("commandId", "command-1").put("outcome", "succeeded");
                });
        assertTrue(followUp);
        assertEquals(1, state.getJSONArray("commandResults").length());

        assertFalse(CloudV3Sync.processCommands(state, new JSONArray().put(command), 11_000,
                value -> { executions.incrementAndGet(); return null; }));
        assertEquals(1, executions.get());
    }

    @Test public void offlineCommandRemainsPendingAndExpiredCommandNeverExecutes() throws Exception {
        JSONObject state = emptyState();
        AtomicInteger executions = new AtomicInteger();
        JSONObject future = command("offline", Instant.ofEpochMilli(20_000));
        assertFalse(CloudV3Sync.processCommands(state, new JSONArray().put(future), 10_000,
                value -> { executions.incrementAndGet(); return null; }));
        assertEquals(0, state.getJSONArray("commandResults").length());
        assertFalse(state.getJSONObject("executedCommands").has("offline"));

        JSONObject expired = command("expired", Instant.ofEpochMilli(9_999));
        assertTrue(CloudV3Sync.processCommands(state, new JSONArray().put(expired), 10_000,
                value -> { executions.incrementAndGet(); return null; }));
        assertEquals(1, executions.get());
        assertEquals("command_expired", state.getJSONArray("commandResults")
                .getJSONObject(0).getString("error"));
    }

    private static JSONObject stateWithOutbox(JSONObject item) throws Exception {
        return emptyState().put("outbox", new JSONArray().put(item));
    }

    private static JSONObject emptyState() throws Exception {
        return new JSONObject().put("outbox", new JSONArray())
                .put("workoutReceipts", new JSONObject()).put("sleepReceipts", new JSONObject())
                .put("conflicts", new JSONArray()).put("executedCommands", new JSONObject())
                .put("commandResults", new JSONArray());
    }

    private static JSONObject command(String id, Instant expiresAt) throws Exception {
        return new JSONObject().put("commandId", id).put("type", "pause")
                .put("expiresAt", expiresAt.toString()).put("controlRevision", 1);
    }

    private static JSONObject localLibrary(long revision, String name) throws Exception {
        JSONObject cloud = cloudLibrary(name);
        cloud.remove("schemaVersion");
        JSONArray plans = cloud.getJSONArray("plans");
        for (int index = 0; index < plans.length(); index++) {
            plans.getJSONObject(index).put("updatedAt", 1).put("revision", revision);
        }
        return cloud.put("schemaVersion", 3).put("revision", revision)
                .put("deletedPlanIds", new JSONArray());
    }

    private static JSONObject cloudLibrary(String name) throws Exception {
        return new JSONObject().put("schemaVersion", 1).put("selectedPlanId", "plan-1")
                .put("groups", new JSONArray().put(new JSONObject().put("id", "group-1")
                        .put("name", "Intervals").put("sortOrder", 0)))
                .put("plans", new JSONArray().put(new JSONObject().put("id", "plan-1")
                        .put("name", name).put("groupId", "group-1").put("requirement", "Run")
                        .put("sortOrder", 0).put("stages", new JSONArray().put(new JSONObject()
                                .put("kind", "RUN").put("unit", "TIME").put("target", 60)))));
    }
}

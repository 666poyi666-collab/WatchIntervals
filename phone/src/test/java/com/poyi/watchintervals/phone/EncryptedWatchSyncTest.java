package com.poyi.watchintervals.phone;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class EncryptedWatchSyncTest {
    private static final String DEVICE_ID = "watch-device-test";

    @Test public void onlyUnauthorizedCloudResponsesRetireTheDeviceToken() {
        assertTrue(EncryptedWatchSync.isRevokedDeviceTokenFailure(
                new EncryptedWatchSync.CloudHttpException(401)));
        assertFalse(EncryptedWatchSync.isRevokedDeviceTokenFailure(
                new EncryptedWatchSync.CloudHttpException(403)));
        assertTrue(CloudSyncCredentials.matchesExpectedToken("old-token", "old-token"));
        assertFalse(CloudSyncCredentials.matchesExpectedToken("old-token", "rotated-token"));
    }

    @Test public void revokedCredentialsDoNotRequestAnotherWorkerRetry() {
        assertFalse(EncryptedWatchSyncWorker.shouldRetry(
                CloudV3Sync.SyncOutcome.PERMANENT_FAILURE, false));
        assertTrue(EncryptedWatchSyncWorker.shouldRetry(
                CloudV3Sync.SyncOutcome.TRANSIENT_FAILURE, true));
        assertFalse(EncryptedWatchSyncWorker.shouldRetry(
                CloudV3Sync.SyncOutcome.SUCCESS, true));
    }

    @Test public void onlyRetryableCloudStatusesRemainTransient() {
        assertTrue(EncryptedWatchSync.isPermanentFailure(
                new EncryptedWatchSync.CloudHttpException(403)));
        assertTrue(EncryptedWatchSync.isPermanentFailure(
                new EncryptedWatchSync.CloudHttpException(409)));
        assertFalse(EncryptedWatchSync.isPermanentFailure(
                new EncryptedWatchSync.CloudHttpException(429)));
        assertFalse(EncryptedWatchSync.isPermanentFailure(
                new EncryptedWatchSync.CloudHttpException(503)));
        assertTrue(EncryptedWatchSync.isPermanentFailure(
                new IllegalArgumentException("invalid_encrypted_exchange_response")));
    }

    @Test public void stableJsonAndAadAreCanonical() throws Exception {
        JSONObject first = new JSONObject().put("z", 1).put("a",
                new JSONObject().put("b", true).put("a", "x"));
        JSONObject second = new JSONObject().put("a",
                new JSONObject().put("a", "x").put("b", true)).put("z", 1);
        assertEquals("{\"a\":{\"a\":\"x\",\"b\":true},\"z\":1}",
                EncryptedWatchSync.stableJson(first));
        assertEquals(EncryptedWatchSync.stableJson(first), EncryptedWatchSync.stableJson(second));
        assertThrows(IllegalArgumentException.class,
                () -> EncryptedWatchSync.stableJson(Double.NaN));
    }

    @Test public void mutationRoundTripsAndRejectsDifferentAad() throws Exception {
        SecretKey root = root();
        JSONObject payload = new JSONObject().put("name", "间歇跑")
                .put("stages", new JSONArray().put(new JSONObject().put("target", 60)));
        JSONObject mutation = EncryptedWatchSync.mutation(root, "plan", "plan-1", 0,
                "upsert", payload);
        String aad = EncryptedWatchSync.stableJson(
                EncryptedWatchSync.aad("plan", "plan-1", "upsert", 1, 1));
        String cleartext = EncryptedWatchSync.decrypt(root, mutation.getString("ciphertext"),
                mutation.getString("nonce"), aad);
        assertEquals(EncryptedWatchSync.stableJson(payload), cleartext);
        assertThrows(Exception.class, () -> EncryptedWatchSync.decrypt(root,
                mutation.getString("ciphertext"), mutation.getString("nonce"),
                aad.replace("upsert", "delete")));
    }

    @Test public void workoutProjectionKeepsSummaryAndDropsSensitiveSamples() throws Exception {
        JSONObject source = new JSONObject().put("id", "workout-1").put("steps", 1234)
                .put("route", new JSONArray().put(new JSONObject()
                        .put("latitude", 1).put("longitude", 2)))
                .put("heartRateSamples", new JSONArray().put(new JSONArray().put(1).put(120)))
                .put("sleep", new JSONObject().put("score", 90))
                .put("credential", "must-not-leave-device");
        JSONObject summary = EncryptedWatchSync.workoutSummary(source);
        assertEquals("workout-1", summary.getString("id"));
        assertEquals(1234, summary.getInt("steps"));
        assertFalse(summary.has("route"));
        assertFalse(summary.has("heartRateSamples"));
        assertFalse(summary.has("sleep"));
        assertFalse(summary.has("credential"));
        String entityId = EncryptedWatchSync.workoutEntityId("workout-1722222222000");
        assertTrue(entityId.startsWith("w:"));
        assertFalse(entityId.contains("1722222222000"));
    }

    @Test public void mcpReadProjectionContainsOnlyNamesAndBoundedWorkoutSummary()
            throws Exception {
        JSONObject plan = localEntity("plan-1")
                .put("payload", new JSONObject().put("name", "户外间歇")
                        .put("credential", "must-not-leave-device"));
        JSONObject workout = new JSONObject().put("entityType", "workout")
                .put("entityId", "w:" + "a".repeat(64)).put("confirmedRevision", 1)
                .put("deleted", false).put("payload", new JSONObject()
                        .put("startedAt", 1000).put("endedAt", 2000).put("durationMs", 900)
                        .put("distanceMeters", 1200.5).put("steps", 1500)
                        .put("planName", "户外间歇")
                        .put("route", new JSONArray().put(new JSONObject()
                                .put("latitude", 1).put("longitude", 2)))
                        .put("heartRateSamples", new JSONArray().put(120))
                        .put("sleep", new JSONObject().put("score", 90))
                        .put("credential", "must-not-leave-device"));
        JSONObject state = EncryptedWatchSync.Store.fresh().put("entities",
                new JSONObject().put("plan\u0000plan-1", plan)
                        .put("workout\u0000w:" + "a".repeat(64), workout));

        JSONObject projection = EncryptedWatchSync.Store.forTesting(state).readProjection();
        assertEquals(List.of("entityKey", "name"),
                sortedKeys(projection.getJSONArray("plans").getJSONObject(0)));
        assertEquals(List.of("distanceMeters", "durationMs", "endedAt", "entityKey",
                        "startedAt", "steps", "workoutType"),
                sortedKeys(projection.getJSONArray("workouts").getJSONObject(0)));
        String serialized = projection.toString();
        assertFalse(serialized.contains("route"));
        assertFalse(serialized.contains("latitude"));
        assertFalse(serialized.contains("heartRate"));
        assertFalse(serialized.contains("sleep"));
        assertFalse(serialized.contains("credential"));
        assertFalse(serialized.contains("plan-1"));
    }

    @Test public void invalidLocalProjectionRowsAreSkippedBeforeTheyCanBlockSync()
            throws Exception {
        JSONObject badName = localEntity("plan-bad")
                .put("payload", new JSONObject().put("name", "bad\u0000name"));
        JSONObject impossibleWorkout = new JSONObject().put("entityType", "workout")
                .put("entityId", "workout-impossible").put("confirmedRevision", 1)
                .put("deleted", false).put("payload", new JSONObject()
                        .put("startedAt", 2_000).put("endedAt", 1_000)
                        .put("durationMs", 1_500).put("distanceMeters", 1_000_000_001d)
                        .put("steps", 1_000_000_001L));
        JSONObject validWorkout = new JSONObject().put("entityType", "workout")
                .put("entityId", "not-a-wire-key").put("confirmedRevision", 1)
                .put("deleted", false).put("payload", new JSONObject()
                        .put("startedAt", 1_000).put("endedAt", 2_000)
                        .put("durationMs", 900).put("distanceMeters", 100d).put("steps", 120));
        JSONObject state = EncryptedWatchSync.Store.fresh().put("entities",
                new JSONObject().put("plan\u0000plan-bad", badName)
                        .put("workout\u0000workout-impossible", impossibleWorkout)
                        .put("workout\u0000not-a-wire-key", validWorkout));

        JSONObject projection = EncryptedWatchSync.Store.forTesting(state).readProjection();
        assertEquals(0, projection.getJSONArray("plans").length());
        assertEquals(1, projection.getJSONArray("workouts").length());
        assertTrue(projection.getJSONArray("workouts").getJSONObject(0)
                .getString("entityKey").matches("^w:[0-9a-f]{64}$"));
    }

    @Test public void revisionsUseTheContractSafeIntegerRange() throws Exception {
        long baseRevision = (long) Integer.MAX_VALUE + 10L;
        JSONObject mutation = EncryptedWatchSync.mutation(root(), "plan", "large-revision",
                baseRevision, "upsert", new JSONObject().put("name", "保留 long"));
        assertEquals(baseRevision, mutation.getLong("baseRevision"));
        JSONObject aad = EncryptedWatchSync.aad("plan", "large-revision", "upsert", 1,
                baseRevision + 1L);
        assertEquals(baseRevision + 1L, aad.getLong("revision"));
    }

    @Test public void oversizedPlaintextNeverEntersTheOutbox() throws Exception {
        char[] characters = new char[500_001];
        java.util.Arrays.fill(characters, 'x');
        JSONObject payload = new JSONObject().put("value", new String(characters));
        assertThrows(IllegalArgumentException.class, () -> EncryptedWatchSync.mutation(
                root(), "plan", "too-large", 0, "upsert", payload));
    }

    @Test public void strictCursorRejectsOverflowAndNonCanonicalCharacters() {
        assertTrue(EncryptedWatchSync.validCursor("c0"));
        assertTrue(EncryptedWatchSync.validCursor("c2gosa7pa2gv"));
        assertFalse(EncryptedWatchSync.validCursor(null));
        assertFalse(EncryptedWatchSync.validCursor("C1"));
        assertFalse(EncryptedWatchSync.validCursor("c00"));
        assertFalse(EncryptedWatchSync.validCursor("c01"));
        assertFalse(EncryptedWatchSync.validCursor("c2gosa7pa2gw"));
        assertFalse(EncryptedWatchSync.validCursor("czzzzzzzzzzzzzzzz"));
    }

    @Test public void fractionalAcknowledgementRevisionIsRejected() throws Exception {
        JSONObject acknowledgement = new JSONObject().put("outcome", "acknowledged")
                .put("opId", "11111111-1111-4111-8111-111111111111")
                .put("entityType", "plan").put("entityId", "plan-1")
                .put("operation", "upsert").put("revision", 1.5d);
        JSONObject response = response(new JSONArray().put(acknowledgement), new JSONArray(),
                new JSONArray(), "c0");
        assertThrows(IllegalArgumentException.class,
                () -> EncryptedWatchSync.validateExchangeResponse(response));
    }

    @Test public void acknowledgementAndCursorCommitRemoveOutboxTogether() throws Exception {
        SecretKey root = root();
        JSONObject mutation = EncryptedWatchSync.mutation(root, "plan", "plan-delete", 0,
                "delete", null);
        mutation.put("localFingerprint", JSONObject.NULL).put("state", "inflight")
                .put("leaseId", "lease-1").put("leaseExpiresAt", 99_999L)
                .put("attemptCount", 0).put("createdAt", 1L).put("updatedAt", 1L)
                .put("error", JSONObject.NULL);
        JSONObject entity = new JSONObject().put("entityType", "plan")
                .put("entityId", "plan-delete").put("confirmedRevision", 0)
                .put("confirmedFingerprint", JSONObject.NULL).put("deleted", true)
                .put("payload", JSONObject.NULL);
        JSONObject state = EncryptedWatchSync.Store.fresh()
                .put("bootstrapComplete", true)
                .put("outbox", new JSONArray().put(mutation))
                .put("entities", new JSONObject().put("plan\u0000plan-delete", entity));
        EncryptedWatchSync.Store store = EncryptedWatchSync.Store.forTesting(state);
        JSONObject acknowledgement = new JSONObject().put("outcome", "acknowledged")
                .put("opId", mutation.getString("opId")).put("entityType", "plan")
                .put("entityId", "plan-delete").put("operation", "delete")
                .put("revision", 1);
        JSONObject response = response(new JSONArray().put(acknowledgement), new JSONArray(),
                new JSONArray(), "c1");
        EncryptedWatchSync.validateExchangeResponse(response);

        EncryptedWatchSync.ApplyResult applied = store.apply(response, "lease-1", List.of());
        JSONObject committed = store.snapshotForTesting();
        assertEquals(0, committed.getJSONArray("outbox").length());
        assertEquals("c1", committed.getString("cursor"));
        assertEquals(1, committed.getJSONObject("entities")
                .getJSONObject("plan\u0000plan-delete").getInt("confirmedRevision"));
        assertTrue(applied.acknowledgedPlanDeletes.contains("plan-delete"));
        assertEquals("plan\u0000plan-delete",
                committed.getJSONArray("projectionPending").getString(0));
    }

    @Test public void revisionConflictPreservesLocalCandidateAndRemotePlaintext() throws Exception {
        SecretKey root = root();
        JSONObject localPayload = new JSONObject().put("name", "本地候选");
        JSONObject local = EncryptedWatchSync.mutation(root, "plan", "plan-conflict", 0,
                "upsert", localPayload);
        local.put("localFingerprint", EncryptedWatchSync.sha256(
                        EncryptedWatchSync.stableJson(localPayload)))
                .put("state", "inflight").put("leaseId", "lease-conflict")
                .put("leaseExpiresAt", 99_999L).put("attemptCount", 0)
                .put("createdAt", 1L).put("updatedAt", 1L).put("error", JSONObject.NULL);
        JSONObject localEntity = new JSONObject().put("entityType", "plan")
                .put("entityId", "plan-conflict").put("confirmedRevision", 0)
                .put("confirmedFingerprint", JSONObject.NULL).put("deleted", false)
                .put("payload", localPayload);
        JSONObject state = EncryptedWatchSync.Store.fresh()
                .put("bootstrapComplete", true)
                .put("outbox", new JSONArray().put(local))
                .put("entities", new JSONObject().put("plan\u0000plan-conflict", localEntity));
        EncryptedWatchSync.Store store = EncryptedWatchSync.Store.forTesting(state);

        JSONObject remotePayload = new JSONObject().put("name", "远端候选");
        JSONObject remote = EncryptedWatchSync.mutation(root, "plan", "plan-conflict", 0,
                "upsert", remotePayload);
        JSONObject change = changeFromMutation(remote, 1);
        JSONObject conflict = new JSONObject().put("outcome", "conflict")
                .put("opId", local.getString("opId")).put("entityType", "plan")
                .put("entityId", "plan-conflict").put("operation", "upsert")
                .put("error", "REVISION_CONFLICT").put("current", JSONObject.NULL)
                .put("candidate", new JSONObject(local.toString()));
        JSONObject response = response(new JSONArray(), new JSONArray().put(conflict),
                new JSONArray().put(change), "c1");
        EncryptedWatchSync.validateExchangeResponse(response);
        List<EncryptedWatchSync.MaterializedChange> changes =
                EncryptedWatchSync.materialize(response, root);
        store.apply(response, "lease-conflict", changes);

        JSONObject committed = store.snapshotForTesting();
        assertEquals("conflict", committed.getJSONArray("outbox")
                .getJSONObject(0).getString("state"));
        assertEquals(2, committed.getJSONArray("conflicts").length());
        assertEquals("本地候选", committed.getJSONObject("entities")
                .getJSONObject("plan\u0000plan-conflict").getJSONObject("payload")
                .getString("name"));
        assertEquals("远端候选", committed.getJSONArray("conflicts").getJSONObject(1)
                .getJSONObject("remotePayload").getString("name"));
        assertEquals(0, committed.getJSONArray("projectionPending").length());
        assertEquals("c1", committed.getString("cursor"));
    }

    @Test public void missingMutationOutcomeCannotAdvanceCursor() throws Exception {
        JSONObject mutation = EncryptedWatchSync.mutation(root(), "plan", "missing-ack", 0,
                "upsert", new JSONObject().put("name", "待 ACK"));
        mutation.put("localFingerprint", "fingerprint").put("state", "inflight")
                .put("leaseId", "lease-missing").put("leaseExpiresAt", 99_999L)
                .put("attemptCount", 0).put("createdAt", 1L).put("updatedAt", 1L)
                .put("error", JSONObject.NULL);
        JSONObject state = EncryptedWatchSync.Store.fresh()
                .put("bootstrapComplete", true)
                .put("outbox", new JSONArray().put(mutation));
        EncryptedWatchSync.Store store = EncryptedWatchSync.Store.forTesting(state);
        JSONObject response = response(new JSONArray(), new JSONArray(), new JSONArray(), "c1");

        assertThrows(IllegalStateException.class,
                () -> store.apply(response, "lease-missing", List.of()));
        JSONObject committed = store.snapshotForTesting();
        assertTrue(committed.isNull("cursor"));
        assertEquals("inflight", committed.getJSONArray("outbox")
                .getJSONObject(0).getString("state"));
    }

    @Test public void acknowledgementMismatchRollsBackEveryInMemoryChange() throws Exception {
        JSONObject first = leasedMutation("first", "lease-mismatch");
        JSONObject second = leasedMutation("second", "lease-mismatch");
        JSONObject state = EncryptedWatchSync.Store.fresh()
                .put("bootstrapComplete", true)
                .put("outbox", new JSONArray().put(first).put(second))
                .put("entities", new JSONObject()
                        .put("plan\u0000first", localEntity("first"))
                        .put("plan\u0000second", localEntity("second")));
        EncryptedWatchSync.Store store = EncryptedWatchSync.Store.forTesting(state);
        JSONObject valid = acknowledgement(first, "first");
        JSONObject mismatched = acknowledgement(second, "wrong-entity");
        JSONObject response = response(new JSONArray().put(valid).put(mismatched),
                new JSONArray(), new JSONArray(), "c1");

        assertThrows(IllegalStateException.class,
                () -> store.apply(response, "lease-mismatch", List.of()));
        JSONObject committed = store.snapshotForTesting();
        assertTrue(committed.isNull("cursor"));
        assertEquals(2, committed.getJSONArray("outbox").length());
        assertEquals(0, committed.getJSONObject("entities")
                .getJSONObject("plan\u0000first").getInt("confirmedRevision"));
        assertEquals("inflight", committed.getJSONArray("outbox")
                .getJSONObject(0).getString("state"));
    }

    @Test public void planDeleteRequiresRootAuthenticatedMetadataTombstone() throws Exception {
        JSONObject entities = new JSONObject();
        assertFalse(EncryptedWatchSync.hasAuthenticatedPlanDelete(entities, "plan-delete"));
        JSONObject metadata = new JSONObject().put("entityType", "plan")
                .put("entityId", EncryptedWatchSync.PLAN_LIBRARY_ENTITY_ID)
                .put("deleted", false).put("payload", new JSONObject()
                        .put("syncEntity", "plan_library")
                        .put("deletedPlanIds", new JSONArray().put(new JSONObject()
                                .put("id", "plan-delete").put("acknowledged", false))));
        entities.put("plan\u0000" + EncryptedWatchSync.PLAN_LIBRARY_ENTITY_ID, metadata);
        assertTrue(EncryptedWatchSync.hasAuthenticatedPlanDelete(entities, "plan-delete"));
        assertFalse(EncryptedWatchSync.hasAuthenticatedPlanDelete(entities, "forged-delete"));
    }

    private static JSONObject changeFromMutation(JSONObject mutation, int revision) throws Exception {
        return new JSONObject().put("entityType", mutation.getString("entityType"))
                .put("entityId", mutation.getString("entityId")).put("revision", revision)
                .put("operation", mutation.getString("operation"))
                .put("keyVersion", mutation.getInt("keyVersion"))
                .put("ciphertext", mutation.get("ciphertext")).put("nonce", mutation.get("nonce"))
                .put("aadHash", mutation.getString("aadHash"))
                .put("objects", mutation.getJSONArray("objects"))
                .put("changedAt", "2026-07-28T00:00:00.000Z")
                .put("originDeviceId", DEVICE_ID)
                .put("operationId", mutation.getString("opId"));
    }

    private static JSONObject leasedMutation(String entityId, String leaseId) throws Exception {
        JSONObject mutation = EncryptedWatchSync.mutation(root(), "plan", entityId, 0,
                "upsert", new JSONObject().put("name", entityId));
        return mutation.put("localFingerprint", entityId + "-fingerprint")
                .put("state", "inflight").put("leaseId", leaseId)
                .put("leaseExpiresAt", 99_999L).put("attemptCount", 0)
                .put("createdAt", 1L).put("updatedAt", 1L).put("error", JSONObject.NULL);
    }

    private static JSONObject localEntity(String entityId) throws Exception {
        return new JSONObject().put("entityType", "plan").put("entityId", entityId)
                .put("confirmedRevision", 0).put("confirmedFingerprint", JSONObject.NULL)
                .put("deleted", false).put("payload", new JSONObject().put("name", entityId));
    }

    private static JSONObject acknowledgement(JSONObject mutation, String entityId)
            throws Exception {
        return new JSONObject().put("outcome", "acknowledged")
                .put("opId", mutation.getString("opId")).put("entityType", "plan")
                .put("entityId", entityId).put("operation", "upsert").put("revision", 1);
    }

    private static List<String> sortedKeys(JSONObject value) {
        List<String> result = new java.util.ArrayList<>();
        value.keys().forEachRemaining(result::add);
        java.util.Collections.sort(result);
        return result;
    }

    private static JSONObject response(JSONArray acknowledgements, JSONArray conflicts,
                                       JSONArray changes, String cursor) throws Exception {
        return new JSONObject().put("protocolVersion", 2).put("envelopeVersion", 1)
                .put("product", "watch").put("acknowledged", acknowledgements)
                .put("conflicts", conflicts).put("changes", changes)
                .put("nextCursor", cursor).put("hasMore", false)
                .put("serverTime", "2026-07-28T00:00:00.000Z");
    }

    private static SecretKey root() {
        byte[] bytes = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        assertEquals(32, bytes.length);
        return new SecretKeySpec(bytes, "AES");
    }
}

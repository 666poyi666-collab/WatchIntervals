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

    @Test public void onlyUnauthorizedCloudResponsesRetireTheDeviceToken() {
        assertTrue(EncryptedWatchSync.isRevokedDeviceTokenFailure(
                new EncryptedWatchSync.CloudHttpException(401)));
        assertFalse(EncryptedWatchSync.isRevokedDeviceTokenFailure(
                new EncryptedWatchSync.CloudHttpException(403)));
    }
    private static final String DEVICE_ID = "watch-device-test";

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
        assertFalse(EncryptedWatchSync.validCursor("c2gosa7pa2gw"));
        assertFalse(EncryptedWatchSync.validCursor("czzzzzzzzzzzzzzzz"));
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
        assertEquals(1, committed.getJSONArray("conflicts").length());
        assertEquals("远端候选", committed.getJSONObject("entities")
                .getJSONObject("plan\u0000plan-conflict").getJSONObject("payload")
                .getString("name"));
        assertEquals("plan\u0000plan-conflict",
                committed.getJSONArray("projectionPending").getString(0));
        assertEquals("c1", committed.getString("cursor"));
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

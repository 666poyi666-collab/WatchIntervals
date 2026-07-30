package com.poyi.watchintervals.phone;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class PhoneSyncOutboxTest {
    private static JSONObject library(long revision, String name) throws Exception {
        return new JSONObject().put("schemaVersion", 3).put("revision", revision)
                .put("selectedPlanId", "p1")
                .put("groups", new JSONArray().put(new JSONObject()
                        .put("id", "g1").put("name", "Group").put("sortOrder", 0)))
                .put("plans", new JSONArray().put(new JSONObject()
                        .put("id", "p1").put("name", name).put("groupId", "g1")
                        .put("requirement", "").put("stages", new JSONArray()
                                .put(new JSONObject().put("kind", "RUN")
                                        .put("unit", "DISTANCE").put("target", 1000)))))
                .put("deletedPlanIds", new JSONArray());
    }

    @Test public void sameSnapshotPreservesOperationIdAcrossAckLoss() throws Exception {
        JSONObject desired = PhoneSyncOutbox.buildLibraryOperation(
                library(4, "Plan"), "cloud_replace", "library", "v3d.production-owner");
        JSONObject firstAttempt = new JSONObject(desired.toString())
                .put("operationId", "11111111-1111-4111-8111-111111111111");
        JSONArray reconciled = PhoneSyncOutbox.reconcileOperations(
                new JSONArray().put(firstAttempt), desired, "");

        assertEquals(1, reconciled.length());
        assertEquals(firstAttempt.getString("operationId"),
                reconciled.getJSONObject(0).getString("operationId"));
    }

    @Test public void newerSnapshotSupersedesOldPendingAndAckedSnapshotStaysEmpty()
            throws Exception {
        JSONObject old = PhoneSyncOutbox.buildLibraryOperation(
                library(3, "Old"), "cloud_replace", "library", "v3d.production-owner");
        JSONObject desired = PhoneSyncOutbox.buildLibraryOperation(
                library(4, "New"), "cloud_replace", "library", "v3d.production-owner");
        JSONArray replaced = PhoneSyncOutbox.reconcileOperations(
                new JSONArray().put(old), desired, "");
        assertEquals(1, replaced.length());
        assertEquals(desired.getString("operationId"),
                replaced.getJSONObject(0).getString("operationId"));

        JSONArray acked = PhoneSyncOutbox.reconcileOperations(replaced, desired,
                desired.getString("projectionFingerprint"));
        assertEquals(0, acked.length());
    }

    @Test public void corruptedOutboxNeverParsesAsAnEmptySuccess() throws Exception {
        try {
            PhoneSyncOutbox.parseOperations("{not-an-array}");
            fail("corruption must fail closed");
        } catch (Exception expected) {
            assertNotNull(expected);
        }
        try {
            PhoneSyncOutbox.parseOperations("[null]");
            fail("non-object entries must fail closed");
        } catch (Exception expected) {
            assertNotNull(expected);
        }
        try {
            PhoneSyncOutbox.parseOperations("[{}]");
            fail("structurally incomplete entries must fail closed");
        } catch (Exception expected) {
            assertNotNull(expected);
        }
    }

    @Test public void oldAckCannotDeleteANewerSnapshotEnqueuedDuringNetworkIo()
            throws Exception {
        JSONObject sent = PhoneSyncOutbox.buildLibraryOperation(
                library(3, "Old"), "cloud_replace", "library", "v3d.production-owner");
        JSONObject newer = PhoneSyncOutbox.buildLibraryOperation(
                library(4, "New"), "cloud_replace", "library", "v3d.production-owner");
        JSONArray acknowledgements = new JSONArray().put(new JSONObject()
                .put("operationId", sent.getString("operationId"))
                .put("status", "applied"));

        JSONObject merged = PhoneSyncOutbox.reconcileAcknowledgements(
                new JSONArray().put(newer), new JSONArray().put(sent), acknowledgements, "");

        assertEquals(1, merged.getJSONArray("operations").length());
        assertEquals(newer.getString("operationId"),
                merged.getJSONArray("operations").getJSONObject(0).getString("operationId"));
        assertEquals(sent.getString("projectionFingerprint"), merged.getString("lastAck"));
    }

    @Test public void unsolicitedAckCannotRemoveAnOperationThatWasNotSent() throws Exception {
        JSONObject current = PhoneSyncOutbox.buildLibraryOperation(
                library(4, "Current"), "cloud_replace", "library", "v3d.production-owner");
        JSONArray acknowledgements = new JSONArray().put(new JSONObject()
                .put("operationId", current.getString("operationId"))
                .put("status", "applied"));

        JSONObject merged = PhoneSyncOutbox.reconcileAcknowledgements(
                new JSONArray().put(current), new JSONArray(), acknowledgements, "");

        assertEquals(1, merged.getJSONArray("operations").length());
        assertEquals("", merged.getString("lastAck"));
    }

    @Test public void returningToAnOlderSnapshotCreatesANewOperationId() throws Exception {
        JSONObject firstA = PhoneSyncOutbox.buildLibraryOperation(
                library(3, "A"), "cloud_replace", "library", "v3d.production-owner");
        PhoneSyncOutbox.buildLibraryOperation(
                library(4, "B"), "cloud_replace", "library", "v3d.production-owner");
        JSONObject secondA = PhoneSyncOutbox.buildLibraryOperation(
                library(5, "A"), "cloud_replace", "library", "v3d.production-owner");

        assertNotEquals(firstA.getString("operationId"), secondA.getString("operationId"));
    }

    @Test public void ackReceiptIsBoundToThePairingGeneration() {
        String first = PhoneSyncOutbox.receiptKeyForTarget("watch-1", "secret-a");
        assertEquals(first, PhoneSyncOutbox.receiptKeyForTarget("watch-1", "secret-a"));
        assertNotEquals(first, PhoneSyncOutbox.receiptKeyForTarget("watch-2", "secret-a"));
        assertNotEquals(first, PhoneSyncOutbox.receiptKeyForTarget("watch-1", "secret-b"));
    }

    @Test public void aNewPairingAlwaysBuildsANewDesiredProjection() throws Exception {
        JSONObject library = library(4, "Current");
        JSONObject first = PhoneSyncOutbox.buildLibraryOperation(library, "cloud_replace",
                "library", "v3d.production-owner", "target-a");
        JSONObject second = PhoneSyncOutbox.buildLibraryOperation(library, "cloud_replace",
                "library", "v3d.production-owner", "target-b");

        assertNotEquals(first.getString("projectionFingerprint"),
                second.getString("projectionFingerprint"));
    }

    @Test public void acknowledgedAStillRequeuesFreshAWhenBMayHaveReachedWatch() throws Exception {
        JSONObject libraryA = library(4, "A");
        JSONObject libraryB = library(5, "B");
        JSONObject firstA = PhoneSyncOutbox.buildLibraryOperation(
                libraryA, "cloud_replace", "library", "v3d.production-owner", "watch-pair");
        String acknowledgedAFingerprint = firstA.getString("projectionFingerprint");

        JSONArray pendingB = PhoneSyncOutbox.reconcileOperations(new JSONArray(),
                PhoneSyncOutbox.buildLibraryOperation(libraryB, "cloud_replace", "library",
                        "v3d.production-owner", "watch-pair"), acknowledgedAFingerprint);
        assertEquals(1, pendingB.length());
        String bOperationId = pendingB.getJSONObject(0).getString("operationId");

        JSONObject secondADesired = PhoneSyncOutbox.buildLibraryOperation(
                libraryA, "cloud_replace", "library", "v3d.production-owner", "watch-pair");
        JSONArray reconciled = PhoneSyncOutbox.reconcileOperations(
                pendingB, secondADesired, acknowledgedAFingerprint);

        assertEquals(1, reconciled.length());
        assertEquals(secondADesired.getString("operationId"),
                reconciled.getJSONObject(0).getString("operationId"));
        assertNotEquals(firstA.getString("operationId"),
                reconciled.getJSONObject(0).getString("operationId"));
        assertNotEquals(bOperationId, reconciled.getJSONObject(0).getString("operationId"));
        assertEquals(acknowledgedAFingerprint,
                reconciled.getJSONObject(0).getString("projectionFingerprint"));
    }

    @Test public void legacyDeleteJournalParsesAndMigratesToLibraryUpsert() throws Exception {
        JSONObject library = library(6, "After delete");
        JSONObject legacyDelete = PhoneSyncOutbox.buildLibraryOperation(
                library, "delete", "removed-plan", "", "watch-pair");

        JSONArray parsed = PhoneSyncOutbox.parseOperations(
                new JSONArray().put(legacyDelete).toString());
        JSONObject metadata = PhoneSyncOutbox.recoverProjectionMetadata(parsed, library);
        JSONObject desired = PhoneSyncOutbox.buildLibraryOperation(library,
                metadata.getString("operation"), "library",
                metadata.optString("cloudSourceId"), "watch-pair");
        JSONArray migrated = PhoneSyncOutbox.reconcileOperations(parsed, desired, "");

        assertEquals("upsert", metadata.getString("operation"));
        assertEquals(1, migrated.length());
        assertEquals("upsert", migrated.getJSONObject(0).getString("operation"));
        assertNotEquals(legacyDelete.getString("operationId"),
                migrated.getJSONObject(0).getString("operationId"));
    }

    @Test public void legacyPendingCloudProjectionRestoresItsSourceMetadata() throws Exception {
        JSONObject library = library(4, "Cloud");
        JSONObject legacy = PhoneSyncOutbox.buildLibraryOperation(
                library, "cloud_replace", "library", "legacy.0123456789abcdef");

        JSONObject metadata = PhoneSyncOutbox.recoverProjectionMetadata(
                new JSONArray().put(legacy), library);

        assertNotNull(metadata);
        assertEquals("cloud_replace", metadata.getString("operation"));
        assertEquals("legacy.0123456789abcdef", metadata.getString("cloudSourceId"));
    }
}

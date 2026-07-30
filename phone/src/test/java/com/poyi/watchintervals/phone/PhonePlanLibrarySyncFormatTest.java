package com.poyi.watchintervals.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.lang.reflect.Modifier;

public class PhonePlanLibrarySyncFormatTest {
    @Test public void schemaTwoMigratesWithEmptyExplicitTombstones() throws Exception {
        JSONObject migrated = PhonePlanLibrary.normalizeForTesting(new JSONObject()
                .put("schemaVersion", 2).put("revision", 10)
                .put("groups", new JSONArray()).put("plans", new JSONArray())
                .put("selectedPlanId", ""));
        assertEquals(3, migrated.getInt("schemaVersion"));
        assertEquals(0, migrated.getJSONArray("deletedPlanIds").length());
    }

    @Test public void onlyExplicitAbsentPlanIdsRemainTombstones() throws Exception {
        JSONObject plan = new JSONObject().put("id", "kept-plan").put("name", "保留")
                .put("group", "我的计划").put("stages", new JSONArray()
                        .put(new JSONObject().put("kind", "RUN").put("unit", "TIME").put("target", 60)));
        JSONObject normalized = PhonePlanLibrary.normalizeForTesting(new JSONObject()
                .put("revision", 10).put("groups", new JSONArray())
                .put("plans", new JSONArray().put(plan)).put("selectedPlanId", "kept-plan")
                .put("deletedPlanIds", new JSONArray()
                        .put(new JSONObject().put("id", "kept-plan").put("deletedAt", 1))
                        .put(new JSONObject().put("id", "deleted-plan").put("deletedAt", 2))));
        assertFalse(PhonePlanLibrary.pendingSyncDeletes(normalized).contains("kept-plan"));
        assertTrue(PhonePlanLibrary.pendingSyncDeletes(normalized).contains("deleted-plan"));
        JSONObject metadata = PhonePlanLibrary.syncMetadata(normalized);
        assertEquals(1, metadata.getJSONArray("deletedPlanIds").length());
        assertEquals("kept-plan", metadata.getString("selectedPlanId"));
    }

    @Test public void acknowledgedTombstoneRemainsAuthenticatedButIsNotRestaged()
            throws Exception {
        JSONObject normalized = PhonePlanLibrary.normalizeForTesting(new JSONObject()
                .put("revision", 10).put("groups", new JSONArray())
                .put("plans", new JSONArray()).put("selectedPlanId", "")
                .put("deletedPlanIds", new JSONArray().put(new JSONObject()
                        .put("id", "deleted-plan").put("deletedAt", 2)
                        .put("acknowledged", true).put("confirmedAt", 3))));
        assertFalse(PhonePlanLibrary.pendingSyncDeletes(normalized).contains("deleted-plan"));
        JSONObject metadata = PhonePlanLibrary.syncMetadata(normalized);
        assertTrue(metadata.getJSONArray("deletedPlanIds").getJSONObject(0)
                .getBoolean("acknowledged"));
    }

    @Test public void reservedMetadataIdIsRemappedDuringNormalization() throws Exception {
        JSONObject plan = new JSONObject().put("id", EncryptedWatchSync.PLAN_LIBRARY_ENTITY_ID)
                .put("name", "旧计划").put("group", "我的计划")
                .put("stages", new JSONArray().put(new JSONObject()
                        .put("kind", "RUN").put("unit", "TIME").put("target", 60)));
        JSONObject normalized = PhonePlanLibrary.normalizeForTesting(new JSONObject()
                .put("groups", new JSONArray()).put("plans", new JSONArray().put(plan))
                .put("selectedPlanId", EncryptedWatchSync.PLAN_LIBRARY_ENTITY_ID));
        assertFalse(EncryptedWatchSync.PLAN_LIBRARY_ENTITY_ID.equals(
                normalized.getJSONArray("plans").getJSONObject(0).getString("id")));
    }

    @Test public void cloudCompareAndApplyIsOneSynchronizedCriticalSection()
            throws Exception {
        assertTrue(Modifier.isSynchronized(PhonePlanLibrary.class.getDeclaredMethod(
                "applyCloudV3IfUnchanged", android.content.Context.class, JSONObject.class,
                String.class, String.class, long.class, String.class).getModifiers()));
        assertTrue(PhonePlanLibrary.cloudMetadataMatches(
                "v3d.production-owner", 4, "fingerprint",
                "v3d.production-owner", 4, "fingerprint", "fingerprint"));
        assertFalse(PhonePlanLibrary.cloudMetadataMatches(
                "v3d.production-owner", 4, "fingerprint",
                "v3d.production-owner", 4, "fingerprint", "locally-edited"));
    }

    @Test public void explicitNullSelectionAndGroupStayUnselectedAndUngrouped()
            throws Exception {
        JSONObject source = new JSONObject().put("schemaVersion", 3).put("revision", 4)
                .put("selectedPlanId", "").put("groups", new JSONArray())
                .put("deletedPlanIds", new JSONArray()).put("plans", new JSONArray().put(
                        new JSONObject().put("id", "plan-1").put("name", "Ungrouped")
                                .put("groupId", JSONObject.NULL).put("sortOrder", 9)
                                .put("stages", new JSONArray().put(new JSONObject()
                                        .put("kind", "RUN").put("unit", "TIME")
                                        .put("target", 60)))));

        JSONObject normalized = PhonePlanLibrary.normalizeForTesting(source);

        assertEquals("", normalized.getString("selectedPlanId"));
        assertEquals("", normalized.getJSONArray("plans").getJSONObject(0)
                .getString("groupId"));
        assertEquals(9, normalized.getJSONArray("plans").getJSONObject(0)
                .getInt("sortOrder"));
        assertEquals(0, normalized.getJSONArray("groups").length());
    }
}

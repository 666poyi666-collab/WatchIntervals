package com.poyi.watchintervals.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

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
        assertFalse(metadata.has("deletedPlanIds"));
        assertEquals("kept-plan", metadata.getString("selectedPlanId"));
    }
}

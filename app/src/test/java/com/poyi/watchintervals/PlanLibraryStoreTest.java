package com.poyi.watchintervals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PlanLibraryStoreTest {
    @Test public void cloudRevisionUsesItsOwnMonotonicDomain() {
        assertFalse(PlanLibraryStore.shouldRejectIncomingRevision(
                true, 6, 1_785_000_000_000L, 0));
        assertFalse(PlanLibraryStore.shouldRejectIncomingRevision(
                true, 7, 6, 6));
        assertTrue(PlanLibraryStore.shouldRejectIncomingRevision(
                true, 5, 6, 6));
    }

    @Test public void aNewCloudSourceStartsItsOwnRevisionDomain() {
        assertEquals(0L, PlanLibraryStore.cloudRevisionFloor(
                true, "legacy.0123456789abcdef", 9L, "v3d.production-owner"));
        assertEquals(9L, PlanLibraryStore.cloudRevisionFloor(
                true, "v3d.production-owner", 9L, "v3d.production-owner"));
        assertEquals(9L, PlanLibraryStore.cloudRevisionFloor(
                true, "v3d.production-owner", 9L, ""));
        assertFalse(PlanLibraryStore.shouldRejectIncomingRevision(true, 3, 9, 0));
        assertTrue(PlanLibraryStore.shouldRejectIncomingRevision(true, 2, 9, 3));
        assertTrue(PlanLibraryStore.cloudSourceTransitionAllowed(
                "0123456789abcdef", "v3d.production-owner"));
        assertTrue(PlanLibraryStore.cloudSourceTransitionAllowed(
                "v3d.production-owner", "v3d.production-owner"));
        assertFalse(PlanLibraryStore.cloudSourceTransitionAllowed(
                "v3d.production-owner", "legacy.0123456789abcdef"));
        assertFalse(PlanLibraryStore.cloudSourceTransitionAllowed(
                "v3d.production-owner", "v3d.retired-staging"));
        assertFalse(PlanLibraryStore.cloudSourceTransitionAllowed(
                "v3d.production-owner", ""));
    }

    @Test public void anEmptyLibraryClearsTheSelectedPlan() throws Exception {
        assertEquals("", PlanLibraryStore.normalizedSelectedPlanId(
                new java.util.HashSet<>(), new org.json.JSONArray(), "deleted-plan"));
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        ids.add("plan-1");
        org.json.JSONArray plans = new org.json.JSONArray().put(
                new org.json.JSONObject().put("id", "plan-1"));
        assertEquals("", PlanLibraryStore.normalizedSelectedPlanId(ids, plans, ""));
    }

    @Test public void legacyPhoneRevisionStillProtectsLocalOrdering() {
        assertTrue(PlanLibraryStore.shouldRejectIncomingRevision(
                false, 10, 11, 0));
        assertFalse(PlanLibraryStore.shouldRejectIncomingRevision(
                false, 11, 11, 0));
    }
}

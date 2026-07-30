package com.poyi.watchintervals;

import static org.junit.Assert.assertFalse;
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

    @Test public void legacyPhoneRevisionStillProtectsLocalOrdering() {
        assertTrue(PlanLibraryStore.shouldRejectIncomingRevision(
                false, 10, 11, 0));
        assertFalse(PlanLibraryStore.shouldRejectIncomingRevision(
                false, 11, 11, 0));
    }
}

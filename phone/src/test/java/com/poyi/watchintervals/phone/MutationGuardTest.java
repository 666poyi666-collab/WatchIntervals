package com.poyi.watchintervals.phone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MutationGuardTest {
    @Test public void matchingCachedHashIsDuplicate() {
        assertEquals(MutationGuard.Decision.DUPLICATE,
                MutationGuard.decide("request", "hash", "hash", true, 1, 2));
    }

    @Test public void reusedRequestIdWithDifferentPayloadConflicts() {
        assertEquals(MutationGuard.Decision.REQUEST_ID_REUSED,
                MutationGuard.decide("request", "new", "old", false, 0, 0));
    }

    @Test public void staleRevisionConflictsBeforeExecution() {
        assertEquals(MutationGuard.Decision.REVISION_CONFLICT,
                MutationGuard.decide("request", "hash", null, true, 9, 10));
    }

    @Test public void legacyRequestWithoutRevisionExecutes() {
        assertEquals(MutationGuard.Decision.EXECUTE,
                MutationGuard.decide("", "hash", null, false, 0, 10));
    }
}

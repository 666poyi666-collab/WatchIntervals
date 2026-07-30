package com.poyi.watchintervals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PlanStoreTest {
    @Test public void explicitEmptyMarkerDoesNotResurrectTheDefaultPlan() {
        assertTrue(PlanStore.resolveLoadedStages(null, true).isEmpty());
        assertTrue(PlanStore.resolveLoadedStages("[]", true).isEmpty());
        assertEquals(2, PlanStore.resolveLoadedStages(null, false).size());
    }
}

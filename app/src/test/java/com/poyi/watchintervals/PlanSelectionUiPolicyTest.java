package com.poyi.watchintervals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PlanSelectionUiPolicyTest {
    @Test public void listAndDetailAreMutuallyExclusive() {
        PlanSelectionUiPolicy policy = new PlanSelectionUiPolicy();
        assertEquals(PlanSelectionUiPolicy.Screen.LIST, policy.screen());

        policy.openDetails("plan-a");

        assertEquals(PlanSelectionUiPolicy.Screen.DETAIL, policy.screen());
        assertEquals("plan-a", policy.detailPlanId());
    }

    @Test public void blankRowsCannotOpenAnEmptyDetail() {
        PlanSelectionUiPolicy policy = new PlanSelectionUiPolicy();
        policy.openDetails("");
        policy.openDetails(null);

        assertEquals(PlanSelectionUiPolicy.Screen.LIST, policy.screen());
        assertEquals("", policy.detailPlanId());
    }

    @Test public void backReturnsFromDetailThenFinishesFromList() {
        PlanSelectionUiPolicy policy = new PlanSelectionUiPolicy();
        policy.openDetails("plan-a");

        assertEquals(PlanSelectionUiPolicy.BackAction.SHOW_LIST, policy.consumeBack());
        assertEquals(PlanSelectionUiPolicy.Screen.LIST, policy.screen());
        assertEquals(PlanSelectionUiPolicy.BackAction.FINISH, policy.consumeBack());
    }

    @Test public void resumeProjectionDropsADeletedDetail() {
        PlanSelectionUiPolicy policy = new PlanSelectionUiPolicy();
        policy.openDetails("plan-a");

        policy.reconcile(Collections.singleton("plan-b"));

        assertEquals(PlanSelectionUiPolicy.Screen.LIST, policy.screen());
        assertEquals("", policy.detailPlanId());
    }

    @Test public void resumeProjectionKeepsAnExistingDetail() {
        PlanSelectionUiPolicy policy = new PlanSelectionUiPolicy();
        policy.openDetails("plan-a");
        Set<String> ids = new HashSet<>();
        ids.add("plan-a");
        ids.add("plan-b");

        policy.reconcile(ids);

        assertEquals(PlanSelectionUiPolicy.Screen.DETAIL, policy.screen());
        assertTrue(policy.canSelect(ids));
    }

    @Test public void anEmptyLibraryNeverEnablesSelection() {
        PlanSelectionUiPolicy policy = new PlanSelectionUiPolicy();
        assertFalse(policy.canSelect(Collections.emptySet()));
        policy.openDetails("deleted-plan");
        policy.reconcile(Collections.emptySet());

        assertEquals(PlanSelectionUiPolicy.Screen.LIST, policy.screen());
        assertFalse(policy.canSelect(Collections.emptySet()));
    }
}

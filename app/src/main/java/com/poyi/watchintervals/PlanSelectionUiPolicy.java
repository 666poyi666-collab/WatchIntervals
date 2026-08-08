package com.poyi.watchintervals;

import java.util.Collections;
import java.util.Set;

/** Pure navigation policy for the mutually exclusive plan-list and plan-detail surfaces. */
final class PlanSelectionUiPolicy {
    enum Screen { LIST, DETAIL }
    enum BackAction { SHOW_LIST, FINISH }

    private Screen screen = Screen.LIST;
    private String detailPlanId = "";

    Screen screen() { return screen; }
    String detailPlanId() { return detailPlanId; }

    void openDetails(String planId) {
        if (planId == null || planId.isEmpty()) return;
        detailPlanId = planId;
        screen = Screen.DETAIL;
    }

    void showList() {
        detailPlanId = "";
        screen = Screen.LIST;
    }

    void reconcile(Set<String> availablePlanIds) {
        Set<String> ids = availablePlanIds == null ? Collections.emptySet() : availablePlanIds;
        if (screen == Screen.DETAIL && !ids.contains(detailPlanId)) showList();
    }

    boolean canSelect(Set<String> availablePlanIds) {
        return screen == Screen.DETAIL && availablePlanIds != null
                && availablePlanIds.contains(detailPlanId);
    }

    BackAction consumeBack() {
        if (screen == Screen.DETAIL) {
            showList();
            return BackAction.SHOW_LIST;
        }
        return BackAction.FINISH;
    }
}

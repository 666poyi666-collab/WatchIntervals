package com.poyi.watchintervals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class WatchCommandRouterTest {
    @Test public void toggleIsResolvedOnceToAnExplicitIdempotentAction() {
        assertEquals("pause", WatchCommandRouter.resolveExplicitAction("toggle", "RUNNING"));
        assertEquals("resume", WatchCommandRouter.resolveExplicitAction("toggle", "PAUSED"));
        assertEquals("", WatchCommandRouter.resolveExplicitAction("toggle", "STOPPED"));
        assertEquals("stop", WatchCommandRouter.resolveExplicitAction("stop", "RUNNING"));
    }

    @Test public void startPlanIdParticipatesInSignatureAndResolution() throws Exception {
        JSONObject commandA = command("plan-a");
        JSONObject commandB = command("plan-b");
        assertNotEquals(WatchCommandRouter.commandSignature("start", commandA),
                WatchCommandRouter.commandSignature("start", commandB));

        JSONObject library = new JSONObject().put("selectedPlanId", "plan-a")
                .put("plans", new JSONArray()
                        .put(new JSONObject().put("id", "plan-a"))
                        .put(new JSONObject().put("id", "plan-b")));
        assertEquals("plan-b", WatchCommandRouter.resolveStartPlanId(library, "plan-b"));
        assertEquals("plan-a", WatchCommandRouter.resolveStartPlanId(library, ""));
        try {
            WatchCommandRouter.resolveStartPlanId(library, "deleted-plan");
            throw new AssertionError("a missing requested plan must not fall back to selection");
        } catch (IllegalArgumentException expected) {
            assertEquals("plan_not_found", expected.getMessage());
        }
    }

    @Test public void failedPrepareCommitCannotRunTheSideEffect() throws Exception {
        AtomicInteger effects = new AtomicInteger();
        try {
            WatchCommandRouter.commitBeforeSideEffect(
                    () -> { throw new IllegalStateException("journal_commit_failed"); },
                    effects::incrementAndGet);
            throw new AssertionError("failed durable prepare must abort");
        } catch (IllegalStateException expected) {
            assertEquals("journal_commit_failed", expected.getMessage());
        }
        assertEquals(0, effects.get());
        assertEquals(Integer.valueOf(1), WatchCommandRouter.commitBeforeSideEffect(
                () -> {}, effects::incrementAndGet));
        assertEquals(1, effects.get());
    }

    @Test public void journalTrimmingNeverEvictsTheNewPreparedCommand() throws Exception {
        JSONObject journal = new JSONObject();
        for (int index = 0; index < 100; index++) {
            journal.put("old-" + index, new JSONObject().put("result", true));
        }
        journal.put("new-command", new JSONObject().put("resolvedAction", "pause"));

        WatchCommandRouter.trimJournal(journal, "new-command");

        assertEquals(100, journal.length());
        assertEquals("pause", journal.getJSONObject("new-command")
                .getString("resolvedAction"));
    }

    private static JSONObject command(String planId) throws Exception {
        return new JSONObject().put("expiresAt", 1234L).put("expectedState", "STOPPED")
                .put("controlRevision", 7L).put("planId", planId);
    }
}

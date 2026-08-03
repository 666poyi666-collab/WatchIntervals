package com.poyi.watchintervals;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorkoutUxPolicyTest {
    @Test public void activeWorkoutRoutesEveryAppEntryStraightToTraining() {
        assertEquals(WorkoutUxPolicy.EntryDestination.TRAINING,
                WorkoutUxPolicy.entryDestination(true));
        assertEquals(WorkoutUxPolicy.EntryDestination.MAIN,
                WorkoutUxPolicy.entryDestination(false));
    }

    @Test public void onlyTrainingSurfaceCountsAsAnAlreadyRestoredActiveEntry() {
        assertFalse(WorkoutUxPolicy.shouldRouteAppEntryToTraining(true,
                WorkoutUxPolicy.AppSurface.TRAINING));
        assertTrue(WorkoutUxPolicy.shouldRouteAppEntryToTraining(true,
                WorkoutUxPolicy.AppSurface.MAIN));
        assertTrue(WorkoutUxPolicy.shouldRouteAppEntryToTraining(true,
                WorkoutUxPolicy.AppSurface.OTHER_IN_APP));
        for (WorkoutUxPolicy.AppSurface surface : WorkoutUxPolicy.AppSurface.values()) {
            assertFalse(WorkoutUxPolicy.shouldRouteAppEntryToTraining(false, surface));
        }
    }

    @Test public void firstSnapshotAndRepeatedSnapshotsDoNotCreateTransitionCards() {
        assertFalse(WorkoutUxPolicy.stageNotice(-1, 1, false).visible);
        assertFalse(WorkoutUxPolicy.stageNotice(1, 1, false).visible);
        assertFalse(WorkoutUxPolicy.stageNotice(2, 1, false).visible);
    }

    @Test public void advancingStageCreatesShortNonBlockingNotice() {
        WorkoutUxPolicy.StageNotice notice = WorkoutUxPolicy.stageNotice(1, 2, false);

        assertTrue(notice.visible);
        assertTrue(notice.durationMillis > 0L);
        assertTrue(notice.durationMillis <= WorkoutUxPolicy.MAX_STAGE_NOTICE_MILLIS);
        assertFalse(notice.blocksInput);
        assertFalse(notice.requiresAcknowledgement);
    }

    @Test public void planCompletionDoesNotCoverFreeRecordingWithAStageCard() {
        assertFalse(WorkoutUxPolicy.stageNotice(2, 3, true).visible);
    }

    @Test public void kilometreCueDoesNotStackOverCoincidentStageChange() {
        assertFalse(WorkoutUxPolicy.allowLapCue(true));
        assertTrue(WorkoutUxPolicy.allowLapCue(false));
    }

    @Test public void resumedPresentationBaselinesStageAndLapWithoutReplayingOldCues() {
        WorkoutUxPolicy.TransientCueTracker tracker =
                new WorkoutUxPolicy.TransientCueTracker();

        assertFalse(tracker.observeStage(1, false).visible);
        assertFalse(tracker.shouldShowLap(0, false));
        assertTrue(tracker.observeStage(2, false).visible);
        assertTrue(tracker.shouldShowLap(1, false));

        tracker.reset();

        assertFalse(tracker.observeStage(3, false).visible);
        assertFalse(tracker.shouldShowLap(2, false));
        assertTrue(tracker.observeStage(4, false).visible);
        assertTrue(tracker.shouldShowLap(3, false));
    }

    @Test public void trackerConsumesCoincidentLapEvenWhenItsCardIsSuppressed() {
        WorkoutUxPolicy.TransientCueTracker tracker =
                new WorkoutUxPolicy.TransientCueTracker();
        assertFalse(tracker.shouldShowLap(0, false));
        assertFalse(tracker.shouldShowLap(1, true));
        assertFalse(tracker.shouldShowLap(1, false));
        assertTrue(tracker.shouldShowLap(2, false));
    }

    @Test public void cueIsBriefAndCallersCannotMutateItsPattern() {
        WorkoutUxPolicy.Cue cue = WorkoutUxPolicy.cue(false);
        long[] first = cue.vibrationPattern();
        long[] second = cue.vibrationPattern();

        assertEquals(160, cue.toneDurationMillis);
        assertTrue(cue.toneVolumePercent > 0 && cue.toneVolumePercent <= 100);
        assertArrayEquals(new long[]{0L, 80L, 60L, 120L}, first);
        assertNotSame(first, second);
        first[1] = 999L;
        assertArrayEquals(new long[]{0L, 80L, 60L, 120L}, cue.vibrationPattern());
    }

    @Test public void completionCueRemainsDistinctButNonBlocking() {
        assertArrayEquals(new long[]{0L, 100L, 70L, 160L},
                WorkoutUxPolicy.cue(true).vibrationPattern());
    }
}

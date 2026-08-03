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

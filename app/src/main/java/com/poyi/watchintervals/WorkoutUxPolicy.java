package com.poyi.watchintervals;

/**
 * Pure workout UX contract shared by the service and activities.
 *
 * <p>{@link WorkoutService} remains the only owner of workout state. This class only turns an
 * already-known active-session/stage snapshot into deterministic presentation decisions, which
 * keeps entry routing and transient cues unit-testable without an Android runtime.</p>
 */
final class WorkoutUxPolicy {
    static final long MAX_STAGE_NOTICE_MILLIS = 2_000L;
    static final long STAGE_NOTICE_MILLIS = 1_800L;
    static final int STAGE_TONE_DURATION_MILLIS = 160;
    static final int STAGE_TONE_VOLUME_PERCENT = 78;

    private static final long[] NEXT_STAGE_VIBRATION = {0L, 80L, 60L, 120L};
    private static final long[] PLAN_COMPLETE_VIBRATION = {0L, 100L, 70L, 160L};

    enum EntryDestination { MAIN, TRAINING }
    enum AppSurface { TRAINING, MAIN, OTHER_IN_APP }

    private WorkoutUxPolicy() {}

    static EntryDestination entryDestination(boolean hasRecoverableWorkout) {
        return hasRecoverableWorkout ? EntryDestination.TRAINING : EntryDestination.MAIN;
    }

    /** An explicit entry may replace any in-app surface, but never fights another foreground app. */
    static boolean shouldRouteAppEntryToTraining(boolean hasRecoverableWorkout,
                                                  AppSurface currentSurface) {
        return hasRecoverableWorkout && currentSurface != AppSurface.TRAINING;
    }

    static StageNotice stageNotice(int displayedStageNumber, int currentStageNumber,
                                   boolean planCompleted) {
        boolean advancing = displayedStageNumber > 0
                && currentStageNumber > displayedStageNumber
                && !planCompleted;
        return advancing
                ? new StageNotice(true, STAGE_NOTICE_MILLIS, false, false)
                : StageNotice.HIDDEN;
    }

    /** A coincident kilometre split must not stack a second card/buzz over the stage change. */
    static boolean allowLapCue(boolean stageChangedOnSameUpdate) {
        return !stageChangedOnSameUpdate;
    }

    static Cue cue(boolean planCompleted) {
        return new Cue(STAGE_TONE_DURATION_MILLIS, STAGE_TONE_VOLUME_PERCENT,
                planCompleted ? PLAN_COMPLETE_VIBRATION : NEXT_STAGE_VIBRATION);
    }

    /**
     * Activity-local cursor for transient cards. Resetting it when the presentation stops makes
     * the first snapshot after screen-off/task restore a baseline, so a cue that already happened
     * in the service is not replayed by a reused {@code TrainingActivity}.
     */
    static final class TransientCueTracker {
        private int displayedStageNumber = -1;
        private int displayedSplitCount = -1;

        StageNotice observeStage(int currentStageNumber, boolean planCompleted) {
            StageNotice notice = stageNotice(displayedStageNumber, currentStageNumber,
                    planCompleted);
            displayedStageNumber = currentStageNumber;
            return notice;
        }

        boolean shouldShowLap(int currentSplitCount, boolean stageChangedOnSameUpdate) {
            if (displayedSplitCount < 0) {
                displayedSplitCount = currentSplitCount;
                return false;
            }
            if (currentSplitCount <= displayedSplitCount) return false;
            displayedSplitCount = currentSplitCount;
            return allowLapCue(stageChangedOnSameUpdate);
        }

        void reset() {
            displayedStageNumber = -1;
            displayedSplitCount = -1;
        }
    }

    static final class StageNotice {
        private static final StageNotice HIDDEN = new StageNotice(false, 0L, false, false);

        final boolean visible;
        final long durationMillis;
        final boolean blocksInput;
        final boolean requiresAcknowledgement;

        private StageNotice(boolean visible, long durationMillis, boolean blocksInput,
                            boolean requiresAcknowledgement) {
            this.visible = visible;
            this.durationMillis = durationMillis;
            this.blocksInput = blocksInput;
            this.requiresAcknowledgement = requiresAcknowledgement;
        }
    }

    static final class Cue {
        final int toneDurationMillis;
        final int toneVolumePercent;
        private final long[] vibrationPattern;

        private Cue(int toneDurationMillis, int toneVolumePercent, long[] vibrationPattern) {
            this.toneDurationMillis = toneDurationMillis;
            this.toneVolumePercent = toneVolumePercent;
            this.vibrationPattern = vibrationPattern.clone();
        }

        long[] vibrationPattern() {
            return vibrationPattern.clone();
        }
    }
}

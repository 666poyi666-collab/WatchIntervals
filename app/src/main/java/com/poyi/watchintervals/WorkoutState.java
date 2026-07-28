package com.poyi.watchintervals;

final class WorkoutState {
    enum SessionState { PREPARING, RUNNING, PAUSED, STOPPED }
    enum PlanState { ACTIVE, COMPLETED }

    private WorkoutState() {}
}

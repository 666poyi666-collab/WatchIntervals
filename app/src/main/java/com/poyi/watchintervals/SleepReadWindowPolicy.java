package com.poyi.watchintervals;

/** Pure guard for HealthKit's hasMore-without-cursor time-window bisection. */
final class SleepReadWindowPolicy {
    private SleepReadWindowPolicy() {}

    static boolean shouldSplit(boolean hasMore, int depth, long rangeSeconds) {
        return hasMore && depth < 10 && rangeSeconds > 3_600L;
    }
}

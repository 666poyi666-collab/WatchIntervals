package com.poyi.watchintervals;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SleepReadWindowPolicyTest {
    @Test public void hasMoreBisectsBoundedRangesButEventuallyStops() {
        assertTrue(SleepReadWindowPolicy.shouldSplit(true, 0, 31L * 86_400L));
        assertFalse(SleepReadWindowPolicy.shouldSplit(false, 0, 31L * 86_400L));
        assertFalse(SleepReadWindowPolicy.shouldSplit(true, 10, 31L * 86_400L));
        assertFalse(SleepReadWindowPolicy.shouldSplit(true, 2, 3_600L));
    }
}

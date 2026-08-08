package com.poyi.watchintervals.phone;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneSleepSyncWorkerTest {
    @Test public void userPermissionStateDoesNotSpinRetryLoop() {
        assertFalse(PhoneSleepSyncWorker.shouldRetry("ready", true));
        assertFalse(PhoneSleepSyncWorker.shouldRetry("permission_required", true));
        assertTrue(PhoneSleepSyncWorker.shouldRetry("error", true));
        assertTrue(PhoneSleepSyncWorker.shouldRetry("", false));
    }
}

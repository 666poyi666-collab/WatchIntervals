package com.poyi.watchintervals.phone;

import static org.junit.Assert.*;
import com.poyi.watchintervals.phone.connection.ConnectionState;
import org.junit.Test;

public class PhonePlanProjectionSyncTest {
    @Test public void pendingProjectionRetriesOnlyWhenWatchTransportIsUsable() {
        assertTrue(PhonePlanProjectionSync.shouldAttempt(
                ConnectionState.CONNECTED_BLE, 1, false));
        assertTrue(PhonePlanProjectionSync.shouldAttempt(
                ConnectionState.CONNECTED_BLE_LAN, 1, false));
        assertTrue(PhonePlanProjectionSync.shouldAttempt(
                ConnectionState.CONNECTED_LAN, 1, false));
        assertFalse(PhonePlanProjectionSync.shouldAttempt(
                ConnectionState.BACKOFF, 1, false));
        assertFalse(PhonePlanProjectionSync.shouldAttempt(
                ConnectionState.CONNECTED_BLE, 0, false));
        assertFalse(PhonePlanProjectionSync.shouldAttempt(
                ConnectionState.CONNECTED_BLE, 1, true));
    }

    @Test public void projectionWorkerRetriesWithoutCloudCredentialOrInternetState() {
        assertTrue(PhonePlanProjectionWorker.shouldRetry(false, 1));
        assertTrue(PhonePlanProjectionWorker.shouldRetry(true, 1));
        assertFalse(PhonePlanProjectionWorker.shouldRetry(true, 0));
    }
}

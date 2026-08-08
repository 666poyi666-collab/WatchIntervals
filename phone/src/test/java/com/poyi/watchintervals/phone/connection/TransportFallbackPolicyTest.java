package com.poyi.watchintervals.phone.connection;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransportFallbackPolicyTest {
    @Test public void onlyReadRequestsFallBackFromLanToBle() {
        assertTrue(TransportFallbackPolicy.shouldRetryOnBle("GET", TransportType.LAN, true));
        assertTrue(TransportFallbackPolicy.shouldRetryOnBle("get", TransportType.LAN, true));
        assertFalse(TransportFallbackPolicy.shouldRetryOnBle("DELETE", TransportType.LAN, true));
        assertFalse(TransportFallbackPolicy.shouldRetryOnBle("GET", TransportType.BLE, true));
        assertFalse(TransportFallbackPolicy.shouldRetryOnBle("GET", TransportType.LAN, false));
    }
}

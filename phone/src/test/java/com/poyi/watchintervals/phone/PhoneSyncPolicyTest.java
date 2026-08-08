package com.poyi.watchintervals.phone;

import com.poyi.watchintervals.phone.connection.ConnectionState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneSyncPolicyTest {
    @Test public void reconnectStartsOneRefreshButRepeatedSnapshotsDoNot() {
        assertTrue(PhoneSyncPolicy.shouldAutoSync(null, ConnectionState.CONNECTED_BLE, false));
        assertFalse(PhoneSyncPolicy.shouldAutoSync(ConnectionState.CONNECTED_BLE,
                ConnectionState.CONNECTED_BLE_LAN, false));
        assertFalse(PhoneSyncPolicy.shouldAutoSync(ConnectionState.DISCONNECTED,
                ConnectionState.CONNECTED_LAN, true));
        assertTrue(PhoneSyncPolicy.shouldAutoSync(ConnectionState.BACKOFF,
                ConnectionState.CONNECTED_BLE, false));
    }

    @Test public void progressAndSuccessLabelsRemainUserReadable() {
        assertEquals("读取睡眠  ·  2/4", PhoneSyncPolicy.progressLabel(2, 4, "读取睡眠"));
        assertEquals("已同步 · 12:34", PhoneSyncPolicy.successLabel(true, "12:34"));
        assertEquals("已同步训练数据 · 睡眠保留上次记录",
                PhoneSyncPolicy.successLabel(false, "12:34"));
    }
}

package com.poyi.watchintervals.phone.connection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WatchCloudBridgeEventTest {
    private static ResponseEnvelope event(String body) {
        return new ResponseEnvelope("message", "event", 200, body);
    }

    @Test public void acceptsOnlyTheExactVersionedHistoryHint() {
        assertTrue(WatchCloudBridgeEvent.isHistoryChanged(
                event("{\"eventVersion\":1,\"event\":\"history_changed\"}")));
        assertFalse(WatchCloudBridgeEvent.isHistoryChanged(
                event("{\"eventVersion\":2,\"event\":\"history_changed\"}")));
        assertFalse(WatchCloudBridgeEvent.isHistoryChanged(
                event("{\"eventVersion\":1,\"event\":\"history_changed\",\"steps\":1}")));
        assertFalse(WatchCloudBridgeEvent.isHistoryChanged(
                new ResponseEnvelope("message", "request-id", 200,
                        "{\"eventVersion\":1,\"event\":\"history_changed\"}")));
        assertFalse(WatchCloudBridgeEvent.isHistoryChanged(
                new ResponseEnvelope("message", "event", 401,
                        "{\"eventVersion\":1,\"event\":\"history_changed\"}")));
        assertFalse(WatchCloudBridgeEvent.isHistoryChanged(event("not-json")));
    }
}

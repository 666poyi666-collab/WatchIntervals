package com.poyi.watchintervals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class WatchCloudBridgeEventTest {
    @Test public void historyHintContainsNoBusinessOrSensitiveData() throws Exception {
        JSONObject envelope = WatchCloudBridgeEvent.historyChangedEnvelope();
        assertEquals("EVENT", envelope.getString("type"));
        assertEquals("event", envelope.getString("replyTo"));
        JSONObject body = new JSONObject(envelope.getJSONObject("payload").getString("body"));
        assertEquals(2, body.length());
        assertEquals(1, body.getInt("eventVersion"));
        assertEquals("history_changed", body.getString("event"));

        String serialized = envelope.toString();
        assertFalse(serialized.contains("latitude"));
        assertFalse(serialized.contains("longitude"));
        assertFalse(serialized.contains("heart"));
        assertFalse(serialized.contains("sleep"));
        assertFalse(serialized.contains("credential"));
        assertFalse(serialized.contains("deviceId"));
        assertTrue(envelope.getString("messageId").length() > 20);
    }
}

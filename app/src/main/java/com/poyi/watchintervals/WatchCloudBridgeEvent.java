package com.poyi.watchintervals;

import java.util.UUID;
import org.json.JSONObject;

/**
 * Privacy-minimal BLE event contract used only to wake the phone's encrypted cloud sync.
 *
 * <p>No workout, route, heart, sleep, credential, or device identity data belongs here. The phone
 * always reads the canonical history through the authenticated Watch API after receiving this
 * hint.
 */
final class WatchCloudBridgeEvent {
    static final int VERSION = 1;
    static final String REPLY_TO = "event";
    static final String HISTORY_CHANGED = "history_changed";

    private WatchCloudBridgeEvent() {}

    static JSONObject historyChangedEnvelope() throws Exception {
        JSONObject body = new JSONObject()
                .put("eventVersion", VERSION)
                .put("event", HISTORY_CHANGED);
        return new JSONObject()
                .put("protocolVersion", 1)
                .put("messageId", UUID.randomUUID().toString())
                .put("replyTo", REPLY_TO)
                .put("type", "EVENT")
                .put("createdAt", System.currentTimeMillis())
                .put("payload", new JSONObject().put("status", 200).put("body", body.toString()));
    }
}

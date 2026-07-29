package com.poyi.watchintervals.phone.connection;

import org.json.JSONObject;

/**
 * Strict parser for privacy-minimal Watch BLE hints.
 *
 * <p>The hint never carries business data. It only causes the phone to read the authenticated
 * Watch API and enqueue the existing encrypted, idempotent cloud synchronization.
 */
public final class WatchCloudBridgeEvent {
    static final int VERSION = 1;
    static final String REPLY_TO = "event";
    static final String HISTORY_CHANGED = "history_changed";

    private WatchCloudBridgeEvent() {}

    public static boolean isHistoryChanged(ResponseEnvelope envelope) {
        if (envelope == null || envelope.status != 200 || !REPLY_TO.equals(envelope.replyTo)) {
            return false;
        }
        try {
            JSONObject body = new JSONObject(envelope.body);
            return body.length() == 2
                    && body.optInt("eventVersion", -1) == VERSION
                    && HISTORY_CHANGED.equals(body.optString("event"));
        } catch (Exception ignored) {
            return false;
        }
    }
}

package com.poyi.watchintervals.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CloudV3DrainPolicyTest {
    @Test public void remainingOutboxImmediatelyStartsAnotherBoundedExchange() throws Exception {
        JSONObject state = new JSONObject().put("outbox", new JSONArray())
                .put("commandResults", new JSONArray());
        assertFalse(CloudV3Sync.shouldContinueDrain(state, false));
        state.getJSONArray("outbox").put(new JSONObject());
        assertTrue(CloudV3Sync.shouldContinueDrain(state, false));
        state.put("outbox", new JSONArray());
        assertTrue(CloudV3Sync.shouldContinueDrain(state, true));
    }
}

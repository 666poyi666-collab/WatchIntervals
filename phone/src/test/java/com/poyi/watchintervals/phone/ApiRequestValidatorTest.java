package com.poyi.watchintervals.phone;

import static org.junit.Assert.assertEquals;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Test;

public class ApiRequestValidatorTest {
    @Test public void writeRequiresUuidAndRevision() throws Exception {
        assertEquals("invalid_request_id", ApiRequestValidator.validateWrite(new JSONObject()));
        JSONObject value = new JSONObject().put("requestId", UUID.randomUUID().toString());
        assertEquals("expected_revision_required", ApiRequestValidator.validateWrite(value));
        assertEquals("expected_revision_required",
                ApiRequestValidator.validateWrite(value.put("expectedRevision", "4")));
        assertEquals("", ApiRequestValidator.validateWrite(value.put("expectedRevision", 4L)));
    }

    @Test public void controlRequiresCommandContractAndRejectsExpiry() throws Exception {
        long now = 1000L;
        JSONObject value = new JSONObject().put("requestId", UUID.randomUUID().toString())
                .put("expectedRevision", 1L).put("commandId", UUID.randomUUID().toString())
                .put("expectedState", "RUNNING").put("expiresAt", now - 1L);
        assertEquals("command_expired", ApiRequestValidator.validateControl(value, now));
        value.put("expiresAt", "1001");
        assertEquals("expires_at_required", ApiRequestValidator.validateControl(value, now));
        value.put("expiresAt", now + 1L);
        assertEquals("", ApiRequestValidator.validateControl(value, now));
    }
}

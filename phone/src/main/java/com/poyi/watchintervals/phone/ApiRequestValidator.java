package com.poyi.watchintervals.phone;

import java.util.UUID;
import org.json.JSONObject;

/** Pure validation shared by all versioned API write paths. */
final class ApiRequestValidator {
    private ApiRequestValidator() {}

    static String validateWrite(JSONObject request) {
        String requestId = request.optString("requestId", "");
        if (!validUuid(requestId)) return "invalid_request_id";
        if (!(request.opt("expectedRevision") instanceof Number))
            return "expected_revision_required";
        return "";
    }

    static String validateControl(JSONObject request, long now) {
        String writeError = validateWrite(request);
        if (!writeError.isEmpty()) return writeError;
        if (!validUuid(request.optString("commandId", ""))) return "invalid_command_id";
        String expectedState = request.optString("expectedState", "");
        if (expectedState.trim().isEmpty()) return "expected_state_required";
        if (!(request.opt("expiresAt") instanceof Number))
            return "expires_at_required";
        if (request.optLong("expiresAt", 0L) < now) return "command_expired";
        return "";
    }

    static boolean validUuid(String value) {
        if (value == null || value.isEmpty()) return false;
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }
}

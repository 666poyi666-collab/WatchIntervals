package com.poyi.watchintervals.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import org.json.JSONObject;

/** Owns the independently generated desktop Gateway API credential. */
final class GatewayApiTokenStore {
    static final class IssueResult {
        final int status;
        final JSONObject body;
        IssueResult(int status, JSONObject body) { this.status = status; this.body = body; }
    }

    private static final String PREFS = "gateway_api_auth";
    private static final String TOKEN = "token";
    private static final String TOKEN_CIPHERTEXT = "token_ciphertext";
    private static final String TOKEN_NONCE = "token_nonce";
    private static final String REVISION = "revision";
    private static final String ISSUE_REQUEST_ID = "issue_request_id";

    private GatewayApiTokenStore() {}

    static boolean matches(Context context, String presented) {
        String expected = loadToken(context);
        if (expected == null || expected.isEmpty() || presented == null || presented.isEmpty()) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    static synchronized IssueResult issue(Context context, String requestId, long expectedRevision) {
        if (!ApiRequestValidator.validUuid(requestId)) return error(422, "invalid_request_id");
        SharedPreferences values = preferences(context);
        long actualRevision = values.getLong(REVISION, 0L);
        String previousRequestId = values.getString(ISSUE_REQUEST_ID, "");
        String token = loadToken(context);
        if (token.isEmpty() && (values.contains(TOKEN) || values.contains(TOKEN_CIPHERTEXT))) {
            return error(500, "token_unavailable");
        }
        if (requestId.equals(previousRequestId) && token != null && !token.isEmpty()) {
            return success(token, actualRevision, true);
        }
        if (actualRevision != expectedRevision) return conflict(expectedRevision, actualRevision);
        if (token != null && !token.isEmpty()) return error(409, "token_already_issued");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        token = Base64.encodeToString(random, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
        long nextRevision = actualRevision + 1L;
        AndroidSecretStore.EncryptedValue encrypted;
        try { encrypted = AndroidSecretStore.encrypt(token, tokenAad()); }
        catch (Exception failure) { return error(500, "token_encryption_failed"); }
        boolean saved = values.edit().putString(TOKEN_CIPHERTEXT, encrypted.ciphertext)
                .putString(TOKEN_NONCE, encrypted.nonce).remove(TOKEN)
                .putLong(REVISION, nextRevision).putString(ISSUE_REQUEST_ID, requestId).commit();
        if (!saved) return error(500, "token_persistence_failed");
        return success(token, nextRevision, false);
    }

    static long revision(Context context) { return preferences(context).getLong(REVISION, 0L); }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static synchronized String loadToken(Context context) {
        SharedPreferences values = preferences(context);
        String decrypted = AndroidSecretStore.decrypt(values.getString(TOKEN_CIPHERTEXT, ""),
                values.getString(TOKEN_NONCE, ""), tokenAad());
        if (decrypted != null && !decrypted.isEmpty()) return decrypted;
        String legacy = values.getString(TOKEN, "");
        if (legacy == null || legacy.isEmpty()) return "";
        try {
            AndroidSecretStore.EncryptedValue encrypted = AndroidSecretStore.encrypt(legacy, tokenAad());
            if (!values.edit().putString(TOKEN_CIPHERTEXT, encrypted.ciphertext)
                    .putString(TOKEN_NONCE, encrypted.nonce).remove(TOKEN).commit()) return "";
            return legacy;
        } catch (Exception failure) { return ""; }
    }

    private static String tokenAad() { return "watch-gateway-api-token-v1"; }

    private static IssueResult success(String token, long revision, boolean duplicate) {
        try {
            return new IssueResult(200, new JSONObject().put("token", token).put("tokenRevision", revision)
                    .put("duplicate", duplicate));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static IssueResult conflict(long expected, long actual) {
        try {
            return new IssueResult(409, new JSONObject().put("error", "revision_conflict")
                    .put("expectedRevision", expected).put("actualRevision", actual));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static IssueResult error(int status, String code) {
        try { return new IssueResult(status, new JSONObject().put("error", code)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}

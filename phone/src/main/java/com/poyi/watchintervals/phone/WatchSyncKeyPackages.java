package com.poyi.watchintervals.phone;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

/** Pure cryptographic package codec used by Android Keystore-backed Watch sync credentials. */
final class WatchSyncKeyPackages {
    static final String RECOVERY_PREFIX = "wrr1.";
    static final String APPROVAL_REQUEST_PREFIX = "war1.";
    static final String APPROVAL_PREFIX = "waa1.";
    static final long APPROVAL_LIFETIME_MILLIS = 10 * 60_000L;
    private static final String PRODUCT = "watch";
    private static final int ROOT_BYTES = 32;
    private static final int RECOVERY_ITERATIONS = 310_000;
    private static final int MAX_PACKAGE_CHARS = 16_384;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);

    private WatchSyncKeyPackages() {}

    static final class ApprovalBinding {
        final String targetDeviceId;
        final String requestNonce;
        final long expiresAt;
        ApprovalBinding(String targetDeviceId, String requestNonce, long expiresAt) {
            this.targetDeviceId = targetDeviceId;
            this.requestNonce = requestNonce;
            this.expiresAt = expiresAt;
        }
    }

    static String createRecoveryPackage(byte[] rootKey, String recoveryKey) throws Exception {
        requireRoot(rootKey);
        char[] normalized = normalizedRecoveryKey(recoveryKey);
        byte[] salt = randomBytes(16);
        byte[] nonce = randomBytes(12);
        SecretKey wrapping = deriveRecoveryKey(normalized, salt, RECOVERY_ITERATIONS);
        Arrays.fill(normalized, '\0');
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, wrapping, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(recoveryAad());
        byte[] ciphertext = cipher.doFinal(Arrays.copyOf(rootKey, rootKey.length));
        JSONObject value = new JSONObject()
                .put("version", 1)
                .put("product", PRODUCT)
                .put("kdf", "PBKDF2-HMAC-SHA256")
                .put("iterations", RECOVERY_ITERATIONS)
                .put("salt", encode(salt))
                .put("nonce", encode(nonce))
                .put("ciphertext", encode(ciphertext));
        return RECOVERY_PREFIX + encode(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    static byte[] restoreRecoveryPackage(String encodedPackage, String recoveryKey) throws Exception {
        JSONObject value = decodeObject(encodedPackage, RECOVERY_PREFIX);
        requireExactKeys(value, "version", "product", "kdf", "iterations", "salt", "nonce", "ciphertext");
        if (value.getInt("version") != 1 || !PRODUCT.equals(value.getString("product")) ||
                !"PBKDF2-HMAC-SHA256".equals(value.getString("kdf")) ||
                value.getInt("iterations") != RECOVERY_ITERATIONS) {
            throw new IllegalArgumentException("invalid_recovery_package");
        }
        byte[] salt = decode(value.getString("salt"), 16, 16);
        byte[] nonce = decode(value.getString("nonce"), 12, 12);
        byte[] ciphertext = decode(value.getString("ciphertext"), ROOT_BYTES + 16, ROOT_BYTES + 16);
        char[] normalized = normalizedRecoveryKey(recoveryKey);
        SecretKey wrapping = deriveRecoveryKey(normalized, salt, RECOVERY_ITERATIONS);
        Arrays.fill(normalized, '\0');
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, wrapping, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(recoveryAad());
        byte[] root = cipher.doFinal(ciphertext);
        requireRoot(root);
        return root;
    }

    static String createApprovalRequest(String targetDeviceId, PublicKey targetPublicKey,
                                        long now) throws Exception {
        requireDeviceId(targetDeviceId);
        if (targetPublicKey == null || !"RSA".equalsIgnoreCase(targetPublicKey.getAlgorithm()) ||
                targetPublicKey.getEncoded() == null) {
            throw new IllegalArgumentException("invalid_approval_public_key");
        }
        byte[] encodedKey = targetPublicKey.getEncoded();
        if (encodedKey.length < 256 || encodedKey.length > 1024) {
            throw new IllegalArgumentException("invalid_approval_public_key");
        }
        String publicKey = encode(encodedKey);
        String fingerprint = sha256Hex(encodedKey);
        JSONObject request = new JSONObject()
                .put("version", 1)
                .put("product", PRODUCT)
                .put("targetDeviceId", targetDeviceId)
                .put("requestNonce", encode(randomBytes(16)))
                .put("publicKey", publicKey)
                .put("keyFingerprint", fingerprint)
                .put("createdAt", now)
                .put("expiresAt", Math.addExact(now, APPROVAL_LIFETIME_MILLIS));
        return APPROVAL_REQUEST_PREFIX + encode(request.toString().getBytes(StandardCharsets.UTF_8));
    }

    static ApprovalBinding requestBinding(String requestPackage) throws Exception {
        JSONObject request = decodeObject(requestPackage, APPROVAL_REQUEST_PREFIX);
        requireExactKeys(request, "version", "product", "targetDeviceId", "requestNonce",
                "publicKey", "keyFingerprint", "createdAt", "expiresAt");
        if (request.getInt("version") != 1 || !PRODUCT.equals(request.getString("product"))) {
            throw new IllegalArgumentException("invalid_approval_request");
        }
        requireDeviceId(request.getString("targetDeviceId"));
        decode(request.getString("requestNonce"), 16, 16);
        return new ApprovalBinding(request.getString("targetDeviceId"),
                request.getString("requestNonce"), request.getLong("expiresAt"));
    }

    static ApprovalBinding approvalBinding(String approvalPackage) throws Exception {
        JSONObject approval = decodeObject(approvalPackage, APPROVAL_PREFIX);
        requireExactKeys(approval, "version", "product", "targetDeviceId", "requestNonce",
                "keyFingerprint", "wrappedKey", "nonce", "ciphertext", "expiresAt");
        if (approval.getInt("version") != 1 || !PRODUCT.equals(approval.getString("product"))) {
            throw new IllegalArgumentException("invalid_device_approval");
        }
        requireDeviceId(approval.getString("targetDeviceId"));
        decode(approval.getString("requestNonce"), 16, 16);
        return new ApprovalBinding(approval.getString("targetDeviceId"),
                approval.getString("requestNonce"), approval.getLong("expiresAt"));
    }

    static String approveRequest(String sourceDeviceId, byte[] rootKey, String requestPackage,
                                 long now) throws Exception {
        requireDeviceId(sourceDeviceId);
        requireRoot(rootKey);
        JSONObject request = decodeObject(requestPackage, APPROVAL_REQUEST_PREFIX);
        requireExactKeys(request, "version", "product", "targetDeviceId", "requestNonce",
                "publicKey", "keyFingerprint", "createdAt", "expiresAt");
        validateRequestTimesAndMetadata(request, now);
        String targetDeviceId = request.getString("targetDeviceId");
        String requestNonce = request.getString("requestNonce");
        byte[] publicKeyBytes = decode(request.getString("publicKey"), 256, 1024);
        if (!sha256Hex(publicKeyBytes).equals(request.getString("keyFingerprint"))) {
            throw new IllegalArgumentException("approval_key_fingerprint_mismatch");
        }
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        long expiresAt = request.getLong("expiresAt");
        JSONObject payload = new JSONObject()
                .put("version", 1)
                .put("product", PRODUCT)
                .put("sourceDeviceId", sourceDeviceId)
                .put("targetDeviceId", targetDeviceId)
                .put("requestNonce", requestNonce)
                .put("rootKey", encode(Arrays.copyOf(rootKey, rootKey.length)))
                .put("issuedAt", now)
                .put("expiresAt", expiresAt);
        String fingerprint = request.getString("keyFingerprint");
        byte[] transferKey = randomBytes(ROOT_BYTES);
        byte[] nonce = randomBytes(12);
        Cipher contentCipher = Cipher.getInstance("AES/GCM/NoPadding");
        contentCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(transferKey, "AES"),
                new GCMParameterSpec(128, nonce));
        contentCipher.updateAAD(approvalAad(targetDeviceId, requestNonce, fingerprint, expiresAt));
        byte[] ciphertext = contentCipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
        byte[] wrappedKey = rsa(Cipher.ENCRYPT_MODE, publicKey, transferKey);
        Arrays.fill(transferKey, (byte) 0);
        JSONObject approval = new JSONObject()
                .put("version", 1)
                .put("product", PRODUCT)
                .put("targetDeviceId", targetDeviceId)
                .put("requestNonce", requestNonce)
                .put("keyFingerprint", fingerprint)
                .put("wrappedKey", encode(wrappedKey))
                .put("nonce", encode(nonce))
                .put("ciphertext", encode(ciphertext))
                .put("expiresAt", expiresAt);
        return APPROVAL_PREFIX + encode(approval.toString().getBytes(StandardCharsets.UTF_8));
    }

    static byte[] importApproval(String targetDeviceId, PrivateKey privateKey,
                                 PublicKey publicKey, String approvalPackage, long now)
            throws Exception {
        requireDeviceId(targetDeviceId);
        if (privateKey == null || publicKey == null) {
            throw new IllegalArgumentException("approval_device_key_missing");
        }
        JSONObject approval = decodeObject(approvalPackage, APPROVAL_PREFIX);
        requireExactKeys(approval, "version", "product", "targetDeviceId", "requestNonce",
                "keyFingerprint", "wrappedKey", "nonce", "ciphertext", "expiresAt");
        if (approval.getInt("version") != 1 || !PRODUCT.equals(approval.getString("product")) ||
                !targetDeviceId.equals(approval.getString("targetDeviceId")) ||
                approval.getLong("expiresAt") < now ||
                !sha256Hex(publicKey.getEncoded()).equals(approval.getString("keyFingerprint"))) {
            throw new IllegalArgumentException("invalid_device_approval");
        }
        String requestNonce = approval.getString("requestNonce");
        String fingerprint = approval.getString("keyFingerprint");
        decode(requestNonce, 16, 16);
        byte[] wrappedKey = decode(approval.getString("wrappedKey"), 256, 1024);
        byte[] nonce = decode(approval.getString("nonce"), 12, 12);
        byte[] ciphertext = decode(approval.getString("ciphertext"), 17, 4096);
        byte[] transferKey = rsa(Cipher.DECRYPT_MODE, privateKey, wrappedKey);
        requireRoot(transferKey);
        Cipher contentCipher = Cipher.getInstance("AES/GCM/NoPadding");
        contentCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(transferKey, "AES"),
                new GCMParameterSpec(128, nonce));
        contentCipher.updateAAD(approvalAad(targetDeviceId, requestNonce, fingerprint,
                approval.getLong("expiresAt")));
        byte[] plaintext = contentCipher.doFinal(ciphertext);
        Arrays.fill(transferKey, (byte) 0);
        JSONObject payload = new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
        requireExactKeys(payload, "version", "product", "sourceDeviceId", "targetDeviceId",
                "requestNonce", "rootKey", "issuedAt", "expiresAt");
        if (payload.getInt("version") != 1 || !PRODUCT.equals(payload.getString("product")) ||
                !targetDeviceId.equals(payload.getString("targetDeviceId")) ||
                !approval.getString("requestNonce").equals(payload.getString("requestNonce")) ||
                approval.getLong("expiresAt") != payload.getLong("expiresAt") ||
                payload.getLong("issuedAt") > now + 60_000L || payload.getLong("expiresAt") < now) {
            throw new IllegalArgumentException("invalid_device_approval_payload");
        }
        requireDeviceId(payload.getString("sourceDeviceId"));
        byte[] root = decode(payload.getString("rootKey"), ROOT_BYTES, ROOT_BYTES);
        requireRoot(root);
        return root;
    }

    private static void validateRequestTimesAndMetadata(JSONObject request, long now) throws Exception {
        if (request.getInt("version") != 1 || !PRODUCT.equals(request.getString("product"))) {
            throw new IllegalArgumentException("invalid_approval_request");
        }
        requireDeviceId(request.getString("targetDeviceId"));
        decode(request.getString("requestNonce"), 16, 16);
        if (!request.getString("keyFingerprint").matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("invalid_approval_request");
        }
        long createdAt = request.getLong("createdAt");
        long expiresAt = request.getLong("expiresAt");
        if (createdAt > now + 60_000L || expiresAt < now || expiresAt <= createdAt ||
                expiresAt - createdAt != APPROVAL_LIFETIME_MILLIS) {
            throw new IllegalArgumentException("expired_approval_request");
        }
    }

    private static SecretKey deriveRecoveryKey(char[] recoveryKey, byte[] salt, int iterations)
            throws Exception {
        PBEKeySpec spec = new PBEKeySpec(recoveryKey, salt, iterations, 256);
        try {
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return new SecretKeySpec(encoded, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private static char[] normalizedRecoveryKey(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value.trim(),
                Normalizer.Form.NFKC);
        if (normalized.length() < 16 || normalized.length() > 1024) {
            throw new IllegalArgumentException("invalid_recovery_key");
        }
        return normalized.toCharArray();
    }

    private static byte[] rsa(int mode, Key key, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(mode, key, OAEP_SHA256);
        return cipher.doFinal(input);
    }

    private static JSONObject decodeObject(String value, String prefix) throws Exception {
        String candidate = value == null ? "" : value.trim();
        if (!candidate.startsWith(prefix) || candidate.length() > MAX_PACKAGE_CHARS) {
            throw new IllegalArgumentException("invalid_key_package");
        }
        byte[] decoded = decode(candidate.substring(prefix.length()), 2, 12_000);
        return new JSONObject(new String(decoded, StandardCharsets.UTF_8));
    }

    private static void requireExactKeys(JSONObject value, String... names) {
        if (value.length() != names.length) throw new IllegalArgumentException("invalid_key_package");
        for (String name : names) if (!value.has(name)) throw new IllegalArgumentException("invalid_key_package");
    }

    private static void requireDeviceId(String value) {
        if (value == null || !value.matches("^[A-Za-z0-9][A-Za-z0-9_-]{2,127}$")) {
            throw new IllegalArgumentException("invalid_device_id");
        }
    }

    private static void requireRoot(byte[] rootKey) {
        if (rootKey == null || rootKey.length != ROOT_BYTES) {
            throw new IllegalArgumentException("invalid_root_key");
        }
    }

    private static byte[] recoveryAad() {
        return "watch-sync-root-recovery-v1".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] approvalAad(String targetDeviceId, String requestNonce,
                                      String fingerprint, long expiresAt) {
        return ("watch-sync-device-approval-v1\0" + targetDeviceId + "\0" + requestNonce +
                "\0" + fingerprint + "\0" + expiresAt).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        RANDOM.nextBytes(value);
        return value;
    }

    private static String sha256Hex(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder(64);
        for (byte item : digest) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }

    private static String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private static byte[] decode(String value, int minimum, int maximum) {
        if (value == null || value.isEmpty() || value.length() > MAX_PACKAGE_CHARS ||
                !value.matches("^[A-Za-z0-9_-]+$")) {
            throw new IllegalArgumentException("invalid_base64url");
        }
        try {
            byte[] decoded = DECODER.decode(value);
            if (decoded.length < minimum || decoded.length > maximum) {
                throw new IllegalArgumentException("invalid_base64url_length");
            }
            return decoded;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid_base64url", invalid);
        }
    }
}

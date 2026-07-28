package com.poyi.watchintervals.phone;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Validates one-time API token bootstrap credentials without logging secrets. */
final class BootstrapCredentialValidator {
    private BootstrapCredentialValidator() {}

    static boolean matches(String presented, String legacyPairingCode, String pairedLanCredential) {
        if (presented == null || presented.isEmpty()) return false;
        return constantTimeEquals(presented, legacyPairingCode)
                || constantTimeEquals(presented, pairedLanCredential);
    }

    private static boolean constantTimeEquals(String presented, String expected) {
        if (expected == null || expected.isEmpty()) return false;
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}

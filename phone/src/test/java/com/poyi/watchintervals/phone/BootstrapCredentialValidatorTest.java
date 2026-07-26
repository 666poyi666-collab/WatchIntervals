package com.poyi.watchintervals.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BootstrapCredentialValidatorTest {
    @Test public void acceptsLegacyPairingCode() {
        assertTrue(BootstrapCredentialValidator.matches("123456", "123456", ""));
    }

    @Test public void acceptsLongTermLanCredentialAfterSecurePairing() {
        assertTrue(BootstrapCredentialValidator.matches("LAN_SECRET", "", "LAN_SECRET"));
    }

    @Test public void rejectsEmptyOrWrongCredential() {
        assertFalse(BootstrapCredentialValidator.matches("", "123456", "LAN_SECRET"));
        assertFalse(BootstrapCredentialValidator.matches("000000", "", ""));
        assertFalse(BootstrapCredentialValidator.matches("000000", "123456", "LAN_SECRET"));
    }
}

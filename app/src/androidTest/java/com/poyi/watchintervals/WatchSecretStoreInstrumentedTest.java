package com.poyi.watchintervals;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Real-watch gate for the provider-generated Android Keystore GCM nonce contract. */
@RunWith(AndroidJUnit4.class)
public final class WatchSecretStoreInstrumentedTest {
    @Test public void watchSecretStoreUsesProviderGeneratedNonce() {
        byte[] plaintext = "instrumentation-secret".getBytes(StandardCharsets.UTF_8);
        WatchSecretStore.EncryptedValue first = WatchSecretStore.encrypt(
                plaintext, "instrumentation-aad");
        WatchSecretStore.EncryptedValue second = WatchSecretStore.encrypt(
                plaintext, "instrumentation-aad");
        assertNotNull(first);
        assertNotNull(second);
        assertFalse("GCM nonces must not repeat", first.nonce.equals(second.nonce));
        assertArrayEquals(plaintext, WatchSecretStore.decrypt(
                first.ciphertext, first.nonce, "instrumentation-aad"));
        assertNull(WatchSecretStore.decrypt(first.ciphertext, first.nonce, "wrong-aad"));
    }
}

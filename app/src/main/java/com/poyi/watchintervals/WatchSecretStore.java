package com.poyi.watchintervals;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Android Keystore envelope for watch-side sensitive data (BLE pairing secrets, LAN credentials).
 * Mirrors the phone module's AndroidSecretStore pattern: AES-256-GCM, randomized encryption,
 * AAD binding to prevent cross-context decryption.
 *
 * Fails closed if Keystore is unavailable; callers must never persist plaintext fallback values.
 */
final class WatchSecretStore {
    private static final String TAG = "WatchSecretStore";
    private static final String KEY_ALIAS = "poyi.watchintervals.watch.secrets.v1";
    private WatchSecretStore() {}

    /** Result of an encryption operation: ciphertext + nonce, both Base64url-encoded. */
    static final class EncryptedValue {
        final String ciphertext;
        final String nonce;
        EncryptedValue(String ciphertext, String nonce) {
            this.ciphertext = ciphertext;
            this.nonce = nonce;
        }
    }

    /**
     * Encrypts arbitrary bytes with AES-256-GCM using the Keystore-backed key.
     *
     * @param plaintext raw bytes to encrypt
     * @param aad       additional authenticated data (binds ciphertext to a context, e.g. phoneId)
     * @return encrypted value, or null if Keystore is unavailable
     */
    static EncryptedValue encrypt(byte[] plaintext, String aad) {
        if (plaintext == null || plaintext.length == 0 || aad == null || aad.isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] nonce = cipher.getIV();
            if (nonce == null || nonce.length != 12) {
                throw new IllegalStateException("invalid_keystore_iv");
            }
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new EncryptedValue(encode(ciphertext), encode(nonce));
        } catch (Exception e) {
            Log.w(TAG, "keystore_encrypt_failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Decrypts bytes previously produced by {@link #encrypt(byte[], String)}.
     *
     * @param ciphertext Base64url-encoded ciphertext
     * @param nonce      Base64url-encoded 12-byte nonce
     * @param aad        same AAD used during encryption
     * @return decrypted bytes, or null if decryption fails (wrong key, tampered, or Keystore unavailable)
     */
    static byte[] decrypt(String ciphertext, String nonce, String aad) {
        if (ciphertext == null || ciphertext.isEmpty() || nonce == null || nonce.isEmpty()
                || aad == null || aad.isEmpty()) {
            return null;
        }
        try {
            byte[] decodedNonce = decode(nonce);
            if (decodedNonce.length != 12) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, decodedNonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(decode(ciphertext));
        } catch (Exception e) {
            Log.w(TAG, "keystore_decrypt_failed: " + e.getMessage());
            return null;
        }
    }

    /** Returns true if the Keystore key is accessible. */
    static boolean isAvailable() {
        try {
            key();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "keystore_unavailable: " + e.getMessage());
            return false;
        }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static String encode(byte[] value) {
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] decode(String value) {
        return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}

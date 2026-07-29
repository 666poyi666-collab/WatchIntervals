package com.poyi.watchintervals.phone;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Small Android Keystore envelope used by non-cloud phone credentials. */
public final class AndroidSecretStore {
    public static final class EncryptedValue {
        public final String ciphertext;
        public final String nonce;
        private EncryptedValue(String ciphertext, String nonce) {
            this.ciphertext = ciphertext;
            this.nonce = nonce;
        }
    }

    private static final String KEY_ALIAS = "poyi.watchintervals.phone.secrets.v1";
    private AndroidSecretStore() {}

    public static EncryptedValue encrypt(String plaintext, String aad) throws Exception {
        if (plaintext == null || plaintext.isEmpty() || aad == null || aad.isEmpty()) {
            throw new IllegalArgumentException("invalid_secret");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] nonce = cipher.getIV();
        if (nonce == null || nonce.length != 12) throw new IllegalStateException("invalid_keystore_iv");
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return new EncryptedValue(encode(ciphertext), encode(nonce));
    }

    public static String decrypt(String ciphertext, String nonce, String aad) {
        if (ciphertext == null || ciphertext.isEmpty() || nonce == null || nonce.isEmpty() ||
                aad == null || aad.isEmpty()) return null;
        try {
            byte[] decodedNonce = decode(nonce);
            if (decodedNonce.length != 12) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, decodedNonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception invalid) {
            return null;
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
        return Base64.encodeToString(value,
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] decode(String value) {
        return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}

package com.poyi.watchintervals.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Real-device gate for Keystore-wrapped encrypted-sync bootstrap. Lives only in the test APK. */
@RunWith(AndroidJUnit4.class)
public final class CloudSyncCredentialsInstrumentedTest {
    @Test public void persistedCredentialsScheduleNetworkCatchUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(CloudSyncCredentials.readyForSync(context));
        EncryptedWatchSyncWorker.schedule(context);
        WorkManager manager = WorkManager.getInstance(context);
        List<WorkInfo> immediate = manager.getWorkInfosForUniqueWork(
                "encrypted-watch-sync-v1").get(15, TimeUnit.SECONDS);
        List<WorkInfo> periodic = manager.getWorkInfosForUniqueWork(
                "encrypted-watch-sync-periodic-v1").get(15, TimeUnit.SECONDS);
        assertFalse("one-time catch-up must be persisted", immediate.isEmpty());
        assertFalse("periodic recovery must be persisted", periodic.isEmpty());
        assertFalse(immediate.get(0).getState() == WorkInfo.State.CANCELLED);
        assertFalse(periodic.get(0).getState() == WorkInfo.State.CANCELLED);
    }

    @Test public void persistedKeystoreStateSurvivesUpgradeRestart() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("persisted device token/root must decrypt after process restart",
                CloudSyncCredentials.readyForSync(context));
        CloudSyncCredentials.Config loaded = CloudSyncCredentials.load(context);
        assertTrue(loaded.configured());
        SharedPreferences encrypted = context.getSharedPreferences(
                "encrypted_watch_sync_v1", Context.MODE_PRIVATE);
        assertTrue(encrypted.contains("device_token_ciphertext"));
        assertTrue(encrypted.contains("root_ciphertext"));
        assertFalse(encrypted.contains("device_token"));
        assertFalse(encrypted.contains("root_key"));
    }

    @Test public void phoneSecretStoreUsesProviderGeneratedNonce() throws Exception {
        AndroidSecretStore.EncryptedValue first = AndroidSecretStore.encrypt(
                "instrumentation-secret", "instrumentation-aad");
        AndroidSecretStore.EncryptedValue second = AndroidSecretStore.encrypt(
                "instrumentation-secret", "instrumentation-aad");
        assertFalse("GCM nonces must not repeat", first.nonce.equals(second.nonce));
        assertEquals("instrumentation-secret",
                AndroidSecretStore.decrypt(first.ciphertext, first.nonce, "instrumentation-aad"));
        assertEquals(null,
                AndroidSecretStore.decrypt(first.ciphertext, first.nonce, "wrong-aad"));
    }

    @Test public void provisionAndInitializeKeystoreRoot() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        String endpoint = arguments.getString("cloud_endpoint", "");
        String token = arguments.getString("cloud_device_token", "");
        assertTrue("canonical HTTPS endpoint argument required", endpoint.startsWith("https://"));
        assertTrue("device credential argument required", token.startsWith("dw1."));
        assertKeystoreEnvelopePrimitive();

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("device credential must be Keystore wrapped",
                CloudSyncCredentials.save(context, endpoint, token));
        CloudSyncCredentials.initializeNewRoot(context);
        assertTrue("Keystore root must decrypt in the target app", CloudSyncCredentials.readyForSync(context));

        CloudSyncCredentials.Config loaded = CloudSyncCredentials.load(context);
        assertEquals(endpoint, loaded.endpoint);
        assertEquals(token, loaded.deviceToken);
        SharedPreferences encrypted = context.getSharedPreferences(
                "encrypted_watch_sync_v1", Context.MODE_PRIVATE);
        assertTrue(encrypted.contains("device_token_ciphertext"));
        assertTrue(encrypted.contains("device_token_nonce"));
        assertTrue(encrypted.contains("root_ciphertext"));
        assertTrue(encrypted.contains("root_nonce"));
        assertTrue(encrypted.contains("root_fingerprint"));
        assertFalse(encrypted.contains("device_token"));
        assertFalse(encrypted.contains("root_key"));
        assertFalse(encrypted.contains("sync_key"));
        SharedPreferences legacy = context.getSharedPreferences(
                "cloud_snapshot_sync", Context.MODE_PRIVATE);
        assertFalse(legacy.contains("sync_key"));
        assertFalse(legacy.contains("endpoint"));
    }

    private static void assertKeystoreEnvelopePrimitive() throws Exception {
        Method encrypt = CloudSyncCredentials.class.getDeclaredMethod(
                "encrypt", String.class, String.class);
        encrypt.setAccessible(true);
        try {
            encrypt.invoke(null, "instrumentation-probe", "instrumentation-aad");
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            throw new AssertionError("Keystore envelope primitive failed: " +
                    (cause == null ? "unknown" : cause.getClass().getName()), cause);
        }
    }
}

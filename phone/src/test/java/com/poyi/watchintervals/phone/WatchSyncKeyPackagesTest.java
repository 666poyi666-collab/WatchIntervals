package com.poyi.watchintervals.phone;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.junit.Test;

public class WatchSyncKeyPackagesTest {
    @Test public void recoveryPackageRoundTripsAndWrongKeyFails() throws Exception {
        byte[] root = root();
        String recovery = WatchSyncKeyPackages.createRecoveryPackage(root,
                "correct horse battery staple 2026");
        assertArrayEquals(root, WatchSyncKeyPackages.restoreRecoveryPackage(recovery,
                "correct horse battery staple 2026"));
        assertThrows(Exception.class, () -> WatchSyncKeyPackages.restoreRecoveryPackage(
                recovery, "wrong recovery key 2026"));
    }

    @Test public void authorizedDeviceApprovalIsTargetBoundAndExpires() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair target = generator.generateKeyPair();
        long now = 1_800_000_000_000L;
        String request = WatchSyncKeyPackages.createApprovalRequest("watch-target", target.getPublic(), now);
        String approval = WatchSyncKeyPackages.approveRequest("watch-source", root(), request, now + 1_000L);
        assertArrayEquals(root(), WatchSyncKeyPackages.importApproval("watch-target",
                target.getPrivate(), target.getPublic(), approval, now + 2_000L));
        assertThrows(IllegalArgumentException.class, () -> WatchSyncKeyPackages.importApproval(
                "watch-other", target.getPrivate(), target.getPublic(), approval, now + 2_000L));
        assertThrows(IllegalArgumentException.class, () -> WatchSyncKeyPackages.importApproval(
                "watch-target", target.getPrivate(), target.getPublic(), approval,
                now + WatchSyncKeyPackages.APPROVAL_LIFETIME_MILLIS + 1));
    }

    private static byte[] root() {
        return "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.US_ASCII);
    }
}

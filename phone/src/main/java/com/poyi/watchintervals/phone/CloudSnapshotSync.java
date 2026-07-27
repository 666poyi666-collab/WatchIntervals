package com.poyi.watchintervals.phone;

import android.content.Context;

/**
 * Compatibility entry point for older phone call sites.
 *
 * <p>It no longer uploads snapshots or accepts /sync/push credentials. Every invocation now
 * enters the encrypted V2 exchange client, whose retry is owned by WorkManager.
 */
final class CloudSnapshotSync {
    private CloudSnapshotSync() {}

    static void syncAsync(Context context) {
        EncryptedWatchSync.syncAsync(context);
    }

    static boolean sync(Context context) {
        return EncryptedWatchSync.sync(context);
    }
}

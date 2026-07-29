package com.poyi.watchintervals.phone;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

/** WorkManager owns network, reboot and Doze recovery for Cloud V3 sync. */
public final class EncryptedWatchSyncWorker extends Worker {
    private static final String UNIQUE_NAME = "watch-cloud-v3-sync";
    private static final String PERIODIC_NAME = "watch-cloud-v3-sync-periodic";

    public EncryptedWatchSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        if (!CloudSyncCredentials.readyForCloudV3(getApplicationContext())) {
            cancel(getApplicationContext());
            return Result.success();
        }
        CloudV3Sync.SyncOutcome outcome = CloudV3Sync.sync(getApplicationContext());
        if (outcome == CloudV3Sync.SyncOutcome.PERMANENT_FAILURE) {
            cancel(getApplicationContext());
            return Result.success();
        }
        return shouldRetry(outcome, CloudSyncCredentials.readyForCloudV3(getApplicationContext()))
                ? Result.retry() : Result.success();
    }

    static boolean shouldRetry(CloudV3Sync.SyncOutcome outcome,
                               boolean credentialsStillReady) {
        return outcome == CloudV3Sync.SyncOutcome.TRANSIENT_FAILURE &&
                credentialsStillReady;
    }

    public static void schedule(Context context) {
        if (!CloudSyncCredentials.readyForCloudV3(context)) return;
        Constraints constraints = networkConstraints();
        ensurePeriodic(context, constraints);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(EncryptedWatchSyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                UNIQUE_NAME, ExistingWorkPolicy.KEEP, request);
    }

    static void ensurePeriodic(Context context) {
        if (!CloudSyncCredentials.readyForCloudV3(context)) return;
        ensurePeriodic(context, networkConstraints());
    }

    static void cancel(Context context) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        manager.cancelUniqueWork(UNIQUE_NAME);
        manager.cancelUniqueWork(PERIODIC_NAME);
    }

    private static void ensurePeriodic(Context context, Constraints constraints) {
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                EncryptedWatchSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                PERIODIC_NAME, androidx.work.ExistingPeriodicWorkPolicy.KEEP, periodic);
    }

    private static Constraints networkConstraints() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }
}

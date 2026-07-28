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

/** WorkManager owns network, reboot and Doze recovery for encrypted watch sync. */
public final class EncryptedWatchSyncWorker extends Worker {
    private static final String UNIQUE_NAME = "encrypted-watch-sync-v1";
    private static final String PERIODIC_NAME = "encrypted-watch-sync-periodic-v1";

    public EncryptedWatchSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        if (!CloudSyncCredentials.readyForSync(getApplicationContext())) {
            cancel(getApplicationContext());
            return Result.success();
        }
        EncryptedWatchSync.SyncOutcome outcome =
                EncryptedWatchSync.sync(getApplicationContext());
        if (outcome == EncryptedWatchSync.SyncOutcome.PERMANENT_FAILURE) {
            cancel(getApplicationContext());
            return Result.success();
        }
        return shouldRetry(outcome, CloudSyncCredentials.readyForSync(getApplicationContext()))
                ? Result.retry() : Result.success();
    }

    static boolean shouldRetry(EncryptedWatchSync.SyncOutcome outcome,
                               boolean credentialsStillReady) {
        return outcome == EncryptedWatchSync.SyncOutcome.TRANSIENT_FAILURE &&
                credentialsStillReady;
    }

    static void schedule(Context context) {
        if (!CloudSyncCredentials.readyForSync(context)) return;
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
        if (!CloudSyncCredentials.readyForSync(context)) return;
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

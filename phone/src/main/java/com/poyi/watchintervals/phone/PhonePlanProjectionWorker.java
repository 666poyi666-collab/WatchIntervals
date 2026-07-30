package com.poyi.watchintervals.phone;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

/**
 * Persists Phone-to-Watch plan projection independently of internet and Cloud credentials.
 */
public final class PhonePlanProjectionWorker extends Worker {
    private static final String UNIQUE_NAME = "watch-plan-projection";
    private static final String PERIODIC_NAME = "watch-plan-projection-periodic";

    public PhonePlanProjectionWorker(@NonNull Context context,
                                     @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        boolean drained = PhonePlanProjectionSync.drainOnce(getApplicationContext());
        return shouldRetry(drained, PhoneSyncOutbox.size(getApplicationContext()))
                ? Result.retry() : Result.success();
    }

    static boolean shouldRetry(boolean drained, int pendingOperations) {
        return !drained || pendingOperations > 0;
    }

    public static void schedule(Context context) {
        Context app = context.getApplicationContext();
        ensurePeriodic(app);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                PhonePlanProjectionWorker.class)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(app).enqueueUniqueWork(
                UNIQUE_NAME, ExistingWorkPolicy.KEEP, request);
    }

    static void ensurePeriodic(Context context) {
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                PhonePlanProjectionWorker.class, 15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic);
    }
}

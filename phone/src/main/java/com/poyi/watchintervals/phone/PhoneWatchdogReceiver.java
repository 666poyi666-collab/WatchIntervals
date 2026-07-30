package com.poyi.watchintervals.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** App-private receiver for the explicit watchdog PendingIntent. */
public final class PhoneWatchdogReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !PhoneBootReceiver.ACTION_WATCHDOG.equals(intent.getAction())) return;
        PhoneBootReceiver.startServices(context);
        PhoneBootReceiver.schedule(context);
        EncryptedWatchSyncWorker.schedule(context.getApplicationContext());
        PhonePlanProjectionWorker.schedule(context.getApplicationContext());
    }
}

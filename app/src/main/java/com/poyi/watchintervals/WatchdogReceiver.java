package com.poyi.watchintervals;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** App-private target for the exact watchdog alarm. */
public final class WatchdogReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !BootReceiver.ACTION_WATCHDOG.equals(intent.getAction())) return;
        BootReceiver.startServices(context);
        BootReceiver.schedule(context);
    }
}

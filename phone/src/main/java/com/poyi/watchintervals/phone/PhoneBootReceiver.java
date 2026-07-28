package com.poyi.watchintervals.phone;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class PhoneBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        context.startForegroundService(new Intent(context, PhonePlanBridgeService.class));
        EncryptedWatchSyncWorker.schedule(context.getApplicationContext());
    }
}

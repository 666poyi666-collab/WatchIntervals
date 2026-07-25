package com.poyi.watchintervals.phone;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class PhoneBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { context.startForegroundService(new Intent(context, PhonePlanBridgeService.class));context.startForegroundService(new Intent(context,PhoneCompanionService.class)); }
}

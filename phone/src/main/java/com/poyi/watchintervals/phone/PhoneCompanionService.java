package com.poyi.watchintervals.phone;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import com.poyi.watchintervals.phone.connection.WatchConnectionManager;

/** Foreground owner that lets the paired BLE link reconnect outside the activity lifecycle. */
public final class PhoneCompanionService extends Service {
    private static final String CHANNEL="watch_companion";
    @Override public void onCreate(){super.onCreate();NotificationChannel channel=new NotificationChannel(CHANNEL,"手表蓝牙连接",NotificationManager.IMPORTANCE_MIN);getSystemService(NotificationManager.class).createNotificationChannel(channel);Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("步序手表连接").setContentText("正在保持蓝牙连接").setOngoing(true).build();startForeground(64,notification);WatchConnectionManager.get(this).connect();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){WatchConnectionManager.get(this).connect();return START_STICKY;}
    @Override public IBinder onBind(Intent intent){return null;}
}

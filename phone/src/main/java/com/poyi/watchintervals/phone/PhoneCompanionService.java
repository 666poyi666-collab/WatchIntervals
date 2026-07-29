package com.poyi.watchintervals.phone;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import com.poyi.watchintervals.phone.connection.WatchConnectionManager;

/** Foreground owner that lets the paired BLE link reconnect outside the activity lifecycle. */
public final class PhoneCompanionService extends Service {
    private static final String CHANNEL="watch_companion";
    private final android.os.Handler syncHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private CloudV3Channel cloudChannel;
    private final Runnable statusUpload=new Runnable(){@Override public void run(){CloudV3Sync.syncLiveAsync(PhoneCompanionService.this);syncHandler.postDelayed(this,CloudV3Sync.lastActiveSession(PhoneCompanionService.this)?10_000L:60_000L);}};
    @Override public void onCreate(){super.onCreate();NotificationChannel channel=new NotificationChannel(CHANNEL,"手表蓝牙连接",NotificationManager.IMPORTANCE_MIN);getSystemService(NotificationManager.class).createNotificationChannel(channel);Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("步序手表连接").setContentText("正在保持蓝牙与云端命令通道").setOngoing(true).build();startForeground(64,notification);WatchConnectionManager.get(this).connect();cloudChannel=new CloudV3Channel(this);cloudChannel.start();syncHandler.post(statusUpload);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){WatchConnectionManager.get(this).connect();if(cloudChannel!=null)cloudChannel.start();EncryptedWatchSyncWorker.schedule(this);return START_STICKY;}
    @Override public void onDestroy(){syncHandler.removeCallbacks(statusUpload);if(cloudChannel!=null)cloudChannel.stop();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}

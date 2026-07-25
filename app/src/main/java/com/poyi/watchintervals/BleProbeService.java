package com.poyi.watchintervals;

import android.app.Service;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Debug-only OWW221 peripheral/GATT feasibility probe. */
public final class BleProbeService extends Service {
    static final UUID SERVICE=UUID.fromString("7b5e1000-88b8-4e08-9b7d-4cd930f0c101");
    static final UUID COMMAND=UUID.fromString("7b5e1001-88b8-4e08-9b7d-4cd930f0c101");
    private BluetoothGattServer server;private BluetoothLeAdvertiser advertiser;private BluetoothGattCharacteristic command;private AdvertiseCallback advertiseCallback;
    @Override public int onStartCommand(Intent intent,int flags,int id){if(!BuildConfig.DEBUG){stopSelf();return START_NOT_STICKY;}startProbe();return START_NOT_STICKY;}
    @SuppressWarnings("MissingPermission") private void startProbe(){BluetoothManager manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();if(adapter==null||!adapter.isEnabled()||!adapter.isMultipleAdvertisementSupported()){Log.w("BleProbe","peripheral advertising unavailable");stopSelf();return;}server=manager.openGattServer(this,new BluetoothGattServerCallback(){@Override public void onCharacteristicWriteRequest(BluetoothDevice device,int requestId,BluetoothGattCharacteristic characteristic,boolean prepared,boolean responseNeeded,int offset,byte[] value){String request=value==null?"":new String(value,StandardCharsets.UTF_8);Log.i("BleProbe","command="+request);if(responseNeeded)server.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,0,"pong".getBytes(StandardCharsets.UTF_8));characteristic.setValue("pong".getBytes(StandardCharsets.UTF_8));server.notifyCharacteristicChanged(device,characteristic,false);}});if(server==null){stopSelf();return;}BluetoothGattService service=new BluetoothGattService(SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY);command=new BluetoothGattCharacteristic(COMMAND,BluetoothGattCharacteristic.PROPERTY_WRITE|BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_NOTIFY,BluetoothGattCharacteristic.PERMISSION_WRITE|BluetoothGattCharacteristic.PERMISSION_READ);service.addCharacteristic(command);server.addService(service);advertiser=adapter.getBluetoothLeAdvertiser();AdvertiseSettings settings=new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setConnectable(true).build();AdvertiseData data=new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(SERVICE)).setIncludeDeviceName(false).build();advertiseCallback=new AdvertiseCallback(){@Override public void onStartSuccess(AdvertiseSettings settings){Log.i("BleProbe","advertising started");}@Override public void onStartFailure(int code){Log.e("BleProbe","advertising failed code="+code);stopSelf();}};advertiser.startAdvertising(settings,data,advertiseCallback);}
    @SuppressWarnings("MissingPermission") @Override public void onDestroy(){if(advertiser!=null&&advertiseCallback!=null)advertiser.stopAdvertising(advertiseCallback);if(server!=null)server.close();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}

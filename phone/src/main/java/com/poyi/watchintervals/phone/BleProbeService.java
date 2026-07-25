package com.poyi.watchintervals.phone;

import android.app.Service;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

/** Debug-only phone central probe for the watch ping/pong GATT service. */
public final class BleProbeService extends Service {
    private static final UUID SERVICE=UUID.fromString("7b5e1000-88b8-4e08-9b7d-4cd930f0c101"),COMMAND=UUID.fromString("7b5e1001-88b8-4e08-9b7d-4cd930f0c101");
    private BluetoothLeScanner scanner;private BluetoothGatt gatt;private ScanCallback scan;
    @Override public int onStartCommand(Intent intent,int flags,int id){if(!BuildConfig.DEBUG||!allowed()){Log.w("BleProbe","Bluetooth permission missing");stopSelf();return START_NOT_STICKY;}startScan();return START_NOT_STICKY;}
    private boolean allowed(){return android.os.Build.VERSION.SDK_INT<31||checkSelfPermission("android.permission.BLUETOOTH_SCAN")==PackageManager.PERMISSION_GRANTED&&checkSelfPermission("android.permission.BLUETOOTH_CONNECT")==PackageManager.PERMISSION_GRANTED;}
    @SuppressWarnings("MissingPermission") private void startScan(){BluetoothManager manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();if(adapter==null||!adapter.isEnabled()){stopSelf();return;}scanner=adapter.getBluetoothLeScanner();scan=new ScanCallback(){@Override public void onScanResult(int callbackType,ScanResult result){scanner.stopScan(this);gatt=result.getDevice().connectGatt(BleProbeService.this,false,callback);}};scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE)).build()),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan);}
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){@SuppressWarnings("MissingPermission") @Override public void onConnectionStateChange(BluetoothGatt value,int status,int state){if(state==BluetoothProfile.STATE_CONNECTED)value.discoverServices();else if(state==BluetoothProfile.STATE_DISCONNECTED)stopSelf();}@SuppressWarnings("MissingPermission") @Override public void onServicesDiscovered(BluetoothGatt value,int status){BluetoothGattService service=value.getService(SERVICE);BluetoothGattCharacteristic command=service==null?null:service.getCharacteristic(COMMAND);if(command==null){stopSelf();return;}command.setValue("ping".getBytes(StandardCharsets.UTF_8));value.writeCharacteristic(command);}@Override public void onCharacteristicChanged(BluetoothGatt value,BluetoothGattCharacteristic characteristic){Log.i("BleProbe","response="+new String(characteristic.getValue(),StandardCharsets.UTF_8));stopSelf();}};
    @SuppressWarnings("MissingPermission") @Override public void onDestroy(){if(scanner!=null&&scan!=null)scanner.stopScan(scan);if(gatt!=null){gatt.disconnect();gatt.close();}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}

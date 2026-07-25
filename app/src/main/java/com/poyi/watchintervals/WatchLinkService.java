package com.poyi.watchintervals;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import com.poyi.watchintervals.connection.ble.BleProtocolCodec;
import com.poyi.watchintervals.connection.ble.BleUuids;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Persistent watch peripheral. All business requests pass through WatchCommandRouter. */
public final class WatchLinkService extends Service {
    private static final String TAG="WatchLink",CHANNEL="watch_link";
    private static final int NOTIFICATION_ID=74,DEFAULT_MTU=23;
    private BluetoothManager manager;private BluetoothGattServer server;private BluetoothLeAdvertiser advertiser;private AdvertiseCallback advertiseCallback;
    private final Map<UUID,BluetoothGattCharacteristic> characteristics=new HashMap<>();
    private final Map<String,Integer> mtuByDevice=new HashMap<>();
    private final Map<String,BleProtocolCodec.Assembler> assemblers=new HashMap<>();
    private final Set<String> authenticated=new HashSet<>(),subscribedEvents=new HashSet<>();
    private final Map<String,ArrayDeque<Outgoing>> outgoing=new HashMap<>();
    private final Set<String> notifying=new HashSet<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private WatchCommandRouter router;
    private volatile boolean advertising;

    @Override public void onCreate(){super.onCreate();router=new WatchCommandRouter(this);NotificationChannel channel=new NotificationChannel(CHANNEL,"手机蓝牙连接",NotificationManager.IMPORTANCE_MIN);getSystemService(NotificationManager.class).createNotificationChannel(channel);Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle("步序蓝牙连接").setContentText("手机可自动连接手表").setOngoing(true).build();startForeground(NOTIFICATION_ID,notification);startPeripheral();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(server==null)startPeripheral();return START_STICKY;}

    @SuppressWarnings("MissingPermission") private synchronized void startPeripheral(){
        if(server!=null)return;manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null||!adapter.isEnabled()||!adapter.isMultipleAdvertisementSupported()){Log.w(TAG,"peripheral_unavailable");return;}
        server=manager.openGattServer(this,callback);if(server==null){Log.e(TAG,"gatt_server_open_failed");return;}
        BluetoothGattService service=new BluetoothGattService(BleUuids.SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY);
        add(service,BleUuids.DEVICE_INFO,BluetoothGattCharacteristic.PROPERTY_READ,BluetoothGattCharacteristic.PERMISSION_READ,false);
        add(service,BleUuids.PAIRING,BluetoothGattCharacteristic.PROPERTY_WRITE|BluetoothGattCharacteristic.PROPERTY_INDICATE,BluetoothGattCharacteristic.PERMISSION_WRITE,false);
        add(service,BleUuids.CONTROL,BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_WRITE,false);
        add(service,BleUuids.EVENTS,BluetoothGattCharacteristic.PROPERTY_INDICATE,BluetoothGattCharacteristic.PERMISSION_READ,true);
        add(service,BleUuids.SYNC_TX,BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_WRITE,false);
        add(service,BleUuids.SYNC_RX,BluetoothGattCharacteristic.PROPERTY_INDICATE,BluetoothGattCharacteristic.PERMISSION_READ,true);
        add(service,BleUuids.LOCATION,BluetoothGattCharacteristic.PROPERTY_WRITE|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,BluetoothGattCharacteristic.PERMISSION_WRITE,false);
        add(service,BleUuids.LAN_ENDPOINT,BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE,false);
        add(service,BleUuids.HEARTBEAT,BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_WRITE|BluetoothGattCharacteristic.PROPERTY_INDICATE,BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE,true);
        if(!server.addService(service)){Log.e(TAG,"gatt_service_add_failed");closeGatt();return;}
    }

    private void add(BluetoothGattService service,UUID uuid,int properties,int permissions,boolean cccd){BluetoothGattCharacteristic value=new BluetoothGattCharacteristic(uuid,properties,permissions);if(cccd||uuid.equals(BleUuids.PAIRING))value.addDescriptor(new BluetoothGattDescriptor(BleUuids.CCCD,BluetoothGattDescriptor.PERMISSION_READ|BluetoothGattDescriptor.PERMISSION_WRITE));service.addCharacteristic(value);characteristics.put(uuid,value);}

    @SuppressWarnings("MissingPermission") private void startAdvertising(){if(advertising||server==null)return;BluetoothAdapter adapter=manager.getAdapter();advertiser=adapter==null?null:adapter.getBluetoothLeAdvertiser();if(advertiser==null){Log.e(TAG,"advertiser_unavailable");return;}AdvertiseSettings settings=new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).setConnectable(true).build();AdvertiseData data=new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(BleUuids.SERVICE)).setIncludeDeviceName(false).build();advertiseCallback=new AdvertiseCallback(){@Override public void onStartSuccess(AdvertiseSettings value){advertising=true;Log.i(TAG,"advertising_ready");}@Override public void onStartFailure(int code){advertising=false;Log.e(TAG,"advertising_failed status="+code);}};advertiser.startAdvertising(settings,data,advertiseCallback);}

    private final BluetoothGattServerCallback callback=new BluetoothGattServerCallback(){
        @Override public void onServiceAdded(int status,BluetoothGattService service){if(status==BluetoothGatt.GATT_SUCCESS)startAdvertising();else Log.e(TAG,"service_add status="+status);}
        @Override public void onConnectionStateChange(BluetoothDevice device,int status,int state){String key=device.getAddress();Log.i(TAG,"connection state="+state+" status="+status+" device="+redact(key));if(state==BluetoothProfile.STATE_CONNECTED){synchronized(WatchLinkService.this){mtuByDevice.put(key,DEFAULT_MTU);}}else if(state==BluetoothProfile.STATE_DISCONNECTED){synchronized(WatchLinkService.this){mtuByDevice.remove(key);authenticated.remove(key);subscribedEvents.remove(key);outgoing.remove(key);notifying.remove(key);removeAssemblers(key);}}}
        @Override public void onMtuChanged(BluetoothDevice device,int mtu){synchronized(WatchLinkService.this){mtuByDevice.put(device.getAddress(),Math.max(DEFAULT_MTU,mtu));}Log.i(TAG,"mtu="+mtu+" device="+redact(device.getAddress()));}
        @Override public void onCharacteristicReadRequest(BluetoothDevice device,int requestId,int offset,BluetoothGattCharacteristic characteristic){byte[] value=readValue(device,characteristic.getUuid());sendRead(device,requestId,offset,value);}
        @Override public void onDescriptorReadRequest(BluetoothDevice device,int requestId,int offset,BluetoothGattDescriptor descriptor){byte[] value=subscribedEvents.contains(device.getAddress())?BluetoothGattDescriptor.ENABLE_INDICATION_VALUE:BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;sendRead(device,requestId,offset,value);}
        @Override public void onDescriptorWriteRequest(BluetoothDevice device,int requestId,BluetoothGattDescriptor descriptor,boolean prepared,boolean responseNeeded,int offset,byte[] value){int status=BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;if(BleUuids.CCCD.equals(descriptor.getUuid())&&offset==0&&!prepared){boolean enabled=Arrays.equals(value,BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)||Arrays.equals(value,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);synchronized(WatchLinkService.this){if(enabled)subscribedEvents.add(device.getAddress());else subscribedEvents.remove(device.getAddress());}status=BluetoothGatt.GATT_SUCCESS;Log.i(TAG,"subscription enabled="+enabled+" device="+redact(device.getAddress()));}if(responseNeeded)sendResponse(device,requestId,status,offset,null);}
        @Override public void onCharacteristicWriteRequest(BluetoothDevice device,int requestId,BluetoothGattCharacteristic characteristic,boolean prepared,boolean responseNeeded,int offset,byte[] value){int status=acceptFrame(device,characteristic.getUuid(),prepared,offset,value);if(responseNeeded)sendResponse(device,requestId,status,offset,null);}
        @Override public void onNotificationSent(BluetoothDevice device,int status){String key=device.getAddress();synchronized(WatchLinkService.this){notifying.remove(key);}if(status!=BluetoothGatt.GATT_SUCCESS)Log.w(TAG,"indication status="+status+" device="+redact(key));sendNext(device);}
    };

    private synchronized int acceptFrame(BluetoothDevice device,UUID characteristic,boolean prepared,int offset,byte[] value){if(prepared||offset!=0)return BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;if(!BleUuids.PAIRING.equals(characteristic)&&!authenticated.contains(device.getAddress()))return BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION;String key=device.getAddress()+":"+characteristic;BleProtocolCodec.Assembler assembler=assemblers.get(key);if(assembler==null){assembler=new BleProtocolCodec.Assembler();assemblers.put(key,assembler);}BleProtocolCodec.Message message=assembler.accept(value,System.currentTimeMillis());if(message!=null)worker.execute(()->handleMessage(device,characteristic,message.payload));return BluetoothGatt.GATT_SUCCESS;}

    private void handleMessage(BluetoothDevice device,UUID characteristic,byte[] payload){try{JSONObject envelope=new JSONObject(new String(payload,StandardCharsets.UTF_8));String type=envelope.optString("type"),messageId=envelope.optString("messageId");if(BleUuids.PAIRING.equals(characteristic)&&"AUTH".equals(type)){JSONObject auth=envelope.optJSONObject("payload");boolean accepted=auth!=null&&WatchBridgeService.pairingCode(this).equals(auth.optString("code"));if(accepted)synchronized(this){authenticated.add(device.getAddress());}sendEnvelope(device,response(messageId,accepted?200:401,new JSONObject().put("authenticated",accepted).put("deviceId",WatchDeviceIdentity.id(this)).toString()));Log.i(TAG,"authentication accepted="+accepted+" device="+redact(device.getAddress()));return;}if(!authenticated.contains(device.getAddress()))return;JSONObject request=envelope.optJSONObject("payload");if(request==null)return;long expiresAt=envelope.optLong("expiresAt");WatchCommandRouter.Result result=expiresAt>0&&expiresAt<System.currentTimeMillis()?new WatchCommandRouter.Result(409,"{\"error\":\"request_expired\"}"):router.route(request.optString("method"),request.optString("path"),request.optString("body"));Log.i(TAG,"request method="+request.optString("method")+" path="+request.optString("path")+" status="+result.status);sendEnvelope(device,response(messageId,result.status,result.body));}catch(Exception error){Log.w(TAG,"message_rejected type="+characteristic,error);}}

    private JSONObject response(String replyTo,int status,String body)throws Exception{return new JSONObject().put("protocolVersion",1).put("messageId",UUID.randomUUID().toString()).put("replyTo",replyTo).put("type","RESPONSE").put("createdAt",System.currentTimeMillis()).put("payload",new JSONObject().put("status",status).put("body",body));}
    private void sendEnvelope(BluetoothDevice device,JSONObject envelope){byte[] payload=envelope.toString().getBytes(StandardCharsets.UTF_8);int mtu; synchronized(this){mtu=mtuByDevice.getOrDefault(device.getAddress(),DEFAULT_MTU);ArrayDeque<Outgoing> queue=outgoing.computeIfAbsent(device.getAddress(),ignored->new ArrayDeque<>());for(byte[] frame:BleProtocolCodec.encode(envelope.optString("messageId"),payload,mtu))queue.add(new Outgoing(device,frame));}sendNext(device);}
    @SuppressWarnings("MissingPermission") private void sendNext(BluetoothDevice device){Outgoing next; synchronized(this){String key=device.getAddress();if(notifying.contains(key)||!subscribedEvents.contains(key))return;ArrayDeque<Outgoing> queue=outgoing.get(key);next=queue==null?null:queue.poll();if(next==null)return;notifying.add(key);}BluetoothGattCharacteristic events=characteristics.get(BleUuids.EVENTS);events.setValue(next.frame);boolean started=server!=null&&server.notifyCharacteristicChanged(device,events,true);if(!started){synchronized(this){notifying.remove(device.getAddress());}Log.w(TAG,"indication_start_failed device="+redact(device.getAddress()));}}
    private byte[] readValue(BluetoothDevice device,UUID uuid){try{if(BleUuids.DEVICE_INFO.equals(uuid))return new JSONObject().put("protocolVersion",1).put("deviceId",WatchDeviceIdentity.id(this)).put("device","OWW221").put("appVersion",BuildConfig.VERSION_NAME).put("capabilities",new org.json.JSONArray().put("control").put("plans").put("history").put("location").put("lan_endpoint")).toString().getBytes(StandardCharsets.UTF_8);if(BleUuids.HEARTBEAT.equals(uuid))return new JSONObject().put("time",System.currentTimeMillis()).put("authenticated",authenticated.contains(device.getAddress())).toString().getBytes(StandardCharsets.UTF_8);if(BleUuids.LAN_ENDPOINT.equals(uuid)&&authenticated.contains(device.getAddress()))return new JSONObject().put("lanAvailable",true).put("lanPort",WatchBridgeService.PORT).put("deviceId",WatchDeviceIdentity.id(this)).toString().getBytes(StandardCharsets.UTF_8);}catch(Exception ignored){}return new byte[0];}
    @SuppressWarnings("MissingPermission") private void sendRead(BluetoothDevice device,int requestId,int offset,byte[] value){if(offset<0||offset>value.length){sendResponse(device,requestId,BluetoothGatt.GATT_INVALID_OFFSET,offset,null);return;}sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,offset,Arrays.copyOfRange(value,offset,value.length));}
    @SuppressWarnings("MissingPermission") private void sendResponse(BluetoothDevice device,int requestId,int status,int offset,byte[] value){if(server!=null)server.sendResponse(device,requestId,status,offset,value);}
    private synchronized void removeAssemblers(String device){Iterator<String> values=assemblers.keySet().iterator();while(values.hasNext())if(values.next().startsWith(device+":"))values.remove();}
    private static String redact(String address){return address==null?"unknown":"**:**:**:"+address.substring(Math.max(0,address.length()-8));}
    public static JSONObject diagnostics(){try{return new JSONObject().put("service","watch_link");}catch(Exception ignored){return new JSONObject();}}

    @SuppressWarnings("MissingPermission") private synchronized void closeGatt(){if(advertiser!=null&&advertiseCallback!=null)try{advertiser.stopAdvertising(advertiseCallback);}catch(Exception ignored){}advertising=false;if(server!=null)server.close();server=null;characteristics.clear();}
    @Override public void onDestroy(){closeGatt();worker.shutdownNow();if(router!=null)router.close();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    private static final class Outgoing {final BluetoothDevice device;final byte[] frame;Outgoing(BluetoothDevice device,byte[] frame){this.device=device;this.frame=frame;}}
}

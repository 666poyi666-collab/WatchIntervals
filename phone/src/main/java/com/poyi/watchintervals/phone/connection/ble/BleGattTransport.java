package com.poyi.watchintervals.phone.connection.ble;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import com.poyi.watchintervals.phone.connection.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Phone central/GATT client with serialized operations and framed request responses. */
public final class BleGattTransport implements WatchTransport {
    public interface StateListener { void onState(ConnectionState state,String reason); }
    private static final String TAG="BleGattTransport";
    private final Context context;private final WatchIdentityStore identity;private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,CompletableFuture<ResponseEnvelope>> pending=new ConcurrentHashMap<>();
    private final BleProtocolCodec.Assembler incoming=new BleProtocolCodec.Assembler();
    private final ArrayDeque<WriteOp> writes=new ArrayDeque<>();
    private final ScheduledExecutorService timers=Executors.newSingleThreadScheduledExecutor();
    private BluetoothAdapter adapter;private BluetoothLeScanner scanner;private ScanCallback scanCallback;private BluetoothGatt gatt;
    private BluetoothGattCharacteristic pairing,control,events,syncTx,location;
    private CompletableFuture<TransportSession> connection;private CompletableFuture<ResponseEnvelope> authentication;
    private EventListener eventListener;private StateListener stateListener;private int mtu=23,rssi;private boolean connected,subscribed,writing;
    private final ArrayDeque<UUID> subscriptionQueue=new ArrayDeque<>();

    public BleGattTransport(Context context,WatchIdentityStore identity){this.context=context.getApplicationContext();this.identity=identity;BluetoothManager manager=this.context.getSystemService(BluetoothManager.class);adapter=manager==null?null:manager.getAdapter();}
    public void setStateListener(StateListener listener){stateListener=listener;}
    private void state(ConnectionState value,String reason){Log.i(TAG,"state="+value+(reason==null?"":" reason="+reason));if(stateListener!=null)main.post(()->stateListener.onState(value,reason));}
    @Override public TransportType type(){return TransportType.BLE;}
    @Override public boolean isAvailable(){return adapter!=null&&adapter.isEnabled()&&allowed();}

    @Override public synchronized CompletableFuture<TransportSession> connect(){if(connected&&subscribed)return CompletableFuture.completedFuture(new TransportSession(TransportType.BLE,identity.watchDeviceId(),mtu));if(connection!=null&&!connection.isDone())return connection;connection=new CompletableFuture<>();if(adapter==null||!adapter.isEnabled()){state(ConnectionState.BLUETOOTH_DISABLED,"adapter_disabled");connection.completeExceptionally(new IllegalStateException("bluetooth_disabled"));return connection;}if(!allowed()){state(ConnectionState.DISCONNECTED,"permission_required");connection.completeExceptionally(new SecurityException("bluetooth_permission_required"));return connection;}startScan();return connection;}

    @SuppressWarnings("MissingPermission") private void startScan(){disconnectGatt(false);scanner=adapter.getBluetoothLeScanner();if(scanner==null){failConnection("scanner_unavailable",null);return;}state(ConnectionState.SCANNING,null);scanCallback=new ScanCallback(){@Override public void onScanResult(int callbackType,ScanResult result){if(result==null)return;stopScan();rssi=result.getRssi();state(ConnectionState.CONNECTING_BLE,null);gatt=result.getDevice().connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);}@Override public void onScanFailed(int code){failConnection("scan_failed_"+code,null);}};scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(BleUuids.SERVICE)).build()),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);timers.schedule(()->{synchronized(BleGattTransport.this){if(connection!=null&&!connection.isDone())failConnection("scan_timeout",null);}},20,TimeUnit.SECONDS);}
    @SuppressWarnings("MissingPermission") private void stopScan(){if(scanner!=null&&scanCallback!=null)try{scanner.stopScan(scanCallback);}catch(Exception ignored){}scanCallback=null;}

    @SuppressLint("MissingPermission") private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt value,int status,int newState){if(status==BluetoothGatt.GATT_SUCCESS&&newState==BluetoothProfile.STATE_CONNECTED){connected=true;state(ConnectionState.DISCOVERING_SERVICES,null);if(!value.discoverServices())failConnection("discover_start_failed",null);}else if(newState==BluetoothProfile.STATE_DISCONNECTED){connected=false;subscribed=false;failPending("disconnected_"+status);state(ConnectionState.DISCONNECTED,"gatt_"+status);disconnectGatt(false);}}
        @Override public void onServicesDiscovered(BluetoothGatt value,int status){if(status!=BluetoothGatt.GATT_SUCCESS){failConnection("service_discovery_"+status,null);return;}BluetoothGattService service=value.getService(BleUuids.SERVICE);if(service==null){failConnection("service_missing",null);return;}pairing=service.getCharacteristic(BleUuids.PAIRING);control=service.getCharacteristic(BleUuids.CONTROL);events=service.getCharacteristic(BleUuids.EVENTS);syncTx=service.getCharacteristic(BleUuids.SYNC_TX);location=service.getCharacteristic(BleUuids.LOCATION);if(pairing==null||control==null||events==null||syncTx==null||location==null){failConnection("characteristic_missing",null);return;}state(ConnectionState.SUBSCRIBING,null);subscriptionQueue.clear();subscriptionQueue.add(BleUuids.EVENTS);subscriptionQueue.add(BleUuids.SYNC_RX);subscriptionQueue.add(BleUuids.PAIRING);subscriptionQueue.add(BleUuids.HEARTBEAT);if(!value.requestMtu(247)){mtu=23;subscribeNext();}}
        @Override public void onMtuChanged(BluetoothGatt value,int nextMtu,int status){mtu=status==BluetoothGatt.GATT_SUCCESS?Math.max(23,nextMtu):23;Log.i(TAG,"mtu="+mtu+" status="+status);subscribeNext();}
        @Override public void onDescriptorWrite(BluetoothGatt value,BluetoothGattDescriptor descriptor,int status){if(status!=BluetoothGatt.GATT_SUCCESS){failConnection("subscription_"+status,null);return;}subscribeNext();}
        @Override public void onCharacteristicWrite(BluetoothGatt value,BluetoothGattCharacteristic characteristic,int status){synchronized(BleGattTransport.this){writing=false;if(status!=BluetoothGatt.GATT_SUCCESS){WriteOp failed=writes.poll();failConnection("write_"+status,failed==null?null:failed.requestId);}else writes.poll();writeNext();}}
        @Override public void onCharacteristicChanged(BluetoothGatt value,BluetoothGattCharacteristic characteristic){handleIncoming(characteristic.getValue());}
        @Override public void onReadRemoteRssi(BluetoothGatt value,int measuredRssi,int status){if(status==BluetoothGatt.GATT_SUCCESS)rssi=measuredRssi;}
    };

    @SuppressWarnings("MissingPermission") private void subscribeNext(){if(gatt==null)return;UUID uuid=subscriptionQueue.poll();if(uuid==null){subscribed=true;authenticate();return;}BluetoothGattCharacteristic value=gatt.getService(BleUuids.SERVICE).getCharacteristic(uuid);BluetoothGattDescriptor cccd=value==null?null:value.getDescriptor(BleUuids.CCCD);if(value==null||cccd==null||!gatt.setCharacteristicNotification(value,true)){failConnection("subscription_setup_failed",null);return;}cccd.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);if(!gatt.writeDescriptor(cccd))failConnection("subscription_write_failed",null);}

    private void authenticate(){try{state(ConnectionState.AUTHENTICATING,null);String id=UUID.randomUUID().toString();JSONObject envelope=new JSONObject().put("protocolVersion",1).put("messageId",id).put("type","AUTH").put("createdAt",System.currentTimeMillis()).put("payload",new JSONObject().put("phoneDeviceId",identity.phoneDeviceId()).put("code",identity.pairingCode()));authentication=new CompletableFuture<>();pending.put(id,authentication);enqueue(pairing,id,envelope.toString().getBytes(StandardCharsets.UTF_8),true);timers.schedule(()->{CompletableFuture<ResponseEnvelope> value=pending.remove(id);if(value!=null)value.completeExceptionally(new TimeoutException("authentication_timeout"));},12,TimeUnit.SECONDS);authentication.whenComplete((response,error)->{if(error!=null||response.status!=200){failConnection("authentication_failed",null);return;}try{JSONObject body=new JSONObject(response.body);String watchId=body.optString("deviceId");String expected=identity.watchDeviceId();if(!expected.isEmpty()&&!expected.equals(watchId)){failConnection("identity_mismatch",null);return;}identity.savePairing(watchId,identity.pairingCode());state(ConnectionState.CONNECTED_BLE,null);CompletableFuture<TransportSession> future=connection;if(future!=null&&!future.isDone())future.complete(new TransportSession(TransportType.BLE,watchId,mtu));}catch(Exception parse){failConnection("authentication_response_invalid",null);}});}catch(Exception error){failConnection("authentication_build_failed",null);}}

    @Override public CompletableFuture<ResponseEnvelope> request(RequestEnvelope request){CompletableFuture<ResponseEnvelope> future=new CompletableFuture<>();if(!connected||!subscribed){future.completeExceptionally(new IllegalStateException("ble_not_connected"));return future;}if(request.expiresAt>0&&request.expiresAt<System.currentTimeMillis()){future.completeExceptionally(new TimeoutException("request_expired"));return future;}try{BluetoothGattCharacteristic target=request.path.startsWith("/v1/control/")?control:request.path.equals("/v1/location")?location:syncTx;pending.put(request.messageId,future);enqueue(target,request.messageId,request.toJson().toString().getBytes(StandardCharsets.UTF_8),true);long timeout=Math.max(1000L,request.expiresAt>0?request.expiresAt-System.currentTimeMillis():15_000L);timers.schedule(()->{CompletableFuture<ResponseEnvelope> value=pending.remove(request.messageId);if(value!=null)value.completeExceptionally(new TimeoutException("ble_request_timeout"));},timeout,TimeUnit.MILLISECONDS);}catch(Exception error){pending.remove(request.messageId);future.completeExceptionally(error);}return future;}

    private synchronized void enqueue(BluetoothGattCharacteristic characteristic,String requestId,byte[] payload,boolean response){for(byte[] frame:BleProtocolCodec.encode(requestId,payload,mtu))writes.add(new WriteOp(characteristic,frame,response,requestId));writeNext();}
    @SuppressWarnings("MissingPermission") private synchronized void writeNext(){if(writing||gatt==null)return;WriteOp next=writes.peek();if(next==null)return;int writeType=next.response?BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;boolean started;if(android.os.Build.VERSION.SDK_INT>=33)started=gatt.writeCharacteristic(next.characteristic,next.frame,writeType)==android.bluetooth.BluetoothStatusCodes.SUCCESS;else{next.characteristic.setWriteType(writeType);next.characteristic.setValue(next.frame);started=gatt.writeCharacteristic(next.characteristic);}if(!started){next.startAttempts++;if(next.startAttempts<=10){main.postDelayed(this::writeNext,50L);return;}Log.w(TAG,"write_start_failed characteristic="+next.characteristic.getUuid()+" attempts="+next.startAttempts);writes.poll();CompletableFuture<ResponseEnvelope> value=pending.remove(next.requestId);if(value!=null)value.completeExceptionally(new IllegalStateException("gatt_write_start_failed"));writeNext();return;}if(next.response)writing=true;else{writes.poll();main.post(this::writeNext);}}
    private void handleIncoming(byte[] frame){BleProtocolCodec.Message message=incoming.accept(frame,System.currentTimeMillis());if(message==null)return;try{ResponseEnvelope response=ResponseEnvelope.fromJson(new JSONObject(new String(message.payload,StandardCharsets.UTF_8)));CompletableFuture<ResponseEnvelope> future=pending.remove(response.replyTo);if(future!=null)future.complete(response);else if(eventListener!=null)eventListener.onEvent(response);}catch(Exception error){Log.w(TAG,"invalid_response",error);}}

    private synchronized void failConnection(String reason,String requestId){state(ConnectionState.DISCONNECTED,reason);if(requestId!=null){CompletableFuture<ResponseEnvelope> request=pending.remove(requestId);if(request!=null)request.completeExceptionally(new IllegalStateException(reason));}CompletableFuture<TransportSession> future=connection;if(future!=null&&!future.isDone())future.completeExceptionally(new IllegalStateException(reason));disconnectGatt(false);}
    private void failPending(String reason){for(CompletableFuture<ResponseEnvelope> value:pending.values())value.completeExceptionally(new IllegalStateException(reason));pending.clear();}
    @SuppressWarnings("MissingPermission") private synchronized void disconnectGatt(boolean updateState){stopScan();BluetoothGatt value=gatt;gatt=null;connected=false;subscribed=false;writing=false;writes.clear();if(value!=null){try{value.disconnect();}catch(Exception ignored){}value.close();}if(updateState)state(ConnectionState.DISCONNECTED,"requested");}
    @Override public void subscribe(EventListener listener){eventListener=listener;}
    @Override public void disconnect(){disconnectGatt(true);}
    public int rssi(){return rssi;}public int mtu(){return mtu;}public boolean notificationsSubscribed(){return subscribed;}
    private boolean allowed(){return android.os.Build.VERSION.SDK_INT<31||context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    private static final class WriteOp {final BluetoothGattCharacteristic characteristic;final byte[] frame;final boolean response;final String requestId;int startAttempts;WriteOp(BluetoothGattCharacteristic characteristic,byte[] frame,boolean response,String requestId){this.characteristic=characteristic;this.frame=frame;this.response=response;this.requestId=requestId;}}
}

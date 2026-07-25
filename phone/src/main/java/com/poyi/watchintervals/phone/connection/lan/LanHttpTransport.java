package com.poyi.watchintervals.phone.connection.lan;

import com.poyi.watchintervals.phone.WatchClient;
import com.poyi.watchintervals.phone.connection.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class LanHttpTransport implements WatchTransport {
    private final Executor io=Executors.newSingleThreadExecutor();
    private volatile WatchClient client; private volatile String host="",deviceId="";

    public void configure(String host,String pairingCode){this.host=host==null?"":host.trim();client=this.host.isEmpty()?null:new WatchClient(this.host,pairingCode==null?"":pairingCode.trim());}
    @Override public TransportType type(){return TransportType.LAN;}
    @Override public boolean isAvailable(){return client!=null;}
    @Override public CompletableFuture<TransportSession> connect(){return CompletableFuture.supplyAsync(()->{try{JSONObject status=new JSONObject(client.get("/v1/status"));deviceId=status.optString("deviceId");return new TransportSession(TransportType.LAN,deviceId,0);}catch(Exception error){throw new RuntimeException(error);}},io);}
    @Override public CompletableFuture<ResponseEnvelope> request(RequestEnvelope request){return CompletableFuture.supplyAsync(()->{try{String body;switch(request.method){case"GET":body=client.get(request.path);break;case"PUT":body=client.put(request.path,request.body);break;case"POST":body=client.post(request.path,request.body);break;case"DELETE":body=client.delete(request.path);break;default:throw new IllegalArgumentException("unsupported_method");}return new ResponseEnvelope(UUID.randomUUID().toString(),request.messageId,200,body);}catch(Exception error){throw new RuntimeException(error);}},io);}
    @Override public void subscribe(EventListener listener){}
    @Override public void disconnect(){client=null;deviceId="";}
    public String host(){return host;}
}

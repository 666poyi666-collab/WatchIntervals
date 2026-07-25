package com.poyi.watchintervals.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.poyi.watchintervals.phone.connection.WatchConnectionManager;

/** Local phone-authoritative plan API consumed by the desktop MCP server. */
public class PhonePlanBridgeService extends Service {
    static final int PORT = 8766;
    private static final String CHANNEL = "phone_plan_bridge";
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private ServerSocket server;
    private NsdManager.RegistrationListener registration;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "计划与 MCP 同步", NotificationManager.IMPORTANCE_MIN));
        Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("训练计划同步已开启").setContentText("手机计划库可供手表与 MCP 使用").build();
        startForeground(63, notification); registerNsd(); workers.execute(this::serve);
    }

    private void serve() {
        try (ServerSocket socket = new ServerSocket(PORT)) {
            server = socket;
            while (!socket.isClosed()) { Socket client = socket.accept(); workers.execute(() -> handle(client)); }
        } catch (Exception ignored) {}
    }

    private void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(5000);
            InputStream input = socket.getInputStream();
            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            int marker = 0;
            while (headerBytes.size() < 32_768) {
                int value = input.read(); if (value < 0) return; headerBytes.write(value);
                marker = marker == 0 && value == '\r' ? 1
                        : marker == 1 && value == '\n' ? 2
                        : marker == 2 && value == '\r' ? 3
                        : marker == 3 && value == '\n' ? 4 : (value == '\r' ? 1 : 0);
                if (marker == 4) break;
            }
            String[] headerLines = headerBytes.toString(StandardCharsets.ISO_8859_1.name()).split("\\r\\n");
            if (headerLines.length == 0) return; String[] first = headerLines[0].split(" ");
            String method = first[0], path = first.length > 1 ? first[1] : "/"; Map<String,String> headers = new HashMap<>(); String line;
            for (int i = 1; i < headerLines.length; i++) { line = headerLines[i]; int split = line.indexOf(':'); if (split > 0) headers.put(line.substring(0,split).trim().toLowerCase(Locale.ROOT), line.substring(split+1).trim()); }
            int length; try { length = Integer.parseInt(headers.getOrDefault("content-length","0")); } catch (Exception ignored) { length = 0; }
            int expectedBytes = Math.max(0, Math.min(length, 256_000));
            byte[] bodyBytes = new byte[expectedBytes]; int offset = 0;
            while (offset < expectedBytes) { int read = input.read(bodyBytes, offset, expectedBytes - offset); if (read < 0) break; offset += read; }
            String body = new String(bodyBytes, 0, offset, StandardCharsets.UTF_8);
            String expected = getSharedPreferences("connection",MODE_PRIVATE).getString("code","");
            if (expected.isEmpty() || !expected.equals(headers.get("x-pairing-code"))) { respond(socket,401,new JSONObject().put("error","pairing_required").toString()); return; }
            route(socket, method, path, body);
        } catch (Exception error) {
            try { respond(socket,400,new JSONObject().put("error","bad_request").put("detail",String.valueOf(error.getMessage())).toString()); }
            catch (Exception ignored) {}
        }
    }

    private void route(Socket socket, String method, String path, String body) throws Exception {
        JSONObject result;
        if ("GET".equals(method) && "/v1/status".equals(path)) result = new JSONObject().put("device","phone").put("phoneDeviceId",phoneDeviceId()).put("appVersion",BuildConfig.VERSION_NAME).put("protocolVersion",2).put("authoritative",true).put("port",PORT).put("libraryRevision",PhonePlanLibrary.load(this).optLong("revision")).put("pendingOperations",PhoneSyncOutbox.size(this));
        else if ("GET".equals(method) && "/v1/plan-library".equals(path)) result = PhonePlanLibrary.load(this);
        else if ("GET".equals(method) && "/v1/plan-groups".equals(path)) result = new JSONObject().put("groups",PhonePlanLibrary.load(this).getJSONArray("groups"));
        else if ("POST".equals(method) && "/v1/plan-groups".equals(path)) { result = PhonePlanLibrary.createGroup(this,new JSONObject(body).optString("name")); result = mutation("group",result); }
        else if (path.startsWith("/v1/plan-groups/") && "PUT".equals(method)) { result=PhonePlanLibrary.renameGroup(this,tail(path),new JSONObject(body).optString("name"));result=mutation("group",result); }
        else if (path.startsWith("/v1/plan-groups/") && "DELETE".equals(method)) { result=PhonePlanLibrary.deleteGroup(this,tail(path));result=mutation("library",result); }
        else if ("GET".equals(method) && "/v1/plans".equals(path)) { JSONObject library=PhonePlanLibrary.load(this);result=new JSONObject().put("plans",library.getJSONArray("plans")).put("selectedPlanId",library.optString("selectedPlanId")); }
        else if ("POST".equals(method) && "/v1/plans".equals(path)) {
            JSONObject request=new JSONObject(body);JSONObject plan=request.optJSONObject("plan");if(plan==null)plan=request;
            final JSONObject value=plan;result=guardedMutation(request,()->mutation("library",PhonePlanLibrary.upsert(this,value)));
        }
        else if (path.startsWith("/v1/plans/") && "PUT".equals(method)) {
            JSONObject request=new JSONObject(body);JSONObject plan=request.optJSONObject("plan");if(plan==null)plan=request;
            final JSONObject value=new JSONObject(plan.toString()).put("id",tail(path));result=guardedMutation(request,()->mutation("library",PhonePlanLibrary.upsert(this,value)));
        }
        else if (path.startsWith("/v1/plans/") && "DELETE".equals(method)) { String id=tail(path);result=PhonePlanLibrary.deletePlan(this,id);PhoneSyncOutbox.enqueueLibrary(this,result,"delete",id);result=new JSONObject().put("library",result).put("sync",syncToWatch()); }
        else if ("PUT".equals(method) && "/v1/plan-selection".equals(path)) {
            JSONObject request=new JSONObject(body);result=guardedMutation(request,()->mutation("library",PhonePlanLibrary.select(this,request.optString("planId"))));
        }
        else if ("POST".equals(method) && "/v1/sync".equals(path)) result=syncToWatch();
        else { respond(socket,404,new JSONObject().put("error","not_found").toString());return; }
        int status=result.optInt("_httpStatus",200);result.remove("_httpStatus");respond(socket,status,result.toString());
    }

    private interface MutationAction { JSONObject run() throws Exception; }
    private JSONObject guardedMutation(JSONObject request,MutationAction action)throws Exception{
        String requestId=request.optString("requestId","");String hash=sha256(request.toString());JSONObject cached=mutationCache().optJSONObject(requestId);
        String cachedHash=cached==null?null:cached.optString("hash",null);long actual=PhonePlanLibrary.load(this).optLong("revision");
        if(cached!=null&&hash.equals(cachedHash)&&"in_progress".equals(cached.optString("status"))){
            if(actual!=cached.optLong("initialRevision")){JSONObject recovered=new JSONObject().put("library",PhonePlanLibrary.load(this)).put("requestId",requestId).put("revision",actual).put("recovered",true);cacheMutation(requestId,hash,"completed",actual,recovered);return recovered.put("duplicate",true);}
            cached=null;cachedHash=null;
        }
        MutationGuard.Decision decision=MutationGuard.decide(requestId,hash,cachedHash,request.has("expectedRevision"),request.optLong("expectedRevision"),actual);
        if(decision==MutationGuard.Decision.DUPLICATE)return new JSONObject(cached.getJSONObject("result").toString()).put("duplicate",true);
        if(decision==MutationGuard.Decision.REQUEST_ID_REUSED)return new JSONObject().put("error","conflict").put("reason","request_id_reused").put("_httpStatus",409);
        if(decision==MutationGuard.Decision.REVISION_CONFLICT){JSONObject conflict=new JSONObject().put("error","conflict").put("expectedRevision",request.optLong("expectedRevision")).put("actualRevision",actual).put("_httpStatus",409);cacheMutation(requestId,hash,"completed",actual,conflict);return conflict;}
        if(!requestId.isEmpty())cacheMutation(requestId,hash,"in_progress",actual,null);
        JSONObject result=action.run();if(!requestId.isEmpty()){long revision=PhonePlanLibrary.load(this).optLong("revision");result.put("requestId",requestId).put("revision",revision);cacheMutation(requestId,hash,"completed",actual,result);}return result;
    }
    private JSONObject mutationCache(){try{return new JSONObject(getSharedPreferences("gateway_mutations",MODE_PRIVATE).getString("items","{}"));}catch(Exception ignored){return new JSONObject();}}
    private void cacheMutation(String id,String hash,String status,long initialRevision,JSONObject result){if(id.isEmpty())return;try{JSONObject item=new JSONObject().put("hash",hash).put("status",status).put("initialRevision",initialRevision);if(result!=null)item.put("result",new JSONObject(result.toString()));JSONObject cache=mutationCache();cache.put(id,item);JSONArray names=cache.names();while(names!=null&&names.length()>500){cache.remove(names.optString(0));names=cache.names();}if(!getSharedPreferences("gateway_mutations",MODE_PRIVATE).edit().putString("items",cache.toString()).commit())throw new IllegalStateException("mutation_cache_commit_failed");}catch(org.json.JSONException error){throw new IllegalArgumentException(error);}}
    private String sha256(String value)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder text=new StringBuilder();for(byte item:digest)text.append(String.format(Locale.ROOT,"%02x",item));return text.toString();}

    private JSONObject mutation(String key, JSONObject value) throws Exception { JSONObject library=PhonePlanLibrary.load(this);PhoneSyncOutbox.enqueueLibrary(this,library,"upsert",key);JSONObject sync=syncToWatch();return new JSONObject().put(key,value).put("sync",sync); }
    private JSONObject syncToWatch() {
        try {
            String host=getSharedPreferences("connection",MODE_PRIVATE).getString("host","");String code=getSharedPreferences("connection",MODE_PRIVATE).getString("code","");
            if(code.length()!=6)return new JSONObject().put("state","pending").put("reason","watch_not_configured");
            if(PhoneSyncOutbox.size(this)==0)PhoneSyncOutbox.enqueueLibrary(this,PhonePlanLibrary.load(this),"upsert","library");
            WatchConnectionManager connection=WatchConnectionManager.get(this);connection.configurePairing(code);connection.configureLan(host,code);
            return PhoneSyncOutbox.drain(this,connection);
        } catch(Exception error){try{return new JSONObject().put("state","pending").put("reason",error.getMessage());}catch(Exception ignored){return new JSONObject();}}
    }
    private String phoneDeviceId(){android.content.SharedPreferences p=getSharedPreferences("device_identity",MODE_PRIVATE);String id=p.getString("phone_device_id","");if(id.isEmpty()){id=java.util.UUID.randomUUID().toString();p.edit().putString("phone_device_id",id).apply();}return id;}
    private void registerNsd(){NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("WatchIntervals-Phone-"+phoneDeviceId().substring(0,8));info.setServiceType("_watchintervals-phone._tcp.");info.setPort(PORT);try{info.setAttribute("deviceId",phoneDeviceId());info.setAttribute("protocolVersion","2");}catch(Exception ignored){}registration=new NsdManager.RegistrationListener(){public void onRegistrationFailed(NsdServiceInfo i,int c){}public void onUnregistrationFailed(NsdServiceInfo i,int c){}public void onServiceRegistered(NsdServiceInfo i){}public void onServiceUnregistered(NsdServiceInfo i){}};try{getSystemService(NsdManager.class).registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);}catch(Exception error){android.util.Log.w("PhonePlanBridge","mDNS registration failed",error);}}
    private String tail(String path){return path.substring(path.lastIndexOf('/')+1);}
    private void respond(Socket socket,int status,String body)throws Exception{byte[] data=body.getBytes(StandardCharsets.UTF_8);String reason=status==200?"OK":status==400?"Bad Request":status==401?"Unauthorized":status==409?"Conflict":"Not Found";String header="HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n\r\n";OutputStream out=socket.getOutputStream();out.write(header.getBytes(StandardCharsets.US_ASCII));out.write(data);out.flush();}
    @Override public void onDestroy(){try{if(server!=null)server.close();}catch(Exception ignored){}try{if(registration!=null)getSystemService(NsdManager.class).unregisterService(registration);}catch(Exception ignored){}workers.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}

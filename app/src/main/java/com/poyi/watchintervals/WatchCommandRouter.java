package com.poyi.watchintervals;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shared business router used by LAN and authenticated Bluetooth transports. */
final class WatchCommandRouter implements AutoCloseable {
    static final class Result { final int status;final String body;Result(int status,String body){this.status=status;this.body=body;} }
    private final Context context;
    private final SystemSleepBridge sleep;

    WatchCommandRouter(Context context){this.context=context.getApplicationContext();sleep=new SystemSleepBridge(this.context);}

    Result route(String method,String path,String body){
        try {
            if("GET".equals(method)&&"/v1/status".equals(path))return ok(status());
            if("GET".equals(method)&&"/v1/plan".equals(path))return ok(PlanStore.encode(PlanStore.load(context)));
            if("GET".equals(method)&&"/v1/plan/profile".equals(path))return ok(new JSONObject().put("name",PlanStore.name(context)).put("group",PlanStore.group(context)).put("requirement",PlanStore.requirement(context)).put("stages",new JSONArray(PlanStore.encode(PlanStore.load(context)))).toString());
            if("GET".equals(method)&&"/v1/plan-library".equals(path))return ok(PlanLibraryStore.load(context).toString());
            if("PUT".equals(method)&&"/v1/plan-library".equals(path)){JSONObject library=PlanLibraryStore.replace(context,new JSONObject(body));return ok(new JSONObject().put("saved",true).put("revision",library.optLong("revision")).put("planCount",library.getJSONArray("plans").length()).put("selectedPlanId",library.optString("selectedPlanId")).toString());}
            if("POST".equals(method)&&"/v1/sync/operations".equals(path))return ok(applySyncOperations(new JSONObject(body)).toString());
            if("PUT".equals(method)&&"/v1/plan-selection".equals(path)){JSONObject selected=PlanLibraryStore.select(context,new JSONObject(body).optString("planId"));return ok(new JSONObject().put("selected",true).put("planId",selected.optString("id")).put("name",selected.optString("name")).toString());}
            if("PUT".equals(method)&&"/v1/plan".equals(path)){java.util.ArrayList<Stage> stages=PlanStore.decode(body);if(stages.isEmpty())return error(422,"invalid_plan");PlanStore.save(context,stages);return ok(new JSONObject().put("saved",true).put("stageCount",stages.size()).toString());}
            if("PUT".equals(method)&&"/v1/plan/profile".equals(path)){JSONObject profile=new JSONObject(body);java.util.ArrayList<Stage> stages=PlanStore.decode(profile.optJSONArray("stages")==null?null:profile.optJSONArray("stages").toString());if(stages.isEmpty())return error(422,"invalid_plan");PlanStore.saveProfile(context,profile.optString("name","自定义计划"),profile.optString("group","我的计划"),profile.optString("requirement","按阶段顺序完成训练。"),stages);return ok(new JSONObject().put("saved",true).put("stageCount",stages.size()).toString());}
            if("GET".equals(method)&&"/v1/history".equals(path))return ok(HistoryStore.toJson(context).toString());
            if("GET".equals(method)&&("/v1/sleep".equals(path)||path.startsWith("/v1/sleep?")))return ok(sleep.read(queryDays(path)).toString());
            if("GET".equals(method)&&path.startsWith("/v1/history/")&&path.contains("/route")){String id=historyId(path,"/route");return ok(HistoryStore.routePage(context,id,queryInt(path,"cursor",0,0,Integer.MAX_VALUE),queryInt(path,"limit",500,1,1000)).toString());}
            if("GET".equals(method)&&path.startsWith("/v1/history/")&&path.contains("/heart")){String id=historyId(path,"/heart");return ok(HistoryStore.heartPage(context,id,queryInt(path,"cursor",0,0,Integer.MAX_VALUE),queryInt(path,"limit",500,1,1000)).toString());}
            if("GET".equals(method)&&path.startsWith("/v1/history/")){WorkoutRecord record=HistoryStore.find(context,path.substring("/v1/history/".length()));return record==null?error(404,"workout_not_found"):ok(record.toJson().toString());}
            if("DELETE".equals(method)&&path.startsWith("/v1/history/")){HistoryStore.delete(context,path.substring("/v1/history/".length()));return ok("{\"deleted\":true}");}
            if("POST".equals(method)&&"/v1/location".equals(path))return location(body);
            if("POST".equals(method)&&path.startsWith("/v1/control/"))return control(path.substring("/v1/control/".length()),body);
            return error(404,"not_found");
        } catch(Exception error){return error(500,"internal_error");}
    }

    private String status()throws Exception{JSONObject value=new JSONObject().put("device","OWW221").put("appVersion",BuildConfig.VERSION_NAME).put("deviceId",WatchDeviceIdentity.id(context)).put("protocolVersion",2).put("activeSession",WorkoutService.hasRecoverableSession(context)).put("sessionState",WorkoutService.persistedSessionState(context)).put("planState",WorkoutService.persistedPlanState(context)).put("backgroundLocation",context.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED).put("transport","multi").put("bleSecurity",WatchLinkService.diagnostics()).put("port",WatchBridgeService.PORT);
        // Live pace/HR/cadence block while a workout runs, so phone and MCP callers see the
        // session itself rather than a bare activeSession flag.
        JSONObject workout=WorkoutService.liveWorkoutJson();if(workout!=null)value.put("workout",workout);
        return value.toString();}
    private Result location(String body)throws Exception{JSONObject point=new JSONObject(body);double latitude=point.optDouble("latitude",Double.NaN),longitude=point.optDouble("longitude",Double.NaN);if(!Double.isFinite(latitude)||!Double.isFinite(longitude))return error(422,"invalid_location");Intent relay=new Intent(context,WorkoutService.class).setAction(WorkoutService.ACTION_EXTERNAL_LOCATION).putExtra(WorkoutService.EXTRA_LATITUDE,latitude).putExtra(WorkoutService.EXTRA_LONGITUDE,longitude).putExtra(WorkoutService.EXTRA_ACCURACY,(float)point.optDouble("accuracy",30d)).putExtra(WorkoutService.EXTRA_SPEED,(float)point.optDouble("speed",-1d));context.startService(relay);return ok("{\"accepted\":true}");}
    private Result control(String action,String body)throws Exception{JSONObject command=body==null||body.isEmpty()?new JSONObject():new JSONObject(body);String commandId=command.optString("commandId",java.util.UUID.randomUUID().toString());JSONObject cached=commandCache().optJSONObject(commandId);if(cached!=null)return result(new JSONObject(cached.toString()).put("duplicate",true));long expiresAt=command.optLong("expiresAt",Long.MAX_VALUE);if(expiresAt<System.currentTimeMillis())return result(cacheCommand(commandId,new JSONObject().put("accepted",false).put("error","command_expired").put("httpStatus",409)));String expected=command.optString("expectedState","").toUpperCase(Locale.US),actual=WorkoutService.persistedSessionState(context);if(!expected.isEmpty()&&!expected.equals(actual))return result(cacheCommand(commandId,new JSONObject().put("accepted",false).put("error","state_mismatch").put("actualState",actual).put("httpStatus",409)));Intent intent=new Intent(context,WorkoutService.class);if("start".equals(action))intent.setAction(WorkoutService.ACTION_START).putExtra("plan",PlanStore.encode(PlanStore.load(context)));else if("pause".equals(action))intent.setAction(WorkoutService.ACTION_PAUSE);else if("resume".equals(action))intent.setAction(WorkoutService.ACTION_RESUME);else if("toggle".equals(action))intent.setAction(WorkoutService.ACTION_TOGGLE);else if("stop".equals(action))intent.setAction(WorkoutService.ACTION_STOP);else return error(422,"invalid_action");context.startForegroundService(intent);return result(cacheCommand(commandId,new JSONObject().put("accepted",true).put("commandId",commandId).put("action",action).put("previousState",actual)));}
    private Result result(JSONObject value){int status=value.optInt("httpStatus",200);value.remove("httpStatus");return new Result(status,value.toString());}
    private JSONObject commandCache(){try{return new JSONObject(context.getSharedPreferences("command_cache",Context.MODE_PRIVATE).getString("items","{}"));}catch(Exception ignored){return new JSONObject();}}
    private JSONObject cacheCommand(String id,JSONObject result){try{JSONObject cache=commandCache();cache.put(id,new JSONObject(result.toString()));JSONArray names=cache.names();if(names!=null&&names.length()>100)cache.remove(names.optString(0));context.getSharedPreferences("command_cache",Context.MODE_PRIVATE).edit().putString("items",cache.toString()).apply();}catch(Exception ignored){}return result;}
    private JSONObject applySyncOperations(JSONObject request)throws Exception{JSONArray operations=request.optJSONArray("operations"),acks=new JSONArray();if(operations==null)return new JSONObject().put("acks",acks);android.content.SharedPreferences p=context.getSharedPreferences("processed_operations",Context.MODE_PRIVATE);JSONObject processed;try{processed=new JSONObject(p.getString("items","{}"));}catch(Exception ignored){processed=new JSONObject();}for(int i=0;i<operations.length();i++){JSONObject op=operations.optJSONObject(i);if(op==null)continue;String id=op.optString("operationId");JSONObject ack=new JSONObject().put("operationId",id);if(processed.has(id)){acks.put(ack.put("status","already_applied"));continue;}if(!"plan_library".equals(op.optString("entityType"))||op.optJSONObject("payload")==null){acks.put(ack.put("status","invalid"));continue;}JSONObject current=PlanLibraryStore.load(context),payload=op.getJSONObject("payload");if(payload.optLong("revision")<current.optLong("revision")){acks.put(ack.put("status","conflict").put("libraryRevision",current.optLong("revision")));continue;}JSONObject saved=PlanLibraryStore.replace(context,payload);processed.put(id,System.currentTimeMillis());acks.put(ack.put("status","applied").put("libraryRevision",saved.optLong("revision")));}JSONArray names=processed.names();while(names!=null&&names.length()>500){processed.remove(names.optString(0));names=processed.names();}p.edit().putString("items",processed.toString()).apply();return new JSONObject().put("acks",acks);}
    private String historyId(String path,String suffix){int start="/v1/history/".length(),end=path.indexOf(suffix,start);return path.substring(start,end);}
    private int queryInt(String path,String name,int fallback,int min,int max){int q=path.indexOf('?');if(q<0)return fallback;for(String item:path.substring(q+1).split("&")){String[] pair=item.split("=",2);if(pair.length==2&&name.equals(pair[0]))try{return Math.max(min,Math.min(max,Integer.parseInt(pair[1])));}catch(Exception ignored){return fallback;}}return fallback;}
    private int queryDays(String path){int marker=path.indexOf("days=");if(marker<0)return 7;int end=path.indexOf('&',marker);try{return Math.max(1,Math.min(31,Integer.parseInt(path.substring(marker+5,end<0?path.length():end))));}catch(Exception ignored){return 7;}}
    private Result ok(String body){return new Result(200,body);}
    private Result error(int status,String code){try{return new Result(status,new JSONObject().put("error",code).toString());}catch(Exception ignored){return new Result(status,"{}");}}
    @Override public void close(){sleep.close();}
}

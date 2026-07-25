package com.poyi.watchintervals.phone;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

final class PhoneSyncOutbox {
    private static final String PREF = "sync_outbox";
    private static final String KEY = "operations";
    private PhoneSyncOutbox() {}

    static synchronized JSONObject enqueueLibrary(Context context, JSONObject library, String operation, String entityId) throws Exception {
        JSONArray old = load(context), values = new JSONArray();
        // Each operation carries the complete authoritative library. Older unsent
        // library snapshots are superseded and would otherwise conflict forever.
        for (int index = 0; index < old.length(); index++) {
            JSONObject pending = old.optJSONObject(index);
            if (pending != null && !"plan_library".equals(pending.optString("entityType"))) values.put(pending);
        }
        JSONObject item = new JSONObject().put("operationId",java.util.UUID.randomUUID().toString())
                .put("entityType","plan_library").put("entityId",entityId==null?"library":entityId)
                .put("operation",operation).put("libraryRevision",library.optLong("revision"))
                .put("createdAt",System.currentTimeMillis()).put("payload",library);
        values.put(item); save(context,values); return item;
    }

    static synchronized JSONObject drain(Context context, WatchClient client) throws Exception {
        JSONArray pending=load(context);if(pending.length()==0)return new JSONObject().put("state","synced").put("pendingOperations",0);
        JSONObject response=new JSONObject(client.post("/v1/sync/operations",new JSONObject().put("operations",pending).toString()));
        JSONArray acks=response.optJSONArray("acks"),remaining=new JSONArray();
        java.util.HashSet<String> applied=new java.util.HashSet<>();if(acks!=null)for(int i=0;i<acks.length();i++){JSONObject ack=acks.optJSONObject(i);if(ack!=null&&("applied".equals(ack.optString("status"))||"already_applied".equals(ack.optString("status"))))applied.add(ack.optString("operationId"));}
        for(int i=0;i<pending.length();i++){JSONObject item=pending.optJSONObject(i);if(item!=null&&!applied.contains(item.optString("operationId")))remaining.put(item);}
        save(context,remaining);return new JSONObject().put("state",remaining.length()==0?"synced":"pending").put("pendingOperations",remaining.length()).put("acks",acks==null?new JSONArray():acks);
    }

    static synchronized int size(Context context){return load(context).length();}
    private static JSONArray load(Context context){try{return new JSONArray(context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"[]"));}catch(Exception ignored){return new JSONArray();}}
    private static void save(Context context,JSONArray values){context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,values.toString()).commit();}
}

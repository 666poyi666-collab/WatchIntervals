package com.poyi.watchintervals;

import android.content.Context;
import com.poyi.watchintervals.connection.ble.BleSecurity;
import org.json.JSONArray;
import org.json.JSONObject;

final class WatchPairingStore {
    private static final String PREF="ble_pairings_v1",KEY="items",CHALLENGES="auth_challenges";
    private WatchPairingStore(){}
    static synchronized byte[] secret(Context context,String phoneId){try{JSONObject item=load(context).optJSONObject(phoneId);return item==null?null:BleSecurity.decode(item.optString("secret"));}catch(Exception ignored){return null;}}
    static synchronized String lanCredential(Context context,String phoneId){try{JSONObject item=load(context).optJSONObject(phoneId);return item==null?"":item.optString("lanCredential");}catch(Exception ignored){return "";}}
    static synchronized boolean matchesLanCredential(Context context,String credential){if(credential==null||credential.isEmpty())return false;JSONObject items=load(context);JSONArray names=items.names();if(names==null)return false;for(int i=0;i<names.length();i++){JSONObject item=items.optJSONObject(names.optString(i));if(item!=null&&credential.equals(item.optString("lanCredential")))return true;}return false;}
    static synchronized void save(Context context,String phoneId,byte[] secret,String lanCredential)throws Exception{JSONObject items=load(context);items.put(phoneId,new JSONObject().put("secret",BleSecurity.encode(secret)).put("lanCredential",lanCredential).put("pairedAt",System.currentTimeMillis()));JSONArray names=items.names();while(names!=null&&names.length()>4){items.remove(names.optString(0));names=items.names();}if(!context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,items.toString()).commit())throw new IllegalStateException("pairing_store_failed");}
    static synchronized void remove(Context context,String phoneId){JSONObject items=load(context);items.remove(phoneId);context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,items.toString()).commit();}
    static synchronized boolean consumeAuthChallenge(Context context,String challengeId){JSONObject values;try{values=new JSONObject(context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(CHALLENGES,"{}"));}catch(Exception ignored){values=new JSONObject();}if(values.has(challengeId))return false;try{values.put(challengeId,System.currentTimeMillis());JSONArray names=values.names();while(names!=null&&names.length()>100){values.remove(names.optString(0));names=values.names();}return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(CHALLENGES,values.toString()).commit();}catch(Exception ignored){return false;}}
    private static JSONObject load(Context context){try{return new JSONObject(context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"{}"));}catch(Exception ignored){return new JSONObject();}}
}

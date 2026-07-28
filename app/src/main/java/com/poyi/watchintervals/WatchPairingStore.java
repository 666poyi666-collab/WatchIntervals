package com.poyi.watchintervals;

import android.content.Context;
import android.util.Log;
import com.poyi.watchintervals.connection.ble.BleSecurity;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

final class WatchPairingStore {
    private static final String TAG="WatchPairingStore";
    private static final String PREF="ble_pairings_v1",KEY="items",CHALLENGES="auth_challenges";
    /** JSON version marker: entries with v=2 are Keystore-encrypted at rest. */
    private static final int VERSION_ENCRYPTED=2;
    private WatchPairingStore(){}

    static synchronized byte[] secret(Context context,String phoneId){
        try{
            JSONObject items=load(context);
            JSONObject item=items.optJSONObject(phoneId);
            if(item==null)return null;
            if(item.optInt("v",1)>=VERSION_ENCRYPTED){
                byte[] decrypted=WatchSecretStore.decrypt(
                        item.optString("enc_secret"),item.optString("enc_secret_nonce"),aad(phoneId));
                return decrypted;
            }
            // Legacy plaintext entry: migrate atomically.
            byte[] plain=BleSecurity.decode(item.optString("secret"));
            if(plain==null||plain.length==0)return null;
            boolean migrated=migrateEntry(context,items,phoneId,item,plain,
                    item.optString("lanCredential"));
            java.util.Arrays.fill(plain,(byte)0);
            return migrated?secret(context,phoneId):null;
        }catch(Exception ignored){return null;}
    }

    static synchronized String lanCredential(Context context,String phoneId){
        try{
            JSONObject items=load(context);
            JSONObject item=items.optJSONObject(phoneId);
            if(item==null)return "";
            if(item.optInt("v",1)>=VERSION_ENCRYPTED){
                byte[] decrypted=WatchSecretStore.decrypt(
                        item.optString("enc_lan"),item.optString("enc_lan_nonce"),aad(phoneId));
                return decrypted==null?"":new String(decrypted,StandardCharsets.UTF_8);
            }
            // Legacy plaintext entry: migrate atomically.
            String plain=item.optString("lanCredential");
            byte[] secretBytes=null;
            try{secretBytes=BleSecurity.decode(item.optString("secret"));}catch(Exception ignored){}
            if(secretBytes==null||secretBytes.length==0)return "";
            boolean migrated=migrateEntry(context,items,phoneId,item,secretBytes,plain);
            java.util.Arrays.fill(secretBytes,(byte)0);
            return migrated?lanCredential(context,phoneId):"";
        }catch(Exception ignored){return "";}
    }

    /** True once at least one phone has completed pairing, so the home screen can drop the code. */
    static synchronized boolean hasPairedPhone(Context context){JSONArray names=load(context).names();return names!=null&&names.length()>0;}

    static synchronized boolean matchesLanCredential(Context context,String credential){
        if(credential==null||credential.isEmpty())return false;
        JSONObject items=load(context);
        JSONArray names=items.names();
        if(names==null)return false;
        for(int i=0;i<names.length();i++){
            String phoneId=names.optString(i);
            JSONObject item=items.optJSONObject(phoneId);
            if(item==null)continue;
            String stored;
            if(item.optInt("v",1)>=VERSION_ENCRYPTED){
                byte[] decrypted=WatchSecretStore.decrypt(
                        item.optString("enc_lan"),item.optString("enc_lan_nonce"),aad(phoneId));
                stored=decrypted==null?"":new String(decrypted,StandardCharsets.UTF_8);
            }else{
                byte[] legacySecret=null;
                try{legacySecret=BleSecurity.decode(item.optString("secret"));}catch(Exception ignored){}
                boolean migrated=legacySecret!=null&&legacySecret.length>0&&migrateEntry(
                        context,items,phoneId,item,legacySecret,item.optString("lanCredential"));
                if(legacySecret!=null)java.util.Arrays.fill(legacySecret,(byte)0);
                stored=migrated?lanCredential(context,phoneId):"";
            }
            if(credential.equals(stored))return true;
        }
        return false;
    }

    static synchronized void save(Context context,String phoneId,byte[] secret,String lanCredential)throws Exception{
        JSONObject items=load(context);
        JSONObject entry=new JSONObject().put("pairedAt",System.currentTimeMillis());
        WatchSecretStore.EncryptedValue encSecret=WatchSecretStore.encrypt(secret,aad(phoneId));
        WatchSecretStore.EncryptedValue encLan=WatchSecretStore.encrypt(
                lanCredential.getBytes(StandardCharsets.UTF_8),aad(phoneId));
        if(encSecret==null||encLan==null)throw new IllegalStateException("keystore_unavailable");
        entry.put("v",VERSION_ENCRYPTED)
                .put("enc_secret",encSecret.ciphertext).put("enc_secret_nonce",encSecret.nonce)
                .put("enc_lan",encLan.ciphertext).put("enc_lan_nonce",encLan.nonce);
        items.put(phoneId,entry);
        JSONArray names=items.names();
        while(names!=null&&names.length()>4){items.remove(names.optString(0));names=items.names();}
        if(!context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,items.toString()).commit())
            throw new IllegalStateException("pairing_store_failed");
    }

    static synchronized void remove(Context context,String phoneId){JSONObject items=load(context);items.remove(phoneId);context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,items.toString()).commit();}

    static synchronized boolean consumeAuthChallenge(Context context,String challengeId){JSONObject values;try{values=new JSONObject(context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(CHALLENGES,"{}"));}catch(Exception ignored){values=new JSONObject();}if(values.has(challengeId))return false;try{values.put(challengeId,System.currentTimeMillis());JSONArray names=values.names();while(names!=null&&names.length()>100){values.remove(names.optString(0));names=values.names();}return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(CHALLENGES,values.toString()).commit();}catch(Exception ignored){return false;}}

    private static JSONObject load(Context context){try{return new JSONObject(context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"{}"));}catch(Exception ignored){return new JSONObject();}}

    /** AAD binds ciphertext to a specific phone context, preventing cross-context decryption. */
    private static String aad(String phoneId){return "watch.pairing.v2|"+phoneId;}

    /**
     * Atomically migrates a legacy plaintext entry to Keystore-encrypted format.
     * If encryption or persistence fails, the legacy entry is made unusable and never returned.
     */
    private static boolean migrateEntry(Context context,JSONObject items,String phoneId,
            JSONObject oldItem,byte[] secretBytes,String lanCredential){
        try{
            WatchSecretStore.EncryptedValue encSecret=WatchSecretStore.encrypt(secretBytes,aad(phoneId));
            WatchSecretStore.EncryptedValue encLan=WatchSecretStore.encrypt(
                    lanCredential.getBytes(StandardCharsets.UTF_8),aad(phoneId));
            if(encSecret==null||encLan==null){
                Log.w(TAG,"migration_skipped_keystore_unavailable");
                removeUnsafeEntry(context,items,phoneId);
                return false;
            }
            JSONObject migrated=new JSONObject()
                    .put("v",VERSION_ENCRYPTED)
                    .put("enc_secret",encSecret.ciphertext).put("enc_secret_nonce",encSecret.nonce)
                    .put("enc_lan",encLan.ciphertext).put("enc_lan_nonce",encLan.nonce)
                    .put("pairedAt",oldItem.optLong("pairedAt",System.currentTimeMillis()));
            items.put(phoneId,migrated);
            if(!context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                    .putString(KEY,items.toString()).commit()){
                removeUnsafeEntry(context,items,phoneId);
                return false;
            }
            Log.i(TAG,"migrated_plaintext_to_keystore");
            return true;
        }catch(Exception e){
            Log.w(TAG,"migration_failed: "+e.getClass().getSimpleName());
            removeUnsafeEntry(context,items,phoneId);
            return false;
        }
    }

    private static void removeUnsafeEntry(Context context,JSONObject items,String phoneId){
        items.remove(phoneId);
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString(KEY,items.toString()).commit();
    }
}

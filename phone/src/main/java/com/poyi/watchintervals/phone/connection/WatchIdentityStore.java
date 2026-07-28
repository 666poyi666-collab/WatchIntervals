package com.poyi.watchintervals.phone.connection;

import android.content.Context;
import android.content.SharedPreferences;
import com.poyi.watchintervals.phone.AndroidSecretStore;
import java.util.UUID;

public final class WatchIdentityStore {
    private final SharedPreferences values;
    public WatchIdentityStore(Context context){values=context.getSharedPreferences("watch_identity",Context.MODE_PRIVATE);}
    public String phoneDeviceId(){String id=values.getString("phone_device_id","");if(id.isEmpty()){id=UUID.randomUUID().toString();values.edit().putString("phone_device_id",id).commit();}return id;}
    public String watchDeviceId(){return values.getString("watch_device_id","");}
    public String pairingCode(){return secret("pairing_code");}
    public String pairingSecret(){return secret("pairing_secret");}
    public String lanCredential(){return secret("lan_credential");}
    public boolean isPaired(){return !watchDeviceId().isEmpty()&&!pairingSecret().isEmpty();}
    public void setPairingCode(String pairingCode){saveSecret("pairing_code",pairingCode);}
    public void saveLongTermPairing(String watchDeviceId,String pairingSecret,String lanCredential){
        try{
            AndroidSecretStore.EncryptedValue pairing=AndroidSecretStore.encrypt(pairingSecret,aad("pairing_secret"));
            SharedPreferences.Editor editor=values.edit().putString("watch_device_id",watchDeviceId==null?"":watchDeviceId)
                    .putString(ciphertext("pairing_secret"),pairing.ciphertext).putString(nonce("pairing_secret"),pairing.nonce)
                    .remove("pairing_secret").remove("lan_credential").remove("pairing_code")
                    .remove(ciphertext("pairing_code")).remove(nonce("pairing_code"));
            String cleanLan=lanCredential==null?"":lanCredential;
            if(cleanLan.isEmpty())editor.remove(ciphertext("lan_credential")).remove(nonce("lan_credential"));
            else{AndroidSecretStore.EncryptedValue lan=AndroidSecretStore.encrypt(cleanLan,aad("lan_credential"));editor.putString(ciphertext("lan_credential"),lan.ciphertext).putString(nonce("lan_credential"),lan.nonce);}
            if(!editor.commit())throw new IllegalStateException("pairing_identity_commit_failed");
        }catch(Exception error){throw new IllegalStateException("pairing_identity_encryption_failed",error);}
    }
    /** Legacy migration helper retained until all 0.19.0 debug installs have paired once. */
    public void savePairing(String watchDeviceId,String pairingCode){if(!values.edit().putString("watch_device_id",watchDeviceId==null?"":watchDeviceId).commit())throw new IllegalStateException("pairing_identity_commit_failed");setPairingCode(pairingCode);}
    public void clear(){String phone=phoneDeviceId();values.edit().clear().putString("phone_device_id",phone).commit();}
    private synchronized String secret(String name){String decrypted=AndroidSecretStore.decrypt(values.getString(ciphertext(name),""),values.getString(nonce(name),""),aad(name));if(decrypted!=null)return decrypted;String legacy=values.getString(name,"");if(legacy==null||legacy.isEmpty())return "";try{AndroidSecretStore.EncryptedValue encrypted=AndroidSecretStore.encrypt(legacy,aad(name));if(!values.edit().putString(ciphertext(name),encrypted.ciphertext).putString(nonce(name),encrypted.nonce).remove(name).commit())return "";return legacy;}catch(Exception failure){return "";}}
    private synchronized void saveSecret(String name,String value){String clean=value==null?"":value;if(clean.isEmpty()){if(!values.edit().remove(name).remove(ciphertext(name)).remove(nonce(name)).commit())throw new IllegalStateException("pairing_secret_commit_failed");return;}try{AndroidSecretStore.EncryptedValue encrypted=AndroidSecretStore.encrypt(clean,aad(name));if(!values.edit().putString(ciphertext(name),encrypted.ciphertext).putString(nonce(name),encrypted.nonce).remove(name).commit())throw new IllegalStateException("pairing_secret_commit_failed");}catch(Exception error){throw new IllegalStateException("pairing_secret_encryption_failed",error);}}
    private static String ciphertext(String name){return name+"_ciphertext";}
    private static String nonce(String name){return name+"_nonce";}
    private static String aad(String name){return "watch-identity-v1\0"+name;}
}

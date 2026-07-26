package com.poyi.watchintervals.phone.connection;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public final class WatchIdentityStore {
    private final SharedPreferences values;
    public WatchIdentityStore(Context context){values=context.getSharedPreferences("watch_identity",Context.MODE_PRIVATE);}
    public String phoneDeviceId(){String id=values.getString("phone_device_id","");if(id.isEmpty()){id=UUID.randomUUID().toString();values.edit().putString("phone_device_id",id).commit();}return id;}
    public String watchDeviceId(){return values.getString("watch_device_id","");}
    public String pairingCode(){return values.getString("pairing_code","");}
    public String pairingSecret(){return values.getString("pairing_secret","");}
    public String lanCredential(){return values.getString("lan_credential","");}
    public boolean isPaired(){return !watchDeviceId().isEmpty()&&!pairingSecret().isEmpty();}
    public void setPairingCode(String pairingCode){values.edit().putString("pairing_code",pairingCode==null?"":pairingCode).commit();}
    public void saveLongTermPairing(String watchDeviceId,String pairingSecret,String lanCredential){values.edit().putString("watch_device_id",watchDeviceId==null?"":watchDeviceId).putString("pairing_secret",pairingSecret==null?"":pairingSecret).putString("lan_credential",lanCredential==null?"":lanCredential).remove("pairing_code").commit();}
    /** Legacy migration helper retained until all 0.19.0 debug installs have paired once. */
    public void savePairing(String watchDeviceId,String pairingCode){values.edit().putString("watch_device_id",watchDeviceId==null?"":watchDeviceId).putString("pairing_code",pairingCode==null?"":pairingCode).commit();}
    public void clear(){String phone=phoneDeviceId();values.edit().clear().putString("phone_device_id",phone).commit();}
}

package com.poyi.watchintervals;

import android.content.Context;

final class WatchDeviceIdentity {
    private WatchDeviceIdentity() {}
    static String id(Context context){android.content.SharedPreferences p=context.getSharedPreferences("bridge",Context.MODE_PRIVATE);String id=p.getString("device_id","");if(id.isEmpty()){id=java.util.UUID.randomUUID().toString();p.edit().putString("device_id",id).apply();}return id;}
}

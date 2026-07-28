package com.poyi.watchintervals;

import android.content.Context;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Process-wide Baidu Maps initialization and local dark-style installation. */
final class BaiduMapRuntime {
    private static final String PLACEHOLDER_KEY = "TOKEN";
    private static final String STYLE_ASSET = "customconfig/baidu_map_dark.sty";
    private static boolean initialized;

    private BaiduMapRuntime() {}

    static synchronized boolean initialize(Context context) {
        if (initialized) return true;
        if (!isConfigured()) return false;
        Context application = context.getApplicationContext();
        SDKInitializer.setAgreePrivacy(application, true);
        SDKInitializer.setApiKey(BuildConfig.BAIDU_MAP_AK);
        SDKInitializer.setCoordType(CoordType.BD09LL);
        SDKInitializer.setHttpsEnable(true);
        SDKInitializer.initialize(application);
        initialized = true;
        return true;
    }

    static boolean isConfigured() {
        String key = BuildConfig.BAIDU_MAP_AK;
        return key != null && !key.isBlank() && !PLACEHOLDER_KEY.equals(key);
    }

    static String installDarkStyle(Context context) {
        File directory = new File(context.getFilesDir(), "map-style");
        File destination = new File(directory, "baidu-map-dark.sty");
        if (destination.isFile() && destination.length() > 0) {
            return destination.getAbsolutePath();
        }
        if (!directory.exists() && !directory.mkdirs()) return null;
        try (InputStream input = context.getAssets().open(STYLE_ASSET);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return destination.getAbsolutePath();
        } catch (IOException error) {
            return null;
        }
    }
}

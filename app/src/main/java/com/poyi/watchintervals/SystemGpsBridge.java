package com.poyi.watchintervals;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Optional bridge to the legacy MCU GPS state path used by the system sports app. */
final class SystemGpsBridge {
    private static final String TAG = "SystemGpsBridge";
    private static final int GPS_MODULE = 8;
    private static final int GPS_SWITCH_COMMAND = 5;
    private static final int GPS_SWITCH_EVENT = 6;
    private static final int GPS_LOCATE_EVENT = 7;

    interface Listener {
        void onSystemGpsState(boolean available, boolean located, int snr, String detail);
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private Object mcuManager;
    private Object dataListener;
    private Class<?> dataListenerClass;
    private boolean registered;
    private boolean gpsOpened;

    SystemGpsBridge(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void probe() {
        submit(() -> {
            try {
                Class<?> managerClass = Class.forName("android.app.wear.McuManager");
                dataListenerClass = Class.forName("android.app.wear.DataEventListener");
                managerClass.getMethod("GlobalInit", Context.class, int.class).invoke(null, context, 0);
                mcuManager = managerClass.getMethod("getInstance").invoke(null);
                if (mcuManager == null) throw new IllegalStateException("McuManager unavailable");
                dataListener = Proxy.newProxyInstance(dataListenerClass.getClassLoader(),
                        new Class<?>[]{dataListenerClass}, (proxy, method, args) -> {
                            String name = method.getName();
                            if ("toString".equals(name)) return "WatchIntervalsGpsDataListener";
                            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                            if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                            if ("onDataChanged".equals(name) && args != null && args.length > 0 && args[0] != null) {
                                onDataEvent(args[0]);
                            }
                            return null;
                        });
                Method register = mcuManager.getClass().getMethod(
                        "registerListener", dataListenerClass, int.class, int.class);
                boolean switchRegistered = Boolean.TRUE.equals(register.invoke(
                        mcuManager, dataListener, GPS_MODULE, GPS_SWITCH_EVENT));
                boolean locateRegistered = Boolean.TRUE.equals(register.invoke(
                        mcuManager, dataListener, GPS_MODULE, GPS_LOCATE_EVENT));
                registered = switchRegistered && locateRegistered;
                if (!registered) throw new IllegalStateException("MCU GPS listener registration rejected");
                postState(true, false, 0, "系统 GPS 状态已连接");
                Log.i(TAG, "Legacy system GPS listener registered");
            } catch (Throwable error) {
                Log.w(TAG, "Legacy system GPS path unavailable", rootCause(error));
                postState(false, false, 0, "系统 GPS 接口未开放");
            }
        });
    }

    void start() {
        submit(() -> {
            if (!registered || gpsOpened) return;
            try {
                // GpsSwitch: field 1 (switchState) = 1; field 2 defaults to outdoor run type 0.
                boolean sent = sendSwitch(new byte[]{0x08, 0x01});
                if (!sent) throw new IllegalStateException("MCU GPS start rejected");
                gpsOpened = true;
                postState(true, false, 0, "系统 GPS 正在定位");
                Log.i(TAG, "Legacy system GPS opened");
            } catch (Throwable error) {
                Log.w(TAG, "Unable to open legacy system GPS", rootCause(error));
                postState(false, false, 0, "系统 GPS 启动失败");
            }
        });
    }

    void stop() {
        submit(this::stopOnExecutor);
    }

    synchronized void close() {
        if (closed) return;
        closed = true;
        mainHandler.removeCallbacksAndMessages(null);
        try {
            executor.execute(() -> {
                stopOnExecutor();
                unregisterOnExecutor();
            });
        } catch (RejectedExecutionException ignored) {
            registered = false;
            gpsOpened = false;
        }
        executor.shutdown();
    }

    private void stopOnExecutor() {
        if (!gpsOpened || mcuManager == null) return;
        try {
            // Default GpsSwitch message: switchState = 0, outdoor run type = 0.
            sendSwitch(new byte[0]);
            Log.i(TAG, "Legacy system GPS closed");
        } catch (Throwable error) {
            Log.w(TAG, "Unable to close legacy system GPS", rootCause(error));
        }
        gpsOpened = false;
    }

    private boolean sendSwitch(byte[] payload) throws Exception {
        Method send = mcuManager.getClass().getMethod(
                "sendMessageToMcu", int.class, int.class, byte[].class);
        return Boolean.TRUE.equals(send.invoke(mcuManager, GPS_MODULE, GPS_SWITCH_COMMAND, payload));
    }

    private void unregisterOnExecutor() {
        if (!registered || mcuManager == null || dataListener == null) return;
        try {
            Method unregister = mcuManager.getClass().getMethod(
                    "unregisterListener", dataListenerClass, int.class, int.class);
            unregister.invoke(mcuManager, dataListener, GPS_MODULE, GPS_SWITCH_EVENT);
            unregister.invoke(mcuManager, dataListener, GPS_MODULE, GPS_LOCATE_EVENT);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to unregister legacy system GPS listener", rootCause(error));
        }
        registered = false;
        dataListener = null;
        mcuManager = null;
    }

    private void onDataEvent(Object event) {
        try {
            int classId = readIntField(event, "classId");
            int msgId = readIntField(event, "msgId");
            if (classId != GPS_MODULE || msgId != GPS_LOCATE_EVENT) return;
            Field valuesField = event.getClass().getField("values");
            byte[] values = (byte[]) valuesField.get(event);
            int[] parsed = parseGpsLocateData(values);
            int snr = parsed[0];
            boolean located = parsed[1] == 1;
            String detail = located ? "系统 GPS 已定位" : snr > 0 ? "系统 GPS 信号 " + snr : "系统 GPS 正在定位";
            postState(true, located, snr, detail);
            Log.i(TAG, "MCU GPS located=" + located + " snr=" + snr);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to parse MCU GPS state", rootCause(error));
        }
    }

    private int readIntField(Object target, String name) throws Exception {
        return target.getClass().getField(name).getInt(target);
    }

    /** Parses protobuf varint fields: 1=gpsSnr, 2=gpsLocate. */
    private int[] parseGpsLocateData(byte[] values) {
        int[] result = new int[]{0, 0};
        if (values == null) return result;
        int index = 0;
        while (index < values.length) {
            int[] tag = readVarint(values, index);
            if (tag == null) break;
            index = tag[1];
            int field = tag[0] >>> 3;
            int wire = tag[0] & 7;
            if (wire != 0) break;
            int[] value = readVarint(values, index);
            if (value == null) break;
            index = value[1];
            if (field == 1) result[0] = value[0];
            else if (field == 2) result[1] = value[0];
        }
        return result;
    }

    private int[] readVarint(byte[] data, int offset) {
        int value = 0;
        int shift = 0;
        for (int index = offset; index < data.length && shift < 32; index++, shift += 7) {
            int current = data[index] & 0xff;
            value |= (current & 0x7f) << shift;
            if ((current & 0x80) == 0) return new int[]{value, index + 1};
        }
        return null;
    }

    private void submit(Runnable operation) {
        if (closed) return;
        try {
            executor.execute(() -> {
                if (!closed) operation.run();
            });
        } catch (RejectedExecutionException ignored) {
            Log.d(TAG, "Ignoring operation after shutdown");
        }
    }

    private void postState(boolean available, boolean located, int snr, String detail) {
        if (closed) return;
        mainHandler.post(() -> {
            if (!closed) listener.onSystemGpsState(available, located, snr, detail);
        });
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }
}

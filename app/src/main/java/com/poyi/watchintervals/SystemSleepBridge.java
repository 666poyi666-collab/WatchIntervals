package com.poyi.watchintervals;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Read-only adapter for the HealthKit store shipped with supported watches. */
final class SystemSleepBridge {
    private static final String TAG = "SystemSleepBridge";
    private static final String HEALTH_PACKAGE = "com.heytap.wearable.health";
    private static final String STORE_ACTION = "heytap.wearable.intent.action.BIND_STORE_SERVICE";
    private static final String STORE_DESCRIPTOR = "com.oplus.wearable.healthkit.store.IStoreApiService";
    private static final String CALLBACK_DESCRIPTOR = "com.oplus.wearable.healthkit.store.IReadRecordsCallback";
    private static final String PERMISSION_ACTION = "heytap.wearable.intent.action.health.ACTION_REQUEST_PERMISSIONS";
    private static final String RECORD_TYPE = "SleepSessionRecord";
    private static final int TRANSACTION_READ_RECORDS = 5;

    private final Context context;
    private final Object connectionLock = new Object();
    private volatile IBinder store;
    private volatile ClassLoader healthLoader;
    private CountDownLatch connectionLatch;
    private boolean binding;

    SystemSleepBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    static boolean requestPermission(Activity activity, int requestCode) {
        try {
            ClassLoader loader = healthLoader(activity);
            Class<?> permissionProto = Class.forName(
                    "com.oplus.wearable.healthkit.proto.StoreProto$Permission", true, loader);
            Object permissionBuilder = permissionProto.getMethod("newBuilder").invoke(null);
            invoke(permissionBuilder, "setDataType", String.class, RECORD_TYPE);
            invoke(permissionBuilder, "setAccessType", int.class, 1);
            Object permission = permissionBuilder.getClass().getMethod("build").invoke(permissionBuilder);

            Class<?> permissionsProto = Class.forName(
                    "com.oplus.wearable.healthkit.proto.StoreProto$Permissions", true, loader);
            Object permissionsBuilder = permissionsProto.getMethod("newBuilder").invoke(null);
            invoke(permissionsBuilder, "addPermissions", permissionProto, permission);
            Object permissions = permissionsBuilder.getClass().getMethod("build").invoke(permissionsBuilder);
            byte[] data = (byte[]) permissions.getClass().getMethod("toByteArray").invoke(permissions);

            Intent intent = new Intent(PERMISSION_ACTION).setPackage(HEALTH_PACKAGE)
                    .putExtra("EXTRA_DATA", data);
            activity.startActivityForResult(intent, requestCode);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "Unable to open system sleep permission screen", error);
            return false;
        }
    }

    JSONObject read(int requestedDays) {
        return read(requestedDays, 0);
    }

    JSONObject read(int requestedDays, int requestedOffsetDays) {
        int days = Math.max(1, Math.min(requestedDays, 31));
        int offsetDays = Math.max(0, Math.min(requestedOffsetDays, 365));
        long endSeconds = System.currentTimeMillis() / 1000L + 86_400L
                - offsetDays * 86_400L;
        long startSeconds = endSeconds - days * 86_400L;
        try {
            IBinder binder = awaitStore();
            ClassLoader loader = loader();
            ArrayList<JSONObject> records = new ArrayList<>();
            boolean complete = collectRange(binder, loader, startSeconds, endSeconds,
                    days, records, 0);
            Map<String, JSONObject> unique = new LinkedHashMap<>();
            for (JSONObject record : records) unique.put(recordKey(record), record);
            ArrayList<JSONObject> sorted = new ArrayList<>(unique.values());
            sorted.sort(Comparator.comparingLong((JSONObject item) ->
                    item.optLong("timestamp")).reversed());
            JSONArray output = new JSONArray();
            for (JSONObject item : sorted) output.put(item);
            return envelope("ready", days)
                    .put("offsetDays", offsetDays)
                    .put("complete", complete)
                    .put("hasMore", !complete)
                    .put("coverageStart", startSeconds * 1000L)
                    .put("coverageEnd", endSeconds * 1000L)
                    .put("recordCount", output.length())
                    .put("records", output);
        } catch (SleepReadFailure failure) {
            JSONObject result = envelope(failure.state, days);
            try { result.put("offsetDays", offsetDays).put("complete", false); }
            catch (Exception ignored) {}
            return withError(result, failure.getMessage());
        } catch (Throwable error) {
            Log.w(TAG, "System sleep read failed", error);
            JSONObject result = envelope("error", days);
            try { result.put("offsetDays", offsetDays).put("complete", false); }
            catch (Exception ignored) {}
            return withError(result, rootMessage(error));
        }
    }

    private boolean collectRange(IBinder binder, ClassLoader loader, long start, long end,
            int days, List<JSONObject> output, int depth) throws Exception {
        JSONObject page = readRange(binder, loader, start, end, days);
        String state = page.optString("state");
        if (!"ready".equals(state)) {
            throw new SleepReadFailure(state.isEmpty() ? "error" : state,
                    page.optString("error", "sleep range unavailable"));
        }
        boolean hasMore = page.optBoolean("hasMore", false);
        // HealthKit exposes hasMore but no cursor. Bisect the time window until every response is
        // complete, which keeps each binder/BLE payload bounded without dropping older records.
        if (SleepReadWindowPolicy.shouldSplit(hasMore, depth, end - start)) {
            long middle = start + (end - start) / 2L;
            boolean first = collectRange(binder, loader, start, middle, days, output,
                    depth + 1);
            boolean second = collectRange(binder, loader, middle, end, days, output,
                    depth + 1);
            return first && second;
        }
        JSONArray records = page.optJSONArray("records");
        if (records != null) for (int index = 0; index < records.length(); index++) {
            JSONObject record = records.optJSONObject(index);
            if (record != null) output.add(record);
        }
        return !hasMore;
    }

    private JSONObject readRange(IBinder binder, ClassLoader loader, long start, long end,
            int days) throws Exception {
        byte[] request = buildReadRequest(loader, start, end);
        ReadCallback callback = new ReadCallback(loader);
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(STORE_DESCRIPTOR);
            data.writeInt(1);
            data.writeByteArray(request);
            data.writeStrongBinder(callback);
            if (!binder.transact(TRANSACTION_READ_RECORDS, data, reply, 0)) {
                throw new IllegalStateException("sleep store transaction rejected");
            }
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
        if (!callback.done.await(8, TimeUnit.SECONDS)) {
            throw new IllegalStateException("sleep store response timed out");
        }
        if (callback.error != null) {
            String state = callback.error.contains("permission")
                    ? "permission_required" : "error";
            return withError(envelope(state, days), callback.error);
        }
        return parseResponse(loader, callback.responseBytes, days);
    }

    private String recordKey(JSONObject record) {
        long timestamp = Math.max(0L, record.optLong("timestamp"));
        return timestamp > 0L ? "time:" + timestamp : "raw:" + record.toString();
    }

    private JSONObject parseResponse(ClassLoader loader, byte[] responseBytes, int days) throws Exception {
        if (responseBytes == null) return envelope("error", days).put("error", "empty sleep response");
        Object response = parse(loader,
                "com.oplus.wearable.healthkit.proto.StoreProto$ReadRecordsResponse", responseBytes);
        Object recordsBytes = response.getClass().getMethod("getRecords").invoke(response);
        byte[] records = (byte[]) recordsBytes.getClass().getMethod("toByteArray").invoke(recordsBytes);
        Object sleepRecords = parse(loader,
                "com.oplus.wearable.healthkit.proto.SleepProto$SleepRecords", records);
        List<?> items = (List<?>) sleepRecords.getClass().getMethod("getItemsList").invoke(sleepRecords);
        ArrayList<JSONObject> sorted = new ArrayList<>();
        for (Object item : items) sorted.add(recordJson(item));
        sorted.sort(Comparator.comparingLong((JSONObject item) -> item.optLong("timestamp")).reversed());
        JSONArray output = new JSONArray();
        for (JSONObject item : sorted) output.put(item);
        return envelope("ready", days)
                .put("hasMore", bool(response, "getHasMore"))
                .put("recordCount", output.length())
                .put("records", output);
    }

    private JSONObject recordJson(Object record) throws Exception {
        JSONObject json = new JSONObject()
                .put("timestamp", epochMillis(time(record, "getTimestamp2", "getTimestamp")))
                .put("totalDurationMinutes", number(record, "getTotalDuration").intValue())
                .put("sleepScore", number(record, "getSleepScore").intValue())
                .put("spo2AveragePercent", number(record, "getSpo2Avg").intValue())
                .put("osaResult", number(record, "getOsaResult").intValue())
                .put("heartRateBenchmarkBpm", number(record, "getHeartRateBenchmark").intValue())
                .put("breathRateBenchmarkPerMinute", number(record, "getBreathRateBenchmark").doubleValue());
        json.put("heartRateRangeBpm", range(record, "getHeartRateRange"));
        json.put("breathRateRangePerMinute", range(record, "getBreathRateRange"));
        JSONArray sessions = new JSONArray();
        for (Object session : list(record, "getSessionsList")) sessions.put(sessionJson(session));
        json.put("sessions", sessions);
        return json;
    }

    private JSONObject sessionJson(Object session) throws Exception {
        JSONObject json = new JSONObject()
                .put("startTime", epochMillis(time(session, "getStartTime2", "getStartTime")))
                .put("endTime", epochMillis(time(session, "getEndTime2", "getEndTime")))
                .put("sleepDurationMinutes", number(session, "getSleepDuration").intValue())
                .put("deepDurationMinutes", number(session, "getDeepDuration").intValue())
                .put("lightDurationMinutes", number(session, "getLightDuration").intValue())
                .put("remDurationMinutes", number(session, "getRemDuration").intValue())
                .put("awakeDurationMinutes", number(session, "getAwakeDuration").intValue());
        JSONArray stages = new JSONArray();
        for (Object stage : list(session, "getStagesList")) {
            int type = number(stage, "getStageType").intValue();
            stages.put(new JSONObject()
                    .put("type", type)
                    .put("label", "system_" + type)
                    .put("startTime", epochMillis(time(stage, "getStartTime2", "getStartTime")))
                    .put("endTime", epochMillis(time(stage, "getEndTime2", "getEndTime"))));
        }
        return json.put("stages", stages);
    }

    private JSONObject range(Object parent, String getter) throws Exception {
        Object range = parent.getClass().getMethod(getter).invoke(parent);
        return new JSONObject().put("minimum", number(range, "getStart"))
                .put("maximum", number(range, "getEnd"));
    }

    private byte[] buildReadRequest(ClassLoader loader, long start, long end) throws Exception {
        Class<?> requestProto = Class.forName(
                "com.oplus.wearable.healthkit.proto.StoreProto$ReadRecordsRequest", true, loader);
        Object builder = requestProto.getMethod("newBuilder").invoke(null);
        invoke(builder, "setType", String.class, RECORD_TYPE);
        invoke(builder, "setStartTime", long.class, start);
        invoke(builder, "setEndTime", long.class, end);
        invoke(builder, "setAscOrdering", boolean.class, false);
        invoke(builder, "setPageSize", int.class, 64);
        Object proto = builder.getClass().getMethod("build").invoke(builder);
        return (byte[]) proto.getClass().getMethod("toByteArray").invoke(proto);
    }

    private IBinder awaitStore() throws Exception {
        if (store != null && store.isBinderAlive()) return store;
        CountDownLatch latch;
        synchronized (connectionLock) {
            if (store != null && store.isBinderAlive()) return store;
            if (!binding) {
                binding = true;
                connectionLatch = new CountDownLatch(1);
                Intent intent = new Intent(STORE_ACTION).setPackage(HEALTH_PACKAGE);
                if (!context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
                    binding = false;
                    throw new IllegalStateException("system sleep store is unavailable");
                }
            }
            latch = connectionLatch;
        }
        if (latch == null || !latch.await(5, TimeUnit.SECONDS) || store == null) {
            throw new IllegalStateException("system sleep store connection timed out");
        }
        return store;
    }

    private ClassLoader loader() throws Exception {
        if (healthLoader == null) healthLoader = healthLoader(context);
        return healthLoader;
    }

    void close() {
        synchronized (connectionLock) {
            if (binding || store != null) {
                try { context.unbindService(serviceConnection); } catch (Exception ignored) {}
            }
            binding = false;
            store = null;
            healthLoader = null;
        }
    }

    private static ClassLoader healthLoader(Context context) throws Exception {
        return context.createPackageContext(HEALTH_PACKAGE,
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY).getClassLoader();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            store = service;
            synchronized (connectionLock) {
                binding = false;
                if (connectionLatch != null) connectionLatch.countDown();
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            store = null;
        }
    };

    private static final class ReadCallback extends Binder {
        final CountDownLatch done = new CountDownLatch(1);
        final ClassLoader loader;
        volatile byte[] responseBytes;
        volatile String error;

        ReadCallback(ClassLoader loader) {
            this.loader = loader;
            attachInterface(null, CALLBACK_DESCRIPTOR);
        }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(CALLBACK_DESCRIPTOR);
                return true;
            }
            data.enforceInterface(CALLBACK_DESCRIPTOR);
            if (code == 1) {
                if (data.readInt() != 0) responseBytes = data.createByteArray();
                reply.writeNoException();
                done.countDown();
                return true;
            }
            if (code == 2) {
                error = data.readString();
                reply.writeNoException();
                done.countDown();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    private static final class SleepReadFailure extends Exception {
        final String state;
        SleepReadFailure(String state, String message) {
            super(message);
            this.state = state;
        }
    }

    private static JSONObject envelope(String state, int days) {
        JSONObject result = new JSONObject();
        try {
            result.put("state", state).put("source", "system_healthkit")
                    .put("recordType", RECORD_TYPE).put("requestedDays", days)
                    .put("fetchedAt", System.currentTimeMillis()).put("records", new JSONArray());
        } catch (Exception ignored) {}
        return result;
    }

    private static JSONObject withError(JSONObject result, String error) {
        try { result.put("error", error); } catch (Exception ignored) {}
        return result;
    }

    private static Object parse(ClassLoader loader, String name, byte[] bytes) throws Exception {
        return Class.forName(name, true, loader).getMethod("parseFrom", byte[].class)
                .invoke(null, (Object) bytes);
    }

    private static void invoke(Object target, String name, Class<?> type, Object value) throws Exception {
        target.getClass().getMethod(name, type).invoke(target, value);
    }

    private static Number number(Object target, String getter) throws Exception {
        return (Number) target.getClass().getMethod(getter).invoke(target);
    }

    private static boolean bool(Object target, String getter) throws Exception {
        return (Boolean) target.getClass().getMethod(getter).invoke(target);
    }

    private static List<?> list(Object target, String getter) throws Exception {
        return (List<?>) target.getClass().getMethod(getter).invoke(target);
    }

    private static long time(Object target, String longGetter, String legacyGetter) throws Exception {
        long value = number(target, longGetter).longValue();
        return value != 0 ? value : number(target, legacyGetter).longValue();
    }

    private static long epochMillis(long value) {
        return value > 10_000_000_000L ? value : value * 1000L;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}

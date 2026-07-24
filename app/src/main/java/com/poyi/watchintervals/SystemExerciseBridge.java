package com.poyi.watchintervals;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** Feature-detected bridge to the exercise client shipped with the watch health service. */
final class SystemExerciseBridge {
    private static final String TAG = "SystemExerciseBridge";
    private static final String HEALTH_PACKAGE = "com.heytap.wearable.health";
    private static final int OUTDOOR_RUN = 0x2714;

    enum State { PROBING, UNAVAILABLE, READY, REGISTERED, PREPARING, ACTIVE, PAUSED, ERROR }

    interface Listener {
        void onSystemExerciseState(State state, String detail);
        void onSystemExerciseMetrics(Metrics metrics);
    }

    static final class Metrics {
        double distanceTotalMeters = -1d;
        double distanceSampleMeters = -1d;
        double latitude = Double.NaN;
        double longitude = Double.NaN;
        float locationAccuracyMeters = -1f;
        int gpsSatelliteCount = -1;
        int heartRate = 0;
        int stepsTotal = -1;
        String exerciseState = "";
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private ClassLoader healthClassLoader;
    private Object exerciseClient;
    private Object updateCallback;
    private Class<?> updateCallbackClass;
    private volatile String unsupportedReason;
    private boolean nativeSessionOpen;

    SystemExerciseBridge(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void probe() {
        postState(State.PROBING, "正在连接系统运动");
        submit(() -> {
            try {
                ensureClient();
                postState(State.READY, "系统运动可用");
            } catch (Throwable error) {
                postFailure(error);
            }
        });
    }

    void prepare() {
        submit(() -> {
            if (unsupportedReason != null) return;
            try {
                ensureClient();
                ensureUpdateCallback();
                Object configuration = buildWarmUpConfiguration();
                await("prepare", invokeOneArgument("prepareExerciseAsync", configuration));
                nativeSessionOpen = true;
                postState(State.PREPARING, "系统运动预热中");
            } catch (Throwable error) {
                postFailure(error);
            }
        });
    }

    void start() {
        submit(() -> {
            if (unsupportedReason != null) return;
            try {
                ensureClient();
                ensureUpdateCallback();
                Object configuration = buildExerciseConfiguration();
                await("start", invokeOneArgument("startExerciseAsync", configuration));
                nativeSessionOpen = true;
                postState(State.ACTIVE, "系统运动记录中");
            } catch (Throwable error) {
                postFailure(error);
            }
        });
    }

    void pause() { invokeControl("pauseExerciseAsync", State.PAUSED, "系统运动已暂停"); }
    void resume() { invokeControl("resumeExerciseAsync", State.ACTIVE, "系统运动记录中"); }
    void end() { invokeControl("endExerciseAsync", State.READY, "系统运动已结束"); }

    synchronized void close() {
        if (closed) return;
        closed = true;
        mainHandler.removeCallbacksAndMessages(null);
        try {
            executor.execute(() -> {
                if (exerciseClient != null && nativeSessionOpen) {
                    try {
                        await("end on close", exerciseClient.getClass().getMethod("endExerciseAsync").invoke(exerciseClient));
                    } catch (Throwable error) {
                        Log.w(TAG, "Unable to end system exercise while closing", error);
                    }
                    nativeSessionOpen = false;
                }
                if (exerciseClient != null && updateCallback != null) {
                    try { await("clear callback", invokeOneArgument("clearUpdateCallbackAsync", updateCallback)); }
                    catch (Throwable error) { Log.w(TAG, "Unable to clear system exercise callback", error); }
                }
                exerciseClient = null;
                updateCallback = null;
                healthClassLoader = null;
            });
        } catch (RejectedExecutionException ignored) {
            exerciseClient = null;
            updateCallback = null;
            healthClassLoader = null;
        }
        executor.shutdown();
    }

    private void invokeControl(String methodName, State state, String detail) {
        if (closed || unsupportedReason != null) return;
        submit(() -> {
            try {
                ensureClient();
                Object future = exerciseClient.getClass().getMethod(methodName).invoke(exerciseClient);
                await(methodName, future);
                if ("endExerciseAsync".equals(methodName)) nativeSessionOpen = false;
                postState(state, detail);
            } catch (Throwable error) {
                postFailure(error);
            }
        });
    }

    private void submit(Runnable operation) {
        if (closed) return;
        try {
            executor.execute(() -> {
                if (!closed) operation.run();
            });
        } catch (RejectedExecutionException ignored) {
            Log.d(TAG, "Ignoring operation after bridge shutdown");
        }
    }

    private void ensureClient() throws Exception {
        if (exerciseClient != null) return;
        if (closed) throw new IllegalStateException("bridge closed");
        if (unsupportedReason != null) throw new UnsupportedOperationException(unsupportedReason);
        Context healthContext = context.createPackageContext(
                HEALTH_PACKAGE, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        healthClassLoader = healthContext.getClassLoader();
        Class<?> factoryClass = Class.forName(
                "com.oplus.wearable.healthkit.impl.ServiceBackedHealthKit$exerciseClient$2",
                true, healthClassLoader);
        Constructor<?> factoryConstructor = factoryClass.getDeclaredConstructor(Context.class);
        factoryConstructor.setAccessible(true);
        Object factory = factoryConstructor.newInstance(context);
        Method invoke = factoryClass.getDeclaredMethod("invoke");
        invoke.setAccessible(true);
        exerciseClient = invoke.invoke(factory);
        boolean available = (Boolean) exerciseClient.getClass().getMethod("isAvailable").invoke(exerciseClient);
        if (!available) {
            exerciseClient = null;
            throw new IllegalStateException("system exercise API version is unavailable");
        }
        Object future = exerciseClient.getClass().getMethod("getCapabilitiesAsync").invoke(exerciseClient);
        Object capabilities = await("capabilities", future);
        if (!supportsOutdoorRun(capabilities)) {
            exerciseClient = null;
            unsupportedReason = "firmware does not expose OUTDOOR_RUN through HealthKit";
            throw new UnsupportedOperationException(unsupportedReason);
        }
    }

    private void ensureUpdateCallback() throws Exception {
        if (updateCallback != null) return;
        updateCallbackClass = Class.forName(
                "com.oplus.wearable.healthkit.exercise.ExerciseUpdateCallback", true, healthClassLoader);
        updateCallback = Proxy.newProxyInstance(healthClassLoader, new Class<?>[]{updateCallbackClass},
                (proxy, method, arguments) -> {
                    String name = method.getName();
                    if ("toString".equals(name)) return "WatchIntervalsSystemExerciseCallback";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) return proxy == (arguments == null ? null : arguments[0]);
                    if ("onRegistered".equals(name)) {
                        postState(State.REGISTERED, "系统运动数据已连接");
                    } else if ("onRegistrationFailed".equals(name)) {
                        Throwable error = arguments != null && arguments.length > 0 && arguments[0] instanceof Throwable
                                ? (Throwable) arguments[0] : new IllegalStateException("listener registration failed");
                        postFailure(error);
                    } else if ("onExerciseUpdateReceived".equals(name)
                            && arguments != null && arguments.length > 0 && arguments[0] != null) {
                        Metrics metrics = readMetrics(arguments[0]);
                        if (!closed) mainHandler.post(() -> {
                            if (!closed) listener.onSystemExerciseMetrics(metrics);
                        });
                    }
                    return null;
                });
        exerciseClient.getClass().getMethod("setUpdateCallback", updateCallbackClass)
                .invoke(exerciseClient, updateCallback);
    }

    private Object buildWarmUpConfiguration() throws Exception {
        Collection<Object> sampleTypes = dataTypeProtos("v7.o",
                Set.of("Distance", "Heart Rate", "steps", "Location", "GPS satellite num"));
        Class<?> warmUpProto = Class.forName(
                "com.oplus.wearable.healthkit.proto.ExerciseProto$WarmUpConfig", true, healthClassLoader);
        Object builder = warmUpProto.getMethod("newBuilder").invoke(null);
        setExerciseType(builder);
        builder.getClass().getMethod("addAllDataTypes", Iterable.class).invoke(builder, sampleTypes);
        Object proto = builder.getClass().getMethod("build").invoke(builder);
        Class<?> configClass = Class.forName("w7.q", true, healthClassLoader);
        return configClass.getConstructor(warmUpProto).newInstance(proto);
    }

    private Object buildExerciseConfiguration() throws Exception {
        Collection<Object> sampleTypes = dataTypeProtos("v7.o",
                Set.of("Active Duration", "Distance", "Heart Rate", "steps", "Location", "Pace",
                        "GPS satellite num"));
        Collection<Object> statsTypes = dataTypeProtos("v7.q",
                Set.of("Distance Total", "Active Duration", "Steps Total", "Avg Heart Rate Stats"));
        Class<?> exerciseProto = Class.forName(
                "com.oplus.wearable.healthkit.proto.ExerciseProto$ExerciseConfig", true, healthClassLoader);
        Object builder = exerciseProto.getMethod("newBuilder").invoke(null);
        setExerciseType(builder);
        builder.getClass().getMethod("addAllSampleDataTypes", Iterable.class).invoke(builder, sampleTypes);
        builder.getClass().getMethod("addAllStatsDataTypes", Iterable.class).invoke(builder, statsTypes);
        builder.getClass().getMethod("setIsAutoPauseAndResumeEnabled", boolean.class).invoke(builder, false);
        Object proto = builder.getClass().getMethod("build").invoke(builder);
        Class<?> configClass = Class.forName("w7.c", true, healthClassLoader);
        return configClass.getConstructor(exerciseProto).newInstance(proto);
    }

    private void setExerciseType(Object builder) throws Exception {
        Class<?> exerciseType = Class.forName(
                "com.oplus.wearable.healthkit.proto.ExerciseProto$ExerciseType", true, healthClassLoader);
        Object outdoorRun = exerciseType.getMethod("forNumber", int.class).invoke(null, OUTDOOR_RUN);
        builder.getClass().getMethod("setExerciseType", exerciseType).invoke(builder, outdoorRun);
    }

    private Collection<Object> dataTypeProtos(String className, Set<String> requestedNames) throws Exception {
        Class<?> dataTypeClass = Class.forName(className, true, healthClassLoader);
        Collection<?> knownTypes = findStaticCollection(dataTypeClass, requestedNames);
        List<Object> result = new ArrayList<>();
        Set<String> found = new HashSet<>();
        for (Object type : knownTypes) {
            String name = String.valueOf(readField(type, "a"));
            if (requestedNames.contains(name)) {
                result.add(readField(type, "d"));
                found.add(name);
            }
        }
        if (!found.containsAll(requestedNames)) {
            Set<String> missing = new HashSet<>(requestedNames);
            missing.removeAll(found);
            Log.w(TAG, "System exercise data types missing: " + missing);
        }
        if (result.isEmpty()) throw new IllegalStateException("no compatible system exercise data types");
        return result;
    }

    private Collection<?> findStaticCollection(Class<?> type, Set<String> requestedNames) throws Exception {
        Collection<?> best = null;
        int bestMatches = -1;
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Collection.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof Collection<?>)) continue;
            int matches = 0;
            boolean structurallyValid = true;
            for (Object item : (Collection<?>) value) {
                if (item == null) { structurallyValid = false; break; }
                try {
                    Object name = readField(item, "a");
                    Object proto = readField(item, "d");
                    if (!(name instanceof String) || proto == null) { structurallyValid = false; break; }
                    if (requestedNames.contains(name)) matches++;
                } catch (Exception error) {
                    structurallyValid = false;
                    break;
                }
            }
            if (structurallyValid && matches > bestMatches) {
                best = (Collection<?>) value;
                bestMatches = matches;
            }
        }
        if (best == null || bestMatches <= 0) {
            throw new NoSuchFieldException(type.getName() + " compatible data type collection");
        }
        return best;
    }

    private Metrics readMetrics(Object exerciseUpdate) {
        Metrics metrics = new Metrics();
        try {
            Object stateInfo = readField(exerciseUpdate, "c");
            Object state = readField(stateInfo, "b");
            metrics.exerciseState = String.valueOf(state);
            readDataPoints(readField(exerciseUpdate, "a"), "a", metrics, false);
            Object stats = readField(exerciseUpdate, "b");
            if (stats != null) readDataPoints(stats, "b", metrics, true);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to read system exercise update", error);
        }
        return metrics;
    }

    private void readDataPoints(Object container, String listMethod, Metrics metrics, boolean statistics)
            throws Exception {
        Method list = container.getClass().getDeclaredMethod(listMethod);
        list.setAccessible(true);
        for (Object point : (List<?>) list.invoke(container)) {
            Method dataTypeMethod = findMethod(point.getClass(), "a");
            Object dataType = dataTypeMethod.invoke(point);
            String name = String.valueOf(readField(dataType, "a"));
            Object value = readField(point, statistics ? "b" : "c");
            if (!statistics && "Location".equals(name)) {
                readLocation(value, point, metrics);
                continue;
            }
            if (!(value instanceof Number)) continue;
            Number number = (Number) value;
            if (statistics && "Distance Total".equals(name)) metrics.distanceTotalMeters = number.doubleValue();
            else if (!statistics && "Distance".equals(name)) metrics.distanceSampleMeters = number.doubleValue();
            else if (!statistics && "Heart Rate".equals(name)) metrics.heartRate = (int) Math.round(number.doubleValue());
            else if (!statistics && "GPS satellite num".equals(name)) metrics.gpsSatelliteCount = number.intValue();
            else if (statistics && "Steps Total".equals(name)) metrics.stepsTotal = number.intValue();
        }
    }

    private void readLocation(Object value, Object point, Metrics metrics) {
        if (value == null) return;
        try {
            metrics.latitude = ((Number) readField(value, "a")).doubleValue();
            metrics.longitude = ((Number) readField(value, "b")).doubleValue();
            Object accuracy = readField(point, "f");
            if (accuracy != null) {
                Object horizontal = readField(accuracy, "a");
                if (horizontal instanceof Number) metrics.locationAccuracyMeters = ((Number) horizontal).floatValue();
            }
        } catch (Throwable error) {
            Log.d(TAG, "System location shape differs from this firmware", error);
        }
    }

    private Object invokeOneArgument(String methodName, Object argument) throws Exception {
        Method method = null;
        for (Method candidate : exerciseClient.getClass().getMethods()) {
            if (candidate.getName().equals(methodName) && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInstance(argument)) {
                method = candidate;
                break;
            }
        }
        if (method == null) throw new NoSuchMethodException(methodName);
        return method.invoke(exerciseClient, argument);
    }

    private Object await(String operation, Object result) throws Exception {
        if (!(result instanceof Future<?>)) return result;
        try {
            Object value = ((Future<?>) result).get(15, TimeUnit.SECONDS);
            Log.i(TAG, "System exercise operation succeeded: " + operation);
            return value;
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            throw new IllegalStateException(operation + " failed: " + cause.getMessage(), cause);
        }
    }

    private boolean supportsOutdoorRun(Object capabilities) throws Exception {
        if (capabilities == null) return false;
        Object fieldValue = readField(capabilities, "a");
        if (!(fieldValue instanceof Map<?, ?>)) return false;
        Map<?, ?> typeCapabilities = (Map<?, ?>) fieldValue;
        Log.i(TAG, "System exercise capabilities: " + typeCapabilities.keySet());
        if (typeCapabilities.isEmpty()) {
            Log.i(TAG, "OUTDOOR_RUN is not exposed by this firmware");
            return false;
        }
        for (Object exerciseType : typeCapabilities.keySet()) {
            if (exerciseType == null) continue;
            if ("OUTDOOR_RUN".equals(String.valueOf(exerciseType))) return true;
            try {
                Object typeNumber = readField(exerciseType, "a");
                if (typeNumber instanceof Number && ((Number) typeNumber).intValue() == OUTDOOR_RUN) return true;
            } catch (ReflectiveOperationException ignored) {}
        }
        return false;
    }

    private Method findMethod(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private Object readField(Object target, String name) throws Exception {
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    private void postFailure(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        Log.w(TAG, "System exercise bridge unavailable", cause);
        postState(cause instanceof ClassNotFoundException || cause instanceof UnsupportedOperationException
                        ? State.UNAVAILABLE : State.ERROR,
                cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
    }

    private void postState(State state, String detail) {
        if (closed) return;
        mainHandler.post(() -> {
            if (!closed) listener.onSystemExerciseState(state, detail);
        });
    }
}

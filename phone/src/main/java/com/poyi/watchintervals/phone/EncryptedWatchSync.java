package com.poyi.watchintervals.phone;

import android.content.Context;
import android.content.SharedPreferences;
import com.poyi.watchintervals.phone.connection.WatchConnectionManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Device-owned SyncEnvelopeV1 client for WatchIntervals.
 *
 * <p>The phone keeps the local entity state, durable outbox, conflict copies and cursor in one
 * SharedPreferences document. Each stage, lease and acknowledgement/materialization transition is
 * committed as one file update. Cloud requests contain only AES-256-GCM ciphertext plus envelope
 * metadata; raw route, heart, sleep and credential data are intentionally never collected here.
 */
final class EncryptedWatchSync {
    static final int PROTOCOL_VERSION = 2;
    static final int ENVELOPE_VERSION = 1;
    static final String PRODUCT = "watch";
    static final String PLAN_LIBRARY_ENTITY_ID = "sync:library";
    private static final String PREFS = "encrypted_watch_sync_v1";
    private static final String STATE = "state";
    private static final int MAX_MUTATIONS = 25;
    private static final int MAX_PAGES = 100;
    private static final long LEASE_MILLIS = 45_000L;
    private static final int MAX_RESPONSE_BYTES = 1_200_000;
    private static final int MAX_REQUEST_BYTES = 900_000;
    private static final int MAX_PLAINTEXT_BYTES = 500_000;
    private static final int MAX_CIPHERTEXT_CHARS = 900_000;
    private static final int MAX_CONFLICTS = 1_000;
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private EncryptedWatchSync() {}

    static void syncAsync(Context context) {
        Context app = context.getApplicationContext();
        if (!CloudSyncCredentials.readyForSync(app)) return;
        EncryptedWatchSyncWorker.ensurePeriodic(app);
        if (!RUNNING.compareAndSet(false, true)) return;
        WORKER.execute(() -> {
            try {
                if (!sync(app)) EncryptedWatchSyncWorker.schedule(app);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    static boolean sync(Context context) {
        CloudSyncCredentials.Config config = CloudSyncCredentials.load(context);
        if (!config.configured()) return false;
        try {
            String deviceId = config.deviceId();
            SecretKey root = new SecretKeySpec(CloudSyncCredentials.rootKey(context, deviceId), "AES");
            Store store = Store.load(context);
            store.reconcilePlanProjections(context);
            if (!store.bootstrapComplete()) {
                boolean completed = false;
                for (int page = 0; page < MAX_PAGES; page++) {
                    Claim pull = store.beginPull();
                    JSONObject response;
                    try {
                        response = exchange(config, deviceId, store.cursor(), pull.mutations);
                    } catch (Exception failure) {
                        store.retry(pull.leaseId, failure.getClass().getSimpleName());
                        throw failure;
                    }
                    List<MaterializedChange> changes = materialize(response, root);
                    store.apply(response, pull.leaseId, changes);
                    store.reconcilePlanProjections(context);
                    if (!response.getBoolean("hasMore")) {
                        store.markBootstrapComplete();
                        completed = true;
                        break;
                    }
                }
                if (!completed) throw new IllegalStateException("sync_bootstrap_did_not_converge");
            }
            store.stage(collectLocalEntities(context), root);
            for (int page = 0; page < MAX_PAGES; page++) {
                Claim claim = store.claim();
                JSONObject response;
                try {
                    response = exchange(config, deviceId, store.cursor(), claim.mutations);
                } catch (Exception failure) {
                    store.retry(claim.leaseId, failure.getClass().getSimpleName());
                    throw failure;
                }
                List<MaterializedChange> changes = materialize(response, root);
                store.apply(response, claim.leaseId, changes);
                store.reconcilePlanProjections(context);
                if (!response.getBoolean("hasMore") && claim.mutations.length() == 0) {
                    CloudSyncCredentials.recordResult(context, System.currentTimeMillis(), "");
                    return true;
                }
            }
            throw new IllegalStateException("sync_pagination_did_not_converge");
        } catch (Exception failure) {
            CloudSyncCredentials.recordResult(context, 0, failure.getClass().getSimpleName());
            return false;
        }
    }

    private static SyncInput collectLocalEntities(Context context) {
        SyncInput result = new SyncInput();
        Map<String, LocalEntity> entities = new HashMap<>();
        try {
            JSONObject library = PhonePlanLibrary.load(context);
            JSONArray plans = library.optJSONArray("plans");
            if (plans != null) {
                for (int index = 0; index < plans.length(); index++) {
                    JSONObject plan = plans.optJSONObject(index);
                    String id = plan == null ? "" : plan.optString("id");
                    if (plan != null && validEntityId(id)) {
                        JSONObject payload = copy(plan).put("group",
                                PhonePlanLibrary.groupName(library, plan.optString("groupId")));
                        entities.put(key("plan", id), new LocalEntity("plan", id, payload));
                    }
                }
            }
            entities.put(key("plan", PLAN_LIBRARY_ENTITY_ID), new LocalEntity("plan",
                    PLAN_LIBRARY_ENTITY_ID, PhonePlanLibrary.syncMetadata(library)));
            result.deletedPlanIds.addAll(PhonePlanLibrary.pendingSyncDeletes(library));
        } catch (Exception ignored) {
            // A corrupt local library never implies a remote deletion. Its last durable cloud
            // state remains intact until a later local read can stage an explicit mutation.
        }
        try {
            WatchConnectionManager connection = WatchConnectionManager.get(context);
            if (connection.identity().isPaired()) {
                JSONArray history = new JSONArray(connection.requestBlocking("GET", "/v1/history", "", 25_000L));
                for (int index = 0; index < history.length(); index++) {
                    JSONObject workout = history.optJSONObject(index);
                    String id = workout == null ? "" : workout.optString("id");
                    if (workout != null && validEntityId(id)) {
                        entities.put(key("workout", id), new LocalEntity("workout", id, copy(workout)));
                    }
                }
            }
        } catch (Exception ignored) {
            // Plan sync can continue when the watch is outside BLE/LAN range.
        }
        result.entities.addAll(entities.values());
        result.entities.sort(Comparator
                .comparingInt((LocalEntity entity) -> PLAN_LIBRARY_ENTITY_ID.equals(entity.entityId) ? 1 : 0)
                .thenComparing(entity -> entity.entityType)
                .thenComparing(entity -> entity.entityId));
        return result;
    }

    private static JSONObject exchange(CloudSyncCredentials.Config config, String deviceId,
                                       String cursor, JSONArray mutations) throws Exception {
        JSONObject body = new JSONObject()
                .put("protocolVersion", PROTOCOL_VERSION)
                .put("envelopeVersion", ENVELOPE_VERSION)
                .put("product", PRODUCT)
                .put("deviceId", deviceId)
                .put("cursor", cursor == null ? JSONObject.NULL : cursor)
                .put("mutations", mutations);
        byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(config.endpoint).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(request.length);
            connection.setRequestProperty("Authorization", "Bearer " + config.deviceToken);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            try (OutputStream output = connection.getOutputStream()) { output.write(request); }
            int status = connection.getResponseCode();
            String response;
            try (InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                response = readBounded(input, MAX_RESPONSE_BYTES);
            }
            if (status < 200 || status >= 300) throw new IllegalStateException("cloud_http_" + status);
            JSONObject parsed = new JSONObject(response);
            validateExchangeResponse(parsed);
            return parsed;
        } finally {
            connection.disconnect();
        }
    }

    static List<MaterializedChange> materialize(JSONObject response, SecretKey root)
            throws Exception {
        JSONArray source = response.getJSONArray("changes");
        List<MaterializedChange> result = new ArrayList<>();
        for (int index = 0; index < source.length(); index++) {
            JSONObject change = source.getJSONObject(index);
            String entityType = change.getString("entityType");
            String entityId = change.getString("entityId");
            String operation = change.getString("operation");
            int revision = change.getInt("revision");
            int keyVersion = change.getInt("keyVersion");
            String operationId = change.getString("operationId");
            if (!validEntity(entityType, entityId) || revision < 1 || keyVersion < 1 ||
                    !validOperationId(operationId) ||
                    (!"upsert".equals(operation) && !"delete".equals(operation))) {
                throw new IllegalArgumentException("invalid_encrypted_change");
            }
            JSONArray objects = change.getJSONArray("objects");
            if (objects.length() != 0) {
                throw new IllegalArgumentException("encrypted_object_transport_unavailable");
            }
            JSONObject aad = aad(entityType, entityId, operation, keyVersion, revision);
            if (!validSha256(change.optString("aadHash")) ||
                    !sha256(stableJson(aad)).equals(change.getString("aadHash"))) {
                throw new IllegalArgumentException("encrypted_change_aad_mismatch");
            }
            if ("delete".equals(operation)) {
                if (!change.isNull("ciphertext") || !change.isNull("nonce") ||
                        objects.length() != 0) {
                    throw new IllegalArgumentException("invalid_encrypted_delete");
                }
                result.add(new MaterializedChange(entityType, entityId, operation, revision, null,
                        operationId));
                continue;
            }
            String ciphertext = change.getString("ciphertext");
            String nonce = change.getString("nonce");
            if (!validBase64Url(ciphertext) || !validNonce(nonce)) {
                throw new IllegalArgumentException("invalid_encrypted_ciphertext");
            }
            JSONObject payload = new JSONObject(decrypt(root, ciphertext, nonce, stableJson(aad)));
            result.add(new MaterializedChange(entityType, entityId, operation, revision, payload,
                    operationId));
        }
        return result;
    }

    static void validateExchangeResponse(JSONObject value) throws Exception {
        if (value.getInt("protocolVersion") != PROTOCOL_VERSION ||
                value.getInt("envelopeVersion") != ENVELOPE_VERSION ||
                !PRODUCT.equals(value.getString("product")) ||
                !validCursor(value.getString("nextCursor")) ||
                !value.has("acknowledged") || !value.has("conflicts") || !value.has("changes") ||
                !value.has("hasMore") || !value.has("serverTime")) {
            throw new IllegalArgumentException("invalid_encrypted_exchange_response");
        }
        if (!(value.get("hasMore") instanceof Boolean) ||
                !(value.get("acknowledged") instanceof JSONArray) ||
                !(value.get("conflicts") instanceof JSONArray) ||
                !(value.get("changes") instanceof JSONArray)) {
            throw new IllegalArgumentException("invalid_encrypted_exchange_response");
        }
        JSONArray acknowledgements = value.getJSONArray("acknowledged");
        JSONArray conflicts = value.getJSONArray("conflicts");
        JSONArray changes = value.getJSONArray("changes");
        if (acknowledgements.length() > MAX_MUTATIONS || conflicts.length() > MAX_MUTATIONS ||
                changes.length() > 100) {
            throw new IllegalArgumentException("encrypted_exchange_limit_exceeded");
        }
        for (int index = 0; index < acknowledgements.length(); index++) {
            if (!validAcknowledgement(acknowledgements.optJSONObject(index))) {
                throw new IllegalArgumentException("invalid_encrypted_acknowledgement");
            }
        }
        for (int index = 0; index < conflicts.length(); index++) {
            if (!validConflict(conflicts.optJSONObject(index))) {
                throw new IllegalArgumentException("invalid_encrypted_conflict");
            }
        }
    }

    private static String readBounded(InputStream input, int maximum) throws Exception {
        if (input == null) throw new IllegalStateException("cloud_response_missing");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (output.size() + read > maximum) throw new IllegalStateException("cloud_response_too_large");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    static JSONObject mutation(SecretKey root, String entityType, String entityId,
                               int baseRevision, String operation, JSONObject payload)
            throws Exception {
        int revision = baseRevision + 1;
        int keyVersion = 1;
        JSONObject aad = aad(entityType, entityId, operation, keyVersion, revision);
        JSONObject value = new JSONObject()
                .put("opId", UUID.randomUUID().toString())
                .put("entityType", entityType)
                .put("entityId", entityId)
                .put("baseRevision", baseRevision)
                .put("operation", operation)
                .put("keyVersion", keyVersion)
                .put("aadHash", sha256(stableJson(aad)))
                .put("objects", new JSONArray());
        if ("delete".equals(operation)) {
            return value.put("ciphertext", JSONObject.NULL).put("nonce", JSONObject.NULL);
        }
        byte[] plaintext = stableJson(payload).getBytes(StandardCharsets.UTF_8);
        if (plaintext.length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("sync_payload_too_large");
        }
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, root, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(stableJson(aad).getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(plaintext);
        return value.put("ciphertext", encode(ciphertext)).put("nonce", encode(nonce));
    }

    static String decrypt(SecretKey root, String ciphertext, String nonce, String aad)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, root, new GCMParameterSpec(128, decode(nonce)));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(decode(ciphertext)), StandardCharsets.UTF_8);
    }

    static JSONObject aad(String entityType, String entityId, String operation,
                          int keyVersion, int revision) throws Exception {
        return new JSONObject()
                .put("envelopeVersion", ENVELOPE_VERSION)
                .put("entityId", entityId)
                .put("entityType", entityType)
                .put("keyVersion", keyVersion)
                .put("operation", operation)
                .put("product", PRODUCT)
                .put("revision", revision);
    }

    static String stableJson(Object value) throws Exception {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            StringBuilder output = new StringBuilder("{");
            for (int index = 0; index < keys.size(); index++) {
                if (index > 0) output.append(',');
                String key = keys.get(index);
                output.append(JSONObject.quote(key)).append(':').append(stableJson(object.get(key)));
            }
            return output.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder output = new StringBuilder("[");
            for (int index = 0; index < array.length(); index++) {
                if (index > 0) output.append(',');
                output.append(stableJson(array.get(index)));
            }
            return output.append(']').toString();
        }
        if (value instanceof String || value instanceof Character) {
            return JSONObject.quote(String.valueOf(value));
        }
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number) {
            double numeric = ((Number) value).doubleValue();
            if (Double.isNaN(numeric) || Double.isInfinite(numeric)) {
                throw new IllegalArgumentException("non_finite_json_number");
            }
            return value.toString();
        }
        throw new IllegalArgumentException("unsupported_json_value");
    }

    static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder output = new StringBuilder(64);
        for (byte valueByte : digest) output.append(String.format("%02x", valueByte & 0xff));
        return output.toString();
    }

    static boolean validCursor(String value) {
        if (value == null || !value.matches("^c[0-9a-z]+$") || value.length() > 12) return false;
        try {
            long decoded = Long.parseLong(value.substring(1), 36);
            return decoded >= 0 && decoded <= MAX_SAFE_INTEGER;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static boolean validAcknowledgement(JSONObject value) {
        if (value == null || !"acknowledged".equals(value.optString("outcome")) ||
                !validOperationId(value.optString("opId")) ||
                !validEntity(value.optString("entityType"), value.optString("entityId")) ||
                !("upsert".equals(value.optString("operation")) ||
                        "delete".equals(value.optString("operation")))) return false;
        Object revision = value.opt("revision");
        return revision instanceof Number && ((Number) revision).longValue() >= 1 &&
                ((Number) revision).longValue() <= MAX_SAFE_INTEGER;
    }

    private static boolean validConflict(JSONObject value) {
        if (value == null || !"conflict".equals(value.optString("outcome")) ||
                !validOperationId(value.optString("opId")) ||
                !validEntity(value.optString("entityType"), value.optString("entityId")) ||
                !("upsert".equals(value.optString("operation")) ||
                        "delete".equals(value.optString("operation")))) return false;
        String error = value.optString("error", "");
        Object current = value.opt("current");
        Object candidate = value.opt("candidate");
        return !error.isEmpty() && error.length() <= 128 &&
                (current == null || current == JSONObject.NULL || current instanceof JSONObject) &&
                (candidate == null || candidate == JSONObject.NULL || candidate instanceof JSONObject);
    }

    private static boolean validOperationId(String value) {
        return value != null && value.matches(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    }

    private static boolean validSha256(String value) {
        return value != null && value.matches("^[0-9a-f]{64}$");
    }

    private static boolean validBase64Url(String value) {
        return value != null && !value.isEmpty() && value.length() <= MAX_CIPHERTEXT_CHARS &&
                value.matches("^[A-Za-z0-9_-]+$");
    }

    private static boolean validNonce(String value) {
        if (value == null || !value.matches("^[A-Za-z0-9_-]{16}$")) return false;
        try { return decode(value).length == 12; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private static boolean validEntity(String entityType, String entityId) {
        return ("plan".equals(entityType) || "workout".equals(entityType)) && validEntityId(entityId);
    }

    private static boolean validEntityId(String value) {
        return value != null && value.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    }

    private static String key(String entityType, String entityId) {
        return entityType + "\u0000" + entityId;
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static JSONObject copy(JSONObject value) throws Exception {
        return new JSONObject(value.toString());
    }

    private static final class LocalEntity {
        final String entityType;
        final String entityId;
        final JSONObject payload;
        LocalEntity(String entityType, String entityId, JSONObject payload) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.payload = payload;
        }
    }

    private static final class SyncInput {
        final List<LocalEntity> entities = new ArrayList<>();
        final Set<String> deletedPlanIds = new HashSet<>();
    }

    static final class MaterializedChange {
        final String entityType;
        final String entityId;
        final String operation;
        final int revision;
        final JSONObject payload;
        final String operationId;
        MaterializedChange(String entityType, String entityId, String operation, int revision,
                           JSONObject payload, String operationId) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.operation = operation;
            this.revision = revision;
            this.payload = payload;
            this.operationId = operationId;
        }
    }

    static final class Claim {
        final String leaseId;
        final JSONArray mutations;
        Claim(String leaseId, JSONArray mutations) {
            this.leaseId = leaseId;
            this.mutations = mutations;
        }
    }

    static final class ApplyResult {
        final Set<String> acknowledgedPlanDeletes;
        ApplyResult(Set<String> acknowledgedPlanDeletes) {
            this.acknowledgedPlanDeletes = acknowledgedPlanDeletes;
        }
    }

    static final class Store {
        private final Context context;
        private JSONObject state;

        private Store(Context context, JSONObject state) {
            this.context = context;
            this.state = state;
        }

        static Store load(Context context) throws Exception {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = preferences.getString(STATE, null);
            if (raw == null) return new Store(context.getApplicationContext(), fresh());
            try {
                JSONObject value = normalizeState(new JSONObject(raw));
                return new Store(context.getApplicationContext(), value);
            } catch (Exception corrupted) {
                if (!preferences.edit().putString("state_corrupt_backup_" + System.currentTimeMillis(), raw)
                        .commit()) {
                    throw new IllegalStateException("sync_state_corrupt_backup_failed", corrupted);
                }
                throw new IllegalStateException("sync_state_corrupted", corrupted);
            }
        }

        static Store forTesting(JSONObject state) throws Exception {
            return new Store(null, normalizeState(new JSONObject(state.toString())));
        }

        private static JSONObject normalizeState(JSONObject value) throws Exception {
            if (!value.has("cursor")) value.put("cursor", JSONObject.NULL);
            else if (!value.isNull("cursor") && (!(value.opt("cursor") instanceof String) ||
                    !validCursor(value.optString("cursor")))) throw new IllegalArgumentException("corrupt_cursor");
            if (!value.has("entities")) value.put("entities", new JSONObject());
            else if (!(value.opt("entities") instanceof JSONObject)) throw new IllegalArgumentException("corrupt_entities");
            if (!value.has("outbox")) value.put("outbox", new JSONArray());
            else if (!(value.opt("outbox") instanceof JSONArray)) throw new IllegalArgumentException("corrupt_outbox");
            if (!value.has("conflicts")) value.put("conflicts", new JSONArray());
            else if (!(value.opt("conflicts") instanceof JSONArray)) throw new IllegalArgumentException("corrupt_conflicts");
            if (!value.has("projectionPending")) value.put("projectionPending", new JSONArray());
            else if (!(value.opt("projectionPending") instanceof JSONArray)) throw new IllegalArgumentException("corrupt_projection_queue");
            if (!value.has("bootstrapComplete")) value.put("bootstrapComplete", false);
            else if (!(value.opt("bootstrapComplete") instanceof Boolean)) throw new IllegalArgumentException("corrupt_bootstrap");
            return value;
        }

        static JSONObject fresh() {
            try {
                return new JSONObject().put("cursor", JSONObject.NULL).put("entities", new JSONObject())
                        .put("outbox", new JSONArray()).put("conflicts", new JSONArray())
                        .put("projectionPending", new JSONArray())
                        .put("bootstrapComplete", false);
            } catch (Exception impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        synchronized String cursor() {
            String value = state.optString("cursor", "");
            return validCursor(value) ? value : null;
        }

        synchronized boolean bootstrapComplete() {
            return state.optBoolean("bootstrapComplete", false);
        }

        synchronized void markBootstrapComplete() throws Exception {
            state.put("bootstrapComplete", true);
            save();
        }

        synchronized void stage(SyncInput desired, SecretKey root) throws Exception {
            JSONObject entities = state.getJSONObject("entities");
            JSONArray outbox = state.getJSONArray("outbox");
            Set<String> desiredKeys = new HashSet<>();
            for (LocalEntity local : desired.entities) {
                String entityKey = key(local.entityType, local.entityId);
                desiredKeys.add(entityKey);
                String fingerprint = sha256(stableJson(local.payload));
                JSONObject existing = entities.optJSONObject(entityKey);
                if (existing == null) {
                    existing = new JSONObject().put("entityType", local.entityType).put("entityId", local.entityId)
                            .put("confirmedRevision", 0).put("confirmedFingerprint", JSONObject.NULL)
                            .put("deleted", false);
                }
                existing.put("payload", copy(local.payload)).put("localFingerprint", fingerprint)
                        .put("deleted", false).put("updatedAt", System.currentTimeMillis());
                entities.put(entityKey, existing);
                if (fingerprint.equals(existing.optString("confirmedFingerprint", "")) ||
                        hasInflight(outbox, entityKey) || hasBlockingConflict(outbox, entityKey)) continue;
                removePending(outbox, entityKey);
                JSONObject mutation = mutation(root, local.entityType, local.entityId,
                        existing.optInt("confirmedRevision", 0), "upsert", local.payload);
                enqueue(outbox, mutation, fingerprint);
            }
            // Absence from a local read is never a deletion. Only a user action that committed an
            // explicit tombstone alongside the plan library may stage a remote delete.
            for (String entityId : desired.deletedPlanIds) {
                String entityKey = key("plan", entityId);
                if (desiredKeys.contains(entityKey)) continue;
                JSONObject existing = entities.optJSONObject(entityKey);
                if (existing == null) {
                    existing = new JSONObject().put("entityType", "plan").put("entityId", entityId)
                            .put("confirmedRevision", 0).put("confirmedFingerprint", JSONObject.NULL)
                            .put("deleted", false);
                }
                if (existing.optBoolean("deleted") || hasPending(outbox, entityKey) ||
                        hasBlockingConflict(outbox, entityKey)) continue;
                removePending(outbox, entityKey);
                JSONObject mutation = mutation(root, "plan", entityId,
                        existing.optInt("confirmedRevision", 0),
                        "delete", null);
                existing.put("payload", JSONObject.NULL).put("localFingerprint", JSONObject.NULL)
                        .put("deleted", true).put("updatedAt", System.currentTimeMillis());
                entities.put(entityKey, existing);
                enqueue(outbox, mutation, null);
            }
            save();
        }

        synchronized Claim claim() throws Exception {
            JSONArray outbox = state.getJSONArray("outbox");
            String leaseId = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            JSONArray mutations = new JSONArray();
            int requestBytes = 256;
            for (int index = 0; index < outbox.length() && mutations.length() < MAX_MUTATIONS; index++) {
                JSONObject item = outbox.getJSONObject(index);
                String status = item.optString("state", "pending");
                boolean available = "pending".equals(status) || "retry".equals(status) ||
                        ("inflight".equals(status) && item.optLong("leaseExpiresAt", 0) <= now);
                if (!available) continue;
                JSONObject wire = wireMutation(item);
                int mutationBytes = wire.toString().getBytes(StandardCharsets.UTF_8).length + 1;
                if (requestBytes + mutationBytes > MAX_REQUEST_BYTES) {
                    if (mutations.length() == 0) throw new IllegalStateException("sync_mutation_too_large");
                    break;
                }
                item.put("state", "inflight").put("leaseId", leaseId)
                        .put("leaseExpiresAt", now + LEASE_MILLIS).put("updatedAt", now);
                mutations.put(wire);
                requestBytes += mutationBytes;
            }
            state.put("flight", new JSONObject().put("leaseId", leaseId).put("startedAt", now));
            save();
            return new Claim(leaseId, mutations);
        }

        synchronized Claim beginPull() throws Exception {
            String leaseId = UUID.randomUUID().toString();
            state.put("flight", new JSONObject().put("leaseId", leaseId)
                    .put("startedAt", System.currentTimeMillis()).put("kind", "bootstrap_pull"));
            save();
            return new Claim(leaseId, new JSONArray());
        }

        synchronized void retry(String leaseId, String error) throws Exception {
            JSONArray outbox = state.getJSONArray("outbox");
            for (int index = 0; index < outbox.length(); index++) {
                JSONObject item = outbox.getJSONObject(index);
                if (leaseId.equals(item.optString("leaseId")) && "inflight".equals(item.optString("state"))) {
                    item.put("state", "retry").put("leaseId", JSONObject.NULL)
                            .put("leaseExpiresAt", JSONObject.NULL).put("error", error)
                            .put("attemptCount", item.optInt("attemptCount", 0) + 1)
                            .put("updatedAt", System.currentTimeMillis());
                }
            }
            state.remove("flight");
            save();
        }

        synchronized ApplyResult apply(JSONObject response, String leaseId,
                                       List<MaterializedChange> changes)
                throws Exception {
            JSONObject entities = state.getJSONObject("entities");
            JSONArray outbox = state.getJSONArray("outbox");
            JSONArray nextOutbox = new JSONArray();
            Map<String, JSONObject> acknowledged = new HashMap<>();
            Map<String, JSONObject> rejected = new HashMap<>();
            Set<String> acknowledgedPlanDeletes = new HashSet<>();
            JSONArray acknowledgements = response.getJSONArray("acknowledged");
            for (int index = 0; index < acknowledgements.length(); index++) {
                JSONObject ack = acknowledgements.getJSONObject(index);
                acknowledged.put(ack.getString("opId"), ack);
            }
            JSONArray responseConflicts = response.getJSONArray("conflicts");
            for (int index = 0; index < responseConflicts.length(); index++) {
                JSONObject conflict = responseConflicts.getJSONObject(index);
                rejected.put(conflict.getString("opId"), conflict);
            }
            JSONArray conflicts = state.getJSONArray("conflicts");
            for (int index = 0; index < outbox.length(); index++) {
                JSONObject item = outbox.getJSONObject(index);
                String opId = item.getString("opId");
                JSONObject ack = acknowledged.get(opId);
                if (ack != null && leaseId.equals(item.optString("leaseId"))) {
                    int expectedRevision = item.getInt("baseRevision") + 1;
                    int confirmedRevision = ack.getInt("revision");
                    if (confirmedRevision != expectedRevision ||
                            !item.getString("entityType").equals(ack.getString("entityType")) ||
                            !item.getString("entityId").equals(ack.getString("entityId")) ||
                            !item.getString("operation").equals(ack.getString("operation"))) {
                        throw new IllegalStateException("acknowledgement_mismatch");
                    }
                    JSONObject entity = entities.optJSONObject(key(item.getString("entityType"), item.getString("entityId")));
                    if (entity != null) {
                        entity.put("confirmedRevision", confirmedRevision)
                                .put("confirmedFingerprint", item.opt("localFingerprint"));
                    }
                    if ("plan".equals(item.getString("entityType")) &&
                            "delete".equals(item.getString("operation"))) {
                        acknowledgedPlanDeletes.add(item.getString("entityId"));
                    }
                    continue;
                }
                JSONObject conflict = rejected.get(opId);
                if (conflict != null && leaseId.equals(item.optString("leaseId"))) {
                    item.put("state", "conflict").put("leaseId", JSONObject.NULL)
                            .put("leaseExpiresAt", JSONObject.NULL)
                            .put("error", conflict.getString("error"))
                            .put("updatedAt", System.currentTimeMillis());
                    appendConflict(conflicts, new JSONObject(conflict.toString())
                            .put("id", "server-" + opId)
                            .put("createdAt", System.currentTimeMillis()));
                }
                nextOutbox.put(item);
            }
            state.put("outbox", nextOutbox);
            for (MaterializedChange change : changes) {
                String entityKey = key(change.entityType, change.entityId);
                JSONObject existing = entities.optJSONObject(entityKey);
                if (hasPending(nextOutbox, entityKey)) {
                    appendConflict(conflicts, new JSONObject().put("id", "remote-" + change.operationId)
                            .put("entityType", change.entityType).put("entityId", change.entityId)
                            .put("reason", "REMOTE_CHANGE_WHILE_LOCAL_OUTBOX_PENDING")
                            .put("remoteRevision", change.revision)
                            .put("remoteOperation", change.operation)
                            .put("remotePayload", change.payload == null
                                    ? JSONObject.NULL : copy(change.payload))
                            .put("createdAt", System.currentTimeMillis()));
                    continue;
                }
                if (existing != null && change.revision < existing.optInt("confirmedRevision", 0)) {
                    throw new IllegalStateException("remote_revision_regressed");
                }
                JSONObject entity = existing == null ? new JSONObject() : existing;
                entity.put("entityType", change.entityType).put("entityId", change.entityId)
                        .put("confirmedRevision", change.revision)
                        .put("confirmedFingerprint", change.payload == null ? JSONObject.NULL : sha256(stableJson(change.payload)))
                        .put("localFingerprint", change.payload == null ? JSONObject.NULL : sha256(stableJson(change.payload)))
                        .put("deleted", "delete".equals(change.operation)).put("updatedAt", System.currentTimeMillis())
                        .put("payload", change.payload == null ? JSONObject.NULL : copy(change.payload));
                entities.put(entityKey, entity);
                if ("plan".equals(change.entityType)) enqueueProjection(entityKey);
            }
            String nextCursor = response.getString("nextCursor");
            String current = cursor();
            if (current != null && cursorValue(nextCursor) < cursorValue(current)) {
                throw new IllegalStateException("cursor_regressed");
            }
            state.put("cursor", nextCursor).remove("flight");
            save();
            return new ApplyResult(acknowledgedPlanDeletes);
        }

        synchronized void reconcilePlanProjections(Context applicationContext) throws Exception {
            JSONArray pending = state.getJSONArray("projectionPending");
            if (pending.length() == 0) return;
            JSONObject entities = state.getJSONObject("entities");
            List<String> projectionKeys = new ArrayList<>();
            for (int index = 0; index < pending.length(); index++) {
                projectionKeys.add(pending.getString(index));
            }
            projectionKeys.sort(Comparator.comparingInt(entityKey ->
                    entityKey.equals(key("plan", PLAN_LIBRARY_ENTITY_ID)) ? 1 : 0));
            for (String entityKey : projectionKeys) {
                JSONObject entity = entities.optJSONObject(entityKey);
                if (entity == null || !"plan".equals(entity.optString("entityType"))) {
                    throw new IllegalStateException("projection_entity_missing");
                }
                String entityId = entity.getString("entityId");
                boolean deleted = entity.optBoolean("deleted");
                if (PLAN_LIBRARY_ENTITY_ID.equals(entityId)) {
                    if (deleted || entity.isNull("payload")) {
                        throw new IllegalStateException("plan_library_metadata_deleted");
                    }
                    PhonePlanLibrary.applySyncMetadata(applicationContext,
                            entity.getJSONObject("payload"));
                } else if (deleted) {
                    PhonePlanLibrary.deletePlanFromSync(applicationContext, entityId);
                    PhonePlanLibrary.confirmSyncDelete(applicationContext, entityId);
                } else {
                    PhonePlanLibrary.upsertFromSync(applicationContext,
                            entity.getJSONObject("payload"));
                }
            }
            state.put("projectionPending", new JSONArray());
            save();
        }

        synchronized JSONObject snapshotForTesting() throws Exception {
            return new JSONObject(state.toString());
        }

        private static void enqueue(JSONArray outbox, JSONObject mutation, String fingerprint)
                throws Exception {
            mutation.put("localFingerprint", fingerprint == null ? JSONObject.NULL : fingerprint)
                    .put("state", "pending").put("attemptCount", 0).put("leaseId", JSONObject.NULL)
                    .put("leaseExpiresAt", JSONObject.NULL).put("error", JSONObject.NULL)
                    .put("createdAt", System.currentTimeMillis()).put("updatedAt", System.currentTimeMillis());
            outbox.put(mutation);
        }

        private static JSONObject wireMutation(JSONObject item) throws Exception {
            JSONObject mutation = new JSONObject();
            for (String name : new String[] {"opId", "entityType", "entityId", "baseRevision", "operation",
                    "keyVersion", "ciphertext", "nonce", "aadHash", "objects"}) {
                mutation.put(name, item.get(name));
            }
            return mutation;
        }

        private static boolean hasInflight(JSONArray outbox, String entityKey) {
            for (int index = 0; index < outbox.length(); index++) {
                JSONObject item = outbox.optJSONObject(index);
                if (item != null && entityKey.equals(key(item.optString("entityType"), item.optString("entityId"))) &&
                        "inflight".equals(item.optString("state"))) return true;
            }
            return false;
        }

        private static boolean hasPending(JSONArray outbox, String entityKey) {
            for (int index = 0; index < outbox.length(); index++) {
                JSONObject item = outbox.optJSONObject(index);
                if (item != null && entityKey.equals(key(item.optString("entityType"), item.optString("entityId"))) &&
                        !"conflict".equals(item.optString("state"))) return true;
            }
            return false;
        }

        private static boolean hasBlockingConflict(JSONArray outbox, String entityKey) {
            for (int index = 0; index < outbox.length(); index++) {
                JSONObject item = outbox.optJSONObject(index);
                if (item != null && entityKey.equals(key(item.optString("entityType"), item.optString("entityId"))) &&
                        "conflict".equals(item.optString("state"))) return true;
            }
            return false;
        }

        private static void removePending(JSONArray outbox, String entityKey) throws Exception {
            JSONArray retained = new JSONArray();
            for (int index = 0; index < outbox.length(); index++) {
                JSONObject item = outbox.getJSONObject(index);
                boolean same = entityKey.equals(key(item.getString("entityType"), item.getString("entityId")));
                if (!same || "inflight".equals(item.optString("state")) || "conflict".equals(item.optString("state"))) {
                    retained.put(item);
                }
            }
            while (outbox.length() > 0) outbox.remove(0);
            for (int index = 0; index < retained.length(); index++) outbox.put(retained.get(index));
        }

        private static void appendConflict(JSONArray conflicts, JSONObject conflict) {
            if (conflicts.length() >= MAX_CONFLICTS) {
                throw new IllegalStateException("conflict_store_capacity_exceeded");
            }
            conflicts.put(conflict);
        }

        private void enqueueProjection(String entityKey) {
            JSONArray pending = state.optJSONArray("projectionPending");
            if (pending == null) throw new IllegalStateException("projection_queue_missing");
            for (int index = 0; index < pending.length(); index++) {
                if (entityKey.equals(pending.optString(index))) return;
            }
            pending.put(entityKey);
        }

        private void save() throws Exception {
            if (context == null) return;
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(STATE, state.toString()).commit()) {
                throw new IllegalStateException("sync_store_commit_failed");
            }
        }
    }

    private static long cursorValue(String cursor) {
        if (!validCursor(cursor)) throw new IllegalArgumentException("invalid_cursor");
        return Long.parseLong(cursor.substring(1), 36);
    }
}

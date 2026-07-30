package com.poyi.watchintervals;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Phone-authoritative plan library mirrored locally for offline watch selection. */
final class PlanLibraryStore {
    private static final String PREF = "plan_library_v2";
    private static final String KEY = "snapshot";
    private static final String PROCESSED_PREF = "processed_operations";
    private static final String PROCESSED_KEY = "items";
    private static final String CLOUD_REVISION_KEY = "cloud_plan_revision";
    private static final String CLOUD_SOURCE_KEY = "cloud_plan_source";
    private static final int SCHEMA = 2;

    private PlanLibraryStore() {}

    static synchronized JSONObject load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        try {
            String saved = preferences.getString(KEY, null);
            if (saved != null) return normalize(new JSONObject(saved), context);
        } catch (Exception ignored) {}
        JSONObject migrated = migrateCurrent(context);
        write(context, migrated);
        return migrated;
    }

    static synchronized JSONObject replace(Context context, JSONObject source) throws Exception {
        JSONObject normalized = normalize(new JSONObject(source.toString()), context);
        write(context, normalized);
        String selectedPlanId = normalized.optString("selectedPlanId");
        if (normalized.getJSONArray("plans").length() == 0 || selectedPlanId.isEmpty()) {
            PlanStore.clearProfile(context);
        } else select(context, selectedPlanId);
        return normalized;
    }

    static synchronized JSONObject select(Context context, String planId) throws Exception {
        JSONObject library = load(context);
        JSONArray plans = library.getJSONArray("plans");
        JSONObject selected = null;
        for (int i = 0; i < plans.length(); i++) {
            JSONObject item = plans.optJSONObject(i);
            if (item != null && planId.equals(item.optString("id"))) { selected = item; break; }
        }
        if (selected == null) throw new IllegalArgumentException("plan_not_found");
        ArrayList<Stage> stages = PlanStore.decode(selected.optJSONArray("stages").toString());
        if (stages.isEmpty()) throw new IllegalArgumentException("invalid_plan");
        PlanStore.saveProfile(context, selected.optString("name", "训练计划"), groupName(library, selected.optString("groupId")),
                selected.optString("requirement", "按阶段顺序完成训练。"), stages);
        library.put("selectedPlanId", planId);
        write(context, library);
        return selected;
    }

    static synchronized JSONObject applySyncOperations(Context context, JSONObject request)
            throws Exception {
        JSONArray operations = request.optJSONArray("operations"), acks = new JSONArray();
        if (operations == null) return new JSONObject().put("acks", acks);
        SharedPreferences preferences = context.getSharedPreferences(
                PROCESSED_PREF, Context.MODE_PRIVATE);
        JSONObject processed;
        try { processed = new JSONObject(preferences.getString(PROCESSED_KEY, "{}")); }
        catch (Exception ignored) { processed = new JSONObject(); }
        long cloudRevision = preferences.getLong(CLOUD_REVISION_KEY, 0L);
        String cloudSource = canonicalCloudSource(
                preferences.getString(CLOUD_SOURCE_KEY, ""));
        for (int index = 0; index < operations.length(); index++) {
            JSONObject operation = operations.optJSONObject(index);
            if (operation == null) continue;
            String operationId = operation.optString("operationId");
            JSONObject ack = new JSONObject().put("operationId", operationId);
            if (processed.has(operationId)) {
                acks.put(ack.put("status", "already_applied"));
                continue;
            }
            JSONObject payload = operation.optJSONObject("payload");
            if (!"plan_library".equals(operation.optString("entityType")) || payload == null) {
                acks.put(ack.put("status", "invalid"));
                continue;
            }
            JSONObject current = load(context);
            long incomingRevision = payload.optLong("revision");
            boolean cloudReplace = "cloud_replace".equals(operation.optString("operation"));
            String incomingSource = canonicalCloudSource(operation.optString("cloudSourceId"));
            if (cloudReplace && !cloudSourceTransitionAllowed(cloudSource, incomingSource)) {
                acks.put(ack.put("status", "conflict")
                        .put("error", "cloud_source_conflict")
                        .put("libraryRevision", current.optLong("revision")));
                continue;
            }
            if (shouldRejectIncomingRevision(cloudReplace, incomingRevision,
                    current.optLong("revision"), cloudRevisionFloor(
                            cloudReplace, cloudSource, cloudRevision, incomingSource))) {
                acks.put(ack.put("status", "conflict")
                        .put("libraryRevision", current.optLong("revision")));
                continue;
            }
            JSONObject saved = replace(context, payload);
            processed.put(operationId, System.currentTimeMillis());
            if (cloudReplace) { cloudRevision = incomingRevision; if (!incomingSource.isEmpty()) cloudSource = incomingSource; }
            acks.put(ack.put("status", "applied")
                    .put("libraryRevision", saved.optLong("revision")));
        }
        JSONArray names = processed.names();
        while (names != null && names.length() > 500) {
            processed.remove(names.optString(0));
            names = processed.names();
        }
        SharedPreferences.Editor editor = preferences.edit().putString(
                PROCESSED_KEY, processed.toString());
        if (cloudRevision > 0) editor.putLong(CLOUD_REVISION_KEY, cloudRevision);
        if (!cloudSource.isEmpty()) editor.putString(CLOUD_SOURCE_KEY, cloudSource);
        if (!editor.commit()) throw new IllegalStateException("processed_operation_commit_failed");
        return new JSONObject().put("acks", acks);
    }

    static boolean shouldRejectIncomingRevision(boolean cloudReplace, long incomingRevision,
                                                long currentRevision, long cloudRevision) {
        return incomingRevision < (cloudReplace ? cloudRevision : currentRevision);
    }

    static long cloudRevisionFloor(boolean cloudReplace, String currentSource,
                                   long currentRevision, String incomingSource) {
        String current = canonicalCloudSource(currentSource);
        String incoming = canonicalCloudSource(incomingSource);
        if (cloudReplace && !incoming.isEmpty() && !incoming.equals(current)) return 0L;
        return currentRevision;
    }

    static boolean cloudSourceTransitionAllowed(String currentSource, String incomingSource) {
        String current = canonicalCloudSource(currentSource);
        String incoming = canonicalCloudSource(incomingSource);
        if (incoming.isEmpty()) return current.isEmpty();
        if (!validCloudSource(incoming)) return false;
        if (current.isEmpty() || current.equals(incoming)) return true;
        // A legacy per-device source may upgrade once to a server owner/library domain.
        // Once authority-bound, retired staging/legacy sources cannot switch it back.
        return !isAuthoritySource(current) && isAuthoritySource(incoming);
    }

    static String canonicalCloudSource(String value) {
        String source = value == null ? "" : value.trim();
        return source.matches("^[0-9a-f]{16}$") ? "legacy." + source : source;
    }

    private static boolean validCloudSource(String value) {
        return value.matches("^legacy\\.[0-9a-f]{16}$")
                || value.matches("^v3d\\.[A-Za-z0-9_-]{8,64}$");
    }

    private static boolean isAuthoritySource(String value) {
        return value.startsWith("v3d.");
    }

    static String groupName(JSONObject library, String groupId) {
        JSONArray groups = library.optJSONArray("groups");
        if (groups != null) for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group != null && groupId.equals(group.optString("id"))) return group.optString("name", "我的计划");
        }
        return "我的计划";
    }

    private static JSONObject migrateCurrent(Context context) {
        try {
            long now = System.currentTimeMillis();
            if (PlanStore.isExplicitlyEmpty(context)) {
                return new JSONObject().put("schemaVersion", SCHEMA).put("revision", now)
                        .put("groups", new JSONArray()).put("plans", new JSONArray())
                        .put("selectedPlanId", "");
            }
            String currentGroup = PlanStore.group(context);
            String currentGroupId = stableId("group", currentGroup);
            JSONObject library = new JSONObject().put("schemaVersion", SCHEMA).put("revision", now);
            JSONArray groups = new JSONArray().put(group(currentGroupId, currentGroup, 0));
            JSONArray plans = new JSONArray();
            JSONObject current = plan(stableId("plan", PlanStore.name(context) + PlanStore.encode(PlanStore.load(context))),
                    PlanStore.name(context), currentGroupId, PlanStore.requirement(context), PlanStore.load(context), now);
            plans.put(current);
            addTemplate(groups, plans, "间歇训练", "1千米 + 200米", PlanStore.defaultPlan(),
                    "跑步 1 千米，随后快走恢复 200 米；按阶段顺序完成。", now);
            ArrayList<Stage> fartlek = new ArrayList<>();
            for (int i = 0; i < 6; i++) { fartlek.add(new Stage(Stage.Kind.RUN, Stage.Unit.TIME, 120)); fartlek.add(new Stage(Stage.Kind.WALK, Stage.Unit.TIME, 60)); }
            addTemplate(groups, plans, "变速训练", "法特莱克跑", fartlek, "快跑 2 分钟，快走恢复 1 分钟，连续完成 6 组。", now);
            library.put("groups", groups).put("plans", plans).put("selectedPlanId", current.getString("id"));
            return normalize(library, context);
        } catch (Exception error) { return new JSONObject(); }
    }

    private static void addTemplate(JSONArray groups, JSONArray plans, String groupName, String name, ArrayList<Stage> stages, String requirement, long now) throws Exception {
        String groupId = stableId("group", groupName);
        boolean hasGroup = false, hasPlan = false;
        for (int i = 0; i < groups.length(); i++) if (groupId.equals(groups.getJSONObject(i).optString("id"))) hasGroup = true;
        for (int i = 0; i < plans.length(); i++) if (name.equals(plans.getJSONObject(i).optString("name"))) hasPlan = true;
        if (!hasGroup) groups.put(group(groupId, groupName, groups.length()));
        if (!hasPlan) plans.put(plan(stableId("plan", name), name, groupId, requirement, stages, now));
    }

    private static JSONObject normalize(JSONObject source, Context context) throws Exception {
        JSONArray inputGroups = source.optJSONArray("groups");
        JSONArray inputPlans = source.optJSONArray("plans");
        JSONArray groups = new JSONArray(), plans = new JSONArray();
        Set<String> groupIds = new HashSet<>(), planIds = new HashSet<>();
        if (inputGroups != null) for (int i = 0; i < inputGroups.length(); i++) {
            JSONObject item = inputGroups.optJSONObject(i); if (item == null) continue;
            String name = item.optString("name").trim(); if (name.isEmpty()) continue;
            String id = item.optString("id").trim(); if (id.isEmpty()) id = stableId("group", name);
            if (groupIds.add(id)) groups.put(group(id, name, item.optInt("sortOrder", groups.length())));
        }
        if (inputPlans != null) for (int i = 0; i < inputPlans.length(); i++) {
            JSONObject item = inputPlans.optJSONObject(i); if (item == null) continue;
            String name = item.optString("name").trim(); JSONArray stageJson = item.optJSONArray("stages");
            ArrayList<Stage> stages = PlanStore.decode(stageJson == null ? null : stageJson.toString());
            if (name.isEmpty() || stages.isEmpty()) continue;
            String groupId = item.optString("groupId").trim();
            if (!groupId.isEmpty() && !groupIds.contains(groupId)) {
                String fallbackName = item.optString("group", "我的计划").trim(); if (fallbackName.isEmpty()) fallbackName = "我的计划";
                groupId = stableId("group", fallbackName); if (groupIds.add(groupId)) groups.put(group(groupId, fallbackName, groups.length()));
            }
            String id = item.optString("id").trim(); if (id.isEmpty()) id = UUID.randomUUID().toString();
            if (!planIds.add(id)) continue;
            plans.put(plan(id, name, groupId, item.optString("requirement"), stages, item.optLong("updatedAt", System.currentTimeMillis()))
                    .put("revision", Math.max(1, item.optLong("revision", 1)))
                    .put("sortOrder", Math.max(0, item.optInt("sortOrder", i))));
        }
        String selected = source.optString("selectedPlanId");
        selected = normalizedSelectedPlanId(planIds, plans, selected);
        return new JSONObject().put("schemaVersion", SCHEMA).put("revision", Math.max(1, source.optLong("revision", System.currentTimeMillis())))
                .put("groups", groups).put("plans", plans).put("selectedPlanId", selected);
    }

    static String normalizedSelectedPlanId(Set<String> planIds, JSONArray plans,
                                           String selected) throws Exception {
        if (plans.length() == 0) return "";
        if (selected == null || selected.isEmpty()) return "";
        return planIds.contains(selected) ? selected : plans.getJSONObject(0).getString("id");
    }

    private static JSONObject group(String id, String name, int order) throws Exception {
        return new JSONObject().put("id", id).put("name", name).put("sortOrder", order);
    }

    private static JSONObject plan(String id, String name, String groupId, String requirement, ArrayList<Stage> stages, long updatedAt) throws Exception {
        return new JSONObject().put("id", id).put("name", name).put("groupId", groupId).put("requirement", requirement == null ? "" : requirement)
                .put("stages", new JSONArray(PlanStore.encode(stages))).put("updatedAt", updatedAt).put("revision", 1);
    }

    private static String stableId(String prefix, String value) { return UUID.nameUUIDFromBytes((prefix + ":" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(); }
    private static void write(Context context, JSONObject library) {
        if (!context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY, library.toString()).commit()) {
            throw new IllegalStateException("plan_library_commit_failed");
        }
    }
}

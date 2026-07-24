package com.poyi.watchintervals.phone;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Canonical phone-side training plan library. */
final class PhonePlanLibrary {
    private static final String PREF = "plan_library_v2", KEY = "snapshot";
    private static final int SCHEMA = 2;
    private PhonePlanLibrary() {}

    static synchronized JSONObject load(Context context) {
        try {
            String raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null);
            if (raw != null) return normalize(new JSONObject(raw));
        } catch (Exception ignored) {}
        JSONObject library = migrate(context); save(context, library); return library;
    }

    static synchronized JSONObject save(Context context, JSONObject source) {
        try {
            JSONObject normalized = normalize(source);
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, normalized.toString()).commit();
            return normalized;
        } catch (Exception error) { throw new IllegalArgumentException(error); }
    }

    static synchronized JSONObject upsert(Context context, JSONObject profile) throws Exception {
        JSONObject library = load(context); JSONArray source = library.getJSONArray("plans"), result = new JSONArray();
        String id = profile.optString("id"); if (id.isEmpty()) id = UUID.randomUUID().toString();
        String groupName = profile.optString("group", "我的计划").trim(); if (groupName.isEmpty()) groupName = "我的计划";
        String groupId = ensureGroup(library.getJSONArray("groups"), groupName);
        JSONObject item = new JSONObject(profile.toString()).put("id", id).put("groupId", groupId); item.remove("group");
        item.put("updatedAt", System.currentTimeMillis()).put("revision", Math.max(1, profile.optLong("revision", 0) + 1));
        boolean replaced = false;
        for (int i = 0; i < source.length(); i++) {
            JSONObject old = source.optJSONObject(i);
            if (old != null && id.equals(old.optString("id"))) { result.put(item); replaced = true; }
            else if (old != null) result.put(old);
        }
        if (!replaced) result.put(item);
        library.put("plans", result).put("revision", System.currentTimeMillis());
        return save(context, library);
    }

    static synchronized JSONObject deletePlan(Context context, String id) throws Exception {
        JSONObject library = load(context); JSONArray source = library.getJSONArray("plans"), result = new JSONArray();
        for (int i = 0; i < source.length(); i++) { JSONObject item = source.optJSONObject(i); if (item != null && !id.equals(item.optString("id"))) result.put(item); }
        library.put("plans", result).put("revision", System.currentTimeMillis());
        if (id.equals(library.optString("selectedPlanId"))) library.put("selectedPlanId", result.length() == 0 ? "" : result.getJSONObject(0).optString("id"));
        return save(context, library);
    }

    static synchronized JSONObject select(Context context, String id) throws Exception {
        JSONObject library = load(context); boolean found = false;
        JSONArray plans = library.getJSONArray("plans");
        for (int i = 0; i < plans.length(); i++) if (id.equals(plans.getJSONObject(i).optString("id"))) found = true;
        if (!found) throw new IllegalArgumentException("plan_not_found");
        library.put("selectedPlanId", id).put("revision", System.currentTimeMillis()); return save(context, library);
    }

    static synchronized JSONObject createGroup(Context context, String name) throws Exception {
        String clean = name == null ? "" : name.trim(); if (clean.isEmpty()) throw new IllegalArgumentException("empty_group_name");
        JSONObject library = load(context); JSONArray groups = library.getJSONArray("groups");
        for (int i = 0; i < groups.length(); i++) if (clean.equals(groups.getJSONObject(i).optString("name"))) return groups.getJSONObject(i);
        JSONObject group = new JSONObject().put("id", UUID.randomUUID().toString()).put("name", clean).put("sortOrder", groups.length());
        groups.put(group); library.put("revision", System.currentTimeMillis()); save(context, library); return group;
    }

    static synchronized JSONObject renameGroup(Context context, String id, String name) throws Exception {
        String clean = name == null ? "" : name.trim(); if (clean.isEmpty()) throw new IllegalArgumentException("empty_group_name");
        JSONObject library = load(context); JSONArray groups = library.getJSONArray("groups"); JSONObject found = null;
        for (int i = 0; i < groups.length(); i++) if (id.equals(groups.getJSONObject(i).optString("id"))) { found = groups.getJSONObject(i); found.put("name", clean); }
        if (found == null) throw new IllegalArgumentException("group_not_found");
        library.put("revision", System.currentTimeMillis()); save(context, library); return found;
    }

    static synchronized JSONObject deleteGroup(Context context, String id) throws Exception {
        JSONObject library = load(context); JSONArray source = library.getJSONArray("groups"), groups = new JSONArray();
        boolean found = false; for (int i = 0; i < source.length(); i++) { JSONObject group = source.getJSONObject(i); if (id.equals(group.optString("id"))) found = true; else groups.put(group); }
        if (!found) throw new IllegalArgumentException("group_not_found");
        String fallback = ensureGroup(groups, "我的计划"); JSONArray plans = library.getJSONArray("plans");
        for (int i = 0; i < plans.length(); i++) if (id.equals(plans.getJSONObject(i).optString("groupId"))) plans.getJSONObject(i).put("groupId", fallback);
        library.put("groups", groups).put("revision", System.currentTimeMillis()); return save(context, library);
    }

    static String groupName(JSONObject library, String groupId) {
        JSONArray groups = library.optJSONArray("groups"); if (groups != null) for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i); if (group != null && groupId.equals(group.optString("id"))) return group.optString("name");
        } return "我的计划";
    }

    private static JSONObject migrate(Context context) {
        try {
            JSONArray old;
            try { old = new JSONArray(context.getSharedPreferences("plan_library", Context.MODE_PRIVATE).getString("items", "[]")); }
            catch (Exception ignored) { old = new JSONArray(); }
            JSONArray groups = new JSONArray(), plans = new JSONArray(); long now = System.currentTimeMillis();
            for (int i = 0; i < old.length(); i++) {
                JSONObject source = old.optJSONObject(i); if (source == null) continue;
                String groupName = source.optString("group", "我的计划"); String groupId = ensureGroup(groups, groupName);
                JSONObject migrated = new JSONObject(source.toString()); migrated.remove("group");
                plans.put(migrated.put("groupId", groupId).put("updatedAt", now).put("revision", 1));
            }
            addTemplate(groups, plans, "间歇训练", "1千米 + 200米", "跑步 1 千米，随后快走恢复 200 米；按阶段顺序完成。",
                    new JSONArray().put(stage("RUN", "DISTANCE", 1000)).put(stage("WALK", "DISTANCE", 200)), now);
            JSONArray fartlek = new JSONArray(); for (int i = 0; i < 6; i++) { fartlek.put(stage("RUN", "TIME", 120)); fartlek.put(stage("WALK", "TIME", 60)); }
            addTemplate(groups, plans, "变速训练", "法特莱克跑", "快跑 2 分钟，快走恢复 1 分钟，连续完成 6 组。", fartlek, now);
            String selected = plans.length() == 0 ? "" : plans.getJSONObject(0).optString("id");
            return normalize(new JSONObject().put("schemaVersion", SCHEMA).put("revision", now).put("groups", groups).put("plans", plans).put("selectedPlanId", selected));
        } catch (Exception error) { return new JSONObject(); }
    }

    private static JSONObject normalize(JSONObject source) throws Exception {
        JSONArray sourceGroups = source.optJSONArray("groups"), sourcePlans = source.optJSONArray("plans");
        JSONArray groups = new JSONArray(), plans = new JSONArray(); Set<String> groupIds = new HashSet<>(), planIds = new HashSet<>();
        if (sourceGroups != null) for (int i = 0; i < sourceGroups.length(); i++) {
            JSONObject group = sourceGroups.optJSONObject(i); if (group == null) continue; String name = group.optString("name").trim(); if (name.isEmpty()) continue;
            String id = group.optString("id"); if (id.isEmpty()) id = stableId("group", name); if (!groupIds.add(id)) continue;
            groups.put(new JSONObject().put("id", id).put("name", name).put("sortOrder", group.optInt("sortOrder", groups.length())));
        }
        if (sourcePlans != null) for (int i = 0; i < sourcePlans.length(); i++) {
            JSONObject plan = sourcePlans.optJSONObject(i); if (plan == null || plan.optString("name").trim().isEmpty()) continue;
            JSONArray stages = plan.optJSONArray("stages"); if (stages == null || stages.length() == 0) continue;
            String groupId = plan.optString("groupId"); if (!groupIds.contains(groupId)) groupId = ensureGroup(groups, plan.optString("group", "我的计划"));
            String id = plan.optString("id"); if (id.isEmpty()) id = UUID.randomUUID().toString(); if (!planIds.add(id)) continue;
            JSONObject normalizedPlan = new JSONObject(plan.toString()); normalizedPlan.remove("group");
            plans.put(normalizedPlan.put("id", id).put("groupId", groupId)
                    .put("updatedAt", plan.optLong("updatedAt", System.currentTimeMillis())).put("revision", Math.max(1, plan.optLong("revision", 1))));
        }
        String selected = source.optString("selectedPlanId"); if (!planIds.contains(selected) && plans.length() > 0) selected = plans.getJSONObject(0).optString("id");
        return new JSONObject().put("schemaVersion", SCHEMA).put("revision", Math.max(1, source.optLong("revision", System.currentTimeMillis())))
                .put("groups", groups).put("plans", plans).put("selectedPlanId", selected);
    }

    private static String ensureGroup(JSONArray groups, String name) throws Exception {
        String clean = name == null || name.trim().isEmpty() ? "我的计划" : name.trim();
        for (int i = 0; i < groups.length(); i++) { JSONObject item = groups.getJSONObject(i); if (clean.equals(item.optString("name"))) return item.optString("id"); }
        String id = stableId("group", clean); groups.put(new JSONObject().put("id", id).put("name", clean).put("sortOrder", groups.length())); return id;
    }
    private static void addTemplate(JSONArray groups, JSONArray plans, String group, String name, String requirement, JSONArray stages, long now) throws Exception {
        for (int i = 0; i < plans.length(); i++) if (name.equals(plans.getJSONObject(i).optString("name"))) return;
        plans.put(new JSONObject().put("id", stableId("plan", name)).put("name", name).put("groupId", ensureGroup(groups, group))
                .put("requirement", requirement).put("stages", stages).put("updatedAt", now).put("revision", 1));
    }
    private static JSONObject stage(String kind, String unit, int target) throws Exception { return new JSONObject().put("kind", kind).put("unit", unit).put("target", target); }
    private static String stableId(String prefix, String value) { return UUID.nameUUIDFromBytes((prefix + ":" + value).getBytes(StandardCharsets.UTF_8)).toString(); }
}

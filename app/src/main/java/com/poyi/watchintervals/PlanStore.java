package com.poyi.watchintervals;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;

public final class PlanStore {
    private static final String PREF = "plans";
    private static final String KEY = "current";
    private static final String KEY_NAME = "current_name";
    private static final String KEY_GROUP = "current_group";
    private static final String KEY_REQUIREMENT = "current_requirement";
    private static final String KEY_EXPLICIT_EMPTY = "explicit_empty";

    private PlanStore() {}

    public static ArrayList<Stage> defaultPlan() {
        ArrayList<Stage> result = new ArrayList<>();
        result.add(new Stage(Stage.Kind.RUN, Stage.Unit.DISTANCE, 1000));
        result.add(new Stage(Stage.Kind.WALK, Stage.Unit.DISTANCE, 200));
        return result;
    }

    public static String encode(ArrayList<Stage> stages) {
        JSONArray array = new JSONArray();
        for (Stage stage : stages) {
            try { array.put(stage.toJson()); } catch (JSONException ignored) {}
        }
        return array.toString();
    }

    public static ArrayList<Stage> decode(String text) {
        ArrayList<Stage> result = new ArrayList<>();
        if (text == null) return result;
        try {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) result.add(Stage.fromJson(array.getJSONObject(i)));
        } catch (Exception ignored) {
            result.clear();
        }
        return result;
    }

    public static ArrayList<Stage> load(Context context) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                PREF, Context.MODE_PRIVATE);
        return resolveLoadedStages(preferences.getString(KEY, null),
                preferences.getBoolean(KEY_EXPLICIT_EMPTY, false));
    }

    static ArrayList<Stage> resolveLoadedStages(String encoded, boolean explicitEmpty) {
        if (explicitEmpty) return new ArrayList<>();
        ArrayList<Stage> result = decode(encoded);
        return result.isEmpty() ? defaultPlan() : result;
    }

    public static void save(Context context, ArrayList<Stage> stages) {
        if (!context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY, encode(stages)).putBoolean(KEY_EXPLICIT_EMPTY, false).commit()) {
            throw new IllegalStateException("plan_commit_failed");
        }
    }

    public static String name(Context context) {
        if (isExplicitlyEmpty(context)) return "暂无训练计划";
        android.content.SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (preferences.contains(KEY_NAME)) return preferences.getString(KEY_NAME, "自定义训练");
        return looksLikeFartlek(load(context)) ? "法特莱克跑" : looksLikeDefault(load(context)) ? "1千米 + 200米" : "自定义训练";
    }

    public static String group(Context context) {
        if (isExplicitlyEmpty(context)) return "计划库为空";
        android.content.SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (preferences.contains(KEY_GROUP)) return preferences.getString(KEY_GROUP, "我的计划");
        return looksLikeFartlek(load(context)) ? "变速训练" : looksLikeDefault(load(context)) ? "间歇训练" : "我的计划";
    }

    public static String requirement(Context context) {
        if (isExplicitlyEmpty(context)) return "请在手机或云端添加计划后再开始训练。";
        android.content.SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (preferences.contains(KEY_REQUIREMENT)) return preferences.getString(KEY_REQUIREMENT, "按阶段顺序完成训练。");
        if (looksLikeFartlek(load(context))) return "快跑 2 分钟，快走恢复 1 分钟，连续完成 6 组。\n快跑阶段保持可控强度，恢复阶段等待心率下降。";
        if (looksLikeDefault(load(context))) return "跑步 1 千米，随后快走恢复 200 米；按阶段顺序完成。\n距离阶段优先使用 GPS 轨迹，弱信号时同步记录实际步数。";
        return "按阶段顺序完成；可在训练计划页调整距离或时间。";
    }

    private static boolean looksLikeDefault(ArrayList<Stage> stages) {
        return stages.size() == 2 && stages.get(0).kind == Stage.Kind.RUN && stages.get(0).unit == Stage.Unit.DISTANCE && stages.get(0).target == 1000
                && stages.get(1).kind == Stage.Kind.WALK && stages.get(1).unit == Stage.Unit.DISTANCE && stages.get(1).target == 200;
    }

    private static boolean looksLikeFartlek(ArrayList<Stage> stages) {
        if (stages.size() != 12) return false;
        for (int i = 0; i < stages.size(); i += 2) {
            Stage run = stages.get(i), walk = stages.get(i + 1);
            if (run.kind != Stage.Kind.RUN || run.unit != Stage.Unit.TIME || run.target != 120
                    || walk.kind != Stage.Kind.WALK || walk.unit != Stage.Unit.TIME || walk.target != 60) return false;
        }
        return true;
    }

    public static void saveProfile(Context context, String name, String group, String requirement, ArrayList<Stage> stages) {
        if (!context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY, encode(stages)).putString(KEY_NAME, name).putString(KEY_GROUP, group)
                .putString(KEY_REQUIREMENT, requirement).putBoolean(KEY_EXPLICIT_EMPTY, false)
                .commit()) {
            throw new IllegalStateException("plan_profile_commit_failed");
        }
    }

    public static void clearProfile(Context context) {
        if (!context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .remove(KEY).remove(KEY_NAME).remove(KEY_GROUP).remove(KEY_REQUIREMENT)
                .putBoolean(KEY_EXPLICIT_EMPTY, true).commit()) {
            throw new IllegalStateException("plan_profile_clear_failed");
        }
    }

    public static boolean isExplicitlyEmpty(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_EXPLICIT_EMPTY, false);
    }
}

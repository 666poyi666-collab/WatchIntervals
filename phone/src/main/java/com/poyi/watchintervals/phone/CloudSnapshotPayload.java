package com.poyi.watchintervals.phone;

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds the same read-tool snapshots that the PC-side Watch MCP exposes. */
final class CloudSnapshotPayload {
    private CloudSnapshotPayload() {}

    static JSONObject build(JSONObject phoneStatus, JSONArray history, JSONObject sleep,
                            JSONObject library) throws Exception {
        JSONObject snapshots = new JSONObject();
        if (phoneStatus != null) snapshots.put("watch_get_status", status(phoneStatus));
        if (history != null) {
            snapshots.put("watch_list_workouts", workoutList(history));
            snapshots.put("watch_summarize_workouts", workoutSummary(history));
        }
        if (sleep != null) {
            snapshots.put("watch_get_latest_sleep", latestSleep(sleep));
            snapshots.put("watch_summarize_sleep", sleepSummary(sleep));
        }
        if (library != null) snapshots.put("watch_list_plans", planList(library));
        return snapshots;
    }

    private static JSONObject status(JSONObject phoneStatus) throws Exception {
        JSONObject phone = new JSONObject().put("service", "buxu-phone-api")
                .put("appVersion", BuildConfig.VERSION_NAME).put("authoritative", true);
        JSONObject connection = new JSONObject().put("state", "healthy")
                .put("phone", "online").put("watch", "online")
                .put("watchStatus", new JSONObject(phoneStatus.toString()));
        return new JSONObject().put("phone", phone).put("connection", connection);
    }

    private static JSONObject workoutList(JSONArray history) throws Exception {
        JSONArray items = new JSONArray();
        int count = Math.min(20, history.length());
        for (int index = 0; index < count; index++) items.put(history.get(index));
        return new JSONObject().put("items", items).put("count", count).put("total", history.length());
    }

    private static JSONObject workoutSummary(JSONArray history) throws Exception {
        double distance = 0, duration = 0, heartTotal = 0;
        long steps = 0;
        int heartCount = 0;
        for (int index = 0; index < history.length(); index++) {
            JSONObject row = history.optJSONObject(index);
            if (row == null) continue;
            distance += finite(row.optDouble("distanceMeters", 0));
            duration += finite(row.has("activeDurationMs")
                    ? row.optDouble("activeDurationMs", 0) : row.optDouble("durationMs", 0));
            steps += Math.max(0, row.optLong("steps", 0));
            double heart = finite(row.optDouble("averageHeartRate", 0));
            if (heart > 0) { heartTotal += heart; heartCount++; }
        }
        return new JSONObject().put("workoutCount", history.length())
                .put("totalDistanceMeters", distance).put("totalActiveDurationMs", duration)
                .put("totalSteps", steps)
                .put("averageHeartRate", heartCount == 0 ? JSONObject.NULL
                        : Math.round(heartTotal / heartCount))
                .put("latest", history.length() == 0 ? JSONObject.NULL : history.get(0));
    }

    private static JSONObject latestSleep(JSONObject sleep) throws Exception {
        JSONArray records = sleep.optJSONArray("records");
        JSONObject latest = null;
        long latestTimestamp = Long.MIN_VALUE;
        if (records != null) for (int index = 0; index < records.length(); index++) {
            JSONObject candidate = records.optJSONObject(index);
            if (candidate != null && candidate.optLong("timestamp", 0) >= latestTimestamp) {
                latestTimestamp = candidate.optLong("timestamp", 0);
                latest = new JSONObject(candidate.toString());
            }
        }
        if (latest != null) latest.remove("sessions");
        return new JSONObject().put("state", sleep.optString("state"))
                .put("source", sleep.optString("source"))
                .put("record", latest == null ? JSONObject.NULL : latest)
                .put("detailResource", "watch://sleep/7");
    }

    private static JSONObject sleepSummary(JSONObject sleep) throws Exception {
        JSONArray records = sleep.optJSONArray("records");
        if (records == null) records = new JSONArray();
        double duration = 0, scores = 0, spo2 = 0;
        int durationCount = 0, scoreCount = 0, spo2Count = 0;
        for (int index = 0; index < records.length(); index++) {
            JSONObject row = records.optJSONObject(index);
            if (row == null) continue;
            double value = finite(row.optDouble("totalDurationMinutes", 0));
            if (value > 0) { duration += value; durationCount++; }
            value = finite(row.optDouble("sleepScore", 0));
            if (value > 0) { scores += value; scoreCount++; }
            value = finite(row.optDouble("spo2AveragePercent", 0));
            if (value > 0) { spo2 += value; spo2Count++; }
        }
        return new JSONObject().put("state", sleep.optString("state"))
                .put("source", sleep.optString("source")).put("recordCount", records.length())
                .put("averageDurationMinutes", average(duration, durationCount, false))
                .put("averageSleepScore", average(scores, scoreCount, false))
                .put("averageSpo2Percent", average(spo2, spo2Count, true))
                .put("detailResource", "watch://sleep/7");
    }

    private static JSONObject planList(JSONObject library) throws Exception {
        return new JSONObject().put("plans", library.optJSONArray("plans") == null
                ? new JSONArray() : library.getJSONArray("plans"))
                .put("selectedPlanId", library.optString("selectedPlanId"))
                .put("revision", library.optLong("revision"));
    }

    private static Object average(double total, int count, boolean decimal) {
        if (count == 0) return JSONObject.NULL;
        double value = total / count;
        return decimal ? Math.round(value * 10.0) / 10.0 : Math.round(value);
    }

    private static double finite(double value) { return Double.isFinite(value) ? value : 0; }
}

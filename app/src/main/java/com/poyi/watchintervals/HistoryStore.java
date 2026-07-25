package com.poyi.watchintervals;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small summary index plus one append-only sample directory per workout. */
final class HistoryStore {
    private static final String LEGACY_FILE = "workout_history.json";
    private static final String LEGACY_BACKUP = "workout_history.v2.backup";
    private static final String INDEX_FILE = "workout_index.json";
    private static final String SUMMARY_FILE = "summary.json";
    private static final int MAX_RECORDS = 200;

    private HistoryStore() {}

    static synchronized List<WorkoutRecord> load(Context context) {
        migrateLegacy(context);
        reconcile(context);
        ArrayList<WorkoutRecord> records = new ArrayList<>();
        try {
            JSONArray array = readIndex(context);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) records.add(WorkoutRecord.fromJson(item));
            }
        } catch (Exception error) {
            android.util.Log.w("HistoryStore", "Unable to read workout index", error);
        }
        return records;
    }

    static synchronized void append(Context context, WorkoutRecord record) {
        appendFromActive(context, record, null);
    }

    static synchronized boolean appendFromActive(Context context, WorkoutRecord record, File activeDirectory) {
        try {
            File root = historyRoot(context);
            File target = new File(root, safeId(record.id));
            if (activeDirectory != null && activeDirectory.exists() && !activeDirectory.equals(target)) {
                record.routePointCount = countLines(new File(activeDirectory, WorkoutFileStore.ROUTE));
                WorkoutFileStore.writeAtomic(new File(activeDirectory, SUMMARY_FILE), record.toSummaryJson().toString());
                closeTargetIfNeeded(target);
                try { Files.move(activeDirectory.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE); }
                catch (Exception unsupported) { Files.move(activeDirectory.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                new File(target, WorkoutFileStore.CHECKPOINT).delete();
            } else {
                if (!target.exists() && !target.mkdirs()) throw new IllegalStateException("history_directory_failed");
                record.routePointCount = countLines(new File(target, WorkoutFileStore.ROUTE));
                WorkoutFileStore.writeAtomic(new File(target, SUMMARY_FILE), record.toSummaryJson().toString());
            }
            updateIndex(context, record, target);
            return true;
        } catch (Exception error) {
            android.util.Log.e("HistoryStore", "Unable to finalize workout", error);
            return false;
        }
    }

    static synchronized void delete(Context context, String id) {
        JSONArray next = new JSONArray();
        try {
            JSONArray index = readIndex(context);
            for (int i = 0; i < index.length(); i++) {
                JSONObject item = index.optJSONObject(i);
                if (item != null && !id.equals(item.optString("id"))) next.put(item);
            }
            writeIndex(context, next);
            WorkoutFileStore.deleteTree(new File(historyRoot(context), safeId(id)));
        } catch (Exception error) {
            android.util.Log.w("HistoryStore", "Unable to delete workout", error);
        }
    }

    static synchronized WorkoutRecord find(Context context, String id) {
        migrateLegacy(context);
        File directory = new File(historyRoot(context), safeId(id));
        File summary = new File(directory, SUMMARY_FILE);
        if (!summary.isFile()) return null;
        try {
            WorkoutRecord record = WorkoutRecord.fromJson(new JSONObject(WorkoutFileStore.readText(summary)));
            ArrayList<android.location.Location> full = WorkoutFileStore.readRoute(new File(directory, WorkoutFileStore.ROUTE), Integer.MAX_VALUE);
            record.route.addAll(WorkoutFileStore.simplify(full, 1000));
            record.routePointCount = full.size();
            record.routeTruncated = full.size() > record.route.size();
            WorkoutFileStore.readHeart(new File(directory, WorkoutFileStore.HEART), record.heartTimes, record.heartValues, 7200);
            return record;
        } catch (Exception error) {
            android.util.Log.w("HistoryStore", "Unable to read workout " + safeId(id), error);
            return null;
        }
    }

    static synchronized JSONObject routePage(Context context, String id, int cursor, int limit) {
        File file = new File(new File(historyRoot(context), safeId(id)), WorkoutFileStore.ROUTE);
        ArrayList<android.location.Location> points = WorkoutFileStore.readRoute(file, Integer.MAX_VALUE);
        JSONArray page = new JSONArray();
        int start = Math.max(0, cursor), end = Math.min(points.size(), start + Math.max(1, Math.min(1000, limit)));
        for (int i = start; i < end; i++) {
            android.location.Location p = points.get(i);
            JSONObject item = new JSONObject();
            try {
                item.put("latitude", p.getLatitude()).put("longitude", p.getLongitude()).put("time", p.getTime())
                        .put("accuracy", p.hasAccuracy() ? p.getAccuracy() : 0).put("source", p.getProvider());
                if (p.hasSpeed()) item.put("speed", p.getSpeed());
                if (p.hasAltitude()) item.put("altitude", p.getAltitude());
                page.put(item);
            } catch (Exception ignored) {}
        }
        try { return new JSONObject().put("items", page).put("cursor", start).put("nextCursor", end < points.size() ? end : JSONObject.NULL).put("total", points.size()); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    static synchronized JSONObject heartPage(Context context, String id, int cursor, int limit) {
        ArrayList<Long> times = new ArrayList<>(); ArrayList<Integer> values = new ArrayList<>();
        File file = new File(new File(historyRoot(context), safeId(id)), WorkoutFileStore.HEART);
        WorkoutFileStore.readHeart(file, times, values, Integer.MAX_VALUE);
        JSONArray page = new JSONArray();
        int start=Math.max(0,cursor),end=Math.min(times.size(),start+Math.max(1,Math.min(1000,limit)));
        for(int i=start;i<end;i++)page.put(new JSONArray().put(times.get(i)).put(values.get(i)));
        try{return new JSONObject().put("items",page).put("cursor",start).put("nextCursor",end<times.size()?end:JSONObject.NULL).put("total",times.size());}
        catch(Exception ignored){return new JSONObject();}
    }

    static synchronized JSONArray toJson(Context context) {
        JSONArray result = new JSONArray();
        for (WorkoutRecord record : load(context)) try { result.put(record.toSummaryJson()); } catch (Exception ignored) {}
        return result;
    }

    private static void updateIndex(Context context, WorkoutRecord record, File directory) throws Exception {
        JSONArray old = readIndex(context), next = new JSONArray();
        next.put(record.toSummaryJson());
        for (int i = 0; i < old.length() && next.length() < MAX_RECORDS; i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && !record.id.equals(item.optString("id"))) next.put(item);
        }
        while (old.length() > 0) {
            JSONObject item = old.optJSONObject(old.length() - 1);
            if (item == null || contains(next, item.optString("id"))) break;
            WorkoutFileStore.deleteTree(new File(historyRoot(context), safeId(item.optString("id"))));
            break;
        }
        writeIndex(context, next);
    }

    private static void migrateLegacy(Context context) {
        File legacy = new File(context.getFilesDir(), LEGACY_FILE);
        File backup = new File(context.getFilesDir(), LEGACY_BACKUP);
        if (!legacy.isFile()) {
            if (backup.isFile()) backup.delete();
            return;
        }
        try {
            JSONArray values = new JSONArray(WorkoutFileStore.readText(legacy));
            for (int i = values.length() - 1; i >= 0; i--) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                WorkoutRecord record = WorkoutRecord.fromJson(value);
                File directory = new File(historyRoot(context), safeId(record.id));
                if (!directory.exists()) directory.mkdirs();
                writeLegacySamples(directory, record);
                WorkoutFileStore.writeAtomic(new File(directory, SUMMARY_FILE), record.toSummaryJson().toString());
                updateIndex(context, record, directory);
            }
            Files.move(legacy.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            android.util.Log.w("HistoryStore", "Legacy history migration failed", error);
        }
    }

    private static void writeLegacySamples(File directory, WorkoutRecord record) throws Exception {
        StringBuilder route = new StringBuilder();
        for (android.location.Location point : record.route) {
            JSONObject item = new JSONObject().put("latitude",point.getLatitude()).put("longitude",point.getLongitude()).put("time",point.getTime()).put("source","legacy");
            if(point.hasAccuracy())item.put("accuracy",point.getAccuracy());if(point.hasSpeed())item.put("speed",point.getSpeed());if(point.hasAltitude())item.put("altitude",point.getAltitude());
            route.append(item).append('\n');
        }
        WorkoutFileStore.writeAtomic(new File(directory,WorkoutFileStore.ROUTE),route.toString());
        StringBuilder heart=new StringBuilder();for(int i=0;i<Math.min(record.heartTimes.size(),record.heartValues.size());i++)heart.append(new JSONObject().put("time",record.heartTimes.get(i)).put("value",record.heartValues.get(i))).append('\n');
        WorkoutFileStore.writeAtomic(new File(directory,WorkoutFileStore.HEART),heart.toString());
    }

    private static void reconcile(Context context) {
        File[] directories = historyRoot(context).listFiles(File::isDirectory);
        if (directories == null) return;
        try {
            ArrayList<JSONObject> summaries = new ArrayList<>();
            for (File dir : directories) {
                File summary = new File(dir, SUMMARY_FILE);
                if (!summary.isFile()) continue;
                try { summaries.add(new JSONObject(WorkoutFileStore.readText(summary))); }
                catch (Exception damaged) { android.util.Log.w("HistoryStore", "Skipping damaged summary " + dir.getName()); }
            }
            summaries.sort((left, right) -> Long.compare(right.optLong("endedAt"), left.optLong("endedAt")));
            JSONArray repaired = new JSONArray();
            java.util.HashSet<String> retained = new java.util.HashSet<>();
            for (JSONObject summary : summaries) {
                String id = summary.optString("id");
                if (id.isEmpty() || retained.contains(id)) continue;
                if (repaired.length() < MAX_RECORDS) {
                    repaired.put(summary);
                    retained.add(id);
                } else {
                    WorkoutFileStore.deleteTree(new File(historyRoot(context), safeId(id)));
                }
            }
            writeIndex(context, repaired);
        } catch (Exception error) { android.util.Log.w("HistoryStore", "Unable to reconcile workout index", error); }
    }

    private static File historyRoot(Context context) {
        File root = new File(context.getFilesDir(), WorkoutFileStore.HISTORY_ROOT);
        if (!root.exists()) root.mkdirs();
        return root;
    }
    private static JSONArray readIndex(Context context) throws Exception { File file=new File(context.getFilesDir(),INDEX_FILE);return file.isFile()?new JSONArray(WorkoutFileStore.readText(file)):new JSONArray(); }
    private static void writeIndex(Context context,JSONArray value)throws Exception{WorkoutFileStore.writeAtomic(new File(context.getFilesDir(),INDEX_FILE),value.toString());}
    private static boolean contains(JSONArray array,String id){for(int i=0;i<array.length();i++){JSONObject item=array.optJSONObject(i);if(item!=null&&id.equals(item.optString("id")))return true;}return false;}
    private static String safeId(String id){return id==null?"unknown":id.replaceAll("[^A-Za-z0-9._-]","_");}
    private static int countLines(File file){if(!file.isFile())return 0;try(java.io.BufferedReader r=Files.newBufferedReader(file.toPath(),StandardCharsets.UTF_8)){int n=0;while(r.readLine()!=null)n++;return n;}catch(Exception ignored){return 0;}}
    private static void closeTargetIfNeeded(File target){if(target.exists())WorkoutFileStore.deleteTree(target);}
}

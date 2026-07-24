package com.poyi.watchintervals;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

final class HistoryStore {
    private static final String FILE = "workout_history.json";
    private static final int MAX_RECORDS = 200;

    private HistoryStore() {}

    static synchronized List<WorkoutRecord> load(Context context) {
        ArrayList<WorkoutRecord> records = new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), FILE);
            if (!file.exists()) return records;
            JSONArray array = new JSONArray(new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item != null) records.add(WorkoutRecord.fromJson(item));
            }
        } catch (Exception ignored) { /* A damaged history file must not block training. */ }
        return records;
    }

    static synchronized void append(Context context, WorkoutRecord record) {
        ArrayList<WorkoutRecord> records = new ArrayList<>(load(context));
        records.removeIf(item -> item.id.equals(record.id));
        records.add(0, record);
        while (records.size() > MAX_RECORDS) records.remove(records.size() - 1);
        write(context, records);
    }

    static synchronized void delete(Context context, String id) {
        ArrayList<WorkoutRecord> records = new ArrayList<>(load(context));
        records.removeIf(item -> item.id.equals(id));
        write(context, records);
    }

    static synchronized WorkoutRecord find(Context context, String id) {
        for (WorkoutRecord record : load(context)) if (record.id.equals(id)) return record;
        return null;
    }

    static synchronized JSONArray toJson(Context context) {
        JSONArray result = new JSONArray();
        for (WorkoutRecord record : load(context)) {
            try { result.put(record.toJson()); } catch (Exception ignored) {}
        }
        return result;
    }

    private static void write(Context context, List<WorkoutRecord> records) {
        JSONArray array = new JSONArray();
        for (WorkoutRecord record : records) {
            try { array.put(record.toJson()); } catch (Exception ignored) {}
        }
        File target = new File(context.getFilesDir(), FILE);
        File temporary = new File(context.getFilesDir(), FILE + ".tmp");
        try {
            writeFile(temporary, array.toString());
            if (!temporary.renameTo(target)) {
                writeFile(target, array.toString());
                temporary.delete();
            }
        } catch (Exception ignored) {}
    }

    private static void writeFile(File file, String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }
}

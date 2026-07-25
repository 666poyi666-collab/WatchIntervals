package com.poyi.watchintervals;

import android.content.Context;
import android.location.Location;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;

/** Append-only samples and an atomic, bounded checkpoint for one active workout. */
final class WorkoutFileStore implements AutoCloseable {
    static final String ACTIVE_ROOT = "active_workouts";
    static final String HISTORY_ROOT = "workouts";
    static final String CHECKPOINT = "checkpoint.json";
    static final String ROUTE = "route.ndjson";
    static final String HEART = "heart.ndjson";
    private static final long FLUSH_MILLIS = 5_000L;
    private static final long SYNC_MILLIS = 15_000L;

    private final File directory;
    private final FileOutputStream routeStream;
    private final FileOutputStream heartStream;
    private final BufferedWriter routeWriter;
    private final BufferedWriter heartWriter;
    private long lastFlushAt;
    private long lastSyncAt;
    private int routePointCount;
    private int heartSampleCount;

    private WorkoutFileStore(File directory) throws Exception {
        this.directory = directory;
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("active_workout_directory_failed");
        routeStream = new FileOutputStream(new File(directory, ROUTE), true);
        heartStream = new FileOutputStream(new File(directory, HEART), true);
        routeWriter = new BufferedWriter(new OutputStreamWriter(routeStream, StandardCharsets.UTF_8));
        heartWriter = new BufferedWriter(new OutputStreamWriter(heartStream, StandardCharsets.UTF_8));
        routePointCount = countValidLines(new File(directory, ROUTE));
        heartSampleCount = countValidLines(new File(directory, HEART));
    }

    static WorkoutFileStore create(Context context, String sessionId) throws Exception {
        return new WorkoutFileStore(new File(new File(context.getFilesDir(), ACTIVE_ROOT), sessionId));
    }

    static WorkoutFileStore openRecoverable(Context context) throws Exception {
        File root = new File(context.getFilesDir(), ACTIVE_ROOT);
        File[] candidates = root.listFiles(File::isDirectory);
        if (candidates == null || candidates.length == 0) return null;
        java.util.Arrays.sort(candidates, Comparator.comparingLong(File::lastModified).reversed());
        for (File candidate : candidates) {
            if (new File(candidate, CHECKPOINT).isFile()) return new WorkoutFileStore(candidate);
        }
        return null;
    }

    static boolean hasRecoverable(Context context) {
        File root = new File(context.getFilesDir(), ACTIVE_ROOT);
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return false;
        for (File dir : dirs) if (new File(dir, CHECKPOINT).isFile()) return true;
        return false;
    }

    static JSONObject readRecoverableCheckpoint(Context context) throws Exception {
        File root = new File(context.getFilesDir(), ACTIVE_ROOT);
        File[] candidates = root.listFiles(File::isDirectory);
        if (candidates == null || candidates.length == 0) return null;
        java.util.Arrays.sort(candidates, Comparator.comparingLong(File::lastModified).reversed());
        for (File candidate : candidates) {
            File checkpoint = new File(candidate, CHECKPOINT);
            if (checkpoint.isFile()) return new JSONObject(readText(checkpoint));
        }
        return null;
    }

    String sessionId() { return directory.getName(); }
    File directory() { return directory; }
    int routePointCount() { return routePointCount; }
    int heartSampleCount() { return heartSampleCount; }

    synchronized void appendRoute(Location location, String source) throws Exception {
        JSONObject point = new JSONObject()
                .put("latitude", location.getLatitude())
                .put("longitude", location.getLongitude())
                .put("time", location.getTime())
                .put("accuracy", location.hasAccuracy() ? location.getAccuracy() : 0f)
                .put("source", source == null ? "watch_gps" : source);
        if (location.hasSpeed()) point.put("speed", location.getSpeed());
        if (location.hasAltitude()) point.put("altitude", location.getAltitude());
        routeWriter.write(point.toString());
        routeWriter.newLine();
        routePointCount++;
        maintain(false);
    }

    synchronized void appendHeart(long time, int value) throws Exception {
        heartWriter.write(new JSONObject().put("time", time).put("value", value).toString());
        heartWriter.newLine();
        heartSampleCount++;
        maintain(false);
    }

    synchronized void writeCheckpoint(JSONObject checkpoint, boolean force) throws Exception {
        maintain(force);
        checkpoint.put("sessionId", sessionId())
                .put("routePointCount", routePointCount)
                .put("heartSampleCount", heartSampleCount)
                .put("routeOffset", routeStream.getChannel().position())
                .put("heartOffset", heartStream.getChannel().position());
        writeAtomic(new File(directory, CHECKPOINT), checkpoint.toString());
    }

    JSONObject readCheckpoint() throws Exception {
        return new JSONObject(readText(new File(directory, CHECKPOINT)));
    }

    ArrayList<Location> readRoutePreview(int maximum) {
        ArrayList<Location> points = readRoute(new File(directory, ROUTE), Integer.MAX_VALUE);
        return simplify(points, maximum);
    }

    ArrayList<Location> readAllRoute() { return readRoute(new File(directory, ROUTE), Integer.MAX_VALUE); }

    void readAllHeart(ArrayList<Long> times, ArrayList<Integer> values) {
        readHeart(new File(directory, HEART), times, values, Integer.MAX_VALUE);
    }

    synchronized void discard() {
        try { close(); } catch (Exception ignored) {}
        deleteTree(directory);
    }

    @Override public synchronized void close() throws Exception {
        maintain(true);
        routeWriter.close();
        heartWriter.close();
    }

    private void maintain(boolean force) throws Exception {
        long now = android.os.SystemClock.elapsedRealtime();
        if (force || now - lastFlushAt >= FLUSH_MILLIS) {
            routeWriter.flush();
            heartWriter.flush();
            lastFlushAt = now;
        }
        if (force || now - lastSyncAt >= SYNC_MILLIS) {
            routeStream.getFD().sync();
            heartStream.getFD().sync();
            lastSyncAt = now;
        }
    }

    static ArrayList<Location> readRoute(File file, int limit) {
        ArrayList<Location> result = new ArrayList<>();
        if (!file.isFile()) return result;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && result.size() < limit) {
                try {
                    JSONObject item = new JSONObject(line);
                    Location point = new Location(item.optString("source", "history"));
                    point.setLatitude(item.getDouble("latitude"));
                    point.setLongitude(item.getDouble("longitude"));
                    point.setTime(item.optLong("time"));
                    if (item.has("accuracy")) point.setAccuracy((float)item.optDouble("accuracy"));
                    if (item.has("speed")) point.setSpeed((float)item.optDouble("speed"));
                    if (item.has("altitude")) point.setAltitude(item.optDouble("altitude"));
                    result.add(point);
                } catch (Exception ignored) { /* Ignore a damaged trailing line. */ }
            }
        } catch (Exception ignored) {}
        return result;
    }

    static void readHeart(File file, ArrayList<Long> times, ArrayList<Integer> values, int limit) {
        if (!file.isFile()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && times.size() < limit) {
                try {
                    JSONObject item = new JSONObject(line);
                    int value = item.optInt("value");
                    if (value > 0) { times.add(item.optLong("time")); values.add(value); }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    static ArrayList<Location> simplify(ArrayList<Location> source, int maximum) {
        if (maximum < 2 || source.size() <= maximum) return source;
        ArrayList<Location> current = new ArrayList<>(source);
        double tolerance = 2d;
        while (current.size() > maximum) {
            boolean[] keep = new boolean[current.size()];
            keep[0] = true; keep[current.size() - 1] = true;
            simplifySection(current, 0, current.size() - 1, tolerance, keep);
            ArrayList<Location> next = new ArrayList<>();
            for (int i = 0; i < current.size(); i++) if (keep[i]) next.add(current.get(i));
            if (next.size() >= current.size()) tolerance *= 2d;
            else current = next;
            tolerance *= 1.5d;
        }
        return current;
    }

    private static void simplifySection(ArrayList<Location> points, int first, int last, double tolerance, boolean[] keep) {
        if (last <= first + 1) return;
        Location a = points.get(first), b = points.get(last);
        float[] ab = new float[3]; Location.distanceBetween(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude(), ab);
        double max = -1d; int index = -1;
        for (int i = first + 1; i < last; i++) {
            float[] ap = new float[3], pb = new float[3];
            Location p = points.get(i);
            Location.distanceBetween(a.getLatitude(), a.getLongitude(), p.getLatitude(), p.getLongitude(), ap);
            Location.distanceBetween(p.getLatitude(), p.getLongitude(), b.getLatitude(), b.getLongitude(), pb);
            double distance = ab[0] <= 0.01 ? ap[0] : Math.max(0d, (ap[0] + pb[0] - ab[0]) / 2d);
            if (distance > max) { max = distance; index = i; }
        }
        if (index >= 0 && max > tolerance) {
            keep[index] = true;
            simplifySection(points, first, index, tolerance, keep);
            simplifySection(points, index, last, tolerance, keep);
        }
    }

    static void writeAtomic(File target, String value) throws Exception {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(temporary, false)) {
            stream.write(value.getBytes(StandardCharsets.UTF_8));
            stream.flush();
            stream.getFD().sync();
        }
        try {
            Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception unsupported) {
            Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) android.util.Log.w("WorkoutFileStore", "Unable to delete " + file.getName());
    }

    private static int countValidLines(File file) {
        int count = 0;
        if (!file.isFile()) return 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) count++;
        } catch (Exception ignored) {}
        return count;
    }
}

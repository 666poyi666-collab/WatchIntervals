package com.poyi.watchintervals.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PhoneSleepRepositoryTest {
    @Test public void shorterRefreshUpdatesMatchingNightWithoutDroppingOlderCache()
            throws Exception {
        JSONObject cached = ready(31, new JSONArray()
                .put(record(3000L, 70)).put(record(2000L, 80)).put(record(1000L, 90)));
        JSONObject incoming = ready(14, new JSONArray().put(record(3000L, 95)));

        JSONObject merged = PhoneSleepRepository.merge(cached, incoming, 9999L);

        assertEquals(31, merged.getInt("requestedDays"));
        assertEquals(9999L, merged.getLong("cachedAt"));
        JSONArray records = merged.getJSONArray("records");
        assertEquals(3, records.length());
        assertEquals(95, records.getJSONObject(0).getInt("sleepScore"));
        assertEquals(2000L, records.getJSONObject(1).getLong("timestamp"));
        assertEquals(1000L, records.getJSONObject(2).getLong("timestamp"));
        assertEquals(1, records.getJSONObject(0).getJSONArray("sessions").length());
    }

    @Test public void legacyReadyEnvelopeRemainsReadableAndCorruptionDegradesToEmpty()
            throws Exception {
        String legacy = ready(14, new JSONArray().put(record(42L, 88)))
                .put("fetchedAt", 1234L).toString();

        JSONObject decoded = PhoneSleepRepository.decode(legacy);

        assertNotNull(decoded);
        assertEquals(1, decoded.getInt("schemaVersion"));
        assertEquals(1234L, decoded.getLong("cachedAt"));
        assertEquals(42L, decoded.getJSONArray("records").getJSONObject(0)
                .getLong("timestamp"));
        assertNull(PhoneSleepRepository.decode("{broken"));
        assertNull(PhoneSleepRepository.decode(new JSONObject().put("schemaVersion", 99)
                .put("state", "ready").put("records", new JSONArray()).toString()));
    }

    @Test public void emptySuccessfulReadKeepsTheOriginalDataFreshness() throws Exception {
        JSONObject cached = ready(31, new JSONArray().put(record(42L, 88)))
                .put("cachedAt", 1234L);

        JSONObject merged = PhoneSleepRepository.merge(cached,
                ready(31, new JSONArray()), 9999L);

        assertEquals(1, merged.getJSONArray("records").length());
        assertEquals(1234L, merged.getLong("cachedAt"));
        assertEquals(9999L, merged.getLong("lastCheckedAt"));
    }

    @Test public void legacyCacheIsDeduplicatedSortedAndBoundedOnRead() throws Exception {
        JSONArray records = new JSONArray();
        for (int timestamp = 1; timestamp <= 45; timestamp++) {
            records.put(record(timestamp, timestamp));
        }
        records.put(record(45L, 99));

        JSONObject decoded = PhoneSleepRepository.decode(ready(31, records).toString());

        assertNotNull(decoded);
        JSONArray normalized = decoded.getJSONArray("records");
        assertEquals(31, normalized.length());
        assertEquals(45L, normalized.getJSONObject(0).getLong("timestamp"));
        assertEquals(15L, normalized.getJSONObject(30).getLong("timestamp"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void temporaryFailureCannotReplaceTheLastReadySnapshot() throws Exception {
        PhoneSleepRepository.merge(ready(31, new JSONArray()),
                new JSONObject().put("state", "error").put("records", new JSONArray()), 1L);
    }

    @Test public void cacheKeepsOnlyTheLatestThirtyOneNights() throws Exception {
        JSONArray incoming = new JSONArray();
        for (long timestamp = 1L; timestamp <= 40L; timestamp++) {
            incoming.put(record(timestamp, (int) timestamp));
        }

        JSONArray records = PhoneSleepRepository.merge(null, ready(31, incoming), 50L)
                .getJSONArray("records");

        assertEquals(31, records.length());
        assertEquals(40L, records.getJSONObject(0).getLong("timestamp"));
        assertEquals(10L, records.getJSONObject(30).getLong("timestamp"));
    }

    private static JSONObject ready(int days, JSONArray records) throws Exception {
        return new JSONObject().put("state", "ready").put("source", "system_healthkit")
                .put("requestedDays", days).put("records", records);
    }

    private static JSONObject record(long timestamp, int score) throws Exception {
        JSONObject stage = new JSONObject().put("type", 2).put("label", "system_2")
                .put("startTime", timestamp).put("endTime", timestamp + 60_000L);
        JSONObject session = new JSONObject().put("startTime", timestamp)
                .put("endTime", timestamp + 60_000L).put("sleepDurationMinutes", 1)
                .put("deepDurationMinutes", 0).put("lightDurationMinutes", 1)
                .put("remDurationMinutes", 0).put("awakeDurationMinutes", 0)
                .put("stages", new JSONArray().put(stage));
        return new JSONObject().put("timestamp", timestamp).put("totalDurationMinutes", 1)
                .put("sleepScore", score).put("sessions", new JSONArray().put(session));
    }
}

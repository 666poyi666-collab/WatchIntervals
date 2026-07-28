package com.poyi.watchintervals;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Test;

public class WorkoutFileStoreTest {
    @Test public void checkpointOffsetIncludesBufferedSampleBytes() throws Exception {
        File directory = Files.createTempDirectory("workout-store").toFile();
        File samples = new File(directory, "route.ndjson");
        String line = "{\"time\":1}\n";
        try (FileOutputStream stream = new FileOutputStream(samples, true);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
            writer.write(line);
            assertEquals(0L, stream.getChannel().position());

            long offset = WorkoutFileStore.flushAndPosition(writer, stream);

            assertEquals(line.getBytes(StandardCharsets.UTF_8).length, offset);
            assertEquals(offset, samples.length());
        }
        WorkoutFileStore.deleteTree(directory);
    }

    @Test public void truncatesCompleteAndPartialTailAtCheckpointBoundary() throws Exception {
        File directory = Files.createTempDirectory("workout-store").toFile();
        File samples = new File(directory, "route.ndjson");
        String committed = "{\"time\":1}\n{\"time\":2}\n";
        String tail = "{\"time\":3}\n{\"time\":";
        Files.write(samples.toPath(), (committed + tail).getBytes(StandardCharsets.UTF_8));

        long offset = WorkoutFileStore.truncateToCompleteLine(
                samples, committed.getBytes(StandardCharsets.UTF_8).length);

        assertEquals(committed.getBytes(StandardCharsets.UTF_8).length, offset);
        assertEquals(committed, new String(Files.readAllBytes(samples.toPath()), StandardCharsets.UTF_8));
        WorkoutFileStore.deleteTree(directory);
    }

    @Test public void movesInvalidOffsetBackToPreviousCompleteLine() throws Exception {
        File directory = Files.createTempDirectory("workout-store").toFile();
        File samples = new File(directory, "heart.ndjson");
        String first = "{\"time\":1,\"value\":80}\n";
        String second = "{\"time\":2,\"value\":81}\n";
        Files.write(samples.toPath(), (first + second).getBytes(StandardCharsets.UTF_8));

        long requested = first.getBytes(StandardCharsets.UTF_8).length + 5L;
        long offset = WorkoutFileStore.truncateToCompleteLine(samples, requested);

        assertEquals(first.getBytes(StandardCharsets.UTF_8).length, offset);
        assertEquals(first, new String(Files.readAllBytes(samples.toPath()), StandardCharsets.UTF_8));
        WorkoutFileStore.deleteTree(directory);
    }

    @Test public void recentHeartWindowKeepsOnlyValidLatestLines() throws Exception {
        File directory = Files.createTempDirectory("workout-store").toFile();
        File samples = new File(directory, "heart.ndjson");
        String content = "{\"time\":1,\"value\":80}\n"
                + "damaged\n"
                + "{\"time\":2,\"value\":81}\n"
                + "{\"time\":3,\"value\":82}\n"
                + "{\"time\":4,\"value\":999}\n"
                + "{\"time\":5,\"value\":241}\n"
                + "{\"time\":6,\"value\":24}\n";
        Files.write(samples.toPath(), content.getBytes(StandardCharsets.UTF_8));

        WorkoutFileStore.HeartWindow window = WorkoutFileStore.readRecentHeart(samples, 2);
        assertEquals(Arrays.asList(2L, 3L), window.times);
        assertEquals(Arrays.asList(81, 82), window.values);

        WorkoutFileStore.deleteTree(directory);
    }
}

package com.poyi.watchintervals;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class WorkoutFileStoreTest {
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
}

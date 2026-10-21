package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.labels.pb.JfrLabels;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Creates {@link SnapshotFile}s for tests outside this package.
 */
public final class TestSnapshotFiles {
    private final Path source;
    private final SnapshotFileRotation rotation;

    public TestSnapshotFiles(Path dir, int slots) {
        this.source = dir.resolve("pyroscope.jfr");
        this.rotation = new SnapshotFileRotation(source, slots);
    }

    public SnapshotFile take(byte[] data) throws IOException {
        Files.write(source, data);
        return rotation.take(Format.JFR, EventType.ITIMER, Instant.EPOCH, Instant.EPOCH,
            JfrLabels.LabelsSnapshot.getDefaultInstance());
    }

    public Path source() {
        return source;
    }
}

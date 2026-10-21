package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.labels.pb.JfrLabels;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.time.Instant;

/**
 * A {@link Snapshot} whose profile data is kept in a file instead of in memory. {@link #data} is always {@code null}.
 * <p>
 * The file is one of a fixed number of rotating slots. Once a newer snapshot reuses the slot, this snapshot is
 * superseded: {@link #open()} returns {@code null} and the data is gone.
 */
public final class SnapshotFile extends Snapshot implements Closeable {
    private final SnapshotFileRotation rotation;
    final int slot;
    final Path file;

    SnapshotFile(Format format, EventType eventType, Instant started, Instant ended,
                 JfrLabels.LabelsSnapshot labels, SnapshotFileRotation rotation, int slot, Path file) {
        super(format, eventType, started, ended, null, labels);
        this.rotation = rotation;
        this.slot = slot;
        this.file = file;
    }

    @NotNull
    public Path file() {
        return file;
    }

    /**
     * Opens the profile data for reading. The returned channel stays readable even if the snapshot is superseded
     * while it is open (on filesystems that allow replacing open files).
     *
     * @return channel positioned at the start of the data, or {@code null} if the snapshot has been superseded or
     * released
     */
    @Nullable
    public FileChannel open() throws IOException {
        return rotation.open(this);
    }

    /**
     * Deletes the file unless the slot has already been reused by a newer snapshot.
     */
    public void close() {
        rotation.release(this);
    }
}

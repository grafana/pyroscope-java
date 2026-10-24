package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.labels.pb.JfrLabels;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Moves a finished profiler output file to {@code <source>.<n>}, where {@code n} cycles through a fixed number of
 * slots. Even if releasing fails, at most that many files exist on disk.
 * <p>
 * Moving into a slot supersedes the snapshot that previously owned it. All slot operations are serialized so that a
 * reader can never open a file that has already been handed to a newer snapshot.
 * <p>
 * Every file is registered with {@link java.io.File#deleteOnExit()}. The JVM deletes registered files in reverse
 * order, so they are removed before the directory, if the caller registered that first.
 */
final class SnapshotFileRotation {
    private final Path source;
    private final Path[] slotPaths;
    private final SnapshotFile[] owners;
    private int next;

    SnapshotFileRotation(Path source, int slots) {
        if (slots < 1) {
            throw new IllegalArgumentException("slots must be positive");
        }
        this.source = source;
        this.slotPaths = new Path[slots];
        this.owners = new SnapshotFile[slots];
        for (int slot = 0; slot < slots; slot++) {
            slotPaths[slot] = source.resolveSibling(source.getFileName() + "." + slot);
            slotPaths[slot].toFile().deleteOnExit();
        }
    }

    /**
     * Checks that files in {@code dir} can be atomically replaced, which {@link #take} relies on.
     *
     * @throws IOException if the filesystem does not support it
     */
    static void checkAtomicReplace(Path dir) throws IOException {
        Path probe = dir.resolve("probe");
        Path target = dir.resolve("probe.target");
        probe.toFile().deleteOnExit();
        target.toFile().deleteOnExit();
        try {
            Files.write(probe, new byte[0]);
            Files.write(target, new byte[0]);
            Files.move(probe, target, REPLACE_EXISTING, ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(probe);
            Files.deleteIfExists(target);
        }
    }

    /**
     * Moves the source file into the next slot.
     *
     * @throws IOException if the file could not be moved; the source file is left in place
     */
    synchronized SnapshotFile take(Format format, EventType eventType, Instant started, Instant ended,
                                   JfrLabels.LabelsSnapshot labels) throws IOException {
        int slot = next;
        Path target = slotPaths[slot];
        Files.move(source, target, REPLACE_EXISTING, ATOMIC_MOVE);
        next = (slot + 1) % owners.length;
        SnapshotFile snapshot = new SnapshotFile(format, eventType, started, ended, labels, this, slot, target);
        owners[slot] = snapshot;
        return snapshot;
    }

    synchronized FileChannel open(SnapshotFile snapshot) throws IOException {
        if (owners[snapshot.slot] != snapshot) {
            return null;
        }
        return FileChannel.open(snapshot.file, StandardOpenOption.READ);
    }

    synchronized void release(SnapshotFile snapshot) {
        if (owners[snapshot.slot] != snapshot) {
            return;
        }
        owners[snapshot.slot] = null;
        try {
            Files.deleteIfExists(snapshot.file);
        } catch (IOException ignored) {
            // the slot is overwritten by a later snapshot anyway
        }
    }
}

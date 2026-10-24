package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.labels.pb.JfrLabels;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Moves a finished profiler output file to {@code <source>.<n>}, using a free slot or else the one holding the oldest
 * snapshot. Even if releasing fails, at most as many files as there are slots exist on disk.
 * <p>
 * Moving into a slot supersedes the snapshot that previously owned it. All slot operations are serialized so that a
 * reader can never open a file that has already been handed to a newer snapshot.
 * <p>
 * Every file is registered with {@link java.io.File#deleteOnExit()}. The JVM deletes registered files in reverse
 * order, so they are removed before the directory, if the caller registered that first.
 */
final class SnapshotFileRotation {
    private final Path source;
    private Path[] slotPaths;
    private SnapshotFile[] owners;
    private long[] takenAt;
    private long taken;

    SnapshotFileRotation(Path source, int slots) {
        checkSlots(slots);
        this.source = source;
        this.slotPaths = new Path[0];
        this.owners = new SnapshotFile[0];
        this.takenAt = new long[0];
        resize(slots);
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
     * Changes the number of slots. Snapshots in removed slots are superseded and their files deleted.
     */
    synchronized void resize(int slots) {
        checkSlots(slots);
        int oldSlots = owners.length;
        for (int slot = slots; slot < oldSlots; slot++) {
            owners[slot] = null;
            deleteQuietly(slotPaths[slot]);
        }
        slotPaths = Arrays.copyOf(slotPaths, slots);
        owners = Arrays.copyOf(owners, slots);
        takenAt = Arrays.copyOf(takenAt, slots);
        for (int slot = oldSlots; slot < slots; slot++) {
            slotPaths[slot] = source.resolveSibling(source.getFileName() + "." + slot);
            slotPaths[slot].toFile().deleteOnExit();
        }
    }

    /**
     * Moves the source file into the next slot.
     *
     * @throws IOException if the file could not be moved; the source file is left in place
     */
    synchronized SnapshotFile take(Format format, EventType eventType, Instant started, Instant ended,
                                   JfrLabels.LabelsSnapshot labels) throws IOException {
        int slot = nextSlot();
        Path target = slotPaths[slot];
        Files.move(source, target, REPLACE_EXISTING, ATOMIC_MOVE);
        takenAt[slot] = ++taken;
        SnapshotFile snapshot = new SnapshotFile(format, eventType, started, ended, labels, this, slot, target);
        owners[slot] = snapshot;
        return snapshot;
    }

    /**
     * Supersedes all snapshots and deletes their files.
     */
    synchronized void close() {
        for (int slot = 0; slot < owners.length; slot++) {
            owners[slot] = null;
            deleteQuietly(slotPaths[slot]);
        }
    }

    synchronized FileChannel open(SnapshotFile snapshot) throws IOException {
        if (!owns(snapshot)) {
            return null;
        }
        return FileChannel.open(snapshot.file, StandardOpenOption.READ);
    }

    synchronized void release(SnapshotFile snapshot) {
        if (!owns(snapshot)) {
            return;
        }
        owners[snapshot.slot] = null;
        deleteQuietly(snapshot.file);
    }

    private int nextSlot() {
        int oldest = 0;
        for (int slot = 0; slot < owners.length; slot++) {
            if (owners[slot] == null) {
                return slot;
            }
            if (takenAt[slot] < takenAt[oldest]) {
                oldest = slot;
            }
        }
        return oldest;
    }

    private boolean owns(SnapshotFile snapshot) {
        return snapshot.slot < owners.length && owners[snapshot.slot] == snapshot;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // the slot is overwritten by a later snapshot anyway
        }
    }

    private static void checkSlots(int slots) {
        if (slots < 1) {
            throw new IllegalArgumentException("slots must be positive");
        }
    }
}

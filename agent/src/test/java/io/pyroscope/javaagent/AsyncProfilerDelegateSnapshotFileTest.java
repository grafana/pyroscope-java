package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

class AsyncProfilerDelegateSnapshotFileTest {
    private static final Logger NOOP_LOGGER = (level, msg, args) -> {};

    @TempDir
    Path tempDir;

    @Test
    void jfrRecordingIsMovedAsideAndRecreatedOnNextStart() throws Exception {
        AsyncProfilerDelegate delegate = newDelegate();
        delegate.enableSnapshotFiles(2, NOOP_LOGGER);

        SnapshotFile first = record(delegate);
        byte[] firstData = Files.readAllBytes(first.file());
        SnapshotFile second = record(delegate);
        byte[] secondData = Files.readAllBytes(second.file());

        assertNotEquals(first.file(), second.file());
        assertJfr(firstData);
        assertJfr(secondData);
        assertArrayEquals(firstData, Files.readAllBytes(first.file()),
            "the next recording must not write into the moved file");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void recordingsStayInOwnerOnlyDirectory() throws Exception {
        AsyncProfilerDelegate delegate = newDelegate();
        delegate.enableSnapshotFiles(2, NOOP_LOGGER);

        SnapshotFile first = record(delegate);
        SnapshotFile second = record(delegate);

        Path dir = jfrFile(delegate).getParent();
        assertEquals(tempDir, dir.getParent());
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
        assertEquals(dir, first.file().getParent());
        assertEquals(dir, second.file().getParent());
    }

    @Test
    void fallsBackToMemoryAndWarnsWhenMoveFails() throws Exception {
        AsyncProfilerDelegate delegate = newDelegate();
        List<Logger.Level> levels = Collections.synchronizedList(new ArrayList<>());
        delegate.enableSnapshotFiles(2, (level, msg, args) -> levels.add(level));
        Path jfrFile = jfrFile(delegate);
        Path slot = jfrFile.resolveSibling(jfrFile.getFileName() + ".0");
        Files.createDirectory(slot);
        Files.createFile(slot.resolve("blocker"));

        delegate.start();
        burnCpu(200);
        delegate.stop();
        Snapshot snapshot = delegate.dumpProfile(Instant.now(), Instant.now());

        assertFalse(snapshot instanceof SnapshotFile);
        assertJfr(snapshot.data);
        assertEquals(Collections.singletonList(Logger.Level.WARN), levels);
    }

    @Test
    void enablingAgainResizesTheExistingRotation() throws Exception {
        AsyncProfilerDelegate delegate = newDelegate();
        delegate.enableSnapshotFiles(3, NOOP_LOGGER);
        SnapshotFile first = record(delegate);
        SnapshotFile second = record(delegate);
        SnapshotFile third = record(delegate);

        delegate.enableSnapshotFiles(1, NOOP_LOGGER);

        assertTrue(Files.exists(first.file()), "the remaining slot keeps its snapshot");
        try (FileChannel channel = first.open()) {
            assertNotNull(channel);
        }
        assertFalse(Files.exists(second.file()));
        assertFalse(Files.exists(third.file()));
        assertNull(second.open());
        assertNull(third.open());
        assertEquals(first.file(), record(delegate).file());
        assertNull(first.open(), "the old snapshot is superseded by the resized rotation, not by a second one");
    }

    private AsyncProfilerDelegate newDelegate() {
        Config config = new Config.Builder()
            .setFormat(Format.JFR)
            .setProfilingEvent(EventType.ITIMER)
            .setTmpDir(tempDir.toString())
            .build();
        return new AsyncProfilerDelegate(config);
    }

    private static Path jfrFile(AsyncProfilerDelegate delegate) throws ReflectiveOperationException {
        Field field = AsyncProfilerDelegate.class.getDeclaredField("tempJFRFile");
        field.setAccessible(true);
        return ((File) field.get(delegate)).toPath();
    }

    private static SnapshotFile record(AsyncProfilerDelegate delegate) {
        delegate.start();
        burnCpu(200);
        delegate.stop();
        Snapshot snapshot = delegate.dumpProfile(Instant.now(), Instant.now());
        return assertInstanceOf(SnapshotFile.class, snapshot);
    }

    private static void assertJfr(byte[] data) {
        assertTrue(data.length > 4, "JFR recording is empty");
        assertEquals("FLR\0", new String(data, 0, 4, US_ASCII));
    }

    private static void burnCpu(long millis) {
        long end = System.nanoTime() + millis * 1_000_000;
        long x = 0;
        while (System.nanoTime() < end) {
            x += Long.numberOfTrailingZeros(x + 1);
        }
        assertTrue(x >= 0);
    }
}

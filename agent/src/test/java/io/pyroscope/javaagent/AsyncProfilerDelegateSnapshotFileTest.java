package io.pyroscope.javaagent;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

class AsyncProfilerDelegateSnapshotFileTest {

    @TempDir
    Path tempDir;

    @Test
    void jfrRecordingIsMovedAsideAndRecreatedOnNextStart() throws Exception {
        Config config = new Config.Builder()
            .setFormat(Format.JFR)
            .setProfilingEvent(EventType.ITIMER)
            .setTmpDir(tempDir.toString())
            .build();
        AsyncProfilerDelegate delegate = new AsyncProfilerDelegate(config);
        delegate.enableSnapshotFiles(2);

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

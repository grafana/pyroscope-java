package io.pyroscope.javaagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotFileRotationTest {

    @TempDir
    Path tempDir;

    @Test
    void movesSourceIntoRotatingSlots() throws IOException {
        TestSnapshotFiles files = new TestSnapshotFiles(tempDir, 2);

        SnapshotFile a = files.take(bytes("a"));
        SnapshotFile b = files.take(bytes("b"));
        SnapshotFile c = files.take(bytes("c"));

        assertFalse(Files.exists(files.source()));
        assertEquals(tempDir.resolve("pyroscope.jfr.0"), a.file());
        assertEquals(tempDir.resolve("pyroscope.jfr.1"), b.file());
        assertEquals(a.file(), c.file());
        assertNull(c.data);
        assertEquals("c", new String(Files.readAllBytes(c.file()), UTF_8));
        try (Stream<Path> dir = Files.list(tempDir)) {
            assertEquals(2, dir.count());
        }
    }

    @Test
    void supersededSnapshotCannotBeOpenedOrDeleted() throws IOException {
        TestSnapshotFiles files = new TestSnapshotFiles(tempDir, 1);

        SnapshotFile a = files.take(bytes("a"));
        SnapshotFile b = files.take(bytes("b"));

        assertNull(a.open());
        a.close();
        assertTrue(Files.exists(b.file()), "superseded snapshot must not delete the newer file");
        try (FileChannel channel = b.open()) {
            assertNotNull(channel);
            assertEquals("b", read(channel));
        }
    }

    @Test
    void closeDeletesFile() throws IOException {
        TestSnapshotFiles files = new TestSnapshotFiles(tempDir, 2);

        SnapshotFile a = files.take(bytes("a"));
        a.close();

        assertFalse(Files.exists(a.file()));
        assertNull(a.open());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void openChannelKeepsDataWhenSuperseded() throws IOException {
        TestSnapshotFiles files = new TestSnapshotFiles(tempDir, 1);

        SnapshotFile a = files.take(bytes("a"));
        try (FileChannel channel = a.open()) {
            assertNotNull(channel);
            files.take(bytes("b"));
            assertEquals("a", read(channel));
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(UTF_8);
    }

    private static String read(FileChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, buffer.position()) < 0) {
                break;
            }
        }
        return new String(buffer.array(), 0, buffer.position(), UTF_8);
    }
}

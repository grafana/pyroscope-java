package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.SnapshotFile;
import io.pyroscope.javaagent.TestSnapshotFiles;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class QueuedExporterTest {
    private static final Logger NOOP_LOGGER = (level, msg, args) -> {};

    @TempDir
    Path tempDir;

    @Test
    void retryingUploadKeepsItsFileWhenQueueIsFull() throws Exception {
        BlockingExporter impl = new BlockingExporter();
        QueuedExporter exporter = new QueuedExporter(config(2), impl, NOOP_LOGGER);
        TestSnapshotFiles files = new TestSnapshotFiles(tempDir, exporter.maxLiveSnapshots());
        try {
            exporter.export(files.take(new byte[] {0}));
            assertTrue(impl.entered.await(5, TimeUnit.SECONDS));
            exporter.export(files.take(new byte[] {1}));
            exporter.export(files.take(new byte[] {2}));
            exporter.export(files.take(new byte[] {3}));

            impl.release.countDown();
            assertTrue(impl.opened.await(5, TimeUnit.SECONDS));
            assertTrue(impl.firstOpenSucceeded.get(), "the snapshot being uploaded must not be superseded");
        } finally {
            exporter.stop();
        }
    }

    private static Config config(int pushQueueCapacity) {
        return new Config.Builder().setPushQueueCapacity(pushQueueCapacity).build();
    }

    /**
     * Holds the first snapshot as if its upload were being retried, then opens it like {@link PyroscopeExporter}.
     */
    private static final class BlockingExporter implements Exporter {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch opened = new CountDownLatch(1);
        final AtomicBoolean firstOpenSucceeded = new AtomicBoolean();

        @Override
        public void export(@NotNull Snapshot snapshot) {
            SnapshotFile file = (SnapshotFile) snapshot;
            try {
                if (entered.getCount() > 0) {
                    entered.countDown();
                    release.await();
                    try (FileChannel channel = file.open()) {
                        firstOpenSucceeded.set(channel != null);
                    }
                    opened.countDown();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                file.close();
            }
        }

        @Override
        public void stop() {
        }
    }
}

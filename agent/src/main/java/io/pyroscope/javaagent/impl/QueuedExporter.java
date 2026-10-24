package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.OverfillQueue;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.SnapshotFile;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import org.jetbrains.annotations.NotNull;

public class QueuedExporter implements Exporter {
    final Exporter impl;
    final Logger logger;
    private final Thread thread;
    private final OverfillQueue<Snapshot> queue;
    private final int capacity;
    private volatile Snapshot exporting;
    private volatile boolean stopped;

    public QueuedExporter(Config config, Exporter impl, Logger logger) {
        this.impl = impl;
        this.logger = logger;
        this.thread = new Thread(this::exportLoop);
        this.thread.setDaemon(true);
        this.capacity = config.pushQueueCapacity;
        this.queue = new OverfillQueue<>(capacity);

        this.thread.start();
    }

    /**
     * The most snapshots that can be alive at once: a full queue, the one being exported, and a new one being
     * created before it is passed to {@link #export}.
     */
    public int maxLiveSnapshots() {
        return capacity + 2;
    }

    private void exportLoop() {
        logger.log(Logger.Level.DEBUG, "Uploading started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final Snapshot snapshot = queue.take();
                exporting = snapshot;
                try {
                    impl.export(snapshot);
                } finally {
                    exporting = null;
                }
            }
        } catch (final InterruptedException e) {
            logger.log(Logger.Level.DEBUG, "Uploading interrupted");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void export(@NotNull Snapshot snapshot) {
        if (stopped) {
            close(snapshot);
            return;
        }
        try {
            close(queue.put(snapshot));
        } catch (final InterruptedException ignored) {
            close(snapshot);
            Thread.currentThread().interrupt();
        }
        if (stopped) {
            closeQueued();
        }
    }

    /**
     * Stops exporting and deletes the files of all pending snapshots. An upload in progress keeps reading its already
     * opened file.
     */
    @Override
    public void stop() {
        stopped = true;
        try {
            this.thread.interrupt();
        } catch (Exception e) {
            logger.log(Logger.Level.ERROR, "Error stopping thread", e);
        }
        closeQueued();
        close(exporting);
    }

    private void closeQueued() {
        for (Snapshot snapshot : queue.drain()) {
            close(snapshot);
        }
    }

    private static void close(Snapshot snapshot) {
        if (snapshot instanceof SnapshotFile) {
            ((SnapshotFile) snapshot).close();
        }
    }
}

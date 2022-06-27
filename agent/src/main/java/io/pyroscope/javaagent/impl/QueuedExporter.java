package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.OverfillQueue;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;

public class QueuedExporter implements Exporter {
    final Exporter impl;
    final Logger logger;
    private final Thread thread;
    private final OverfillQueue<Snapshot> queue;

    public QueuedExporter(Config config, Exporter impl, Logger logger) {
        this.impl = impl;
        this.logger = logger;
        this.thread = new Thread(new Runnable() {
            @Override
            public void run() {
                exportLoop();
            }
        });
        this.queue = new OverfillQueue<>(config.pushQueueCapacity);

        this.thread.start();
    }

    private void exportLoop() {
        logger.log(Logger.Level.DEBUG, "Uploading started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final Snapshot snapshot = queue.take();
                impl.export(snapshot);
            }
        } catch (final InterruptedException e) {
            logger.log(Logger.Level.DEBUG, "Uploading interrupted");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void export(Snapshot snapshot) {
        try {
            queue.put(snapshot);
        } catch (final InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

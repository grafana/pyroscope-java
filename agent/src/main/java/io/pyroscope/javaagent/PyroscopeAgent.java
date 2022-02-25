package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;
import one.profiler.Events;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedNoReferenceMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.util.PropertiesUtil;

import java.lang.instrument.Instrumentation;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PyroscopeAgent {
    // The number of snapshots simultaneously stored in memory is limited by this.
    // The number is fairly arbitrary. If an average snapshot is 5KB, it's about 1 MB.
    private static final int UPLOAD_QUEUE_CAPACITY = 200;
    private static final OverfillQueue<Snapshot> uploadQueue = new OverfillQueue<>(UPLOAD_QUEUE_CAPACITY);

    public static void premain(final String agentArgs,
                               final Instrumentation inst) {
        final Config config;
        final Logger logger;
        try {
            Profiler.init();
            config = Config.build();

            logger = new SimpleLogger(
                    "PyroscopeAgent", config.logLevel,
                    false, true, true, false,
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    ParameterizedNoReferenceMessageFactory.INSTANCE,
                    new PropertiesUtil(new Properties()),
                    System.err);
        } catch (final Throwable e) {
            PreConfigLogger.LOGGER.error("Error starting profiler", e);
            return;
        }

        try {
            final Profiler profiler = new Profiler(logger, config.profilingEvent, config.profilingInterval);

            final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            profiler.start();

            final Runnable dumpProfile = () -> {
                try {
                    uploadQueue.put(profiler.dump());
                } catch (final InterruptedException ignored) {
                    // It's fine to swallow InterruptedException here and exit.
                    // It's a cue to end the work and exit and we have nothing to clean up.
                }
            };
            executor.scheduleAtFixedRate(dumpProfile,
                    config.uploadInterval.toMillis(), config.uploadInterval.toMillis(), TimeUnit.MILLISECONDS);

            final Thread uploaderThread = new Thread(new Uploader(logger, uploadQueue, config));
            uploaderThread.setDaemon(true);
            uploaderThread.start();

            // Stop profiling whenever the main program stops.
            final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
            ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
            while (threadGroup.getParent() != null)
                threadGroup = threadGroup.getParent();
            final int threadCount = threadGroup.activeCount() + 1;
            final Runnable maybeShutdown = () -> {
                ThreadGroup group = Thread.currentThread().getThreadGroup();
                while (group.getParent() != null)
                    group = group.getParent();
                if (group.activeCount() == threadCount) {
                    executor.shutdown();
                    watchdog.shutdown();
                    uploaderThread.interrupt();
                }
            };
            watchdog.scheduleAtFixedRate(maybeShutdown, 1, 1, TimeUnit.SECONDS);
        } catch (final Throwable e) {
            logger.error("Error starting profiler", e);
        }
    }
}

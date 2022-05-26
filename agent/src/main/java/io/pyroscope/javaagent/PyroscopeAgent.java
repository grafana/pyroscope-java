package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedNoReferenceMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.util.PropertiesUtil;

import java.lang.instrument.Instrumentation;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class PyroscopeAgent {

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
        logger.debug("Config {}", config);

        final OverfillQueue<Snapshot> pushQueue = new OverfillQueue<>(config.pushQueueCapacity);


        try {
            final Profiler profiler = new Profiler(
                    logger,
                    config.profilingEvent,
                    config.profilingAlloc,
                    config.profilingLock,
                    config.profilingInterval,
                    config.format);

            final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                    public Thread newThread(Runnable r) {
                        Thread t = Executors.defaultThreadFactory().newThread(r);
                        t.setDaemon(true);
                        return t;
                    }
                });
            profiler.start();

            final Runnable dumpProfile = () -> {
                try {
                    pushQueue.put(profiler.dump());
                } catch (final InterruptedException ignored) {
                    // It's fine to swallow InterruptedException here and exit.
                    // It's a cue to end the work and exit and we have nothing to clean up.
                }
            };
            executor.scheduleAtFixedRate(dumpProfile,
                    config.uploadInterval.toMillis(), config.uploadInterval.toMillis(), TimeUnit.MILLISECONDS);

            final Thread uploaderThread = new Thread(new Uploader(logger, pushQueue, config));
            uploaderThread.setDaemon(true);
            uploaderThread.start();
        } catch (final Throwable e) {
            logger.error("Error starting profiler", e);
        }
    }
}

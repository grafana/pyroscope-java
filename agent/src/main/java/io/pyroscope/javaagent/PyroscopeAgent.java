package io.pyroscope.javaagent;

import io.pyroscope.javaagent.config.Config;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedNoReferenceMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.util.PropertiesUtil;

import java.lang.instrument.Instrumentation;
import java.util.Properties;

public class PyroscopeAgent {

    public static void premain(final String agentArgs,
                               final Instrumentation inst) {
        final Config config;
        final Logger logger;
        try {
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

            final ProfilingScheduler scheduler = new ProfilingScheduler(config, profiler, pushQueue);
            scheduler.start();
            logger.info("Profiling started");

            final Thread uploaderThread = new Thread(new Uploader(logger, pushQueue, config));
            uploaderThread.setDaemon(true);
            uploaderThread.start();
        } catch (final Throwable e) {
            logger.error("Error starting profiler", e);
        }
    }
}

package io.pyroscope.javaagent;

import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.impl.*;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

public class PyroscopeAgent {
    private static final AtomicBoolean started = new AtomicBoolean(false);

    public static void premain(final String agentArgs,
                               final Instrumentation inst) {
        final Config config;
        try {
            config = Config.build(DefaultConfigurationProvider.INSTANCE);
        } catch (final Throwable e) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.ERROR, "Error starting profiler %s", e);
            return;
        }
        start(config);
    }

    public static void start() {
        start(new Config.Builder().build());
    }

    public static void start(Config config) {
        start(new Options.Builder(config).build());
    }

    public static void start(Options options) {
        Logger logger = options.logger;
        if (!started.compareAndSet(false, true)) {
            logger.log(Logger.Level.ERROR, "Failed to start profiling - already started");
            return;
        }
        logger.log(Logger.Level.DEBUG, "Config %s", options.config);
        try {
            options.scheduler.start(options.profiler);
            logger.log(Logger.Level.INFO, "Profiling started");
        } catch (final Throwable e) {
            logger.log(Logger.Level.ERROR, "Error starting profiler %s", e);
        }
    }

    /**
     * Options allow to swap pyroscope components:
     * - io.pyroscope.javaagent.api.ProfilingScheduler
     * - org.apache.logging.log4j.Logger
     * - io.pyroscope.javaagent.api.Exporter for io.pyroscope.javaagent.impl.ContinuousProfilingScheduler
     */
    public static class Options {
        final Config config;
        final ProfilingScheduler scheduler;
        final Logger logger;
        final Profiler profiler;

        private Options(Builder b) {
            this.config = b.config;
            this.profiler = b.profiler;
            this.scheduler = b.scheduler;
            this.logger = b.logger;
        }

        public static class Builder {
            final Config config;
            final Profiler profiler;
            Exporter exporter;
            ProfilingScheduler scheduler;
            Logger logger;

            public Builder(Config config) {
                this.config = config;
                this.profiler = new Profiler(config);
            }

            public Builder setExporter(Exporter exporter) {
                this.exporter = exporter;
                return this;
            }

            public Builder setScheduler(ProfilingScheduler scheduler) {
                this.scheduler = scheduler;
                return this;
            }

            public Builder setLogger(Logger logger) {
                this.logger = logger;
                return this;
            }

            public Options build() {
                if (logger == null) {
                    logger = new DefaultLogger(config.logLevel, System.err);
                }
                if (scheduler == null) {
                    if (exporter == null) {
                        exporter = new QueuedExporter(config, new PyroscopeExporter(config, logger), logger);
                    }
                    if (config.samplingDuration == null) {
                        scheduler = new ContinuousProfilingScheduler(config, exporter);
                    } else {
                        scheduler = new SamplingProfilingScheduler(config, exporter);
                    }
                }
                return new Options(this);
            }
        }

    }

}

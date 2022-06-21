package io.pyroscope.javaagent;

import io.pyroscope.javaagent.api.ConfigurationProvider;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.impl.ContinuousProfilingScheduler;
import io.pyroscope.javaagent.impl.DefaultConfigurationProvider;
import io.pyroscope.javaagent.impl.PyroscopeExporter;
import org.apache.logging.log4j.Logger;

import java.lang.instrument.Instrumentation;

public class PyroscopeAgent {

    public static void premain(final String agentArgs,
                               final Instrumentation inst) {
        final Config config;
        try {
            config = Config.build(new DefaultConfigurationProvider());
        } catch (final Throwable e) {
            LoggerUtils.PRECONFIG_LOGGER.error("Error starting profiler", e);
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
        logger.debug("Config {}", options.config);
        try {
            options.scheduler.start(options.profiler);
            logger.info("Profiling started");
        } catch (final Throwable e) {
            logger.error("Error starting profiler", e);
        }
    }

    public static class Options {
        final Config config;
        final Exporter exporter;
        final ProfilingScheduler scheduler;
        final Logger logger;
        final Profiler profiler;

        private Options(Builder b) {
            this.config = b.config;
            this.profiler = b.profiler;
            this.exporter = b.exporter;
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
                    logger = LoggerUtils.createDefaultPyroscopeLogger(config.logLevel);
                }
                if (exporter == null) {
                    exporter = new PyroscopeExporter(config, logger);
                }
                if (scheduler == null) {
                    scheduler = new ContinuousProfilingScheduler(config, exporter);
                }
                return new Options(this);
            }
        }

    }

}

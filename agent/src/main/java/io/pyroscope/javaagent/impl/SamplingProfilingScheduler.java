package io.pyroscope.javaagent.impl;

import static io.pyroscope.javaagent.DateUtils.truncate;

import io.pyroscope.javaagent.Profiler;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedule profiling in sampling mode.
 * <p>
 * WARNING: still experimental, may go away or behavior may change
 */
public class SamplingProfilingScheduler implements ProfilingScheduler {

    private final Config config;
    private final Exporter exporter;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setName("PyroscopeProfilingScheduler_Sampling");
        t.setDaemon(true);
        return t;
    });

    public SamplingProfilingScheduler(Config config, Exporter exporter) {
        this.config = config;
        this.exporter = exporter;
    }

    @Override
    public void start(Profiler profiler) {
        final long samplingDurationMillis = config.samplingDuration.toMillis();
        final Duration uploadInterval = config.uploadInterval;
        final Runnable dumpProfile = () -> {
            Instant profilingStartTime = Instant.now();
            profiler.start();
            try {
                Thread.sleep(samplingDurationMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            profiler.stop();

            Snapshot snapshot = profiler.dumpProfile(truncate(profilingStartTime, uploadInterval));
            exporter.export(snapshot);
        };

        Duration initialDelay = getInitialDelay();
        executor.scheduleAtFixedRate(
            dumpProfile,
            initialDelay.toMillis(),
            config.uploadInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    private Duration getInitialDelay() {
        Instant now = Instant.now();
        Instant prevUploadInterval = truncate(now, config.uploadInterval);
        Instant nextUploadInterval = prevUploadInterval.plus(config.uploadInterval);
        Duration initialDelay = Duration.between(now, nextUploadInterval);
        return initialDelay;
    }
}

package io.pyroscope.javaagent.impl;


import io.pyroscope.javaagent.Profiler;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
import kotlin.random.Random;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedule profiling in sampling mode.
 * <p>
 * WARNING: still experimental, may go away or behavior may change
 */
public class SamplingProfilingScheduler implements ProfilingScheduler {

    private final Config config;
    private final Exporter exporter;
    private Logger logger;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setName("PyroscopeProfilingScheduler_Sampling");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> job;

    public SamplingProfilingScheduler(Config config, Exporter exporter, Logger logger) {
        this.config = config;
        this.exporter = exporter;
        this.logger = logger;
    }

    @Override
    public void start(Profiler profiler) {
        final long samplingDurationMillis = config.samplingDuration.toMillis();
        final Duration uploadInterval = config.uploadInterval;
        final Runnable dumpProfile = () -> {
            Instant profilingStartTime = Instant.now();
            try {
                profiler.start();
            } catch (Throwable e) {
                logger.log(Logger.Level.ERROR, "Error starting profiler %s", e);
                stop();
                return;
            }
            try {
                Thread.sleep(samplingDurationMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            profiler.stop();
            Instant profilingEndTime = Instant.now();

            Snapshot snapshot = profiler.dumpProfile(profilingStartTime, profilingEndTime);
            exporter.export(snapshot);
        };

        Duration initialDelay = getInitialDelay();
        job = executor.scheduleAtFixedRate(
            dumpProfile,
            initialDelay.toMillis(),
            config.uploadInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    private void stop() {
        if (job != null) {
            job.cancel(true);
        }
        executor.shutdown();
    }

    private Duration getInitialDelay() {
        long uploadIntervalMillis = config.uploadInterval.toMillis();
        float randomOffset = Random.Default.nextFloat();
        uploadIntervalMillis = (long)((float)uploadIntervalMillis * randomOffset);
        if (uploadIntervalMillis < 2000) {
            uploadIntervalMillis = 2000;
        }
        Duration firstProfilingDuration = Duration.ofMillis(uploadIntervalMillis);
        return firstProfilingDuration;
    }
}

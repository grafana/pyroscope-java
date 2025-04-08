package io.pyroscope.javaagent.impl;


import io.pyroscope.javaagent.AsyncProfilerDelegate;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.ProfilerDelegate;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
import kotlin.random.Random;

import io.pyroscope.javaagent.config.Config.Builder;

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
    public void start(ProfilerDelegate profiler) {
        final long samplingDurationMillis = config.samplingDuration.toMillis();
        final Duration uploadInterval = config.uploadInterval;

        final Runnable task = (null != config.samplingEventOrder) ?
        () -> {
            for (int i = 0; i < config.samplingEventOrder.size(); i++) {
                final EventType t = config.samplingEventOrder.get(i);
                final Config tmp = isolate(t, config);
                logger.log(Logger.Level.DEBUG, "Config for %s ordinal %d: %s", t.id, i, tmp);
                profiler.reset(tmp);
                dumpProfile(profiler, samplingDurationMillis, uploadInterval);
            }
        } :
        () -> dumpProfile(profiler, samplingDurationMillis, uploadInterval);

        Duration initialDelay = getInitialDelay();
        job = executor.scheduleAtFixedRate(
            task,
            initialDelay.toMillis(),
            config.uploadInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void stop() {
        throw new RuntimeException("not implemented");
    }

    private void dumpProfile(final ProfilerDelegate profiler, final long samplingDurationMillis, final Duration uploadInterval) {
        Instant profilingStartTime = Instant.now();
        try {
            profiler.start();
        } catch (Throwable e) {
            logger.log(Logger.Level.ERROR, "Error starting profiler %s", e);
            stopProfiling();
            return;
        }
        try {
            Thread.sleep(samplingDurationMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        profiler.stop();

        Snapshot snapshot = profiler.dumpProfile(profilingStartTime, Instant.now());
        exporter.export(snapshot);
    }

    private void stopProfiling() {
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

    private Config isolate(final EventType type, final Config config) {
        final Builder b = new Builder(config);
        b.setProfilingEvent(type);
        if (!EventType.ALLOC.equals(type))
            b.setProfilingAlloc("");
        if (!EventType.LOCK.equals(type))
            b.setProfilingLock("");
        return b.build();
    }
}

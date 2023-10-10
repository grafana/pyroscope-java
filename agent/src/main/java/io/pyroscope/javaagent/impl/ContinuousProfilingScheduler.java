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


public class ContinuousProfilingScheduler implements ProfilingScheduler {
    final Config config;

    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setName("PyroscopeProfilingScheduler");
        t.setDaemon(true);
        return t;
    });
    private final Exporter exporter;
    private Logger logger;
    private Instant profilingIntervalStartTime;
    private ScheduledFuture<?> job;

    public ContinuousProfilingScheduler(Config config, Exporter exporter, Logger logger) {
        this.config = config;
        this.exporter = exporter;
        this.logger = logger;
    }

    @Override
    public void start(Profiler profiler) {
        Duration firstProfilingDuration;
        try {
            firstProfilingDuration = startFirst(profiler);
        } catch (Throwable throwable) {
            stop();
            throw new IllegalStateException(throwable);
        }
        final Runnable dumpProfile = () -> {
            Snapshot snapshot;
            Instant now;
            try {
                profiler.stop();
                now = Instant.now();
                snapshot = profiler.dumpProfile(this.profilingIntervalStartTime, now);
                profiler.start();
            } catch (Throwable throwable) {
                logger.log(Logger.Level.ERROR, "Error dumping profiler %s", throwable);
                stop();
                return;
            }
            profilingIntervalStartTime = now;
            exporter.export(snapshot);
        };

        job = executor.scheduleAtFixedRate(dumpProfile,
            firstProfilingDuration.toMillis(), config.uploadInterval.toMillis(), TimeUnit.MILLISECONDS);

    }

    /**
     * Stops the profiling scheduler which stops the AsyncProfiler.
     * @param profiler
     */
    @Override
    public void stop(Profiler profiler) {
        profiler.stop();
    }

    private void stop() {
        if (job != null) {
            job.cancel(true);
        }
        executor.shutdown();
    }

    /**
     * Starts the first profiling interval.
     * profilingIntervalStartTime is set to now
     * Duration of the first profiling interval is a random fraction of uploadInterval not smaller than 2000ms.
     *
     * @return Duration of the first profiling interval
     */
    private Duration startFirst(Profiler profiler) {
        Instant now = Instant.now();

        long uploadIntervalMillis = config.uploadInterval.toMillis();
        float randomOffset = Random.Default.nextFloat();
        uploadIntervalMillis = (long)((float)uploadIntervalMillis * randomOffset);
        if (uploadIntervalMillis < 2000) {
            uploadIntervalMillis = 2000;
        }
        Duration firstProfilingDuration = Duration.ofMillis(uploadIntervalMillis);

        profiler.start();
        profilingIntervalStartTime = now;
        return firstProfilingDuration;
    }


}

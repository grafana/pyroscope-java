package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.Profiler;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static io.pyroscope.javaagent.DateUtils.truncate;

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
            try {
                profiler.stop();
                snapshot = profiler.dumpProfile(
                    alignProfilingIntervalStartTime(this.profilingIntervalStartTime, config.uploadInterval)
                );
                profiler.start();
            } catch (Throwable throwable) {
                logger.log(Logger.Level.ERROR, "Error dumping profiler %s", throwable);
                stop();
                return;
            }
            profilingIntervalStartTime = Instant.now();
            exporter.export(snapshot);
        };

        job = executor.scheduleAtFixedRate(dumpProfile,
            firstProfilingDuration.toMillis(), config.uploadInterval.toMillis(), TimeUnit.MILLISECONDS);

    }

    private void stop() {
        if (job != null) {
            job.cancel(true);
        }
        executor.shutdown();
    }

    /**
     * Starts the first profiling interval.
     * profilingIntervalStartTime is set to a current time aligned to upload interval
     * Duration of the first profiling interval will be smaller than uploadInterval for alignment.
     * <a href="https://github.com/pyroscope-io/pyroscope-java/issues/40">...</a>
     *
     * @return Duration of the first profiling interval
     */
    private Duration startFirst(Profiler profiler) {
        Instant now = Instant.now();
        Instant prevUploadInterval = truncate(now, config.uploadInterval);
        Instant nextUploadInterval = prevUploadInterval.plus(config.uploadInterval);
        Duration firstProfilingDuration = Duration.between(now, nextUploadInterval);
        profiler.start();
        profilingIntervalStartTime = prevUploadInterval;
        return firstProfilingDuration;
    }

    /**
     * Aligns profilingIntervalStartTime to the closest aligned upload time either forward or backward
     * For example if upload interval is 10s and profilingIntervalStartTime is 00:00.01 it will return 00:00
     * and if profilingIntervalStartTime is 00:09.239 it will return 00:10
     * <a href="https://github.com/pyroscope-io/pyroscope-java/issues/40">...</a>
     *
     * @param profilingIntervalStartTime the time to align
     * @param uploadInterval
     * @return the aligned
     */
    public static Instant alignProfilingIntervalStartTime(Instant profilingIntervalStartTime, Duration uploadInterval) {
        Instant prev = truncate(profilingIntervalStartTime, uploadInterval);
        Instant next = prev.plus(uploadInterval);
        Duration d1 = Duration.between(prev, profilingIntervalStartTime);
        Duration d2 = Duration.between(profilingIntervalStartTime, next);
        if (d1.compareTo(d2) < 0) {
            return prev;
        } else {
            return next;
        }
    }
}

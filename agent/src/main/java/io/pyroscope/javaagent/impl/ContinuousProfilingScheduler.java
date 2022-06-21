package io.pyroscope.javaagent.impl;

import io.pyroscope.javaagent.OverfillQueue;
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
    private Instant profilingIntervalStartTime;


    public ContinuousProfilingScheduler(Config config, Exporter exporter) {
        this.config = config;
        this.exporter = exporter;

    }

    @Override
    public void start(Profiler profiler) {
        Duration firstProfilingDuration = startFirst(profiler);
        final Runnable dumpProfile = () -> {
            Snapshot snapshot = profiler.dump(
                alignProfilingIntervalStartTime(this.profilingIntervalStartTime, config.uploadInterval)
            );
            profilingIntervalStartTime = Instant.now();
            exporter.export(snapshot);
        };
        executor.scheduleAtFixedRate(dumpProfile,
            firstProfilingDuration.toMillis(), config.uploadInterval.toMillis(), TimeUnit.MILLISECONDS);

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
